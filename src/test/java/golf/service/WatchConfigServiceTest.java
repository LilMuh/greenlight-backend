package golf.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import golf.error.ApiErrorCode;
import golf.error.ApiException;
import golf.model.dto.WatchConfigBatchDto;
import golf.model.dto.WatchConfigDto;
import golf.model.entity.Course;
import golf.model.entity.WatchConfig;
import golf.repository.CourseRepository;
import golf.repository.WatchConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * watch 的写入规则。这里真正想守住的是一条业务不变式：
 * **(邮箱, 球场) 唯一** —— 同一个人在同一个球场只该有一条 watch。
 *
 * 不变式一旦破了，症状是「同一个空位收到好几封一模一样的邮件」，
 * 而且从界面上看只是列表里多了几行长得一样的记录，不容易联想到是重复提交造成的。
 *
 * 两个仓库接口用 Mockito 顶着（JpaRepository 继承了二十来个方法，手写假对象不划算），
 * 但背后接的是一个真的 Map：save/find 之间要能互相看见，upsert 那条路才测得出来。
 */
class WatchConfigServiceTest {

    private static final String EMAIL = "tom@example.com";
    private static final Long FRASERVIEW = 1L;
    private static final Long LANGARA = 2L;

    /** 假库：id 自增，save 之后 find 立刻看得见。 */
    private final Map<Long, WatchConfig> rows = new LinkedHashMap<>();
    private final List<WatchesSavedEvent> events = new ArrayList<>();
    private long nextId = 100;

    private WatchConfigService service;

    @BeforeEach
    void setUp() {
        rows.clear();
        events.clear();

        WatchConfigRepository repository = mock(WatchConfigRepository.class);
        when(repository.save(any(WatchConfig.class))).thenAnswer(call -> {
            WatchConfig watch = call.getArgument(0);
            if (watch.getId() == null) {
                watch.setId(nextId++);
            }
            rows.put(watch.getId(), watch);
            return watch;
        });
        when(repository.findById(anyLong()))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.<Long>getArgument(0))));
        when(repository.existsById(anyLong())).thenAnswer(call -> rows.containsKey(call.<Long>getArgument(0)));
        doAnswer(call -> rows.remove(call.<Long>getArgument(0))).when(repository).deleteById(anyLong());
        when(repository.findByEmailAndCourseId(any(), any())).thenAnswer(call -> {
            String email = call.getArgument(0);
            Long courseId = call.getArgument(1);
            return rows.values().stream()
                    .filter(watch -> watch.getEmail().equals(email))
                    .filter(watch -> watch.getCourse().getId().equals(courseId))
                    .findFirst();
        });

        CourseRepository courseRepository = mock(CourseRepository.class);
        when(courseRepository.findById(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return id.equals(FRASERVIEW) || id.equals(LANGARA)
                    ? Optional.of(course(id))
                    : Optional.empty();
        });

        ApplicationEventPublisher publisher = event -> {
            if (event instanceof WatchesSavedEvent saved) {
                events.add(saved);
            }
        };

        service = new WatchConfigService(repository, courseRepository, publisher);
    }

    @Test
    void 每个球场各落一条() {
        List<WatchConfigDto> saved = service.create(batch(EMAIL, List.of(FRASERVIEW, LANGARA), "06:00"));

        assertThat(saved).hasSize(2);
        assertThat(rows).hasSize(2);
        assertThat(saved).extracting(WatchConfigDto::courseId).containsExactly(FRASERVIEW, LANGARA);
    }

    @Test
    void 同一个邮箱同一个球场再提交时改的是现有那条() {
        Long firstId = service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00")).get(0).id();

        List<WatchConfigDto> again = service.create(batch(EMAIL, List.of(FRASERVIEW), "09:30"));

        // 没有新增行，返回的还是原来那条，内容换成了这次提交的
        assertThat(rows).hasSize(1);
        assertThat(again).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(firstId);
            assertThat(dto.timeStart()).isEqualTo("09:30");
        });
    }

    @Test
    void 邮箱的大小写和首尾空白不影响判重() {
        service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00"));

        service.create(batch("  ToM@Example.COM  ", List.of(FRASERVIEW), "09:30"));

        // 同一个收件箱，还是那一条；库里存的是规范化之后的小写
        assertThat(rows).hasSize(1);
        assertThat(rows.values()).singleElement()
                .satisfies(watch -> assertThat(watch.getEmail()).isEqualTo(EMAIL));
    }

    @Test
    void 不同邮箱在同一个球场各自成一条() {
        service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00"));
        service.create(batch("someone.else@example.com", List.of(FRASERVIEW), "06:00"));

        assertThat(rows).hasSize(2);
    }

    @Test
    void 改到现有那条也照样触发补抓和基准邮件() {
        Long firstId = service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00")).get(0).id();
        events.clear();

        service.create(batch(EMAIL, List.of(FRASERVIEW), "09:30"));

        // 条件变了，人期待的就是「按新条件现在有哪些」——事件照发，带的是被改的那条
        assertThat(events).singleElement()
                .satisfies(event -> assertThat(event.watchIds()).containsExactly(firstId));
    }

    @Test
    void 一个星期都没勾直接拒掉() {
        WatchConfigBatchDto noWeekdays = new WatchConfigBatchDto(
                List.of(FRASERVIEW), List.of(), "06:00", "10:00", 4, 90, EMAIL, true);

        assertThatThrownBy(() -> service.create(noWeekdays))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ApiErrorCode.WATCH_WEEKDAYS_REQUIRED);

        assertThat(rows).isEmpty();
    }

    @Test
    void 未知球场id报COURSE_NOT_FOUND() {
        assertThatThrownBy(() -> service.create(batch(EMAIL, List.of(999L), "06:00")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ApiErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void 改一条不存在的watch报WATCH_NOT_FOUND() {
        assertThatThrownBy(() -> service.update(404L, dto(404L, FRASERVIEW, EMAIL)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ApiErrorCode.WATCH_NOT_FOUND);
    }

    @Test
    void 改一条watch时不给换球场() {
        Long id = service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00")).get(0).id();

        assertThatThrownBy(() -> service.update(id, dto(id, LANGARA, EMAIL)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ApiErrorCode.WATCH_COURSE_IMMUTABLE);

        // 拒了就是拒了，库里那条一点没动
        assertThat(rows.get(id).getCourse().getId()).isEqualTo(FRASERVIEW);
    }

    @Test
    void 改邮箱撞上同球场已有的那条时报WATCH_DUPLICATE() {
        Long mine = service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00")).get(0).id();
        service.create(batch("other@example.com", List.of(FRASERVIEW), "06:00"));

        assertThatThrownBy(() -> service.update(mine, dto(mine, FRASERVIEW, "other@example.com")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ApiErrorCode.WATCH_DUPLICATE);

        assertThat(rows.get(mine).getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void 删一条不存在的watch报WATCH_NOT_FOUND() {
        // deleteById 对着不存在的 id 是静默 no-op，于是「另一个标签页已经删过了」和
        // 「真的删掉了」返回一模一样的 204，界面上那条已经没了的记录会安静地留着
        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ApiErrorCode.WATCH_NOT_FOUND);
    }

    @Test
    void 删存在的watch就真删掉() {
        Long id = service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00")).get(0).id();

        service.delete(id);

        assertThat(rows).isEmpty();
    }

    @Test
    void 改一条watch时保持原邮箱不算撞自己() {
        Long id = service.create(batch(EMAIL, List.of(FRASERVIEW), "06:00")).get(0).id();

        WatchConfigDto updated = service.update(id, dto(id, FRASERVIEW, EMAIL));

        assertThat(updated.id()).isEqualTo(id);
        assertThat(rows).hasSize(1);
    }

    private static Course course(Long id) {
        Course course = new Course();
        ReflectionTestUtils.setField(course, "id", id);
        ReflectionTestUtils.setField(course, "slug", id.equals(FRASERVIEW) ? "fraserview" : "langara");
        ReflectionTestUtils.setField(course, "name", id.equals(FRASERVIEW) ? "Fraserview" : "Langara");
        ReflectionTestUtils.setField(course, "source", "cps");
        ReflectionTestUtils.setField(course, "site", "golfvancouver");
        return course;
    }

    private static WatchConfigBatchDto batch(String email, List<Long> courseIds, String timeStart) {
        return new WatchConfigBatchDto(
                courseIds, List.of("SAT", "SUN"), timeStart, "16:00", 4, 90, email, true);
    }

    private static WatchConfigDto dto(Long id, Long courseId, String email) {
        return new WatchConfigDto(
                id, courseId, null, null, List.of("SAT"), "06:00", "10:00", 4, 90, email, true);
    }
}
