package golf.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import golf.model.entity.Course;
import golf.model.entity.WatchConfig;
import golf.service.ScrapePlanService.ScrapeJob;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 抓取计划的推导：关注的星期怎么变成「该抓哪些 (站点, 日期, 球场)」。 */
class ScrapePlanServiceTest {

    private final ScrapePlanService service = new ScrapePlanService(null);

    private static final LocalDate TODAY = LocalDate.now();

    /** course 表归 Liquibase，实体只有 getter，测试里用反射造。 */
    private static Course course(String slug) {
        Course course = new Course();
        ReflectionTestUtils.setField(course, "slug", slug);
        ReflectionTestUtils.setField(course, "source", "cps");
        ReflectionTestUtils.setField(course, "site", "golfvancouver");
        return course;
    }

    private static WatchConfig watch(String slug, DayOfWeek... weekdays) {
        WatchConfig watch = new WatchConfig();
        watch.setCourse(course(slug));
        watch.setWeekdays(weekdays.length == 0
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(List.of(weekdays)));
        return watch;
    }

    /** 窗口里第 n 次出现某个星期的那一天。断言不写死日期，跨周也成立。 */
    private static LocalDate nextDate(DayOfWeek weekday) {
        for (int offset = 0; offset <= WatchWindow.MAX_DAYS_AHEAD; offset++) {
            if (TODAY.plusDays(offset).getDayOfWeek() == weekday) {
                return TODAY.plusDays(offset);
            }
        }
        throw new IllegalStateException("8 天窗口里必然覆盖每个星期");
    }

    @Test
    void 没有关注时不产生任何抓取() {
        assertThat(service.planFor(List.of())).isEmpty();
    }

    @Test
    void 只关注一个星期就只抓那几天() {
        List<ScrapeJob> jobs = service.planFor(List.of(watch("fraserview", DayOfWeek.SATURDAY)));

        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.date().getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
            assertThat(job.site()).isEqualTo("golfvancouver");
            assertThat(job.courseSlugs()).containsExactly("fraserview");
        });
        assertThat(jobs).extracting(ScrapeJob::date).contains(nextDate(DayOfWeek.SATURDAY));
    }

    @Test
    void 同一天的多个球场并成一次抓取() {
        DayOfWeek weekday = TODAY.plusDays(2).getDayOfWeek();

        List<ScrapeJob> jobs = service.planFor(List.of(
                watch("fraserview", weekday),
                watch("langara", weekday),
                watch("mccleery", weekday)));

        assertThat(jobs).allSatisfy(job -> assertThat(job.courseSlugs())
                .containsExactlyInAnyOrder("fraserview", "langara", "mccleery"));
    }

    @Test
    void 一个星期都没勾的关注不抓() {
        assertThat(service.planFor(List.of(watch("fraserview")))).isEmpty();
    }

    @Test
    void 抓取窗口只有今天起八天() {
        // 七天全勾 —— 窗口里每一天都进计划，一天不多一天不少
        List<ScrapeJob> jobs = service.planFor(
                List.of(watch("fraserview", DayOfWeek.values())));

        assertThat(jobs).hasSize(WatchWindow.MAX_DAYS_AHEAD + 1);
        assertThat(jobs).extracting(ScrapeJob::date)
                .startsWith(TODAY)
                .endsWith(TODAY.plusDays(WatchWindow.MAX_DAYS_AHEAD));
    }

    @Test
    void 同一个星期出现两次时两天都抓() {
        // 8 天窗口跨了 8 个日期，必然有某个星期出现两次（今天和今天+7）
        DayOfWeek twice = TODAY.getDayOfWeek();

        List<ScrapeJob> jobs = service.planFor(List.of(watch("fraserview", twice)));

        assertThat(jobs).extracting(ScrapeJob::date)
                .containsExactly(TODAY, TODAY.plusDays(WatchWindow.MAX_DAYS_AHEAD));
    }

    @Test
    void 多条关注重叠的星期不会重复抓() {
        Set<DayOfWeek> both = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

        List<ScrapeJob> jobs = service.planFor(List.of(
                watch("fraserview", DayOfWeek.SATURDAY),
                watch("fraserview", both.toArray(DayOfWeek[]::new))));

        assertThat(jobs).extracting(ScrapeJob::date).doesNotHaveDuplicates();
        assertThat(jobs).allSatisfy(job -> assertThat(job.courseSlugs()).containsExactly("fraserview"));
    }
}
