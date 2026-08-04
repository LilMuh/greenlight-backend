package golf.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * dev 默认实现：不真发邮件，只写日志。greenlight.mail.mode 缺省或为 dev 时启用。
 *
 * 只打收件人和标题，不打正文——正文现在是几 KB 的 HTML，倒进控制台会把日志淹掉。
 * 想看正文长什么样用 GET /api/notifications/preview；想知道命中了哪些时段看
 * NotificationService 打的那行明细。
 */
@Component
@ConditionalOnProperty(name = "greenlight.mail.mode", havingValue = "dev", matchIfMissing = true)
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String html) {
        log.info("[mail-dev] not actually sent: to={} subject=\"{}\" ({} chars of HTML)",
                to, subject, html == null ? 0 : html.length());
    }
}
