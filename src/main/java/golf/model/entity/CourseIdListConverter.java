package golf.model.entity;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** course_ids 这一列用 CSV 存 slug 列表，读写时在 List&lt;String&gt; 和 "a,b,c" 之间转换。 */
@Converter
public class CourseIdListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> courseIds) {
        return (courseIds == null || courseIds.isEmpty()) ? "" : String.join(",", courseIds);
    }

    @Override
    public List<String> convertToEntityAttribute(String csv) {
        return (csv == null || csv.isBlank()) ? List.of() : List.of(csv.split(","));
    }
}
