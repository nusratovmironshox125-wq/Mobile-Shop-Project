package sayt.dto.catalog;

import jakarta.validation.constraints.*;
import java.util.List;

public record Result(List<Card> items, boolean hasNext) {}
