package golf.service.mail;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * greenlight.mail.* 的绑定对象。
 *
 * 单独拎出来是因为邮件正文要用到的两项（预订链接模板、球场地点）都不是单值字符串，
 * 用 @Value 绑 Map 很别扭。
 */
@ConfigurationProperties(prefix = "greenlight.mail")
public class MailProperties {

    /** dev=只写日志；prod=走 SMTP 真发。 */
    private String mode = "dev";

    /** 发件地址。 */
    private String from = "";

    /**
     * 邮件里每个日期分组那条「BOOK →」链接的模板。占位符：
     *   {site}    = tee_time.site，如 golfvancouver
     *   {source}  = tee_time.source，如 cps
     *   {slug}    = course.slug
     *   {date}    = 开球日期 ISO，如 2026-08-07
     *   {timeMin} / {timeMax} = 当天最早那个时段往前 1 小时到最晚那个往后 1 小时，小数小时
     *   {players} = watch 的人数（时段本身没有这个概念）
     *   {holes}   = tee_time.holes
     * 置空则邮件里不出现预订链接。CPS 那边各参数对应什么名字见 BookingLinkBuilder。
     */
    private String bookingUrlTemplate = "";

    /**
     * 球场地点，按 site 配。course 表目前没有地点这一列（表结构归 greenlight-database 管），
     * 这里先用站点级的展示名顶上。
     *
     * 邮件正文顶部「球场短名 · 地点」那行的后半截，见 AlertMailFactory#location。
     * 查不到就返回 null，模板里那一段整个不渲染，只剩球场名。
     */
    private Map<String, String> siteLocations = new HashMap<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getBookingUrlTemplate() {
        return bookingUrlTemplate;
    }

    public void setBookingUrlTemplate(String bookingUrlTemplate) {
        this.bookingUrlTemplate = bookingUrlTemplate;
    }

    public Map<String, String> getSiteLocations() {
        return siteLocations;
    }

    public void setSiteLocations(Map<String, String> siteLocations) {
        this.siteLocations = siteLocations;
    }
}
