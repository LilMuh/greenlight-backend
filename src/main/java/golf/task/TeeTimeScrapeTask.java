package golf.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import golf.client.ScraperClient;
import golf.model.dto.ScrapeRequestDto;
import golf.model.dto.ScrapeResultDto;
import golf.model.dto.ScrapeSummary;
import golf.service.CourseInfoService;
import golf.service.NotificationService;
import golf.service.ScrapePlanService;
import golf.service.ScrapePlanService.ScrapeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 触发抓取：按 ScrapePlanService 算出的计划，逐项 POST /scrape。
 * 只负责“喊 scraper 去抓+落库”，本身不碰 tee_time 数据。
 *
 * 抓什么完全由 watch_config 决定——没人关注的日期不抓，一条 watch 都没有就整轮跳过。
 */
@Component
public class TeeTimeScrapeTask {

    private static final Logger log = LoggerFactory.getLogger(TeeTimeScrapeTask.class);

    private static final int HOLES = 18;

    private final ScraperClient scraperClient;
    private final ScrapePlanService scrapePlanService;
    private final NotificationService notificationService;
    private final CourseInfoService courseInfoService;

    public TeeTimeScrapeTask(
            ScraperClient scraperClient,
            ScrapePlanService scrapePlanService,
            NotificationService notificationService,
            CourseInfoService courseInfoService) {
        this.scraperClient = scraperClient;
        this.scrapePlanService = scrapePlanService;
        this.notificationService = notificationService;
        this.courseInfoService = courseInfoService;
    }

    /**
     * 定时入口：默认启动即先抓一轮，之后每隔一段（上一轮结束算起）再抓。
     * 用 fixedDelay 而非 fixedRate/cron —— 一轮是多次串行抓取，耗时不定，
     * 隔到上轮结束再开下轮可避免两轮重叠、把 OAB 挤爆。间隔写成配置项，改动不必重编译。
     */
    @Scheduled(
            initialDelayString = "${greenlight.scrape.initial-delay-ms:0}",
            fixedDelayString = "${greenlight.scrape.interval-ms:1800000}")
    public void scheduledRun() {
        run();
    }

    /** 跑一轮：按计划逐项抓，抓完对比快照发通知。 */
    public ScrapeSummary run() {
        List<ScrapeJob> jobs = scrapePlanService.planForActiveWatches();
        if (jobs.isEmpty()) {
            log.info("No active watch covers a scrapable date; skipping this round");
            return new ScrapeSummary(0, 0, List.of());
        }
        log.info("Scrape plan for this round ({} jobs): {}", jobs.size(), jobs.stream()
                .map(job -> job.site() + " " + job.date() + " " + job.courseSlugs())
                .toList());

        // 抓之前先给每条 watch 拍一张「当前满足的时段」快照，作为本轮上升沿判断的基线。
        // 某个日期这轮没进计划，它的命中集前后一致、不会产生上升沿，不会误发邮件。
        Map<Long, Set<Long>> satisfiedBeforeScrape = notificationService.snapshotSatisfying();

        ScrapeSummary summary = execute(jobs);

        // 抓完对比快照，只对「本轮新变得满足」的时段发通知邮件。
        notificationService.notifyRisingEdges(satisfiedBeforeScrape);

        return summary;
    }

    /**
     * 执行一批抓取计划，单项失败不影响其余。不发通知——
     * 新建 watch 那条路径要先抓完再单独发基准邮件，不能走上升沿那套。
     *
     * synchronized：所有抓取都从这里过。定时轮和「新建 watch 立刻抓」是两个线程，
     * 同时驱动同一个 OAB profile 会打架，这里串起来，后到的等前一批抓完。
     */
    public synchronized ScrapeSummary execute(List<ScrapeJob> jobs) {
        int partitions = 0;
        int saved = 0;
        List<String> errors = new ArrayList<>();

        for (ScrapeJob job : jobs) {
            try {
                // 顺带把过期的球场信息刷一遍。名单由后端算、结果由后端写，scraper 只负责抓。
                // 第一个 job 写回后 updated_at 就变新了，后面几个 job 查出来是空的——天然只跑一次。
                ScrapeRequestDto request = new ScrapeRequestDto(
                        job.source(), job.site(), job.courseSlugs(), job.date().toString(), HOLES,
                        courseInfoService.findStale(job.courseSlugs()));
                ScrapeResultDto result = scraperClient.scrape(request);
                saved += result.count();
                partitions++;
                applyCourseInfo(result);
            } catch (Exception e) {
                String reason = job.site() + " " + job.date() + ": " + e.getMessage();
                errors.add(reason);
                log.warn("Scrape request failed {}", reason);
            }
        }

        log.info("Scrape round finished: planned={} partition={} saved={} error={}",
                jobs.size(), partitions, saved, errors.size());
        return new ScrapeSummary(partitions, saved, errors);
    }

    /**
     * 写回球场的地址/评分。单独 catch 而不是并进上面那个 try——
     * 时段已经落库了，球场信息只是装饰，写它失败不该把这个 job 记成抓取失败。
     * 跳过的这次不写 updated_at，下一轮还会再来一遍。
     */
    private void applyCourseInfo(ScrapeResultDto result) {
        try {
            courseInfoService.apply(result.courseInfos());
        } catch (Exception e) {
            log.warn("Course info write failed, tee times were saved fine: {}", e.getMessage());
        }
    }
}
