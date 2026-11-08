package sayt.dto.identity;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record AdminResponse(long id, String login, String name, String role, boolean enabled, boolean mustChange,
                   Instant createdAt, Instant lastLogin, long version) {}
