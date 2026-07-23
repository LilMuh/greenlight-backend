package golf.model.dto;

import java.math.BigDecimal;

/** 返回给前端展示的一条时段。 */
public record TeeTimeDto(
        String courseId,
        String course, // 球场展示名（slug 经 CourseCatalog 映射）
        String date,
        String time,
        int holes,
        int players,
        BigDecimal price,
        boolean available) {
}
