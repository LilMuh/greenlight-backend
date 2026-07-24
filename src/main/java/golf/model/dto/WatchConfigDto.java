package golf.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 关注订阅的读写载体，字段与前端 Watch Alerts 表单一一对应。
 * 创建时 id 为 null，更新时带上已有 id。
 */
public record WatchConfigDto(
        Long id,
        List<String> courseIds,
        LocalDate dateStart,
        LocalDate dateEnd,
        String timeStart,
        String timeEnd,
        int players,
        int maxPrice,
        String email,
        boolean active) {
}
