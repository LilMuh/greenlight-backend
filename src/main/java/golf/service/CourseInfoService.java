package golf.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import golf.model.dto.CourseInfoDto;
import golf.model.dto.CourseRefreshDto;
import golf.model.entity.Course;
import golf.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 球场的地址和 Google 评分：什么时候该刷新、抓回来的东西怎么落库。
 *
 * 分工——**抓在 scraper，判断和写在这里**。浏览器能力（OAB）只有 scraper 有，
 * 但 course 表归后端写，所以 scraper 抓完原样返回，由这里写回去。
 *
 * 触发点不是独立的定时器，而是挂在每轮抓取上：TeeTimeScrapeTask 每发一次 /scrape，
 * 就先问这里「本次涉及的球场有谁过期了」，把名单塞进请求，再把响应里的结果交回来写。
 * 这样天然继承了抓取那把串行锁（同时驱动同一个 OAB profile 会打架），不用另外同步。
 *
 * 一轮抓取有多个 job（同一批球场 × 多个日期），但第一个 job 写回后就把 updated_at
 * 推到了现在，后面几个 job 再问就查不出人了——**不用去重，天然只跑一次**。
 */
@Service
public class CourseInfoService {

    private static final Logger log = LoggerFactory.getLogger(CourseInfoService.class);

    private final CourseRepository courseRepository;
    private final Duration maxAge;

    public CourseInfoService(
            CourseRepository courseRepository,
            @Value("${greenlight.course-info.max-age-days:7}") int maxAgeDays) {
        this.courseRepository = courseRepository;
        this.maxAge = Duration.ofDays(maxAgeDays);
    }

    /**
     * 这批球场里该去 Google Maps 刷新的那些，转成发给 scraper 的请求项。
     * 一个都不用刷就返回空列表，scraper 见到空的连浏览器都不会开。
     */
    public List<CourseRefreshDto> findStale(List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        return courseRepository.findStale(slugs, Instant.now().minus(maxAge)).stream()
                .map(course -> new CourseRefreshDto(course.getSlug(), course.getName(), course.getMapsUrl()))
                .toList();
    }

    /**
     * 把 scraper 抓回来的信息写进 course 表。
     *
     * 逐字段判空覆盖：某次只取到地址没取到评分时，只更新地址，**不把库里已有的评分抹成 null**。
     * 全是 null 的记录（页面结构变了、超时）整条跳过——连 updated_at 都不动，
     * 这样下一轮还会重试；要是动了，一次失败就得等满一个周期才会再试。
     */
    @Transactional
    public void apply(List<CourseInfoDto> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }

        Map<String, CourseInfoDto> bySlug = infos.stream()
                .filter(info -> info.slug() != null && hasAnything(info))
                .collect(Collectors.toMap(CourseInfoDto::slug, Function.identity(), (first, second) -> second));

        if (bySlug.isEmpty()) {
            log.warn("Google Maps returned {} course record(s) but every field was empty; nothing written, will retry next round",
                    infos.size());
            return;
        }

        for (Course course : courseRepository.findBySlugIn(bySlug.keySet())) {
            CourseInfoDto info = bySlug.get(course.getSlug());
            if (info.address() != null) {
                course.setAddress(info.address());
            }
            if (info.rating() != null) {
                course.setRating(info.rating());
            }
            if (info.ratingCount() != null) {
                course.setRatingCount(info.ratingCount());
            }
            if (info.mapsUrl() != null) {
                course.setMapsUrl(info.mapsUrl());
            }
            // updated_at 同时是刷新判据，不写的话下一轮会重复去查同一个球场
            course.setUpdatedAt(Instant.now());

            log.info("course {} refreshed from Google Maps: address={} rating={} ({})",
                    course.getSlug(), course.getAddress(), course.getRating(), course.getRatingCount());
        }
        // 托管实体，事务提交时脏检查会 flush，不用显式 save
    }

    /** 三个业务字段全空 = 这次什么都没拿到，不值得写库。mapsUrl 只是定位用，不算数据。 */
    private static boolean hasAnything(CourseInfoDto info) {
        return info.address() != null || info.rating() != null || info.ratingCount() != null;
    }
}
