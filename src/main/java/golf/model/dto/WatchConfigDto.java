package golf.model.dto;

import java.util.List;

/**
 * 一条关注订阅（读取 / 更新用）。一条 watch 只关注一个球场。
 * courseSlug / courseName 由后端从 course 关系带出，方便前端直接展示。
 *
 * weekdays 是三字母缩写的列表，如 ["SAT","SUN"]，按 ISO 序（周一在前）。
 * 关注的是星期而不是日期区间：抓哪几天由后端按今天往后推算。
 */
public record WatchConfigDto(
        Long id,
        Long courseId,
        String courseSlug,
        String courseName,
        List<String> weekdays,
        String timeStart,
        String timeEnd,
        int players,
        int maxPrice,
        String email,
        boolean active) {
}
