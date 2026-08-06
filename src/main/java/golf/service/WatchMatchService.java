package golf.service;

import java.math.BigDecimal;
import java.util.List;

import golf.model.dto.MatchResultDto;
import golf.model.dto.TeeTimeDto;
import golf.model.entity.Course;
import golf.model.entity.TeeTime;
import golf.model.entity.WatchConfig;
import golf.repository.TeeTimeRepository;
import golf.repository.WatchConfigRepository;
import org.springframework.stereotype.Service;

/**
 * 匹配引擎（只读）：把每条启用中的 watch_config 和已落库的 tee_time 对上，
 * 算出符合条件的空位。只负责“算命中”，什么时候发邮件归 NotificationService。
 *
 * 两个出口共用同一个查询（findMatchingRows）：
 *   - findAllMatches   → GET /api/matches，给前端卡片上那个「N matching now」角标
 *   - findMatchingRows → NotificationService 的基准邮件和上升沿判断都拿它当数据源
 */
@Service
public class WatchMatchService {

    private final WatchConfigRepository watchConfigRepository;
    private final TeeTimeRepository teeTimeRepository;
    private final TeeTimeService teeTimeService;

    public WatchMatchService(
            WatchConfigRepository watchConfigRepository,
            TeeTimeRepository teeTimeRepository,
            TeeTimeService teeTimeService) {
        this.watchConfigRepository = watchConfigRepository;
        this.teeTimeRepository = teeTimeRepository;
        this.teeTimeService = teeTimeService;
    }

    /** 遍历所有启用中的 watch，逐条算出当前命中，返回给前端展示。 */
    public List<MatchResultDto> findAllMatches() {
        return watchConfigRepository.findByActiveTrue().stream()
                .map(this::matchOne)
                .toList();
    }

    /**
     * 一条 watch 当前命中的原始行（含 id / 空位数），供匹配展示和通知去重共用。
     * 条件：球场、日期区间、时间区间、空位数 ≥ 需求人数、价格 ≤ 上限、仍可订。
     */
    public List<TeeTime> findMatchingRows(WatchConfig watch) {
        Course course = watch.getCourse();
        // maxPrice 存的是加元整数，tee_time.price 是 BigDecimal，比较前统一成 BigDecimal。
        BigDecimal maxPrice = BigDecimal.valueOf(watch.getMaxPrice());

        return teeTimeRepository
                .findByCourse_IdAndPlayDateBetweenAndTimeLocalBetweenAndAvailableSeatsGreaterThanEqualAndPriceLessThanEqualAndAvailableTrueOrderByPlayDateAscTimeLocalAsc(
                        course.getId(),
                        watch.getDateStart(),
                        watch.getDateEnd(),
                        watch.getTimeStart(),
                        watch.getTimeEnd(),
                        watch.getPlayers(),
                        maxPrice);
    }

    /** 算一条 watch 的命中并打包成前端 DTO。 */
    private MatchResultDto matchOne(WatchConfig watch) {
        Course course = watch.getCourse();
        List<TeeTimeDto> hits = findMatchingRows(watch).stream().map(teeTimeService::toDto).toList();

        return new MatchResultDto(
                watch.getId(),
                course.getId(),
                course.getSlug(),
                course.getName(),
                watch.getEmail(),
                hits.size(),
                hits);
    }
}
