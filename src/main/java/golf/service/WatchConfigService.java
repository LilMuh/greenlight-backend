package golf.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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

    /** 批量创建：一组球场 + 一份共享 config，逐个球场建一条 watch。 */
    @Transactional
    public List<WatchConfigDto> create(WatchConfigBatchDto batch) {
        requireTimeWindow(batch.timeStart(), batch.timeEnd());
        List<WatchConfigDto> created = new ArrayList<>();
        List<Long> createdIds = new ArrayList<>();
        for (Long courseId : batch.courseIds()) {
            WatchConfig watch = new WatchConfig();
            watch.setCourse(loadCourse(courseId));
            watch.setWeekdays(requireWeekdays(batch.weekdays()));
            watch.setTimeStart(batch.timeStart());
            watch.setTimeEnd(batch.timeEnd());
            watch.setPlayers(batch.players());
            watch.setMaxPrice(batch.maxPrice());
            watch.setEmail(batch.email());
            watch.setActive(batch.active());
            watch.setUpdatedAt(Instant.now());
            WatchConfig saved = repository.save(watch);
            createdIds.add(saved.getId());
            created.add(toDto(saved));
        }
        // 补数（抓这批关注的日期）和基准邮件都放到事务提交后异步做，
        // 别让开浏览器抓取的几十秒卡在这个事务和 HTTP 响应里。见 WatchBootstrapListener。
        eventPublisher.publishEvent(new WatchesCreatedEvent(List.copyOf(createdIds)));
        return created;
    }

    /** 更新一条：改 config（可含换球场），用 find-then-save。 */
    @Transactional
    public WatchConfigDto update(Long id, WatchConfigDto dto) {
        requireTimeWindow(dto.timeStart(), dto.timeEnd());
        WatchConfig watch = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown watch id: " + id));
        watch.setCourse(loadCourse(dto.courseId()));
        watch.setWeekdays(requireWeekdays(dto.weekdays()));
        watch.setTimeStart(dto.timeStart());
        watch.setTimeEnd(dto.timeEnd());
        watch.setPlayers(dto.players());
        watch.setMaxPrice(dto.maxPrice());
        watch.setEmail(dto.email());
        watch.setActive(dto.active());
        watch.setUpdatedAt(Instant.now());
        return toDto(repository.save(watch));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private Course loadCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown course id: " + courseId));
    }

    /**
     * 一个星期都没勾就直接拒掉。这种 watch 存下来既不会抓也不会命中，
     * 静静躺在列表里像是在生效——宁可当场报错，也别让人对着一条僵尸订阅等邮件。
     */
    /** 24 小时制的 "HH:MM"，零填充。和 tee_time.time_local 同格式，好直接比较。 */
    private static final Pattern CLOCK = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /**
     * 时间窗必须格式合法，且结束严格晚于开始。
     *
     * 倒挂的窗口（结束早于开始）在 WatchMatchService 那句 BETWEEN 里恒为空集：
     * 不报错、不告警，这条 watch 就永远不命中、永远不发邮件，建了等于没建。
     * 两个值都零填充，所以字符串比较就是时间先后，和 SQL 里的口径一致。
     */
    private static void requireTimeWindow(String timeStart, String timeEnd) {
        requireClock(timeStart);
        requireClock(timeEnd);
        if (timeEnd.compareTo(timeStart) <= 0) {
            throw new IllegalArgumentException(
                    "A watch needs an end later than its start, got: " + timeStart + "–" + timeEnd);
        }
    }

    private static void requireClock(String value) {
        if (value == null || !CLOCK.matcher(value).matches()) {
            throw new IllegalArgumentException("Time must be 24-hour HH:MM, got: " + value);
        }
    }

    private static Set<DayOfWeek> requireWeekdays(List<String> codes) {
        Set<DayOfWeek> weekdays = Weekdays.of(codes);
        if (weekdays.isEmpty()) {
            throw new IllegalArgumentException("A watch needs at least one weekday, got: " + codes);
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
