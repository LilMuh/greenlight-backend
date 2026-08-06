package golf.model.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * course 表的映射。表结构由 greenlight-database 的 Liquibase 拥有，ddl-auto=validate 只做校验。
 *
 * 目录本身（slug / name / source / site / image_url）仍是参考数据，只有 Liquibase 写，
 * 后端只读——所以那几个字段没有 setter。
 *
 * 例外是下面四个从 Google Maps 抓来的字段：由 CourseInfoService 定期写回，
 * 所以单独给了 @Setter。抓取本身在 scraper（浏览器能力只在那个仓），
 * 但它只负责抓、原样返回，写库归后端。
 */
@Entity
@Table(name = "course")
@Getter
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug; // 业务键，如 "fraserview"
    private String name; // 展示名
    private String source; // "cps"
    private String site; // "golfvancouver"

    @Column(name = "image_url")
    private String imageUrl; // 照片地址，可空；前端原样塞进 <img src>

    /**
     * 以下四个来自 Google Maps，由 scraper 抓、CourseInfoService 写回。
     * 全部可空且**会长期为空**——解析页面拿不到时写 null 不写假值，前端据此降级。
     * 包装类型不用原生类型：int 会把 null 变成 0，前端就分不清「没数据」和「零分」。
     */
    @Setter
    private String address; // Google 的完整地址，前端自己截短显示

    @Setter
    private BigDecimal rating; // 0.0–5.0

    @Setter
    @Column(name = "rating_count")
    private Integer ratingCount;

    @Setter
    @Column(name = "maps_url")
    private String mapsUrl; // 上次解析到的地点链接，下次刷新直接用它，不再走搜索

    /**
     * 整行的修改时间。也是「地址/评分该不该刷新」的判据（见 CourseInfoService）——
     * 所以刷新时必须一起写，否则每轮都会重复去查 Google Maps。
     */
    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;
}
