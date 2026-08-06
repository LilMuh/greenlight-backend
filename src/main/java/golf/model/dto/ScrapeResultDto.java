package golf.model.dto;

import java.util.List;

/**
 * scraper POST /scrape 的 200 摘要（抓取+写库的结果，不含时段数据本身）。
 *
 * courseInfos 是这趟顺带从 Google Maps 抓到的球场信息。scraper 不写 course 表，
 * 抓到什么原样交回来，由后端写库（见 CourseInfoService#apply）。
 * 没让刷新、或者一个都没抓到时是空列表。
 */
public record ScrapeResultDto(
        String source,
        String site,
        String date,
        int count,
        List<CourseInfoDto> courseInfos) {
}
