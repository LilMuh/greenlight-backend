package golf.service.mail;

import java.time.LocalTime;
import java.util.Locale;

import golf.model.entity.TeeTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 拼「Book this time」按钮的链接。
 *
 * tee_time 表里没有存预订页 URL（表结构归 greenlight-database 管），所以只能按
 * source/site/slug 反推。模板写在配置里（greenlight.mail.booking-url-template），
 * 换站点或改深链参数不用动代码；置空则邮件里不出现预订按钮。
 *
 * CPS 搜索页的深链参数是 2026-08-04 用 OAB 驱动真实浏览器实测出来的：SPA 的
 * 参数名枚举里是 Date / Player / Hole / TeeOffTimeMin / TeeOffTimeMax（注意首字母大写，
 * 跟它转发给后端 API 时用的 searchDate / numberOfPlayer 不是一套）。判据是页面自己发出的
 * TeeTimes 请求里 searchDate 变成了指定那天。日期给 ISO（yyyy-MM-dd）即可。
 *
 * 时间窗口给小数小时，页面会截断到整点（17.3 → 17），所以前后各放 1 小时后
 * 实际落地会比 1 小时略宽——目标时段一定还在窗口内，够用。
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
     * 没配模板就返回 null，模板里据此不渲染按钮。
     * players 来自 watch（时段本身没有「几个人要打」这个概念），落地页据此预选人数。
     */
    public String buildFor(TeeTime teeTime, int players) {
        return build(
                teeTime.getSite(),
                teeTime.getSource(),
                teeTime.getCourse() == null ? null : teeTime.getCourse().getSlug(),
                String.valueOf(teeTime.getPlayDate()),
                teeTime.getTimeLocal(),
                players,
                teeTime.getHoles());
    }

    /** 显式参数版：给预览的样例数据用（TeeTime 是只读实体，造不出假对象）。 */
    public String build(
            String site, String source, String slug, String isoDate, String timeLocal, int players, int holes) {
        String template = mailProperties.getBookingUrlTemplate();
        if (template == null || template.isBlank()) {
            return null;
        }
        double[] window = timeWindow(timeLocal);
        return template
                .replace("{site}", blankIfNull(site))
                .replace("{source}", blankIfNull(source))
                .replace("{slug}", blankIfNull(slug))
                .replace("{date}", blankIfNull(isoDate))
                .replace("{timeMin}", decimalHours(window[0]))
                .replace("{timeMax}", decimalHours(window[1]))
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
