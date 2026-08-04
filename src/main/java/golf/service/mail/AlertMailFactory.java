package golf.service.mail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import golf.model.entity.Course;
import golf.model.entity.TeeTime;
import golf.model.entity.WatchConfig;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 把「一条 watch + 一批命中时段」渲染成一封提醒邮件（标题 + HTML 正文）。
 *
 * 排版在 templates/mail/tee-time-alert.html，文案措辞在这里。分工是：
 * 所有格式化（日期写法、12 小时制、价格带币种、单复数）在 Java 做完，
 * 模板只拿现成字符串摆位置——模板里没有表达式逻辑，改样式和改文案互不干扰。
 */
@Component
public class AlertMailFactory {

    /** 两种触发场景，决定标题和引导语的措辞。 */
    public enum Kind {
        /** 新建 watch 时的基准邮件：此刻已经满足条件的时段。 */
        BASELINE,
        /** 每轮抓取后的上升沿：这一轮新变得可约的时段。 */
        NEW
    }

    private static final DateTimeFormatter CARD_DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter RANGE_DATE =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private static final String TEMPLATE = "mail/tee-time-alert";

    private final SpringTemplateEngine templateEngine;
    private final BookingLinkBuilder bookingLinkBuilder;
    private final MailProperties mailProperties;

    public AlertMailFactory(
            SpringTemplateEngine templateEngine,
            BookingLinkBuilder bookingLinkBuilder,
            MailProperties mailProperties) {
        this.templateEngine = templateEngine;
        this.bookingLinkBuilder = bookingLinkBuilder;
        this.mailProperties = mailProperties;
    }

    /** 渲染结果：标题和正文一起产出，避免两边各算一次命中数量而对不上。 */
    public record RenderedMail(String subject, String html) {
    }

    public RenderedMail render(WatchConfig watch, List<TeeTime> teeTimes, Kind kind) {
        return process(
                kind,
                courseName(watch.getCourse()),
                location(watch.getCourse()),
                criteria(watch),
                teeTimes.stream().map(teeTime -> toCard(teeTime, watch.getPlayers())).toList());
    }

    /**
     * 预览/自检用的样例邮件：不查库，直接喂假数据走同一套模板和文案。
     * 供 GET /api/notifications/preview 和 POST /api/notifications/test 使用——
     * 改完模板不用等一轮抓取命中才能看到效果。
     *
     * 三张卡片刻意覆盖了三种形态：有价格有链接、单个空位、没抓到价格。
     */
    public RenderedMail renderSample(Kind kind) {
        String date = LocalDate.now().plusDays(3).toString();
        List<TeeTimeCard> cards = List.of(
                new TeeTimeCard("Fri, Aug 7", "6:18 PM", "4 spots left", "$52.00 CAD",
                        bookingLinkBuilder.build("golfvancouver", "cps", "fraserview", date, "18:18", 4, 18)),
                new TeeTimeCard("Sun, Aug 9", "7:30 AM", "1 spot left", "$78.50 CAD",
                        bookingLinkBuilder.build("golfvancouver", "cps", "fraserview", date, "07:30", 4, 18)),
                new TeeTimeCard("Sun, Aug 9", "2:06 PM", "3 spots left", null,
                        bookingLinkBuilder.build("golfvancouver", "cps", "fraserview", date, "14:06", 4, 18)));

        return process(
                kind,
                "Fraserview Golf Course",
                mailProperties.getSiteLocations().getOrDefault("golfvancouver", null),
                "4 players · Aug 4 – Aug 11 · 6:00 AM – 10:00 AM · up to $90",
                cards);
    }

    /** 文案在这里定，模板只摆位置。标题和正文标题分开写：收件箱里那行要更短。 */
    private RenderedMail process(
            Kind kind, String courseName, String location, String criteria, List<TeeTimeCard> cards) {
        int count = cards.size();

        String subject = courseName + ": " + switch (kind) {
            case BASELINE -> count == 1
                    ? "1 tee time matches your alert"
                    : count + " tee times match your alert";
            case NEW -> count == 1
                    ? "a new tee time just opened"
                    : count + " new tee times just opened";
        };

        String headline = switch (kind) {
            case BASELINE -> count == 1
                    ? "1 tee time matches your alert"
                    : count + " tee times match your alert";
            case NEW -> count == 1
                    ? "A new tee time just opened"
                    : count + " new tee times just opened";
        };

        String intro = switch (kind) {
            case BASELINE -> "Here's what's open right now.";
            case NEW -> "These became bookable since the last check.";
        };

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("subject", subject);
        context.setVariable("headline", headline);
        context.setVariable("intro", intro);
        context.setVariable("courseName", courseName);
        context.setVariable("location", location);
        context.setVariable("criteria", criteria);
        context.setVariable("cards", cards);

        return new RenderedMail(subject, templateEngine.process(TEMPLATE, context));
    }

    private TeeTimeCard toCard(TeeTime teeTime, int players) {
        return new TeeTimeCard(
                formatDate(teeTime.getPlayDate(), CARD_DATE),
                formatClock(teeTime.getTimeLocal()),
                seats(teeTime.getAvailableSeats()),
                price(teeTime.getPrice()),
                bookingLinkBuilder.buildFor(teeTime, players));
    }

    /** 页脚那行「你为什么会收到这封信」：把 watch 的筛选条件平铺出来。 */
    private String criteria(WatchConfig watch) {
        StringBuilder text = new StringBuilder();
        text.append(watch.getPlayers() == 1 ? "1 player" : watch.getPlayers() + " players");

        String dateStart = formatDate(watch.getDateStart(), RANGE_DATE);
        String dateEnd = formatDate(watch.getDateEnd(), RANGE_DATE);
        if (!dateStart.isEmpty() && !dateEnd.isEmpty()) {
            text.append(" · ").append(dateStart).append(" – ").append(dateEnd);
        }

        String from = formatClock(watch.getTimeStart());
        String until = formatClock(watch.getTimeEnd());
        if (!from.isEmpty() && !until.isEmpty()) {
            text.append(" · ").append(from).append(" – ").append(until);
        }

        if (watch.getMaxPrice() > 0) {
            text.append(" · up to $").append(watch.getMaxPrice());
        }
        return text.toString();
    }

    private String location(Course course) {
        if (course == null || course.getSite() == null) {
            return null;
        }
        // course 表没有地点这一列，先按 site 查配置；查不到返回 null，模板不渲染这行
        return mailProperties.getSiteLocations().get(course.getSite());
    }

    private static String courseName(Course course) {
        return course == null || course.getName() == null ? "Your course" : course.getName();
    }

    private static String seats(int availableSeats) {
        return availableSeats == 1 ? "1 spot left" : availableSeats + " spots left";
    }

    private static String price(BigDecimal price) {
        return price == null ? null : "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString() + " CAD";
    }

    private static String formatDate(LocalDate date, DateTimeFormatter formatter) {
        return date == null ? "" : formatter.format(date);
    }

    /** "18:18" → "6:18 PM"。解析不了就原样返回，不为了排版把内容弄丢。 */
    private static String formatClock(String timeLocal) {
        if (timeLocal == null || timeLocal.isBlank()) {
            return "";
        }
        try {
            return CLOCK.format(LocalTime.parse(timeLocal));
        } catch (Exception e) {
            return timeLocal;
        }
    }
}
