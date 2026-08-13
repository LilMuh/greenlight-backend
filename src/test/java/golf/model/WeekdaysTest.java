package golf.model;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 星期在 DB 文本 / EnumSet / API 列表三种形态之间的换算。
 * 这层薄，但它是 watch 唯一的时间维度——转错一个词条，整条订阅就抓错天。
 */
class WeekdaysTest {

    @Test
    void 文本和集合来回一趟不变形() {
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

        assertThat(Weekdays.format(days)).isEqualTo("SAT,SUN");
        assertThat(Weekdays.parse("SAT,SUN")).isEqualTo(days);
    }

    @Test
    void 输出一律按ISO序排好周一在前() {
        // 存进去的顺序是乱的，出来必须是 MON…SUN
        Set<DayOfWeek> days = Set.of(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY);

        assertThat(Weekdays.format(days)).isEqualTo("MON,WED,SUN");
        assertThat(Weekdays.codes(days)).containsExactly("MON", "WED", "SUN");
    }

    @Test
    void 大小写和空白都认() {
        assertThat(Weekdays.of(List.of(" sat ", "Sun")))
                .containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void 认不出来的词条丢掉而不是整条炸掉() {
        assertThat(Weekdays.parse("SAT,XYZ,,SUN"))
                .containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void 空值都归成空集合() {
        assertThat(Weekdays.parse(null)).isEmpty();
        assertThat(Weekdays.parse("")).isEmpty();
        assertThat(Weekdays.of(null)).isEmpty();
        assertThat(Weekdays.format(Set.of())).isEmpty();
    }

    @Test
    void JPA转换器两个方向都对得上() {
        Weekdays.Converter converter = new Weekdays.Converter();
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        assertThat(converter.convertToDatabaseColumn(days)).isEqualTo("MON,FRI");
        assertThat(converter.convertToEntityAttribute("MON,FRI")).isEqualTo(days);
        // 列是 NOT NULL：null 也得写出个能存的空串，不能真写 null
        assertThat(converter.convertToDatabaseColumn(null)).isEmpty();
    }
}
