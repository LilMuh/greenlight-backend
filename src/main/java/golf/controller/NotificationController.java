package golf.controller;

import java.util.Map;

import golf.service.mail.AlertMailFactory;
import golf.service.mail.AlertMailFactory.Kind;
import golf.service.mail.AlertMailFactory.RenderedMail;
import golf.service.mail.MailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件模板的两个自检入口，都用样例数据走真实模板，不用等一轮抓取命中：
 *   GET  /api/notifications/preview  在浏览器里看正文长什么样（改模板 → 刷新）
 *   POST /api/notifications/test     真发一封到指定邮箱，验证 SMTP 通不通、
 *                                    以及正文在你自己的邮件客户端里渲染成什么样
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final MailSender mailSender;
    private final AlertMailFactory alertMailFactory;
    private final String mailMode;

    public NotificationController(
            MailSender mailSender,
            AlertMailFactory alertMailFactory,
            @Value("${greenlight.mail.mode:dev}") String mailMode) {
        this.mailSender = mailSender;
        this.alertMailFactory = alertMailFactory;
        this.mailMode = mailMode;
    }

    /**
     * 浏览器直接打开就能看排版。kind=new（默认）看上升沿那封，kind=baseline 看新建 watch 那封。
     * dev 模式下这是迭代模板最快的路子：改完 .html 文件刷新页面即可
     * （spring.thymeleaf.cache=false 时不用重启）。
     */
    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@RequestParam(value = "kind", defaultValue = "new") String kind) {
        return alertMailFactory.renderSample(parseKind(kind)).html();
    }

    /** dev 模式下只会写日志（符合预期）；要真收到信得先把 greenlight.mail.mode 切成 prod。 */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTest(
            @RequestParam("to") String to,
            @RequestParam(value = "kind", defaultValue = "new") String kind) {
        try {
            RenderedMail mail = alertMailFactory.renderSample(parseKind(kind));
            mailSender.send(to, mail.subject(), mail.html());
            return ResponseEntity.ok(Map.of("mode", mailMode, "to", to, "subject", mail.subject(), "sent", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("mode", mailMode, "to", to, "sent", false, "error", String.valueOf(e.getMessage())));
        }
    }

    private static Kind parseKind(String kind) {
        return "baseline".equalsIgnoreCase(kind) ? Kind.BASELINE : Kind.NEW;
    }
}
