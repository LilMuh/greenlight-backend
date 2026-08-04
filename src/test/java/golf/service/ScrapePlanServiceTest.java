package golf.service;

import java.time.LocalDate;
import java.util.List;

import golf.model.entity.Course;
import golf.model.entity.WatchConfig;
import golf.service.ScrapePlanService.ScrapeJob;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 抓取计划的推导：关注区间怎么变成「该抓哪些 (站点, 日期, 球场)」。 */
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

    private static WatchConfig watch(String slug, LocalDate from, LocalDate to) {
        WatchConfig watch = new WatchConfig();
        watch.setCourse(course(slug));
        watch.setDateStart(from);
        watch.setDateEnd(to);
        return watch;
    }

    @Test
    void 没有关注时不产生任何抓取() {
        assertThat(service.planFor(List.of())).isEmpty();
    }

    @Test
    void 只关注一天就只抓那一天() {
        LocalDate target = TODAY.plusDays(5);

        List<ScrapeJob> jobs = service.planFor(List.of(watch("fraserview", target, target)));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.date()).isEqualTo(target);
            assertThat(job.site()).isEqualTo("golfvancouver");
            assertThat(job.courseSlugs()).containsExactly("fraserview");
        });
    }

    @Test
    void 同一天的多个球场并成一次抓取() {
        LocalDate target = TODAY.plusDays(2);

        List<ScrapeJob> jobs = service.planFor(List.of(
                watch("fraserview", target, target),
                watch("langara", target, target),
                watch("mccleery", target, target)));

        assertThat(jobs).singleElement()
                .satisfies(job -> assertThat(job.courseSlugs())
                        .containsExactlyInAnyOrder("fraserview", "langara", "mccleery"));
    }

    @Test
    void 已经过去的日期被裁掉() {
        List<ScrapeJob> jobs = service.planFor(
                List.of(watch("fraserview", TODAY.minusDays(10), TODAY.plusDays(1))));

        assertThat(jobs).extracting(ScrapeJob::date)
                .containsExactly(TODAY, TODAY.plusDays(1));
    }

    @Test
    void 整段都在过去的关注不抓() {
        List<ScrapeJob> jobs = service.planFor(
                List.of(watch("fraserview", TODAY.minusDays(10), TODAY.minusDays(3))));

        assertThat(jobs).isEmpty();
    }

    @Test
    void 关注区间太远时截到上限() {
        List<ScrapeJob> jobs = service.planFor(
                List.of(watch("fraserview", TODAY, TODAY.plusYears(1))));

        // 今天 + 后 7 天，共 8 天
        assertThat(jobs).hasSize(ScrapePlanService.MAX_DAYS_AHEAD + 1);
        assertThat(jobs.get(jobs.size() - 1).date())
                .isEqualTo(TODAY.plusDays(ScrapePlanService.MAX_DAYS_AHEAD));
    }

    @Test
    void 多条关注重叠的日期不会重复抓() {
        LocalDate target = TODAY.plusDays(3);

        List<ScrapeJob> jobs = service.planFor(List.of(
                watch("fraserview", TODAY, target),
                watch("fraserview", TODAY.plusDays(1), target)));

        assertThat(jobs).extracting(ScrapeJob::date).doesNotHaveDuplicates();
        assertThat(jobs).allSatisfy(job -> assertThat(job.courseSlugs()).containsExactly("fraserview"));
    }
}
