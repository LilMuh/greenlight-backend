package golf.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量创建关注的请求：一组球场 + 一份共享 config。
 * 后端按 courseIds 逐个球场建一条 watch（单个球场就是长度为 1 的列表）。
 */
public record WatchConfigBatchDto(
        List<Long> courseIds,
        LocalDate dateStart,
        LocalDate dateEnd,
        String timeStart,
        String timeEnd,
        int players,
        int maxPrice,
        String email,
        boolean active) {
}
