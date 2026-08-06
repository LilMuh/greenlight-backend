package golf.model.dto;

import java.util.List;

/**
 * 一条 watch 当前命中的结果：watch 摘要 + 命中的时段列表。
 * 只读展示用，通知归 NotificationService（两边共用 WatchMatchService#findMatchingRows）。
 */
public record MatchResultDto(
        Long watchId,
        Long courseId,
        String courseSlug,
        String courseName,
        String email,
        int hitCount,
        List<TeeTimeDto> hits) {
}
