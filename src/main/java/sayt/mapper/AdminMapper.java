package sayt.mapper;

import sayt.dto.identity.AdminResponse;
import sayt.entity.AdminUser;

public final class AdminMapper {
    private AdminMapper() {}

    public static AdminResponse view(AdminUser user) {
        return new AdminResponse(user.getId(), user.getLogin(), user.getName(), user.getRole(), user.isEnabled(), user.isMustChange(),
            user.getCreatedAt(), user.getLastLogin(), user.getVersion());
    }
}
