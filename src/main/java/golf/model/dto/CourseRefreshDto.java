package golf.model.dto;

/**
 * 发给 scraper 的「顺带去 Google Maps 查一下这个球场」请求项。
 *
 * 谁该刷新由后端判断（course 表归后端写），scraper 只管抓。
 * mapsUrl 是上次解析到的地点链接，带上它 scraper 就直接导航过去、不再走搜索——
 * 既省一跳，也不会某次静默匹配到隔壁的商户。第一次为 null。
 */
public record CourseRefreshDto(String slug, String name, String mapsUrl) {
}
