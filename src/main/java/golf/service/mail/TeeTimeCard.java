package golf.service.mail;

/**
 * 邮件模板里一张时段卡片的视图模型。
 *
 * 全部是已经格式化好的字符串：日期怎么写、时间用 12 小时制还是 24、价格带不带币种，
 * 这些决定放在 Java 里做完，模板只负责摆位置。这样模板里不会出现格式化表达式，
 * 改排版和改文案不会互相干扰。
 *
 * price / bookingUrl 允许为 null，模板对应的块直接不渲染。
 */
public record TeeTimeCard(
        String date,        // "Fri, Aug 7"
        String time,        // "6:18 PM"
        String seats,       // "4 spots left"
        String price,       // "$52.00 CAD"，抓不到价格时为 null
        String bookingUrl   // 没配链接模板时为 null
) {
}
