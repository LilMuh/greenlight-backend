package golf.error;

import java.util.List;

import golf.model.dto.ApiErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误出口的映射表。这里守的是一条规矩：
 * **API 的每一个非 2xx 响应都带一个有含义的 code**，绝不漏出 Spring 的 Whitelabel HTML。
 *
 * 为什么值得单独测：这些 handler 平时一个都不会被调到，坏了也没有任何症状，
 * 直到线上真出错的那一刻——那时候拿到的是一坨 HTML，前端只能显示「出错了」，
 * 而真正的原因（少了个参数、密钥不对、后端有 bug）谁也看不见。
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void 业务异常按自己的code和状态回() {
        ResponseEntity<ApiErrorDto> response =
                handler.handle(new ApiException(ApiErrorCode.WATCH_DUPLICATE, "already there"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("WATCH_DUPLICATE");
        assertThat(response.getBody().message()).isEqualTo("already there");
    }

    @Test
    void 请求体不是合法json() {
        ResponseEntity<ApiErrorDto> response =
                handler.handle(new HttpMessageNotReadableException("boom", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_JSON_BODY");
    }

    @Test
    void 少了必填参数时把参数名带上() {
        ResponseEntity<ApiErrorDto> response =
                handler.handle(new MissingServletRequestParameterException("date", "LocalDate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MISSING_PARAMETER");
        // 不带参数名的话，调用方只知道"少了点什么"
        assertThat(response.getBody().message()).contains("date");
    }

    @Test
    void 参数值转不成目标类型() {
        ResponseEntity<ApiErrorDto> response = handler.handle(new MethodArgumentTypeMismatchException(
                "not-a-date", java.time.LocalDate.class, "date", mock(), null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().message()).contains("date").contains("not-a-date");
    }

    @Test
    void 方法不对() {
        ResponseEntity<ApiErrorDto> response =
                handler.handle(new HttpRequestMethodNotSupportedException("DELETE", List.of("GET")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void 路径不存在() {
        ResponseEntity<ApiErrorDto> response =
                handler.handleNotFound(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ENDPOINT_NOT_FOUND");
    }

    /**
     * 兜底那条不是"未知错误"：它明确表示后端出 bug 了，前端据此说「不是你的问题」。
     * 而且回给调用方的 message 是固定的一句，不把内部异常信息往外漏。
     */
    @Test
    void 没预料到的异常走INTERNAL_ERROR且不外泄细节() {
        ResponseEntity<ApiErrorDto> response =
                handler.handle(new NullPointerException("watch.getCourse() is null at line 42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("line 42").doesNotContain("NullPointer");
    }

    /** 每个 code 都得有状态码，且别把服务端错误标成 2xx。 */
    @Test
    void 每个错误码都映射到一个非2xx状态() {
        for (ApiErrorCode code : ApiErrorCode.values()) {
            assertThat(code.status()).as("%s 的状态码", code).isNotNull();
            assertThat(code.status().is2xxSuccessful()).as("%s 不该是 2xx", code).isFalse();
        }
    }

    /** MethodArgumentTypeMismatchException 要一个 MethodParameter，随便给一个真的。 */
    private static MethodParameter mock() {
        try {
            return new MethodParameter(
                    ApiExceptionHandlerTest.class.getDeclaredMethod("参数值转不成目标类型"), -1);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
