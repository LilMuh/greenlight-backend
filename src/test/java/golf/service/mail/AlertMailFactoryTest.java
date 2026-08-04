package golf.service.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import golf.service.mail.AlertMailFactory.Kind;
import golf.service.mail.AlertMailFactory.RenderedMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件模板的渲染自测。不起 Spring 容器（那要连库），自己拼一个 SpringTemplateEngine
 * 指向同一份 templates/mail/*.html。
 *
 * 这组断言真正想守住的是三件事：
 *   1. TeeTimeCard 是 record，模板里 ${card.date} 这种属性写法在 SpEL 下解析得了；
 *   2. price / bookingUrl 为 null 时对应的块整块不渲染，不会漏出空壳；
 *   3. 标题的单复数和两种 Kind 的措辞对得上。
 */
class AlertMailFactoryTest {

    private AlertMailFactory factory;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        MailProperties properties = new MailProperties();
        properties.setBookingUrlTemplate(
                "https://{site}.cps.golf/onlineresweb/search-teetime"
                        + "?Date={date}&TeeOffTimeMin={timeMin}&TeeOffTimeMax={timeMax}"
                        + "&Player={players}&Hole={holes}");
        properties.setSiteLocations(Map.of("golfvancouver", "Vancouver, BC"));

        factory = new AlertMailFactory(engine, new BookingLinkBuilder(properties), properties);
    }

    @Test
    void rendersEveryCardField() {
        String html = factory.renderSample(Kind.NEW).html();

        assertThat(html)
                .contains("Fraserview Golf Course")
                .contains("Vancouver, BC")
                .contains("Fri, Aug 7")
                .contains("6:18 PM")
                .contains("4 spots left")
                .contains("$52.00 CAD")
                .contains("1 spot left")
                .contains("Book this time");

        // 模板里不该留下未求值的表达式
        assertThat(html).doesNotContain("th:text").doesNotContain("${");
    }

    @Test
    void omitsPriceBlockWhenPriceIsMissing() {
        String html = factory.renderSample(Kind.NEW).html();

        // 样例三张卡片里只有两张有价格，第三张的价格块必须整块消失
        assertThat(countOccurrences(html, "CAD")).isEqualTo(2);
    }

    @Test
    void omitsBookingButtonWhenTemplateIsNotConfigured() {
        MailProperties noLink = new MailProperties();
        noLink.setBookingUrlTemplate("");
        AlertMailFactory withoutLinks = new AlertMailFactory(
                templateEngine(), new BookingLinkBuilder(noLink), noLink);

        String html = withoutLinks.renderSample(Kind.NEW).html();

        assertThat(html).doesNotContain("Book this time").contains("6:18 PM");
    }

    @Test
    void buildsBookingLinkThatLandsOnTheRightDayAndTimeWindow() {
        String html = factory.renderSample(Kind.NEW).html();
        String date = LocalDate.now().plusDays(3).toString();

        // 18:18 → 前后各 1 小时；Date 给 ISO；Player 来自 watch
        // （参数名和大小写是实测过的，见 BookingLinkBuilder 的注释）
        assertThat(html).contains(
                "Date=" + date + "&amp;TeeOffTimeMin=17.3000&amp;TeeOffTimeMax=19.3000&amp;Player=4&amp;Hole=18");
    }

    @Test
    void wordsSubjectPerKind() {
        RenderedMail fresh = factory.renderSample(Kind.NEW);
        RenderedMail baseline = factory.renderSample(Kind.BASELINE);

        assertThat(fresh.subject()).isEqualTo("Fraserview Golf Course: 3 new tee times just opened");
        assertThat(baseline.subject()).isEqualTo("Fraserview Golf Course: 3 tee times match your alert");
        assertThat(fresh.html()).contains("These became bookable since the last check.");
        // th:text 会 HTML 转义，撇号出去是 &#39;（客户端里显示正常）
        assertThat(baseline.html()).contains("Here&#39;s what&#39;s open right now.");
    }

    /**
     * 顺手把两种邮件的渲染结果写到 build/mail-preview/ 下。
     * 不是断言，是个便利：跑完 gradlew test 直接用浏览器打开就能看排版，
     * 不用起应用、也不用连 Postgres。build/ 已在 .gitignore 里。
     */
    @Test
    void dumpsPreviewsForEyeballing() throws IOException {
        Path outputDir = Path.of("build", "mail-preview");
        Files.createDirectories(outputDir);
        for (Kind kind : Kind.values()) {
            Path file = outputDir.resolve("tee-time-alert-" + kind.name().toLowerCase(Locale.ROOT) + ".html");
            Files.writeString(file, factory.renderSample(kind).html(), StandardCharsets.UTF_8);
            assertThat(file).exists();
        }
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
