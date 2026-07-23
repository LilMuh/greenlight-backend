package golf.client;

import java.util.Map;
import java.util.stream.Collectors;

import golf.model.dto.CourseDto;
import org.springframework.stereotype.Component;

/**
 * 球场 slug → 展示名 的缓存。首次用时从 scraper /courses 拉一份缓存住；
 * 万一 scraper 暂时不可达，就先回退用 slug，不缓存空结果、下次再试。
 */
@Component
public class CourseCatalog {

    private final ScraperClient scraperClient;
    private volatile Map<String, String> slugToName;

    public CourseCatalog(ScraperClient scraperClient) {
        this.scraperClient = scraperClient;
    }

    public String nameOf(String slug) {
        Map<String, String> names = slugToName;
        if (names == null) {
            names = load();
        }
        return names.getOrDefault(slug, slug);
    }

    private synchronized Map<String, String> load() {
        if (slugToName != null) {
            return slugToName;
        }
        try {
            slugToName = scraperClient.getCourses().stream()
                    .collect(Collectors.toMap(CourseDto::id, CourseDto::name));
            return slugToName;
        } catch (Exception e) {
            return Map.of(); // 拿不到就先用 slug，下次再试
        }
    }
}
