package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.util.List;

public record Result(List<Card> items, boolean hasNext, int page, int size, long totalElements, int totalPages) {}
