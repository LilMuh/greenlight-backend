package golf.service.mail;

/**
 * 发一封 HTML 邮件。两种实现按 greenlight.mail.mode 二选一注入：
 * dev（默认）只写日志、不真发；prod 走 SMTP。业务代码只依赖这个接口。
 *
 * 只发 HTML、不带 text/plain 兜底：真人收件人里看不到 HTML 的比例已接近于零，
 * 维护两份文案不划算。正文长什么样在 dev 模式下用 GET /api/notifications/preview 看。
 */
public interface MailSender {

    void send(String to, String subject, String html);
}
