package golf.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import golf.client.ScraperClient;
import golf.model.dto.MatchResultDto;
import golf.model.dto.ScrapeRequestDto;
import golf.model.dto.ScrapeResultDto;
import golf.model.dto.ScrapeSummary;
import golf.service.WatchMatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 触发抓取：对每个球场目标、未来若干天各发一次 POST /scrape。
 * 只负责“喊 scraper 去抓+落库”，本身不碰 tee_time 数据。
 */
@Component
public class TeeTimeScrapeTask {

    private static final Logger log = LoggerFactory.getLogger(TeeTimeScrapeTask.class);

    private static final int PLAYERS = 4;
    private static final int HOLES = 18;
    private static final int DAYS_AHEAD = 7; // 今天 + 后 7 天（含今天共 8 天，offset 0..7）

    /** 要抓的球场组：一个 site 下的一批 course slug。 */
    enum Target {
        GOLFVANCOUVER("cps", "golfvancouver", List.of("fraserview", "langara", "mccleery"));

        final String source;
        final String site;
        final List<String> courses;

        Target(String source, String site, List<String> courses) {
            this.source = source;
            this.site = site;
            this.courses = courses;
        }
    }

    private final ScraperClient scraperClient;
    private final WatchMatchService watchMatchService;

    public TeeTimeScrapeTask(ScraperClient scraperClient, WatchMatchService watchMatchService) {
        this.scraperClient = scraperClient;
        this.watchMatchService = watchMatchService;
    }

    /**
     * 定时入口：默认启动即先抓一轮，之后每隔一段（上一轮结束算起）再抓。
     * 用 fixedDelay 而非 fixedRate/cron —— 一轮是多次串行抓取，耗时不定，
     * 隔到上轮结束再开下轮可避免两轮重叠、把 OAB 挤爆。间隔写成配置项，改动不必重编译。
     */
    @Scheduled(
            initialDelayString = "${greenlight.scrape.initial-delay-ms:0}",
            fixedDelayString = "${greenlight.scrape.interval-ms:3600000}")
    public void scheduledRun() {
        run();
    }

    /** 跑一轮：目标 × 日期，逐个触发抓取，单个失败不影响其余。 */
    public ScrapeSummary run() {
        int partitions = 0;
        int saved = 0;
        List<String> errors = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Target target : Target.values()) {
            for (int dayOffset = 0; dayOffset <= DAYS_AHEAD; dayOffset++) {
                LocalDate date = today.plusDays(dayOffset);
                try {
                    ScrapeRequestDto request = new ScrapeRequestDto(
                            target.source, target.site, target.courses, date.toString(), HOLES, PLAYERS);
                    ScrapeResultDto result = scraperClient.scrape(request);
                    saved += result.count();
                    partitions++;
                } catch (Exception e) {
                    String reason = target.site + " " + date + ": " + e.getMessage();
                    errors.add(reason);
                    log.warn("触发抓取失败 {}", reason);
                }
            }
        }

        log.info("抓取触发完成：partition={} saved={} error={}", partitions, saved, errors.size());

        // 每轮抓完顺带算一遍匹配，日志汇报命中情况；发通知留给后续 M3。
        logMatches();

        return new ScrapeSummary(partitions, saved, errors);
    }

    /** 算一遍所有启用中 watch 的命中，把结果打进日志（当前只观察，不发邮件）。 */
    private void logMatches() {
        List<MatchResultDto> matches = watchMatchService.findAllMatches();
        int totalHits = matches.stream().mapToInt(MatchResultDto::hitCount).sum();
        log.info("匹配算完：watch={} 命中总数={}", matches.size(), totalHits);
        for (MatchResultDto match : matches) {
            log.info("  watch#{} {} 命中 {} 个空位", match.watchId(), match.courseName(), match.hitCount());
        }
    }
}
