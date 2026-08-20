package golf.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /api/** 的共享密钥关卡。后端没有账号体系，而 Tailscale Funnel 把它放到了公网上，
 * 建/改/删 watch、触发抓取、发测试邮件这些写接口不能就这么裸着。
 */
class ApiKeyFilterTest {

    private static final String KEY = "s3cret";

    private record Result(int status, boolean reachedApp, String allowOrigin) {
    }

    /** 跑一次过滤器，返回状态码和请求有没有走到后面的应用。 */
    private static Result run(String configuredKey, String method, String uri, String sentKey, String origin)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (sentKey != null) request.addHeader(ApiKeyFilter.HEADER, sentKey);
        if (origin != null) request.addHeader("Origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new ApiKeyFilter(configuredKey).doFilter(request, response, chain);

        return new Result(
                response.getStatus(),
                chain.getRequest() != null,
                response.getHeader("Access-Control-Allow-Origin"));
    }

    /** 本地开发不配密钥：整道关卡关闭，不用带头也能调。 */
    @Test
    void passesEverythingWhenNoKeyConfigured() throws Exception {
        assertThat(run("", "POST", "/api/watch-configs", null, null).reachedApp()).isTrue();
    }

    @Test
    void passesWhenKeyMatches() throws Exception {
        assertThat(run(KEY, "POST", "/api/watch-configs", KEY, null).reachedApp()).isTrue();
    }

    @Test
    void rejectsWhenKeyMissing() throws Exception {
        Result result = run(KEY, "POST", "/api/watch-configs", null, null);
        assertThat(result.reachedApp()).isFalse();
        assertThat(result.status()).isEqualTo(401);
    }

    @Test
    void rejectsWhenKeyWrong() throws Exception {
        Result result = run(KEY, "DELETE", "/api/watch-configs/1", "guess", null);
        assertThat(result.reachedApp()).isFalse();
        assertThat(result.status()).isEqualTo(401);
    }

    /**
     * 预检请求不带自定义头（浏览器就是这么定的），拦掉的话正式请求根本发不出去。
     * 预检本身不碰数据，放行是安全的。
     */
    @Test
    void letsCorsPreflightThrough() throws Exception {
        assertThat(run(KEY, "OPTIONS", "/api/watch-configs", null, "https://lilmuh.github.io").reachedApp()).isTrue();
    }

    /** 探活留给 Funnel 和监控用，它不泄露任何东西。 */
    @Test
    void leavesHealthOpen() throws Exception {
        assertThat(run(KEY, "GET", "/api/health", null, null).reachedApp()).isTrue();
    }

    /**
     * 拒绝时也要带 CORS 头，否则浏览器只报一句跨域失败，
     * 看不出真正的原因是密钥不对——排查时能省很多时间。
     */
    @Test
    void rejectionStillCarriesCorsHeaderForAllowedOrigin() throws Exception {
        Result result = run(KEY, "GET", "/api/courses", null, "https://lilmuh.github.io");
        assertThat(result.status()).isEqualTo(401);
        assertThat(result.allowOrigin()).isEqualTo("https://lilmuh.github.io");
    }

    /** 不在白名单里的来源不给回声，免得把 CORS 白名单变成摆设。 */
    @Test
    void rejectionGivesNoCorsHeaderForUnknownOrigin() throws Exception {
        assertThat(run(KEY, "GET", "/api/courses", null, "https://evil.example.com").allowOrigin()).isNull();
    }
}
