package golf.service.mail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import golf.model.entity.Course;
import golf.model.entity.TeeTime;
import golf.model.entity.WatchConfig;
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
 * 这组断言真正想守住的是四件事：
 *   1. TeeTimeDateGroup / TeeTimeCard 是 record，模板里 ${group.slots} 这种属性写法
 *      在 SpEL 下解析得了；
 *   2. 时段按日期分组，一天只出一条预订链接，链接的时间窗口覆盖当天首尾两个时段；
 *   3. 地点跟在球场名后面；没配链接模板 / 查不到地点时对应那块整个不渲染，排版照常收尾；
 *   4. 标题的单复数和两种 Kind 的措辞对得上。
 */
class AlertMailFactoryTest {

    private static final DateTimeFormatter GROUP_DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private AlertMailFactory factory;

    @BeforeEach
    void setUp() {
        MailProperties properties = properties(BOOKING_URL_TEMPLATE);
        factory = new AlertMailFactory(templateEngine(), new BookingLinkBuilder(properties), properties);
    }

    private static final String BOOKING_URL_TEMPLATE =
            "https://{site}.cps.golf/onlineresweb/search-teetime"
                    + "?Date={date}&TeeOffTimeMin={timeMin}&TeeOffTimeMax={timeMax}"
                    + "&Player={players}&Hole={holes}";

    /** 下划线只划在 BOOK 这个词上，后面那段空隙和箭头不划。 */
    private static final String BOOK_LINK =
            "<span style=\"text-decoration:underline;\">BOOK</span> &nbsp;&rarr;";

    @Test
    void rendersEverySlotField() {
        String html = factory.renderSample(Kind.NEW).html();

        assertThat(html)
                .contains("GREENLIGHT")
                .contains("FRASERVIEW")                              // 顶部只有短名，且已大写
                .contains(sampleDate(3))                             // 两个日期各自成组
                .contains(sampleDate(5))
                .contains("18:18")                                   // 24 小时制
                .contains("07:30")
                .contains("4 spots")                                 // 「left」已经去掉
                .contains("1 spot")
                .contains("$52.00")                                  // 价格不带 CAD
                .contains("Vancouver, BC")                           // 地点跟在球场名后面
                .contains(BOOK_LINK)
                .contains("You set up a GreenLight alert for Fraserview Golf Course.");

        // 顶部标签是短名，页脚才用完整球场名
        assertThat(html).doesNotContain("FRASERVIEW GOLF COURSE");

        // 改版后不再出现的东西：引导语、每行一个按钮、价格后缀
        assertThat(html)
                .doesNotContain("These became bookable since the last check.")
                .doesNotContain("Book this time")
                .doesNotContain("CAD");

        // 计数那句话只留在 <title> 里，正文不再有大标题
        assertThat(countOccurrences(html, "3 new tee times just opened")).isEqualTo(1);

        // 模板里不该留下未求值的表达式
        assertThat(html).doesNotContain("th:text").doesNotContain("${");
    }

    @Test
    void groupsSlotsByDateAndLinksOncePerDate() {
        String html = factory.renderSample(Kind.NEW).html();

        // 3 个时段分在 2 天里：两个日期标题、两条预订链接、三行时段
        assertThat(countOccurrences(html, "'Palatino Linotype'")).isEqualTo(2);
        assertThat(countOccurrences(html, BOOK_LINK)).isEqualTo(2);
        assertThat(countOccurrences(html, "border-top:1px solid #f0ebe2")).isEqualTo(9); // 3 行 × 3 列

        // 日期各出现两次：预览行里一次，日期标题里一次
        assertThat(countOccurrences(html, sampleDate(3))).isEqualTo(2);
        assertThat(countOccurrences(html, sampleDate(5))).isEqualTo(2);
    }

    @Test
    void putsTheLocationAfterTheCourseName() {
        String html = factory.renderSample(Kind.NEW).html();

        // 顺序是「球场短名 → 地点 → 第一个日期分组」
        assertThat(html.indexOf("FRASERVIEW")).isLessThan(html.indexOf("Vancouver, BC"));
        assertThat(html.indexOf("Vancouver, BC")).isLessThan(html.indexOf("'Palatino Linotype'"));

        // 地点用次要灰，并且不跟着球场名一起被字距和粗体拉开
        assertThat(html).contains("letter-spacing:normal; font-weight:normal; color:#6b7280;");
    }

    @Test
    void omitsTheLocationWhenTheSiteIsNotMapped() {
        MailProperties noLocations = properties(BOOKING_URL_TEMPLATE);
        noLocations.setSiteLocations(Map.of());
        AlertMailFactory withoutLocation =
                new AlertMailFactory(templateEngine(), new BookingLinkBuilder(noLocations), noLocations);

        String html = withoutLocation.renderSample(Kind.NEW).html();

        // 查不到地点时那一段整个不渲染，球场名照常
        assertThat(html).contains("FRASERVIEW").doesNotContain("Vancouver, BC").doesNotContain("&nbsp;·&nbsp;");
    }

    @Test
    void dropsOnlyTheBookLinkWhenNoBookingTemplateIsConfigured() {
        MailProperties noLinks = properties("");
        AlertMailFactory withoutLinks =
                new AlertMailFactory(templateEngine(), new BookingLinkBuilder(noLinks), noLinks);

        String html = withoutLinks.renderSample(Kind.NEW).html();

        // BOOK 链接整个不渲染，但日期分组本身照常收尾
        assertThat(html)
                .doesNotContain("BOOK")
                .doesNotContain("<a ")
                .contains(sampleDate(3))
                .contains("18:18")
                .contains("$52.00")
                .contains("You set up a GreenLight alert for Fraserview Golf Course.");
    }

    @Test
    void buildsOneBookingLinkPerDaySpanningItsFirstAndLastSlot() {
        String html = factory.renderSample(Kind.NEW).html();

        // 单个时段那天：18:18 → 前后各 1 小时
        // （参数名和大小写是实测过的，见 BookingLinkBuilder 的注释）
        assertThat(html).contains("Date=" + LocalDate.now().plusDays(3)
                + "&amp;TeeOffTimeMin=17.3000&amp;TeeOffTimeMax=19.3000&amp;Player=4&amp;Hole=18");

        // 两个时段那天：窗口从最早 07:30 往前 1 小时到最晚 14:06 往后 1 小时，
        // 落地页会把小数截断成整点（6.5 → 6，15.1 → 15），一天的时段一次看全
        assertThat(html).contains("Date=" + LocalDate.now().plusDays(5)
                + "&amp;TeeOffTimeMin=6.5000&amp;TeeOffTimeMax=15.1000&amp;Player=4&amp;Hole=18");
    }

    @Test
    void wordsSubjectPerKind() {
        RenderedMail fresh = factory.renderSample(Kind.NEW);
        RenderedMail baseline = factory.renderSample(Kind.BASELINE);

        assertThat(fresh.subject()).isEqualTo("Fraserview Golf Course: 3 new tee times just opened");
        assertThat(baseline.subject()).isEqualTo("Fraserview Golf Course: 3 tee times match your alert");

        // 标题进 <title>，正文里不再有计数那句话
        assertThat(fresh.html()).contains("<title>Fraserview Golf Course: 3 new tee times just opened</title>");
    }

    @Test
    void putsAHiddenPreheaderFirstInTheBody() {
        String html = factory.renderSample(Kind.NEW).html();

        assertThat(html).contains(sampleDate(3) + " 18:18 · " + sampleDate(5)
                + " 07:30 and 14:06 — inside your alert window.");
        assertThat(html.indexOf("display:none")).isLessThan(html.indexOf("GREENLIGHT"));
    }

    @Test
    void groupsRealTeeTimesByPlayDateInChronologicalOrder() {
        LocalDate firstDay = LocalDate.of(2026, 8, 7);
        LocalDate secondDay = LocalDate.of(2026, 8, 9);

        // 故意打乱顺序传进来：排序是 AlertMailFactory 的责任，不能指望调用方
        String html = factory.render(
                watch(4),
                List.of(
                        teeTime(secondDay, "14:06", 3, new BigDecimal("61")),
                        teeTime(firstDay, "18:18", 4, new BigDecimal("52")),
                        teeTime(secondDay, "07:30", 1, new BigDecimal("78.5"))),
                Kind.NEW).html();

        assertThat(html).contains("Fri, Aug 7").contains("Sun, Aug 9");
        assertThat(countOccurrences(html, "'Palatino Linotype'")).isEqualTo(2); // 两个日期标题
        assertThat(countOccurrences(html, BOOK_LINK)).isEqualTo(2);   // 一天一条链接

        // 组内按时间升序
        assertThat(html.indexOf("07:30")).isLessThan(html.indexOf("14:06"));

        // 8/9 那条链接的窗口覆盖当天首尾两个时段
        assertThat(html).contains("Date=2026-08-09&amp;TeeOffTimeMin=6.5000&amp;TeeOffTimeMax=15.1000");

        // 价格来自实体：52 / 78.5 都补成两位小数
        assertThat(html).contains("$52.00").contains("$78.50").contains("$61.00");

        // 地点按 tee_time/course 的 site 查配置
        assertThat(html).contains("Vancouver, BC");
    }

    /**
     * 顺手把渲染结果写到 build/mail-preview/ 下。
     * 不是断言，是个便利：跑完 gradlew test 直接用浏览器打开就能看排版，
     * 不用起应用、也不用连 Postgres。build/ 已在 .gitignore 里。
     *
     * 三份分别对应要肉眼确认的情况：多个日期分组（NEW/BASELINE），
     * 以及没有预订链接时分组怎么收尾（no-booking-link 那份）。
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

        MailProperties noLinks = properties("");
        AlertMailFactory withoutLinks =
                new AlertMailFactory(templateEngine(), new BookingLinkBuilder(noLinks), noLinks);
        Path edgeCase = outputDir.resolve("tee-time-alert-new-no-booking-link.html");
        Files.writeString(edgeCase, withoutLinks.renderSample(Kind.NEW).html(), StandardCharsets.UTF_8);
        assertThat(edgeCase).exists();
    }

    /** 样例邮件用的是「今天 + N 天」，断言里跟着算，免得跨月/跨年那几天挂掉。 */
    private static String sampleDate(int plusDays) {
        return GROUP_DATE.format(LocalDate.now().plusDays(plusDays));
    }

    private static WatchConfig watch(int players) {
        WatchConfig watch = new WatchConfig();
        watch.setCourse(course());
        watch.setWeekdays(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        watch.setTimeStart("06:00");
        watch.setTimeEnd("10:00");
        watch.setPlayers(players);
        watch.setMaxPrice(90);
        watch.setEmail("golfer@example.com");
        return watch;
    }

    /**
     * tee_time / course 后端只读，实体没有 setter（写库归 scraper），所以测试里只能
     * 反射塞值。造假对象是为了能测真正的入口 render(...)——分组、排序、按天拼链接
     * 都只在那条路径上跑得到。
     */
    private static Course course() {
        Course course = newInstance(Course.class);
        setField(course, "slug", "fraserview");
        setField(course, "name", "Fraserview Golf Course");
        setField(course, "source", "cps");
        setField(course, "site", "golfvancouver");
        return course;
    }

    private static TeeTime teeTime(LocalDate playDate, String timeLocal, int availableSeats, BigDecimal price) {
        TeeTime teeTime = newInstance(TeeTime.class);
        setField(teeTime, "source", "cps");
        setField(teeTime, "site", "golfvancouver");
        setField(teeTime, "course", course());
        setField(teeTime, "playDate", playDate);
        setField(teeTime, "timeLocal", timeLocal);
        setField(teeTime, "holes", 18);
        setField(teeTime, "availableSeats", availableSeats);
        setField(teeTime, "price", price);
        setField(teeTime, "available", true);
        return teeTime;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MailProperties properties(String bookingUrlTemplate) {
        MailProperties properties = new MailProperties();
        properties.setBookingUrlTemplate(bookingUrlTemplate);
        properties.setSiteLocations(Map.of("golfvancouver", "Vancouver, BC"));
        return properties;
    }

    private static SpringTemplateEngine templateEngine() {
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
