package golf.error;

import org.springframework.http.HttpStatus;

/**
 * /api/** 出错时回给前端的机器可读代号。
 *
 * 前端按 code 分支显示文案（见 greenlight-frontend 的 config.js），所以这些字符串
 * 是跨仓库的契约：**只能新增，不能改名**——改一个名字，线上那份静态页会静默地
 * 落回通用提示，页面照常渲染，谁都不会发现。
 *
 * 文案不在这里：同一个 code 在不同语言、不同页面上要说的话不一样，那是前端的事。
 * 后端 message 只是给调试和 curl 看的英文短句。
 */
public enum ApiErrorCode {

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

    /** 兜底：没归类的非法入参。前端认不出就显示通用提示。 */
    BAD_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
