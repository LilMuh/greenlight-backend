package golf.model.dto;

import java.util.List;

/**
 * 批量创建关注的请求：一组球场 + 一份共享 config。
 * 后端按 courseIds 逐个球场建一条 watch（单个球场就是长度为 1 的列表）。
 *
 * weekdays 是三字母缩写的列表，如 ["SAT","SUN"]；至少要有一个，见 WatchConfigService。
 */
public record WatchConfigBatchDto(
        List<Long> courseIds,
        List<String> weekdays,
        String timeStart,
        String timeEnd,
        int players,
        int maxPrice,
        String email,
        boolean active) {
}
