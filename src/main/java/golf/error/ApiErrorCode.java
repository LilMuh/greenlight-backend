package golf.error;

import org.springframework.http.HttpStatus;

/**
 * /api/** 出错时回给前端的机器可读代号。
 *
 * 规矩：**API 的每一个非 2xx 响应都带其中一个 code**，没有例外。漏掉一处的代价是
 * 前端只能显示一句笼统的「出错了」，而人对着这句话没法知道下一步该做什么。
 * 兜底那条走 INTERNAL_ERROR（见下），它不是"未知错误"的同义词——它有确切含义：
 * 后端出了没预料到的异常，日志里有栈。
 *
 * 前端按 code 分支显示文案（见 greenlight-frontend 的 config.js），所以这些字符串
 * 是跨仓库的契约：**只能新增，不能改名**——改一个名字，线上那份静态页会静默地
 * 落回通用提示，页面照常渲染，谁都不会发现。
 *
 * 文案不在这里：同一个 code 在不同语言、不同页面上要说的话不一样，那是前端的事。
 * 后端 message 只是给调试和 curl 看的英文短句。
 */
public enum ApiErrorCode {

    // --- 业务规则 ------------------------------------------------------------

    /** 这个邮箱在这个球场已经有一条 watch 了。POST 会自动改成更新，所以只有 PUT 撞得到。 */
    WATCH_DUPLICATE(HttpStatus.CONFLICT),

    /** 改一条 watch 时想换球场。一条 watch 的球场是它的身份的一半，不给改，请新建。 */
    WATCH_COURSE_IMMUTABLE(HttpStatus.BAD_REQUEST),

    /** 一个星期都没勾。存下来既不抓也不命中，当场拒掉。 */
    WATCH_WEEKDAYS_REQUIRED(HttpStatus.BAD_REQUEST),

    /** 要改/删的那条 watch 不在了（多半是另一个标签页删掉了）。 */
    WATCH_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 请求里的球场 id 在 course 表里查不到。 */
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 测试邮件没发出去（SMTP 拒了、网络不通）。502：是下游的问题，不是这次请求的问题。 */
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY),

    // --- 请求本身不合法 ------------------------------------------------------

    /** 没带或带错 X-Greenlight-Key。见 ApiKeyFilter。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** 请求体不是合法 JSON，或者该有 body 却没有。 */
    MALFORMED_JSON_BODY(HttpStatus.BAD_REQUEST),

    /** 少了必填的查询参数，如 /api/tee-times 不带 date。 */
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST),

    /** 参数在，但值转不成要的类型（date=abc、id=abc）。 */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),

    /** 路径对、方法不对（往 GET 的接口 POST）。 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** 这个路径后端根本没有。 */
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND),

    // --- 兜底 ----------------------------------------------------------------

    /**
     * 后端出了没预料到的异常。含义明确：**这是后端的 bug，不是调用方填错了**，
     * 所以前端该说的是「不是你的问题，稍后再试」，而不是让人回去改表单。
     * 每次都会带栈写进 error 级日志。
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
