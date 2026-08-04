package golf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 打开定时能力，供 TeeTimeScrapeTask 周期性触发抓取
@EnableAsync // 新建 watch 后的补数抓取走异步，别卡住 HTTP 响应（见 WatchBootstrapListener）
@ConfigurationPropertiesScan // 绑定 MailProperties 等 @ConfigurationProperties
public class GolfApplication {
    public static void main(String[] args) {
        SpringApplication.run(GolfApplication.class, args);
    }
}
