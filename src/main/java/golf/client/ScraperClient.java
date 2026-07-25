package golf.client;

import golf.model.dto.ScrapeRequestDto;
import golf.model.dto.ScrapeResultDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 调 greenlight-scraper 的 HTTP 客户端。 */
@Component
public class ScraperClient {

    private static final String BASE_URL = "http://localhost:8090";

    private final RestClient rest;

    public ScraperClient(RestClient.Builder builder) {
        this.rest = builder.baseUrl(BASE_URL).build();
    }

    /** 触发一次抓取+写库，返回摘要。 */
    public ScrapeResultDto scrape(ScrapeRequestDto request) {
        return rest.post().uri("/scrape").body(request).retrieve().body(ScrapeResultDto.class);
    }
}
