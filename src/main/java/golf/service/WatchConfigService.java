package golf.service;

import java.time.Instant;
import java.util.List;

import golf.model.dto.WatchConfigDto;
import golf.model.entity.WatchConfig;
import golf.repository.WatchConfigRepository;
import org.springframework.stereotype.Service;

/** 关注订阅的增删改查。这是后端第一处**写库**（写 watch_config，不碰 tee_time）。 */
@Service
public class WatchConfigService {

    private final WatchConfigRepository repository;

    public WatchConfigService(WatchConfigRepository repository) {
        this.repository = repository;
    }

    public List<WatchConfigDto> findAll() {
        return repository.findAllByOrderByIdDesc().stream().map(this::toDto).toList();
    }

    /** 带 id 就更新那一条，没 id 就新建；用 find-then-save，不写原生 SQL。 */
    public WatchConfigDto save(WatchConfigDto dto) {
        WatchConfig entity = (dto.id() != null)
                ? repository.findById(dto.id()).orElseGet(WatchConfig::new)
                : new WatchConfig();
        apply(dto, entity);
        return toDto(repository.save(entity));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void apply(WatchConfigDto dto, WatchConfig entity) {
        entity.setCourseIds(dto.courseIds());
        entity.setDateStart(dto.dateStart());
        entity.setDateEnd(dto.dateEnd());
        entity.setTimeStart(dto.timeStart());
        entity.setTimeEnd(dto.timeEnd());
        entity.setPlayers(dto.players());
        entity.setMaxPrice(dto.maxPrice());
        entity.setEmail(dto.email());
        entity.setActive(dto.active());
        entity.setUpdatedAt(Instant.now());
    }

    private WatchConfigDto toDto(WatchConfig w) {
        return new WatchConfigDto(
                w.getId(),
                w.getCourseIds(),
                w.getDateStart(),
                w.getDateEnd(),
                w.getTimeStart(),
                w.getTimeEnd(),
                w.getPlayers(),
                w.getMaxPrice(),
                w.getEmail(),
                w.isActive());
    }
}
