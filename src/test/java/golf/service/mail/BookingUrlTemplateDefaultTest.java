package golf.service.mail;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 application.yml 里 booking-url-template 的出厂值。
 *
 * AlertMailFactoryTest 用的是它自己拼的那份模板常量，于是真正发出去的这条链接一直没人盯着。
 * 这里直接读 application.yml，只管一件事：链接里不许钉死人数。
 *
 * 为什么不许——CPS 落地页会把 Player 原样转成它自己 TeeTimes 接口的 numberOfPlayer，
 * 而这个参数不只决定「显示几个空位」，它会把**不接受这个人数的时段整条筛掉**
 * （2026-08-25 实测：同一天同一批球场，numberOfPlayer=4 比 =2 少返回一条
 * minPlayer=2/maxPlayer=2 的时段）。scraper 是用 numberOfPlayer=0、即不筛的口径抓的，
 * 所以一条 minPlayer 大于 watch 人数的时段，我们看得见、收信人点着 Player=2 的链接进去看不见，
 * 人就以为提醒是假的。2026-08-25 McCleery / Langara 那两封 13:33 的提醒就是这么来的。
 *
 * 不给 Player，落地页自己发的就是 numberOfPlayer=0（实测），正好和抓取口径对上。
 * 前端查询页的链接（greenlight-frontend main.js）出于同样的理由也不带 Player。
 */
class BookingUrlTemplateDefaultTest {

    private static final String PROPERTY = "greenlight.mail.booking-url-template";

    @Test
    void shippedBookingLinkDoesNotPinPlayerCount() {
        assertThat(shippedBookingUrlTemplate())
                .as("application.yml 的 %s", PROPERTY)
                .doesNotContain("Player=")
                .doesNotContain("{players}");
    }

    /**
     * 同样不许钉死时间窗口。理由和人数那条不同：时间窗口不会把时段筛掉，只是把落地页
     * 预先收窄到开球前后一小时——但收信人点进来往往不是要订我们提醒的那一条，而是想看看
     * 那天还剩什么。窗口一收，同一天别的时段得先把筛选条件清掉才看得见。
     *
     * 只落到日期，落地页给的就是这一天的完整时段列表；我们提醒的那几条也在里面。
     * BookingLinkBuilder 仍然算得出 {timeMin}/{timeMax}，想收窄把占位符配回去就行。
     */
    @Test
    void shippedBookingLinkDoesNotPinTimeWindow() {
        assertThat(shippedBookingUrlTemplate())
                .as("application.yml 的 %s", PROPERTY)
                .doesNotContain("TeeOffTime")
                .doesNotContain("{timeMin}")
                .doesNotContain("{timeMax}");
    }

    /** 直接从 classpath 上的 application.yml 读，不起容器（起容器要连库）。 */
    private static String shippedBookingUrlTemplate() {
        List<PropertySource<?>> sources;
        try {
            sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        } catch (IOException e) {
            throw new IllegalStateException("读不到 application.yml", e);
        }
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(PROPERTY);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        throw new IllegalStateException("application.yml 里没有 " + PROPERTY);
    }
}
