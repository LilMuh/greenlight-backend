package golf.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import golf.client.ScraperClient;
import golf.model.dto.ScrapeRequestDto;
import golf.model.dto.ScrapeResultDto;
import golf.model.dto.ScrapeSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final int DAYS_AHEAD = 14;

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

    public TeeTimeScrapeTask(ScraperClient scraperClient) {
        this.scraperClient = scraperClient;
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
        return new ScrapeSummary(partitions, saved, errors);
    }
}
