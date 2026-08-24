package golf.model.dto;

/**
 * /api/** 的统一错误体。
 *
 * code 是给代码看的（前端据此分支显示文案，取值见 golf.error.ApiErrorCode），
 * message 是给人看的英文短句——调试和 curl 用，前端不该把它直接摆到界面上，
 * 因为它不随界面语言变化。
 */
public record ApiErrorDto(String code, String message) {
}
