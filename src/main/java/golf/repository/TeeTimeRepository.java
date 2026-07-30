package golf.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import golf.model.entity.TeeTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeeTimeRepository extends JpaRepository<TeeTime, Long> {

    /** 某天所有仍可订的时段，按时间排序。 */
    List<TeeTime> findByPlayDateAndAvailableTrueOrderByTimeLocalAsc(LocalDate playDate);

    /** 某天某球场（按 slug）仍可订的时段。 */
    List<TeeTime> findByPlayDateAndCourse_SlugAndAvailableTrueOrderByTimeLocalAsc(LocalDate playDate, String slug);

    /**
     * 匹配引擎用：一条 watch 的全部命中时段。
     * 条件对应 watch 字段——球场、日期区间、时间区间（"HH:mm" 字符串按字典序即时间序）、
     * 可订人数不小于需求、单人价不超过上限、仍可订。
     * timeLocal / playDate 双排序，命中列表按“先到的日期、再到的时刻”展示。
     */
    List<TeeTime> findByCourse_IdAndPlayDateBetweenAndTimeLocalBetweenAndPlayersGreaterThanEqualAndPriceLessThanEqualAndAvailableTrueOrderByPlayDateAscTimeLocalAsc(
            Long courseId,
            LocalDate playDateStart,
            LocalDate playDateEnd,
            String timeLocalStart,
            String timeLocalEnd,
            int players,
            BigDecimal maxPrice);
}
