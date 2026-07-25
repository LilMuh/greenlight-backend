package golf.service;

import java.time.LocalDate;
import java.util.List;

import golf.model.dto.TeeTimeDto;
import golf.model.entity.Course;
import golf.model.entity.TeeTime;
import golf.repository.TeeTimeRepository;
import org.springframework.stereotype.Service;

/** 读路径：从库里取仍可订的时段，转成给前端的 DTO。 */
@Service
public class TeeTimeService {

    private final TeeTimeRepository repository;

    public TeeTimeService(TeeTimeRepository repository) {
        this.repository = repository;
    }

    public List<TeeTimeDto> findAvailable(LocalDate date, String courseSlug) {
        List<TeeTime> rows = (courseSlug == null || courseSlug.isBlank())
                ? repository.findByPlayDateAndAvailableTrueOrderByTimeLocalAsc(date)
                : repository.findByPlayDateAndCourse_SlugAndAvailableTrueOrderByTimeLocalAsc(date, courseSlug);
        return rows.stream().map(this::toDto).toList();
    }

    private TeeTimeDto toDto(TeeTime teeTime) {
        Course course = teeTime.getCourse();
        return new TeeTimeDto(
                course.getSlug(),
                course.getName(),
                teeTime.getPlayDate().toString(),
                teeTime.getTimeLocal(),
                teeTime.getHoles(),
                teeTime.getPlayers(),
                teeTime.getPrice(),
                teeTime.isAvailable());
    }
}
