package golf.service;

import java.util.List;

/**
 * 一批 watch 刚落库——可能是新建的，也可能是重复提交时被改掉的现有那条
 * （见 WatchConfigService#create）。两者要做的后续一样：抓一遍它们关注的日期，
 * 再发基准邮件，所以不区分。
 *
 * 事务提交后才处理。整批一个事件（不是一条一个），这样多个球场同一天能并成一次抓取，
 * 也避免并发驱动浏览器。
 */
public record WatchesSavedEvent(List<Long> watchIds) {}
