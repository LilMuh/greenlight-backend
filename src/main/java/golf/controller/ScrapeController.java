package golf.controller;

import golf.model.dto.ScrapeSummary;
import golf.task.TeeTimeScrapeTask;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 手动触发一轮抓取（后续再挂 @Scheduled 定时跑）。 */
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
