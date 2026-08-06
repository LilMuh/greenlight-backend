package golf.model.dto;

import java.math.BigDecimal;

/**
 * 返回给前端的球场条目（来自 course 表）。
 *
 * address / rating / ratingCount 来自 Google Maps，**可空且经常为空**——
 * 前端对每一项都要能降级（拿不到就不显示那块，别显示 0 或空括号）。
 * mapsUrl 不透出去：它只是后端下次刷新时的定位用，前端用不上。
 */
public record CourseDto(
        Long id,
        String slug,
        String name,
        String source,
        String site,
        String imageUrl,
        String address,
        BigDecimal rating,
        Integer ratingCount) {
}
