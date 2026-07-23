package golf.controller;

import java.util.List;

import golf.client.ScraperClient;
import golf.model.dto.CourseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 球场清单：透传 scraper 的 /courses，给前端下拉框用。 */
@RestController
@RequestMapping("/api")
public class CourseController {

    private final ScraperClient scraperClient;

    public CourseController(ScraperClient scraperClient) {
        this.scraperClient = scraperClient;
    }

    @GetMapping("/courses")
    public List<CourseDto> courses() {
        return scraperClient.getCourses();
    }
}
