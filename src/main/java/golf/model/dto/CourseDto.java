package golf.model.dto;

/** scraper GET /courses 返回的球场条目。 */
public record CourseDto(String id, String name, String source, String site) {
}
