package golf.model.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import golf.model.Weekdays;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * watch_config 表的映射：一条关注订阅——命中匹配的 tee time 时邮件提醒。
 * 一条 watch 只关注一个球场（多球场由前端批量勾选、后端建多条）。
 * 这是后端**会写**的第一张表（tee_time / course 仍只读），所以给 @Getter + @Setter。
 * 表结构由 greenlight-database 的 Liquibase 拥有，ddl-auto=validate 只做校验。
 */
@Entity
@Table(name = "watch_config")
@Getter
@Setter
public class WatchConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course; // 关注的球场

    /**
     * 关注的星期，如 {SATURDAY, SUNDAY}；库里存成 "SAT,SUN" 一列文本。
     * 关注的是「每个周六」这种长期成立的事，不是某段会过期的日期区间——
     * 具体抓哪几天由 WatchWindow 按今天往后推算，见那里。
     */
    @Convert(converter = Weekdays.Converter.class)
    @Column(name = "weekdays")
    private Set<DayOfWeek> weekdays;

    @Column(name = "time_start")
    private String timeStart; // "06:00"

    @Column(name = "time_end")
    private String timeEnd; // "10:00"

    private int players;

    @Column(name = "max_price")
    private int maxPrice; // 价格上限，加元整数

    private String email;
    private boolean active;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
