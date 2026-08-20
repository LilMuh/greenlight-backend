package golf.service;

import java.util.List;

import golf.model.dto.WatchConfigBatchDto;
import golf.model.dto.WatchConfigDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 时间窗的校验。time_start / time_end 存的是 24 小时制的 "HH:MM" 文本，匹配时原样
 * 进 SQL 的 BETWEEN（见 WatchMatchService）。结束不晚于开始的窗口在 SQL 里恒为空集，
 * 一条都不会命中，而且不报错——这种 watch 建了等于没建，人还以为自己在等通知。
 * 所以在写进库之前就拒掉。
 */
class WatchConfigServiceTest {

    /** 校验发生在碰仓储之前，所以这里不需要真的仓储。 */
    private final WatchConfigService service = new WatchConfigService(null, null, null);

    private static WatchConfigBatchDto batch(String timeStart, String timeEnd) {
        return new WatchConfigBatchDto(
                List.of(1L), List.of("SAT"), timeStart, timeEnd, 4, 100, "golfer@example.com", true);
    }

    private static WatchConfigDto single(String timeStart, String timeEnd) {
        return new WatchConfigDto(
                7L, 1L, "langara", "Langara Golf Course",
                List.of("SAT"), timeStart, timeEnd, 4, 100, "golfer@example.com", true);
    }

    @Test
    void rejectsEndBeforeStart() {
        // 手机上拨了时和分、漏了 AM/PM 那一下，4:45 PM 就成了这个值
        assertThatThrownBy(() -> service.create(batch("06:00", "04:45")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("04:45");
    }

    @Test
    void rejectsEmptyWindow() {
        assertThatThrownBy(() -> service.create(batch("06:00", "06:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedTime() {
        assertThatThrownBy(() -> service.create(batch("6:00", "20:00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(batch("06:00", "24:00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(batch("06:00", "20:60")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(batch("06:00", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 改一条已有的 watch 同样能把窗口改坏，这条路径也得挡。 */
    @Test
    void rejectsEndBeforeStartOnUpdate() {
        assertThatThrownBy(() -> service.update(7L, single("06:00", "04:45")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 正常的窗口要放行——放行后才会走到仓储（这里是 null），所以不该是 IllegalArgumentException。 */
    @Test
    void acceptsWindowThatRunsForward() {
        assertThatThrownBy(() -> service.create(batch("06:00", "20:00")))
                .isNotInstanceOf(IllegalArgumentException.class);
    }
}
