package golf.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import golf.model.entity.TeeTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeeTimeRepository extends JpaRepository<TeeTime, Long> {

    /** 某天所有还有空位（available_seats > 0）的时段，按时间排序。0 空位的不返回。 */
    List<TeeTime> findByPlayDateAndAvailableSeatsGreaterThanOrderByTimeLocalAsc(LocalDate playDate, int minSeats);

    /** 某天某球场（按 slug）还有空位的时段。 */
    List<TeeTime> findByPlayDateAndCourse_SlugAndAvailableSeatsGreaterThanOrderByTimeLocalAsc(
            LocalDate playDate, String slug, int minSeats);

    /**
     * 匹配引擎用：一条 watch 的全部命中时段。
     * 条件对应 watch 字段——球场、日期区间、时间区间（"HH:mm" 字符串按字典序即时间序）、
     * 空位数不小于需求人数、单人价不超过上限、仍可订。
     * timeLocal / playDate 双排序，命中列表按“先到的日期、再到的时刻”展示。
     */
    List<TeeTime> findByCourse_IdAndPlayDateBetweenAndTimeLocalBetweenAndAvailableSeatsGreaterThanEqualAndPriceLessThanEqualAndAvailableTrueOrderByPlayDateAscTimeLocalAsc(
            Long courseId,
            LocalDate playDateStart,
            LocalDate playDateEnd,
            String timeLocalStart,
            String timeLocalEnd,
            int minSeats,
            BigDecimal maxPrice);
}
