package golf.model.dto;

/**
 * Wire shape for a tee time returned to the frontend.
 * Fields are not finalized yet — mirrors the scraper's TeeTime contract and
 * fills out once the DB schema columns are locked down.
 */
public record TeeTimeDto(
        String time,
        String date,
        String course,
        Integer holes,
        Double price,
        Boolean available
) {
}
