package golf.model.dto;

import java.util.List;

/**
 * 一条 watch 当前命中的结果：watch 摘要 + 命中的时段列表。
 * 只读展示用——先把“哪些空位符合这条关注”算出来给前端看，暂不发通知。
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
