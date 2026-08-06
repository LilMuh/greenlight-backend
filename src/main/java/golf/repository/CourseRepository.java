package golf.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import golf.model.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** 全部球场，按 id 升序，给前端下拉框用。 */
    List<Course> findAllByOrderByIdAsc();

    /**
     * 这批 slug 里，地址/评分该去 Google Maps 刷新的那些。
     *
     * 两个条件缺一不可：
     *   address is null —— 从没查过。新球场是靠 changeset 插进来的，插入时 updated_at
     *     就是 now()，只看时间的话它要白等一个周期才会被补数据，而新球场恰恰最需要立刻补。
     *   updated_at < cutoff —— 查过但过期了。
     */
    @Query("""
            select c from Course c
            where c.slug in :slugs
              and (c.address is null or c.updatedAt is null or c.updatedAt < :cutoff)
            order by c.id asc
            """)
    List<Course> findStale(@Param("slugs") Collection<String> slugs, @Param("cutoff") Instant cutoff);

    /** 写回 Google Maps 信息时按 slug 定位——scraper 那边只认 slug，不认数字 id。 */
    List<Course> findBySlugIn(Collection<String> slugs);
}
