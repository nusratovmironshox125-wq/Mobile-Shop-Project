package sayt.dto.lead;

import jakarta.validation.constraints.*;
import java.util.*;

public record UpdateLeadRequest(@NotBlank @Pattern(regexp="NEW|CONTACTED|COMPLETED|CANCELLED") String status,
    @Size(max=2000) String comment, @Size(max=2000) String reason, @NotNull Long version) {}
