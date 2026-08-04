package golf.service.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * prod 实现：走 SMTP 真发邮件。greenlight.mail.mode=prod 时启用，
 * 需要在配置里给好 spring.mail.*（host/port/username/password）与发件地址 greenlight.mail.from。
 *
 * 配置缺失时在启动阶段就报错退出：prod 模式下「以为在发、其实一封都没出去」比启动失败危险得多。
 */
@Component
@ConditionalOnProperty(name = "greenlight.mail.mode", havingValue = "prod")
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public SmtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${greenlight.mail.from:}") String fromAddress,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        requireConfigured("spring.mail.host", host);
        requireConfigured("spring.mail.username", username);
        requireConfigured("spring.mail.password", password);
        requireConfigured("greenlight.mail.from", fromAddress);
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        log.info("Mail mode=prod: sending for real via {} as {}", host, fromAddress);
    }

    /**
     * 正文是 HTML，所以用 MimeMessage 而不是 SimpleMailMessage
     * （后者只能发 text/plain，HTML 会被当成字面字符串显示出来）。
     * multipart=false：正文里没有内嵌图片或附件，不需要 multipart 容器。
     */
    @Override
    public void send(String to, String subject, String html) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            javaMailSender.send(message);
            log.info("Mail sent to={} subject=\"{}\"", to, subject);
        } catch (Exception e) {
            // 包成 unchecked 往上抛：调用方（NotificationService）统一 catch 后只记日志，
            // 一条 watch 发失败不该中断同一轮里其余 watch 的通知。
            throw new IllegalStateException("failed to send mail to " + to + ": " + e.getMessage(), e);
        }
    }

    private static void requireConfigured(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "greenlight.mail.mode=prod requires " + key + " to be configured, "
                            + "otherwise no notification mail can be sent");
        }
    }
}
