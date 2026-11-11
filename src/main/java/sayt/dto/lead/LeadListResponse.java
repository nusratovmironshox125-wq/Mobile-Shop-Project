package sayt.dto.lead;

import jakarta.validation.constraints.*;
import java.util.*;

public record LeadListResponse(List<LeadResponse> items, boolean hasNext, int page, int size, long totalElements, int totalPages) {}
