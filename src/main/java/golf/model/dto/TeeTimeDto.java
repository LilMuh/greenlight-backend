package golf.model.dto;

import java.math.BigDecimal;

/** 返回给前端展示的一条时段。 */
public record TeeTimeDto(
        String courseId, // 球场 slug（取自 course 关系）
        String course, // 球场展示名（取自 course 关系）
        String date,
        String time,
        int holes,
        int players,
        BigDecimal price,
        boolean available) {
}
