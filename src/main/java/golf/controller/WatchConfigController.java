package golf.controller;

import java.util.List;

import golf.model.dto.WatchConfigDto;
import golf.service.WatchConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 关注订阅的 CRUD，前端 Watch Alerts 页调用。 */
@RestController
@RequestMapping("/api/watch-configs")
public class WatchConfigController {

    private final WatchConfigService service;

    public WatchConfigController(WatchConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<WatchConfigDto> list() {
        return service.findAll();
    }

    /** 新建或更新（带 id 即更新），返回落库后的记录（含 id）。 */
    @PostMapping
    public WatchConfigDto save(@RequestBody WatchConfigDto dto) {
        return service.save(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
