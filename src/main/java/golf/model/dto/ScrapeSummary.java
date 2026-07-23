package golf.model.dto;

import java.util.List;

/** 一轮抓取触发的汇总：成功的 partition 数、累计落库时段数、各失败原因。 */
public record ScrapeSummary(int partitions, int saved, List<String> errors) {
}
