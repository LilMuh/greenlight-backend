package golf.error;

import golf.model.dto.ApiErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把业务异常渲染成带代号的错误体。
 *
 * 在这之前，service 抛的 IllegalArgumentException（未知球场、一个星期都没勾）
 * 一律变成 500 和一段 Whitelabel HTML：前端只能看出「请求挂了」，
 * 于是把所有失败都当成后端离线——明明是用户填错了。
 *
 * 兜底只收 IllegalArgumentException。别的异常继续交给 Spring 默认处理成 500：
 * 那些是 bug，不该被伪装成一条温和的 400 提示。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorDto> handle(ApiException exception) {
        log.debug("{}: {}", exception.code(), exception.getMessage());
        return ResponseEntity.status(exception.code().status())
                .body(new ApiErrorDto(exception.code().name(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDto> handle(IllegalArgumentException exception) {
        log.debug("bad request: {}", exception.getMessage());
        return ResponseEntity.status(ApiErrorCode.BAD_REQUEST.status())
                .body(new ApiErrorDto(ApiErrorCode.BAD_REQUEST.name(), exception.getMessage()));
    }
}
