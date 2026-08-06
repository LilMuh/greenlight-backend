package golf.model.dto;

import java.util.List;

/**
 * 发给 scraper POST /scrape 的请求体。
 *
 * refreshCourses：顺带让 scraper 去 Google Maps 刷新这些球场的地址/评分。
 * 空表示这一趟不查 maps（scraper 据此连浏览器都不开）。谁过期了由后端算，
 * 见 CourseInfoService#findStale。
 */
public record ScrapeRequestDto(
        String source,
        String site,
        List<String> courseIds,
        String date,
        int holes,
        List<CourseRefreshDto> refreshCourses) {
}
