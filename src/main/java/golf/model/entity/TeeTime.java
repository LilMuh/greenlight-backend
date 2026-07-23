package golf.model.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * tee_time 表的映射。后端对这张表**只读**（写库由 scraper 负责），所以只给 @Getter。
 * 表结构由 greenlight-database 的 Liquibase 拥有，ddl-auto=validate 只做校验。
 */
@Entity
@Table(name = "tee_time")
@Getter
public class TeeTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source; // "cps"
    private String site; // "golfvancouver"

    @Column(name = "course_id")
    private String courseId; // slug "langara"

    @Column(name = "play_date")
    private LocalDate playDate;

    @Column(name = "time_local")
    private String timeLocal; // "18:18"

    private int holes;
    private int players; // 按几人查的可订性

    private BigDecimal price; // 单人 green fee，可空
    private boolean available;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
