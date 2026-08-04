package golf.service;

import java.util.List;

/**
 * 一批 watch 刚建好。事务提交后才处理：先抓一遍它们关注的日期，再发基准邮件。
 * 整批一个事件（不是一条一个），这样多个球场同一天能并成一次抓取，也避免并发驱动浏览器。
 */
public record WatchesCreatedEvent(List<Long> watchIds) {}
