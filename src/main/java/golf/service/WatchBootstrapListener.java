package golf.service;

import java.util.List;

import golf.model.entity.WatchConfig;
import golf.repository.WatchConfigRepository;
import golf.service.ScrapePlanService.ScrapeJob;
import golf.task.TeeTimeScrapeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * watch 落库后的补数流程：抓一遍它关注的日期，再发基准邮件。
 * 新建和「重复提交改掉现有那条」走的是同一条路，见 WatchesSavedEvent。
 *
 * 抓取现在是 watch 驱动的，watch 关注的日期很可能库里根本没数据——
 * 直接算基准邮件只会得出「当前无满足时段」。所以先抓后发。
 *
 * AFTER_COMMIT + @Async 的原因：一次抓取要开浏览器过盾，几十秒起步。
 * 放在创建事务里会长时间占着数据库连接，也会让 POST /api/watch-configs 卡住不返回。
 * 提交后异步做，接口立刻返回，抓取和邮件在后台跑完。
 */
@Component
public class WatchBootstrapListener {

    private static final Logger log = LoggerFactory.getLogger(WatchBootstrapListener.class);

    private final WatchConfigRepository watchConfigRepository;
    private final ScrapePlanService scrapePlanService;
    private final TeeTimeScrapeTask scrapeTask;
    private final NotificationService notificationService;

    public WatchBootstrapListener(
            WatchConfigRepository watchConfigRepository,
            ScrapePlanService scrapePlanService,
            TeeTimeScrapeTask scrapeTask,
            NotificationService notificationService) {
        this.watchConfigRepository = watchConfigRepository;
        this.scrapePlanService = scrapePlanService;
        this.scrapeTask = scrapeTask;
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWatchesSaved(WatchesSavedEvent event) {
        List<WatchConfig> watches = watchConfigRepository.findAllById(event.watchIds());
        if (watches.isEmpty()) {
            return;
        }

        List<ScrapeJob> jobs = scrapePlanService.planFor(watches);
        if (!jobs.isEmpty()) {
            log.info("{} watch(es) saved: running {} scrape job(s) before the baseline mail",
                    watches.size(), jobs.size());
            scrapeTask.execute(jobs);
        }

        for (WatchConfig watch : watches) {
            notificationService.sendBaseline(watch);
        }
    }
}
