package golf.service.mail;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import golf.model.entity.TeeTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 拼日期分组上那条「BOOK →」链接。
 *
 * tee_time 表里没有存预订页 URL（表结构归 greenlight-database 管），所以只能按
 * source/site/slug 反推。模板写在配置里（greenlight.mail.booking-url-template），
 * 换站点或改深链参数不用动代码；置空则邮件里不出现预订链接。
 *
 * CPS 搜索页的深链参数是 2026-08-04 用 OAB 驱动真实浏览器实测出来的：SPA 的
 * 参数名枚举里是 Date / Player / Hole / TeeOffTimeMin / TeeOffTimeMax（注意首字母大写，
 * 跟它转发给后端 API 时用的 searchDate / numberOfPlayer 不是一套）。判据是页面自己发出的
 * TeeTimes 请求里 searchDate 变成了指定那天。日期给 ISO（yyyy-MM-dd）即可。
 *
 * 一天一条链接：落地页一次列出这一天的时段，不用为每个时段各点一次。
 *
 * {timeMin}/{timeMax} 仍然算——当天最早那个命中时段往前 1 小时到最晚那个往后 1 小时，
 * 给的是小数小时，页面会截断到整点（17.3 → 17），所以实际落地比 1 小时略宽。
 * 但【出厂模板不再用它们】：收信人点进来往往不是要订我们提醒的那一条，而是想看看那天
 * 还剩什么，窗口一收，同一天别的时段得先把筛选清掉才看得见。想收窄把占位符配回去即可，
 * 见 application.yml 和 BookingUrlTemplateDefaultTest。
 */
@Component
public class BookingLinkBuilder {

    private static final Logger log = LoggerFactory.getLogger(BookingLinkBuilder.class);

    /** 开球时间前后各留 1 小时，落地页不至于只剩一个时段。 */
    private static final double WINDOW_HOURS = 1.0;

    private static final double DAY_START = 0.0;
    private static final double DAY_END = 23.999722222222225; // CPS 搜索页自己用的上界

    private final MailProperties mailProperties;

    public BookingLinkBuilder(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    /**
     * 同一天的一批时段拼一条链接。没配模板就返回 null，模板里据此不渲染 BOOK 链接。
     * players 来自 watch（时段本身没有「几个人要打」这个概念），落地页据此预选人数。
     *
     * slots 必须非空且按时间升序——首尾两条就是窗口的两端，调用方（AlertMailFactory）
     * 分组前已经排好序。
     */
    public String buildForDay(List<TeeTime> slots, int players) {
        TeeTime first = slots.get(0);
        TeeTime last = slots.get(slots.size() - 1);
        return build(
                first.getSite(),
                first.getSource(),
                first.getCourse() == null ? null : first.getCourse().getSlug(),
                String.valueOf(first.getPlayDate()),
                first.getTimeLocal(),
                last.getTimeLocal(),
                players,
                first.getHoles());
    }

    /** 显式参数版：给预览的样例数据用（TeeTime 是只读实体，造不出假对象）。 */
    public String build(
            String site,
            String source,
            String slug,
            String isoDate,
            String earliestTimeLocal,
            String latestTimeLocal,
            int players,
            int holes) {
        String template = mailProperties.getBookingUrlTemplate();
        if (template == null || template.isBlank()) {
            return null;
        }
        double min = timeWindow(earliestTimeLocal)[0];
        double max = timeWindow(latestTimeLocal)[1];
        return template
                .replace("{site}", blankIfNull(site))
                .replace("{source}", blankIfNull(source))
                .replace("{slug}", blankIfNull(slug))
                .replace("{date}", blankIfNull(isoDate))
                .replace("{timeMin}", decimalHours(min))
                .replace("{timeMax}", decimalHours(max))
                .replace("{players}", String.valueOf(players))
                .replace("{holes}", String.valueOf(holes));
    }

    /**
     * "18:18" → 前后 1 小时的小数小时窗口。时间串解析不了就退回整天，
     * 链接照样可用——为了一个按钮的时间窗口让整封邮件发不出去不值得。
     */
    private static double[] timeWindow(String timeLocal) {
        try {
            LocalTime time = LocalTime.parse(timeLocal);
            double hours = time.getHour() + time.getMinute() / 60.0;
            return new double[] {
                    Math.max(DAY_START, hours - WINDOW_HOURS),
                    Math.min(DAY_END, hours + WINDOW_HOURS)
            };
        } catch (Exception e) {
            log.warn("Unparseable tee time '{}', booking link falls back to the whole day", timeLocal);
            return new double[] { DAY_START, DAY_END };
        }
    }

    private static String decimalHours(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
