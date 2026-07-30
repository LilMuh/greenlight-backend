package golf.controller;

import java.util.List;

import golf.model.dto.MatchResultDto;
import golf.service.WatchMatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 匹配结果：每条启用中的 watch 当前命中的空位，给前端展示（只读，不发通知）。 */
@RestController
@RequestMapping("/api")
public class MatchController {

    private final WatchMatchService watchMatchService;

    public MatchController(WatchMatchService watchMatchService) {
        this.watchMatchService = watchMatchService;
    }

    @GetMapping("/matches")
    public List<MatchResultDto> matches() {
        return watchMatchService.findAllMatches();
    }
}
