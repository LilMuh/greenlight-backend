package golf.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import golf.error.ApiErrorCode;
import golf.error.ApiException;
import golf.model.Weekdays;
import golf.model.dto.WatchConfigBatchDto;
import golf.model.dto.WatchConfigDto;
import golf.model.entity.Course;
import golf.model.entity.WatchConfig;
import golf.repository.CourseRepository;
import golf.repository.WatchConfigRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 关注订阅的增删改查。这是后端第一处**写库**（写 watch_config，不碰 tee_time / course）。 */
@Service
public class WatchConfigService {

    private final WatchConfigRepository repository;
    private final CourseRepository courseRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WatchConfigService(
            WatchConfigRepository repository,
            CourseRepository courseRepository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.courseRepository = courseRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<WatchConfigDto> findAll() {
        return repository.findAllByOrderByIdDesc().stream().map(this::toDto).toList();
    }

    /**
     * 批量提交：一组球场 + 一份共享 config，逐个球场落一条 watch。
     *
     * (邮箱, 球场) 是一条 watch 的业务身份：同一个人在同一个球场只该有一条。
     * 所以这里是 upsert 而不是 insert——已经有的那条按新 config 改掉，没有的才新建。
     * 不这么做的话，一个人反复提交同一个表单就会攒出一堆一模一样的 watch，
     * 每来一个空位就收到 N 封内容相同的邮件。
     */
    @Transactional
    public List<WatchConfigDto> create(WatchConfigBatchDto batch) {
        String email = normalizeEmail(batch.email());
        List<WatchConfigDto> saved = new ArrayList<>();
        List<Long> savedIds = new ArrayList<>();
        for (Long courseId : batch.courseIds()) {
            WatchConfig watch = repository.findByEmailAndCourseId(email, courseId)
                    .orElseGet(WatchConfig::new);
            watch.setCourse(loadCourse(courseId));
            watch.setWeekdays(requireWeekdays(batch.weekdays()));
            watch.setTimeStart(batch.timeStart());
            watch.setTimeEnd(batch.timeEnd());
            watch.setPlayers(batch.players());
            watch.setMaxPrice(batch.maxPrice());
            watch.setEmail(email);
            watch.setActive(batch.active());
            watch.setUpdatedAt(Instant.now());
            WatchConfig persisted = repository.save(watch);
            savedIds.add(persisted.getId());
            saved.add(toDto(persisted));
        }
        // 补数（抓这批关注的日期）和基准邮件都放到事务提交后异步做，
        // 别让开浏览器抓取的几十秒卡在这个事务和 HTTP 响应里。见 WatchBootstrapListener。
        // 改出来的那些也一起发：条件变了，人期待的就是「按新条件现在有哪些」。
        eventPublisher.publishEvent(new WatchesSavedEvent(List.copyOf(savedIds)));
        return saved;
    }

    /**
     * 更新一条：改 config，用 find-then-save。
     *
     * 球场不给改：(邮箱, 球场) 是这条 watch 的身份，换球场等于换成另一条 watch，
     * 而那条可能已经存在。要看别的球场就新建一条，别把这条改成别人。
     */
    @Transactional
    public WatchConfigDto update(Long id, WatchConfigDto dto) {
        WatchConfig watch = repository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.WATCH_NOT_FOUND, "Unknown watch id: " + id));

        Long currentCourseId = watch.getCourse().getId();
        if (dto.courseId() != null && !dto.courseId().equals(currentCourseId)) {
            throw new ApiException(ApiErrorCode.WATCH_COURSE_IMMUTABLE,
                    "A watch's course cannot be changed (watch " + id + " is on course " + currentCourseId + ")");
        }

        // 换邮箱可能撞上同一个球场下已有的另一条。不挡的话唯一约束会在 flush 时炸成 500，
        // 前端拿到的是一句没头没尾的错误，看不出到底哪里填重了。
        String email = normalizeEmail(dto.email());
        repository.findByEmailAndCourseId(email, currentCourseId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ApiException(ApiErrorCode.WATCH_DUPLICATE,
                            email + " already has a watch on course " + currentCourseId);
                });

        watch.setWeekdays(requireWeekdays(dto.weekdays()));
        watch.setTimeStart(dto.timeStart());
        watch.setTimeEnd(dto.timeEnd());
        watch.setPlayers(dto.players());
        watch.setMaxPrice(dto.maxPrice());
        watch.setEmail(email);
        watch.setActive(dto.active());
        watch.setUpdatedAt(Instant.now());
        return toDto(repository.save(watch));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 邮箱统一成去空白 + 小写再落库。
     *
     * 「同一个人」得有个说法：Tom@X.com 和 tom@x.com 是同一个收件箱，
     * 不统一的话判重会漏，同一个人能给同一个球场攒出两条 watch。
     * 规范化放在写库这一侧，库里那条唯一约束才建得在裸列上（见 database 的 012）。
     */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private Course loadCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.COURSE_NOT_FOUND, "Unknown course id: " + courseId));
    }

    /**
     * 一个星期都没勾就直接拒掉。这种 watch 存下来既不会抓也不会命中，
     * 静静躺在列表里像是在生效——宁可当场报错，也别让人对着一条僵尸订阅等邮件。
     */
    private static Set<DayOfWeek> requireWeekdays(List<String> codes) {
        Set<DayOfWeek> weekdays = Weekdays.of(codes);
        if (weekdays.isEmpty()) {
            throw new ApiException(ApiErrorCode.WATCH_WEEKDAYS_REQUIRED,
                    "A watch needs at least one weekday, got: " + codes);
        }
        return weekdays;
    }

    private WatchConfigDto toDto(WatchConfig watch) {
        Course course = watch.getCourse();
        return new WatchConfigDto(
                watch.getId(),
                course.getId(),
                course.getSlug(),
                course.getName(),
                Weekdays.codes(watch.getWeekdays()),
                watch.getTimeStart(),
                watch.getTimeEnd(),
                watch.getPlayers(),
                watch.getMaxPrice(),
                watch.getEmail(),
                watch.isActive());
    }
}
