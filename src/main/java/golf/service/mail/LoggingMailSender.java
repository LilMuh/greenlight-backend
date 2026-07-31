package golf.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * dev 默认实现：不真发邮件，只把内容打进日志，方便本地验证通知逻辑。
 * greenlight.mail.mode 缺省或为 dev 时启用。
 */
@Component
@ConditionalOnProperty(name = "greenlight.mail.mode", havingValue = "dev", matchIfMissing = true)
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[邮件-dev] 收件人={} 主题={}\n{}", to, subject, body);
    }
}
