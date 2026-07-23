package golf.model.dto;

/** scraper POST /scrape 的 200 摘要（抓取+写库的结果，不含数据本身）。 */
public record ScrapeResultDto(String source, String site, String date, int count) {
}
