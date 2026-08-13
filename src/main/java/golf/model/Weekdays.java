package golf.model;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;

/**
 * 「关注哪几个星期」这件事在三种形态之间的换算：
 *   DB   watch_config.weekdays 一列文本 "SAT,SUN"
 *   Java EnumSet&lt;DayOfWeek&gt;
 *   API  ["SAT","SUN"]
 *
 * 三字母缩写就是 DayOfWeek 名字的前三位（SATURDAY → SAT），七个刚好互不重复，
 * 于是不用维护一张对照表。顺序统一按 ISO（周一 1 … 周日 7）——EnumSet 天然按序迭代，
 * 存进去、读出来、发邮件印出来都是同一个序，肉眼比对时不会打架。
 */
public final class Weekdays {

    private static final Map<String, DayOfWeek> BY_CODE = new LinkedHashMap<>();

    static {
        for (DayOfWeek day : DayOfWeek.values()) {
            BY_CODE.put(code(day), day);
        }
    }

    private Weekdays() {
    }

    /** SATURDAY → "SAT"。 */
    public static String code(DayOfWeek day) {
        return day.name().substring(0, 3);
    }

    /**
     * "SAT,SUN" → {SATURDAY, SUNDAY}。
     * 认不出来的词条直接丢掉：一个脏值不该让整条 watch 加载失败。
     */
    public static Set<DayOfWeek> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return of(List.of(csv.split(",")));
    }

    /** ["SAT","SUN"] → {SATURDAY, SUNDAY}。大小写和空白都容忍，认不出来的丢掉。 */
    public static Set<DayOfWeek> of(Collection<String> codes) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (codes == null) {
            return days;
        }
        for (String code : codes) {
            if (code == null) {
                continue;
            }
            DayOfWeek day = BY_CODE.get(code.strip().toUpperCase(Locale.ENGLISH));
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    /** {SATURDAY, SUNDAY} → "SAT,SUN"，ISO 序。 */
    public static String format(Collection<DayOfWeek> days) {
        return String.join(",", codes(days));
    }

    /** {SATURDAY, SUNDAY} → ["SAT","SUN"]，ISO 序，给 DTO 用。 */
    public static List<String> codes(Collection<DayOfWeek> days) {
        if (days == null) {
            return List.of();
        }
        return sorted(days).stream().map(Weekdays::code).toList();
    }

    /** 任意来源的集合都收拢成 EnumSet，拿到 ISO 序和去重。 */
    public static Set<DayOfWeek> sorted(Collection<DayOfWeek> days) {
        return days.isEmpty()
                ? EnumSet.noneOf(DayOfWeek.class)
                : days.stream().collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    /**
     * JPA 侧的存取。列是 NOT NULL，所以空集合写空串而不是 null——
     * 空串读回来还是空集合，来回一趟不变形。
     */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<Set<DayOfWeek>, String> {

        @Override
        public String convertToDatabaseColumn(Set<DayOfWeek> days) {
            return days == null ? "" : format(days);
        }

        @Override
        public Set<DayOfWeek> convertToEntityAttribute(String csv) {
            return parse(csv);
        }
    }
}
