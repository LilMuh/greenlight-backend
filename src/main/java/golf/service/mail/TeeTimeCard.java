package golf.service.mail;

/**
 * 邮件模板里一行时段的视图模型。
 *
 * 全部是已经格式化好的字符串：时间用 24 小时制、价格怎么写、空位怎么数，
 * 这些决定放在 Java 里做完，模板只负责摆位置。这样模板里不会出现格式化表达式，
 * 改排版和改文案不会互相干扰。
 *
 * 日期和预订链接不在这里——它们属于整个日期分组，见 {@link TeeTimeDateGroup}。
 */
public record TeeTimeCard(
        String time,   // "18:18"
        String seats,  // "4 spots"
        String price   // "$52.00"；抓不到价格时为 null，那一格渲染成空（实际数据里不会出现）
) {
}
