package sayt.dto.identity;

import jakarta.validation.constraints.*;

public record IssuedPasswordResponse(AdminResponse account, String temporaryPassword) {}
