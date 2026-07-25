package golf.controller;

import java.util.List;

import golf.model.dto.WatchConfigBatchDto;
import golf.model.dto.WatchConfigDto;
import golf.service.WatchConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** 批量创建：一组球场 + 共享 config，后端逐个球场建一条，返回创建的记录。 */
    @PostMapping
    public List<WatchConfigDto> create(@RequestBody WatchConfigBatchDto batch) {
        return service.create(batch);
    }

    /** 更新单条（改 config / 换球场 / 暂停启用）。 */
    @PutMapping("/{id}")
    public WatchConfigDto update(@PathVariable Long id, @RequestBody WatchConfigDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
