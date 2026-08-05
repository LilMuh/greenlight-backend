package golf.service.mail;

import java.util.List;

/**
 * 邮件里的一个日期分组：一天一个块，块里是当天所有命中的时段。
 *
 * 邮件的锚点是日期而不是单个时段，所以预订链接也挂在这一层：一天一条链接，
 * 时间窗口覆盖当天最早到最晚的命中时段（见 {@link BookingLinkBuilder#buildForDay}）。
 *
 * bookingUrl 为 null（没配链接模板）时模板不渲染 BOOK 链接，分组照常收尾。
 */
public record TeeTimeDateGroup(
        String date,             // "Fri, Aug 7"
        String bookingUrl,       // 没配链接模板时为 null
        List<TeeTimeCard> slots  // 按时间升序
) {
}
