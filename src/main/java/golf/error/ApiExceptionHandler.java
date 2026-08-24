package golf.error;

import golf.model.dto.ApiErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * /api/** 的错误出口。**这里是唯一的出口**：任何异常都会落到下面某个 handler，
 * 渲染成 {"code","message"}，绝不让 Spring 的 Whitelabel HTML 漏出去。
 *
 * 为什么较真到这个程度：在这之前，service 抛的 IllegalArgumentException 一律变成
 * 500 + 一段 HTML，前端只能看出「请求挂了」，于是把所有失败都当成后端离线——
 * 明明是用户填错了。一个没有 code 的错误响应，在界面上就等于没有信息。
 *
 * 排在最后的 Exception 兜底不是"未知错误"：它有确切含义 = 后端出 bug 了，
 * 而且每次都带栈写进 error 日志。前端据此说「不是你的问题」，不会让人回去改表单。
 *
 * handler 之间不用排序：Spring 自己按异常类型的具体程度挑最贴近的那个。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 业务规则拒绝：code 和状态都由 ApiErrorCode 带着。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorDto> handle(ApiException exception) {
        log.debug("{}: {}", exception.code(), exception.getMessage());
        return body(exception.code(), exception.getMessage());
    }

    /** 请求体不是合法 JSON，或者该有 body 却是空的。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorDto> handle(HttpMessageNotReadableException exception) {
        return body(ApiErrorCode.MALFORMED_JSON_BODY, "Request body is not readable JSON");
    }

    /** 少了必填的查询参数。把参数名带上，不然调用方得靠猜。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorDto> handle(MissingServletRequestParameterException exception) {
        return body(ApiErrorCode.MISSING_PARAMETER,
                "Missing required parameter '" + exception.getParameterName() + "'");
    }

    /** 参数在但转不成目标类型：date=abc、id=abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDto> handle(MethodArgumentTypeMismatchException exception) {
        return body(ApiErrorCode.INVALID_PARAMETER,
                "Parameter '" + exception.getName() + "' has an unusable value: " + exception.getValue());
    }

    /** @Valid 校验没过。现在还没有接口用 @Valid，但加了校验就不会漏成 500。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handle(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request is not valid");
        return body(ApiErrorCode.INVALID_PARAMETER, detail);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorDto> handle(HttpRequestMethodNotSupportedException exception) {
        return body(ApiErrorCode.METHOD_NOT_ALLOWED,
                exception.getMethod() + " is not supported on this path");
    }

    /** 路径不存在。两种异常都收：前者要开 throw-exception-if-no-handler-found，后者是静态资源找不着。 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorDto> handleNotFound(Exception exception) {
        return body(ApiErrorCode.ENDPOINT_NOT_FOUND, "No such endpoint");
    }

    /**
     * 兜底。到这儿说明后端有 bug——所以是 error 级别、带栈，而不是悄悄记一笔。
     * 回给调用方的 message 是固定的一句话，不把内部异常信息往外漏。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handle(Exception exception) {
        log.error("unhandled exception", exception);
        return body(ApiErrorCode.INTERNAL_ERROR, "Something went wrong on the server");
    }

    private static ResponseEntity<ApiErrorDto> body(ApiErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(new ApiErrorDto(code.name(), message));
    }
}
