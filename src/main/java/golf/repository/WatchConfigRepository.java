package golf.repository;

import java.util.List;

import golf.model.entity.WatchConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchConfigRepository extends JpaRepository<WatchConfig, Long> {

    /** 列表按最新在前。 */
    List<WatchConfig> findAllByOrderByIdDesc();

    /** 匹配时只看启用中的关注（给后续邮件匹配用）。 */
    List<WatchConfig> findByActiveTrue();
}
