package golf.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import golf.model.entity.Course;
import golf.model.entity.WatchConfig;
import golf.repository.WatchConfigRepository;
import org.springframework.stereotype.Service;

/**
 * 抓取计划：算出这一轮到底该抓哪些 (站点, 日期, 球场)。
 *
 * 不再按「固定球场 × 未来 8 天」的笛卡尔积盲抓，而是完全由 watch_config 驱动——
 * 只有被人关注到的日期才值得抓。一个都没有就返回空清单，整轮跳过，一次浏览器都不开。
 *
 * watch 记的是星期而不是日期，日期由 WatchWindow 每轮现算：只关注周六的 watch，
 * 一周里就只有一两天进计划，其余六天连请求都不发。
 *
 * 同一个 (source, site, 日期) 下的多个球场合并成一项：上游 courseIds 是多值参数，
 * 三个球场和一个球场都只是一次请求。球场的 source/site 直接从 course 表读，
 * 不再有写死的球场清单。
 */
@Service
public class ScrapePlanService {

    /** 一次抓取调用：某站点某天要抓的一组球场。 */
    public record ScrapeJob(String source, String site, LocalDate date, List<String> courseSlugs) {}

    /** 分组键：同 source+site+日期的球场并成一次调用。 */
    private record GroupKey(String source, String site, LocalDate date) {}

    private final WatchConfigRepository watchConfigRepository;

    public ScrapePlanService(WatchConfigRepository watchConfigRepository) {
        this.watchConfigRepository = watchConfigRepository;
    }

    /** 定时轮用：所有启用中的 watch 合起来需要抓的东西。 */
    public List<ScrapeJob> planForActiveWatches() {
        return planFor(watchConfigRepository.findByActiveTrue());
    }

    /**
     * 新建 watch 时用：只抓这批 watch 关注的日期，好让基准邮件基于新鲜数据算。
     * 整批一起算，同一天的多个球场照样并成一次调用。
     */
    public List<ScrapeJob> planFor(Collection<WatchConfig> watches) {
        // 今天只取一次：跨午夜的那一瞬间，同一轮里的 watch 也得站在同一个「今天」上
        LocalDate today = LocalDate.now();

        Map<GroupKey, Set<String>> coursesByGroup = new LinkedHashMap<>();
        for (WatchConfig watch : watches) {
            Course course = watch.getCourse();
            for (LocalDate date : WatchWindow.upcomingDates(watch, today)) {
                coursesByGroup
                        .computeIfAbsent(new GroupKey(course.getSource(), course.getSite(), date),
                                key -> new LinkedHashSet<>())
                        .add(course.getSlug());
            }
        }

        List<ScrapeJob> jobs = new ArrayList<>();
        coursesByGroup.forEach((key, slugs) ->
                jobs.add(new ScrapeJob(key.source(), key.site(), key.date(), List.copyOf(slugs))));
        // 按日期排序，让日志和抓取顺序稳定可读
        jobs.sort(Comparator.comparing(ScrapeJob::date).thenComparing(ScrapeJob::site));
        return jobs;
    }
}
