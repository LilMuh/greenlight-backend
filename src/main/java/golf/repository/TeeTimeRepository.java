package golf.repository;

import java.time.LocalDate;
import java.util.List;

import golf.model.entity.TeeTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeeTimeRepository extends JpaRepository<TeeTime, Long> {

    /** 某天所有仍可订的时段，按时间排序。 */
    List<TeeTime> findByPlayDateAndAvailableTrueOrderByTimeLocalAsc(LocalDate playDate);

    /** 某天某球场仍可订的时段。 */
    List<TeeTime> findByPlayDateAndCourseIdAndAvailableTrueOrderByTimeLocalAsc(LocalDate playDate, String courseId);
}
