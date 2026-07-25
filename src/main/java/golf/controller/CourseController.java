package golf.controller;

import java.util.List;

import golf.model.dto.CourseDto;
import golf.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 球场清单：从数据库读（course 表由 Liquibase 维护），给前端下拉框用。 */
@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/courses")
    public List<CourseDto> courses() {
        return courseService.findAll();
    }
}
