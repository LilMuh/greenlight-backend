package golf.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import golf.model.entity.WatchConfig;

/**
 * 把一条 watch 的「星期」摊成具体日期：今天起 8 天里，星期对得上的那几天。
 *
 * 抓取和匹配都从这里拿日期，不各算各的。两边必须是同一批天——
 * 只抓这几天却按别的口径去匹配，命中的要么是抓不到的未来，要么是库里遗留的旧行。
 */
public final class WatchWindow {

    /** 最远看到今天 + 7 天（含今天共 8 天）：上游放号窗口就这么长，再远也抓不到。 */
    public static final int MAX_DAYS_AHEAD = 7;

    private WatchWindow() {
    }

    public static List<LocalDate> upcomingDates(WatchConfig watch) {
        return upcomingDates(watch, LocalDate.now());
    }

    /**
     * today 由调用方传入：一轮计划里所有 watch 得站在同一个「今天」上，
     * 否则跨午夜那一瞬间算出来的日期会前后不一致。
     *
     * 一个星期都没勾的 watch 返回空清单——不抓也不匹配。接口层已经拦掉了这种输入，
     * 这里再兜一次，免得直接改库塞进来的行把整轮抓崩。
     */
    public static List<LocalDate> upcomingDates(WatchConfig watch, LocalDate today) {
        Set<DayOfWeek> weekdays = watch.getWeekdays();
        if (weekdays == null || weekdays.isEmpty()) {
            return List.of();
        }

        List<LocalDate> dates = new ArrayList<>();
        for (int offset = 0; offset <= MAX_DAYS_AHEAD; offset++) {
            LocalDate date = today.plusDays(offset);
            if (weekdays.contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }
}
