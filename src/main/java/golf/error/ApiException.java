package golf.error;

/**
 * 带错误代号的业务异常。service 层抛，ApiExceptionHandler 渲染成
 * {"code": "...", "message": "..."} 和代号自带的 HTTP 状态。
 *
 * 刻意不用 ResponseStatusException：那会把 HTTP 状态写进 service 层，
 * 而 service 该说的是「这条 watch 重复了」，不是「回 409」。状态码归 ApiErrorCode。
 */
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;

    public ApiException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode code() {
        return code;
    }
}
