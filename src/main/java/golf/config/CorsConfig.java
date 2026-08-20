package golf.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend is served from a different origin in dev (static files),
 * so the browser needs CORS on the /api/** routes.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** ApiKeyFilter 拒绝请求时要回同一套来源白名单，否则浏览器看不出是密钥不对。 */
    public static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500",
            "https://lilmuh.github.io");

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(ALLOWED_ORIGINS.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
