package golf.model.dto;

import java.math.BigDecimal;

/**
 * scraper 从 Google Maps 抓回来的一个球场的信息，由后端写进 course 表。
 *
 * 每个字段独立可空，而且**经常会是 null**：数据靠解析 Google Maps 的页面拿，
 * Google 改版式就取不到。scraper 那边取不到就填 null、绝不填猜的值，
 * 所以这里的 null 一律理解成「这次没拿到」，而不是「这个球场没有评分」。
 */
public record CourseInfoDto(
        String slug,
        String address,
        BigDecimal rating,
        Integer ratingCount,
        String mapsUrl) {
}
