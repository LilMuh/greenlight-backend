package golf.service.mail;

/**
 * 发一封纯文本邮件。两种实现按 greenlight.mail.mode 二选一注入：
 * dev（默认）只写日志、不真发；prod 走 SMTP。业务代码只依赖这个接口。
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
