package golf.service;

import java.time.LocalDate;
import java.util.List;

import golf.client.CourseCatalog;
import golf.model.dto.TeeTimeDto;
import golf.model.entity.TeeTime;
import golf.repository.TeeTimeRepository;
import org.springframework.stereotype.Service;

/** 读路径：从库里取仍可订的时段，转成给前端的 DTO。 */
@Service
public class TeeTimeService {

    private final TeeTimeRepository repository;
    private final CourseCatalog courseCatalog;

    public TeeTimeService(TeeTimeRepository repository, CourseCatalog courseCatalog) {
        this.repository = repository;
        this.courseCatalog = courseCatalog;
    }

    public List<TeeTimeDto> findAvailable(LocalDate date, String courseId) {
        List<TeeTime> rows = (courseId == null || courseId.isBlank())
                ? repository.findByPlayDateAndAvailableTrueOrderByTimeLocalAsc(date)
                : repository.findByPlayDateAndCourseIdAndAvailableTrueOrderByTimeLocalAsc(date, courseId);
        return rows.stream().map(this::toDto).toList();
    }

    private TeeTimeDto toDto(TeeTime t) {
        return new TeeTimeDto(
                t.getCourseId(),
                courseCatalog.nameOf(t.getCourseId()),
                t.getPlayDate().toString(),
                t.getTimeLocal(),
                t.getHoles(),
                t.getPlayers(),
                t.getPrice(),
                t.isAvailable());
    }
}
