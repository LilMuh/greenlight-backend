package golf.service.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * prod 实现：走 SMTP 真发邮件。greenlight.mail.mode=prod 时启用，
 * 需要在配置里给好 spring.mail.* 与发件地址 greenlight.mail.from。
 */
@Component
@ConditionalOnProperty(name = "greenlight.mail.mode", havingValue = "prod")
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public SmtpMailSender(JavaMailSender javaMailSender, @Value("${greenlight.mail.from}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}
