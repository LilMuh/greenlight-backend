package golf.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * course 表的映射。course 是参考数据，主人是 greenlight-database 的 Liquibase，
 * 后端对它**只读**（写库不归后端），所以只给 @Getter。ddl-auto=validate 只做校验。
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

    @Column(name = "updated_at")
    private Instant updatedAt;
}
