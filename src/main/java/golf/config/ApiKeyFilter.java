package golf.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import golf.error.ApiErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/** 的共享密钥关卡。
 *
 * 这个后端没有账号体系，而 Tailscale Funnel 把它挂到了公网上：建/改/删 watch、
 * 触发抓取、往任意邮箱发测试邮件，本来都是任何人都能调的。CORS 挡不住这些——
 * 那是浏览器里的约定，curl 和脚本根本不看。
 *
 * 说清楚这道关卡的边界：前端是公开仓库里的静态页，密钥随构建产物一起发出去，
 * 打开开发者工具就能拿到。它挡的是扫到域名随手试的人和自动扫描器，不是有心人。
 * 真要挡住得上账号体系。
 *
 * greenlight.api.key 留空（本地默认）则整道关卡关闭，本地开发不用带头。
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Greenlight-Key";

    private final String expectedKey;

    public ApiKeyFilter(@Value("${greenlight.api.key:}") String expectedKey) {
        this.expectedKey = expectedKey == null ? "" : expectedKey.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedKey.isEmpty()
                // 预检不带自定义头，拦了正式请求就永远发不出来；它也不碰数据
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")
                // 探活留给 Funnel 和监控，不泄露任何东西
                || request.getRequestURI().equals("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expectedKey.equals(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }

        // 过滤器跑在 Spring MVC 的 CORS 处理之前，这里不补头的话浏览器只会报一句
        // 跨域失败，看不出真正原因是密钥不对。只对白名单里的来源回声。
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && CorsConfig.ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        // 形状和 ApiExceptionHandler 回的一样：/api/** 的每个非 2xx 都带 code，没有例外。
        // 这条走不到那个 handler（过滤器在 DispatcherServlet 之前），所以在这儿自己拼。
        response.setStatus(ApiErrorCode.UNAUTHORIZED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"" + ApiErrorCode.UNAUTHORIZED.name() + "\","
                        + "\"message\":\"missing or bad " + HEADER + "\"}");
    }
}
