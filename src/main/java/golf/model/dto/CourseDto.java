package golf.model.dto;

/** 返回给前端的球场条目（来自 course 表）。 */
public record CourseDto(Long id, String slug, String name, String source, String site, String imageUrl) {
}
