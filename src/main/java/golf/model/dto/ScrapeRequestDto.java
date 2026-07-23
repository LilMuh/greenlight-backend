package golf.model.dto;

import java.util.List;

/** 发给 scraper POST /scrape 的请求体。 */
public record ScrapeRequestDto(
        String source,
        String site,
        List<String> courseIds,
        String date,
        int holes,
        int players) {
}
