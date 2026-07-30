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
 * 算出符合条件的空位。目前只负责“算命中”，不发通知——通知留给后续 M3。
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

    /** 算一条 watch 的命中：按其条件查 tee_time，转成 DTO 打包。 */
    private MatchResultDto matchOne(WatchConfig watch) {
        Course course = watch.getCourse();
        // maxPrice 存的是加元整数，tee_time.price 是 BigDecimal，比较前统一成 BigDecimal。
        BigDecimal maxPrice = BigDecimal.valueOf(watch.getMaxPrice());

        List<TeeTime> matchedRows = teeTimeRepository
                .findByCourse_IdAndPlayDateBetweenAndTimeLocalBetweenAndPlayersGreaterThanEqualAndPriceLessThanEqualAndAvailableTrueOrderByPlayDateAscTimeLocalAsc(
                        course.getId(),
                        watch.getDateStart(),
                        watch.getDateEnd(),
                        watch.getTimeStart(),
                        watch.getTimeEnd(),
                        watch.getPlayers(),
                        maxPrice);

        List<TeeTimeDto> hits = matchedRows.stream().map(teeTimeService::toDto).toList();

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
