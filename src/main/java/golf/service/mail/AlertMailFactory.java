package golf.service.mail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import golf.model.Weekdays;
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
 * 所有格式化（日期写法、24 小时制、价格写法、单复数）在 Java 做完，
 * 模板只拿现成字符串摆位置——模板里没有表达式逻辑，改样式和改文案互不干扰。
 *
 * 正文按日期分组：一天一个块，块里按时间升序列时段，预订链接挂在分组上而不是每行一条。
 */
@Component
public class AlertMailFactory {

    /** 两种触发场景，决定标题的措辞。 */
    public enum Kind {
        /** 新建 watch 时的基准邮件：此刻已经满足条件的时段。 */
        BASELINE,
        /** 每轮抓取后的上升沿：这一轮新变得可约的时段。 */
        NEW
    }

    private static final DateTimeFormatter GROUP_DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final String TEMPLATE = "mail/tee-time-alert";

    /** 球场名去掉这个后缀就是正文顶部那个短名（FRASERVIEW GOLF COURSE → FRASERVIEW）。 */
    private static final String COURSE_SUFFIX = " golf course";

    /** 预览行（收件箱里标题后面那截灰字）里最多提几个时段。 */
    private static final int PREHEADER_SLOTS = 3;

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
        // 分组和链接窗口都吃「按日期、再按时间升序」这个前提，别指望调用方已经排好
        Comparator<TeeTime> chronological = Comparator
                .<TeeTime, LocalDate>comparing(
                        TeeTime::getPlayDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TeeTime::getTimeLocal, Comparator.nullsLast(Comparator.naturalOrder()));

        List<TeeTime> ordered = teeTimes.stream().sorted(chronological).toList();

        return process(
                kind,
                courseName(watch.getCourse()),
                location(watch.getCourse()),
                criteria(watch),
                groupByDate(ordered, watch.getPlayers()));
    }

    /**
     * 预览/自检用的样例邮件：不查库，直接喂假数据走同一套模板和文案。
     * 供 GET /api/notifications/preview 和 POST /api/notifications/test 使用——
     * 改完模板不用等一轮抓取命中才能看到效果。
     *
     * 样例刻意跨了两个日期，其中一天有两个时段：分组和「一天一条链接」都能看出来。
     */
    public RenderedMail renderSample(Kind kind) {
        LocalDate firstDay = LocalDate.now().plusDays(3);
        LocalDate secondDay = LocalDate.now().plusDays(5);

        TeeTimeDateGroup first = new TeeTimeDateGroup(
                formatDate(firstDay, GROUP_DATE),
                bookingLinkBuilder.build(
                        "golfvancouver", "cps", "fraserview", firstDay.toString(), "18:18", "18:18", 4, 18),
                List.of(new TeeTimeCard("18:18", "4 spots", "$52.00")));

        TeeTimeDateGroup second = new TeeTimeDateGroup(
                formatDate(secondDay, GROUP_DATE),
                bookingLinkBuilder.build(
                        "golfvancouver", "cps", "fraserview", secondDay.toString(), "07:30", "14:06", 4, 18),
                List.of(
                        new TeeTimeCard("07:30", "1 spot", "$78.50"),
                        new TeeTimeCard("14:06", "3 spots", "$61.00")));

        return process(
                kind,
                "Fraserview Golf Course",
                mailProperties.getSiteLocations().getOrDefault("golfvancouver", null),
                "4 players · Sat, Sun · 06:00 – 10:00 · up to $90",
                List.of(first, second));
    }

    /** 文案在这里定，模板只摆位置。 */
    private RenderedMail process(
            Kind kind,
            String courseName,
            String location,
            String criteria,
            List<TeeTimeDateGroup> groups) {

        int count = groups.stream().mapToInt(group -> group.slots().size()).sum();

        String subject = courseName + ": " + switch (kind) {
            case BASELINE -> count == 1
                    ? "1 tee time matches your alert"
                    : count + " tee times match your alert";
            case NEW -> count == 1
                    ? "a new tee time just opened"
                    : count + " new tee times just opened";
        };

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("subject", subject);
        context.setVariable("preheader", preheader(groups));
        context.setVariable("courseLabel", courseLabel(courseName));
        context.setVariable("location", location);
        context.setVariable("groups", groups);
        context.setVariable("footerNote", "You set up a GreenLight alert for " + courseName + ".");
        context.setVariable("criteria", criteria);
        // 没配就传 null 而不是空串：模板那条 th:if 只在 null 上判得干净，
        // 空串在 Thymeleaf 里算真假是版本相关的，不值得赌
        context.setVariable("manageUrl", blankToNull(mailProperties.getManageUrl()));

        return new RenderedMail(subject, templateEngine.process(TEMPLATE, context));
    }

    /** 空白（含全空格）当没配。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 按开球日期分组，组内保持时间升序；预订链接一天一条，窗口覆盖当天首尾两个时段。 */
    private List<TeeTimeDateGroup> groupByDate(List<TeeTime> ordered, int players) {
        // 按格式化后的日期做键：play_date 理论上可能为空，groupingBy 不收 null 键
        Map<String, List<TeeTime>> byDate = new LinkedHashMap<>();
        for (TeeTime teeTime : ordered) {
            byDate.computeIfAbsent(formatDate(teeTime.getPlayDate(), GROUP_DATE), key -> new ArrayList<>())
                    .add(teeTime);
        }

        List<TeeTimeDateGroup> groups = new ArrayList<>(byDate.size());
        byDate.forEach((date, slots) -> groups.add(new TeeTimeDateGroup(
                date,
                bookingLinkBuilder.buildForDay(slots, players),
                slots.stream().map(AlertMailFactory::toCard).toList())));
        return groups;
    }

    /**
     * 正文第一个元素是隐藏的预览行：收件箱列表里标题后面跟的那截灰字。
     * 不给的话客户端会自己抓正文开头的可见文字，这封邮件开头是球场名，读起来是重复的。
     */
    private static String preheader(List<TeeTimeDateGroup> groups) {
        StringBuilder text = new StringBuilder();
        int used = 0;

        for (TeeTimeDateGroup group : groups) {
            if (used >= PREHEADER_SLOTS) {
                break;
            }
            List<String> times = new ArrayList<>();
            for (TeeTimeCard card : group.slots()) {
                if (used >= PREHEADER_SLOTS) {
                    break;
                }
                times.add(card.time());
                used++;
            }
            if (times.isEmpty()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append(group.date()).append(' ').append(joinTimes(times));
        }

        return text.isEmpty() ? "" : text + " — inside your alert window.";
    }

    /** ["07:30","14:06"] → "07:30 and 14:06"。 */
    private static String joinTimes(List<String> times) {
        if (times.size() == 1) {
            return times.get(0);
        }
        return String.join(", ", times.subList(0, times.size() - 1)) + " and " + times.get(times.size() - 1);
    }

    /** 页脚那行「你为什么会收到这封信」：把 watch 的筛选条件平铺出来。 */
    private String criteria(WatchConfig watch) {
        StringBuilder text = new StringBuilder();
        text.append(watch.getPlayers() == 1 ? "1 player" : watch.getPlayers() + " players");

        String weekdays = formatWeekdays(watch.getWeekdays());
        if (!weekdays.isEmpty()) {
            text.append(" · ").append(weekdays);
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
        // course 表没有地点这一列，先按 site 查配置；查不到返回 null，模板就只显示球场名
        return mailProperties.getSiteLocations().get(course.getSite());
    }

    private static TeeTimeCard toCard(TeeTime teeTime) {
        return new TeeTimeCard(
                formatClock(teeTime.getTimeLocal()),
                seats(teeTime.getAvailableSeats()),
                price(teeTime.getPrice()));
    }

    private static String courseName(Course course) {
        return course == null || course.getName() == null ? "Your course" : course.getName();
    }

    /**
     * 正文顶部那行只用短名，全大写：「FRASERVIEW GOLF COURSE」在 16px Arial Black
     * 加 0.10em 字距下会顶到 600px 宽度的边。页脚保留完整球场名。
     */
    private static String courseLabel(String courseName) {
        String label = courseName;
        if (label.toLowerCase(Locale.ENGLISH).endsWith(COURSE_SUFFIX)) {
            label = label.substring(0, label.length() - COURSE_SUFFIX.length()).strip();
        }
        return label.toUpperCase(Locale.ENGLISH);
    }

    private static String seats(int availableSeats) {
        return availableSeats == 1 ? "1 spot" : availableSeats + " spots";
    }

    private static String price(BigDecimal price) {
        return price == null ? null : "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDate(LocalDate date, DateTimeFormatter formatter) {
        return date == null ? "" : formatter.format(date);
    }

    /**
     * {SATURDAY, SUNDAY} → "Sat, Sun"，七天全勾时缩成 "Every day"——
     * 把七个缩写全列出来只是在告诉收信人「没做任何筛选」，不如直接说这句。
     */
    private static String formatWeekdays(Set<DayOfWeek> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return "";
        }
        if (weekdays.size() == DayOfWeek.values().length) {
            return "Every day";
        }
        return Weekdays.sorted(weekdays).stream()
                .map(day -> day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .collect(Collectors.joining(", "));
    }

    /** "18:18" → "18:18"，顺手把 "6:18" 这类补齐成两位。解析不了就原样返回，不为了排版把内容弄丢。 */
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
