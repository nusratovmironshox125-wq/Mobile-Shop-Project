package sayt.dto.lead;

import jakarta.validation.constraints.*;
import java.util.*;

public record LeadDetailResponse(LeadResponse lead, List<Map<String, Object>> history) {}
