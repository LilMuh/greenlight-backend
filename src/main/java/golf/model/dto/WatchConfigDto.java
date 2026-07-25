package golf.model.dto;

import java.time.LocalDate;

/**
 * 一条关注订阅（读取 / 更新用）。一条 watch 只关注一个球场。
 * courseSlug / courseName 由后端从 course 关系带出，方便前端直接展示。
 */
public record WatchConfigDto(
        Long id,
        Long courseId,
        String courseSlug,
        String courseName,
        LocalDate dateStart,
        LocalDate dateEnd,
        String timeStart,
        String timeEnd,
        int players,
        int maxPrice,
        String email,
        boolean active) {
}
