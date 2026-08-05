package golf.service;

import java.util.List;

import golf.model.dto.CourseDto;
import golf.model.entity.Course;
import golf.repository.CourseRepository;
import org.springframework.stereotype.Service;

/** 读路径：球场清单来自数据库（course 表由 Liquibase 维护，后端只读）。 */
@Service
public class CourseService {

    private final CourseRepository repository;

    public CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    public List<CourseDto> findAll() {
        return repository.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    private CourseDto toDto(Course course) {
        return new CourseDto(course.getId(), course.getSlug(), course.getName(), course.getSource(), course.getSite(),
                course.getImageUrl());
    }
}
