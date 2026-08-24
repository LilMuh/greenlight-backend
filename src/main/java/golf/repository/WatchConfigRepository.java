package golf.repository;

import java.util.List;
import java.util.Optional;

import golf.model.entity.WatchConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchConfigRepository extends JpaRepository<WatchConfig, Long> {

    /** 列表按最新在前。 */
    List<WatchConfig> findAllByOrderByIdDesc();

    /** 匹配时只看启用中的关注（给后续邮件匹配用）。 */
    List<WatchConfig> findByActiveTrue();

    /**
     * (邮箱, 球场) 是一条 watch 的业务身份，库里有唯一约束兜底
     * （uq_watch_config_email_course，见 greenlight-database 的 012）。
     * email 存进来之前已经统一成小写去空白，所以这里直接等值比。
     */
    Optional<WatchConfig> findByEmailAndCourseId(String email, Long courseId);
}
