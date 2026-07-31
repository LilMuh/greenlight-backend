package golf.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import golf.model.entity.TeeTime;
import golf.model.entity.WatchConfig;
import golf.repository.WatchConfigRepository;
import golf.service.mail.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * M3 通知：什么时候给一条 watch 发邮件。
 *
 * 去重不靠账本表，而是拿库里的 available_seats 做「上升沿」判断：
 *   - 新建 watch 时立刻发一封**基准**邮件，列出此刻已满足的时段（没有就不发）；
 *   - 之后每轮抓取：抓之前给每条 watch 拍一张「当前满足的时段 id 快照」，抓完再算一遍，
 *     只对「这轮新变得满足」的时段（after 有、before 没有）发邮件；没变化的不打扰。
 *
 * 「满足」= 落在 watch 的球场/日期/时间/价格窗口内 且 available_seats ≥ 需求人数
 * （即 WatchMatchService.findMatchingRows 的结果）。时段 id 跨轮稳定：同一时段被 upsert
 * 更新的是同一行；被订满会停在 available_seats=0（掉出满足集），重新放开又会回到满足集，
 * 于是「订走再放开」天然形成一次新的上升沿，会再发一封。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final WatchConfigRepository watchConfigRepository;
    private final WatchMatchService watchMatchService;
    private final MailSender mailSender;

    public NotificationService(
            WatchConfigRepository watchConfigRepository,
            WatchMatchService watchMatchService,
            MailSender mailSender) {
        this.watchConfigRepository = watchConfigRepository;
        this.watchMatchService = watchMatchService;
        this.mailSender = mailSender;
    }

    /** 新建 watch 时的基准邮件：列出此刻已满足的时段；一个都没有就不发。 */
    public void sendBaseline(WatchConfig watch) {
        List<TeeTime> currentlyMatching = watchMatchService.findMatchingRows(watch);
        if (currentlyMatching.isEmpty()) {
            log.info("watch#{} 新建：当前无满足时段，不发基准邮件", watch.getId());
            return;
        }
        String subject = "GreenLight · " + watch.getCourse().getName() + " 当前有 " + currentlyMatching.size() + " 个可约时段";
        mailSender.send(watch.getEmail(), subject, buildBody("以下是当前符合你关注条件的开球时段：", currentlyMatching));
        log.info("watch#{} 新建：已发基准邮件（{} 个时段）", watch.getId(), currentlyMatching.size());
    }

    /** 抓取前：给每条启用中的 watch 拍下「当前满足的时段 id」快照。 */
    public Map<Long, Set<Long>> snapshotSatisfying() {
        Map<Long, Set<Long>> byWatchId = new HashMap<>();
        for (WatchConfig watch : watchConfigRepository.findByActiveTrue()) {
            byWatchId.put(watch.getId(), matchingIds(watch));
        }
        return byWatchId;
    }

    /** 抓取后：对比快照，只对「本轮新变得满足」的时段发邮件。 */
    public void notifyRisingEdges(Map<Long, Set<Long>> before) {
        for (WatchConfig watch : watchConfigRepository.findByActiveTrue()) {
            Set<Long> satisfiedBefore = before.getOrDefault(watch.getId(), Set.of());
            List<TeeTime> risen = watchMatchService.findMatchingRows(watch).stream()
                    .filter(teeTime -> !satisfiedBefore.contains(teeTime.getId()))
                    .toList();
            if (risen.isEmpty()) {
                continue;
            }
            String subject = "GreenLight · " + watch.getCourse().getName() + " 新增 " + risen.size() + " 个可约时段";
            mailSender.send(watch.getEmail(), subject, buildBody("以下开球时段刚刚变为可约：", risen));
            log.info("watch#{} {} 触发通知：{} 个新满足时段", watch.getId(), watch.getCourse().getName(), risen.size());
        }
    }

    private Set<Long> matchingIds(WatchConfig watch) {
        return watchMatchService.findMatchingRows(watch).stream()
                .map(TeeTime::getId)
                .collect(Collectors.toSet());
    }

    /** 把命中时段拼成一封纯文本邮件正文。 */
    private String buildBody(String intro, List<TeeTime> teeTimes) {
        StringBuilder body = new StringBuilder(intro).append("\n\n");
        for (TeeTime teeTime : teeTimes) {
            body.append("· ")
                    .append(teeTime.getPlayDate()).append(" ").append(teeTime.getTimeLocal())
                    .append("  ").append(teeTime.getAvailableSeats()).append(" 个空位")
                    .append(teeTime.getPrice() == null ? "" : "  CAD$" + teeTime.getPrice())
                    .append("\n");
        }
        return body.toString();
    }
}
