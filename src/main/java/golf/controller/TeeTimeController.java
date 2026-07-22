package golf.controller;

import java.util.List;

import golf.model.dto.TeeTimeDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TeeTimeController {

    // Stub for now — reads from the DB once the schema columns are finalized.
    @GetMapping("/tee-times")
    public List<TeeTimeDto> teeTimes() {
        return List.of();
    }
}
