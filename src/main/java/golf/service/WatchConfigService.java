package golf.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import golf.model.dto.WatchConfigBatchDto;
import golf.model.dto.WatchConfigDto;
import golf.model.entity.Course;
import golf.model.entity.WatchConfig;
import golf.repository.CourseRepository;
import golf.repository.WatchConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 关注订阅的增删改查。这是后端第一处**写库**（写 watch_config，不碰 tee_time / course）。 */
@Service
public class WatchConfigService {

    private final WatchConfigRepository repository;
    private final CourseRepository courseRepository;

    public WatchConfigService(WatchConfigRepository repository, CourseRepository courseRepository) {
        this.repository = repository;
        this.courseRepository = courseRepository;
    }

    public List<WatchConfigDto> findAll() {
        return repository.findAllByOrderByIdDesc().stream().map(this::toDto).toList();
    }

    /** 批量创建：一组球场 + 一份共享 config，逐个球场建一条 watch。 */
    @Transactional
    public List<WatchConfigDto> create(WatchConfigBatchDto batch) {
        List<WatchConfigDto> created = new ArrayList<>();
        for (Long courseId : batch.courseIds()) {
            WatchConfig watch = new WatchConfig();
            watch.setCourse(loadCourse(courseId));
            watch.setDateStart(batch.dateStart());
            watch.setDateEnd(batch.dateEnd());
            watch.setTimeStart(batch.timeStart());
            watch.setTimeEnd(batch.timeEnd());
            watch.setPlayers(batch.players());
            watch.setMaxPrice(batch.maxPrice());
            watch.setEmail(batch.email());
            watch.setActive(batch.active());
            watch.setUpdatedAt(Instant.now());
            created.add(toDto(repository.save(watch)));
        }
        return created;
    }

    /** 更新一条：改 config（可含换球场），用 find-then-save。 */
    @Transactional
    public WatchConfigDto update(Long id, WatchConfigDto dto) {
        WatchConfig watch = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未知 watch id: " + id));
        watch.setCourse(loadCourse(dto.courseId()));
        watch.setDateStart(dto.dateStart());
        watch.setDateEnd(dto.dateEnd());
        watch.setTimeStart(dto.timeStart());
        watch.setTimeEnd(dto.timeEnd());
        watch.setPlayers(dto.players());
        watch.setMaxPrice(dto.maxPrice());
        watch.setEmail(dto.email());
        watch.setActive(dto.active());
        watch.setUpdatedAt(Instant.now());
        return toDto(repository.save(watch));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private Course loadCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未知球场 id: " + courseId));
    }

    private WatchConfigDto toDto(WatchConfig watch) {
        Course course = watch.getCourse();
        return new WatchConfigDto(
                watch.getId(),
                course.getId(),
                course.getSlug(),
                course.getName(),
                watch.getDateStart(),
                watch.getDateEnd(),
                watch.getTimeStart(),
                watch.getTimeEnd(),
                watch.getPlayers(),
                watch.getMaxPrice(),
                watch.getEmail(),
                watch.isActive());
    }
}
