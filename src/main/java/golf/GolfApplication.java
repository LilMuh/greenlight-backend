package golf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 打开定时能力，供 TeeTimeScrapeTask 周期性触发抓取
public class GolfApplication {
    public static void main(String[] args) {
        SpringApplication.run(GolfApplication.class, args);
    }
}
