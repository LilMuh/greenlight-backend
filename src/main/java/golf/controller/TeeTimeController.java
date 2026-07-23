package golf.controller;

import java.time.LocalDate;
import java.util.List;

import golf.model.dto.TeeTimeDto;
import golf.service.TeeTimeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 读路径：前端按日期（可选球场）查库里仍可订的时段。 */
@RestController
@RequestMapping("/api")
public class TeeTimeController {

    private final TeeTimeService teeTimeService;

    public TeeTimeController(TeeTimeService teeTimeService) {
        this.teeTimeService = teeTimeService;
    }

    @GetMapping("/tee-times")
    public List<TeeTimeDto> teeTimes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String course) {
        return teeTimeService.findAvailable(date, course);
    }
}
