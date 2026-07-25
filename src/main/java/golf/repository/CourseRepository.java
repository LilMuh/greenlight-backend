package golf.repository;

import java.util.List;

import golf.model.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** 全部球场，按 id 升序，给前端下拉框用。 */
    List<Course> findAllByOrderByIdAsc();
}
