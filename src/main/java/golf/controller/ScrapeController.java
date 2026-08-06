package golf.controller;

import golf.model.dto.ScrapeSummary;
import golf.task.TeeTimeScrapeTask;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手动触发一轮抓取，和 TeeTimeScrapeTask 的 @Scheduled 走同一个 run()。
 * 定时轮已经在跑（默认 30 分钟一轮），这个口子是给调试用的——不想等下一轮时打一下。
 */
@RestController
@RequestMapping("/api")
public class ScrapeController {

    private final TeeTimeScrapeTask scrapeTask;

    public ScrapeController(TeeTimeScrapeTask scrapeTask) {
        this.scrapeTask = scrapeTask;
    }

    @PostMapping("/scrape")
    public ScrapeSummary scrape() {
        return scrapeTask.run();
    }
}
