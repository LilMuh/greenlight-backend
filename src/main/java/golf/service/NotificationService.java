package golf.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import golf.model.entity.TeeTime;
import golf.model.entity.WatchConfig;
import golf.repository.WatchConfigRepository;
import golf.service.mail.AlertMailFactory;
import golf.service.mail.AlertMailFactory.Kind;
import golf.service.mail.AlertMailFactory.RenderedMail;
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
 *
 * 邮件长什么样归 AlertMailFactory 和 templates/mail/tee-time-alert.html 管，这里只管时机。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final WatchConfigRepository watchConfigRepository;
    private final WatchMatchService watchMatchService;
    private final AlertMailFactory alertMailFactory;
    private final MailSender mailSender;

    public NotificationService(
            WatchConfigRepository watchConfigRepository,
            WatchMatchService watchMatchService,
            AlertMailFactory alertMailFactory,
            MailSender mailSender) {
        this.watchConfigRepository = watchConfigRepository;
        this.watchMatchService = watchMatchService;
        this.alertMailFactory = alertMailFactory;
        this.mailSender = mailSender;
    }

    /** 新建 watch 时的基准邮件：列出此刻已满足的时段；一个都没有就不发。 */
    public void sendBaseline(WatchConfig watch) {
        List<TeeTime> currentlyMatching = watchMatchService.findMatchingRows(watch);
        if (currentlyMatching.isEmpty()) {
            log.info("watch#{} {} created: nothing matches right now, no baseline mail",
                    watch.getId(), watch.getCourse().getName());
            return;
        }
        if (trySend(watch, currentlyMatching, Kind.BASELINE)) {
            log.info("watch#{} created: baseline mail sent, {} tee times [{}]",
                    watch.getId(), currentlyMatching.size(), describe(currentlyMatching));
        }
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
            List<TeeTime> matching = watchMatchService.findMatchingRows(watch);
            List<TeeTime> risen = matching.stream()
                    .filter(teeTime -> !satisfiedBefore.contains(teeTime.getId()))
                    .toList();
            if (risen.isEmpty()) {
                // 不发邮件也要留痕：否则「这轮到底算过没有」在日志里完全看不出来
                log.info("watch#{} {} no newly matching tee times, no mail (matching now={}, before scrape={})",
                        watch.getId(), watch.getCourse().getName(), matching.size(), satisfiedBefore.size());
                continue;
            }
            if (trySend(watch, risen, Kind.NEW)) {
                log.info("watch#{} {} notified: {} newly matching tee times [{}]",
                        watch.getId(), watch.getCourse().getName(), risen.size(), describe(risen));
            }
        }
    }

    /**
     * 渲染并发一封，失败只记日志不抛出：一条 watch 的收件地址写错/被服务商拒收，
     * 不该让同一轮里其余 watch 的通知全部中断。
     *
     * 注意：发失败的时段在下一轮已经进入「满足集」基线，不会再触发上升沿重发。
     * 真要做重试得另建发送账本表，M3 先接受这个取舍。
     */
    private boolean trySend(WatchConfig watch, List<TeeTime> teeTimes, Kind kind) {
        try {
            RenderedMail mail = alertMailFactory.render(watch, teeTimes, kind);
            mailSender.send(watch.getEmail(), mail.subject(), mail.html());
            return true;
        } catch (Exception e) {
            log.error("watch#{} mail send failed to={}: {}", watch.getId(), watch.getEmail(), e.getMessage());
            return false;
        }
    }

    private Set<Long> matchingIds(WatchConfig watch) {
        return watchMatchService.findMatchingRows(watch).stream()
                .map(TeeTime::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 日志用的时段摘要。正文现在是 HTML，往日志里倒没法看，所以命中了哪些时段
     * 由这里以一行紧凑格式留痕（本地排查基本只需要这个）。
     */
    private static String describe(List<TeeTime> teeTimes) {
        return teeTimes.stream()
                .map(teeTime -> teeTime.getPlayDate() + " " + teeTime.getTimeLocal()
                        + "×" + teeTime.getAvailableSeats())
                .collect(Collectors.joining(", "));
    }
}
