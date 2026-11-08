package sayt.identity;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record ShopPrincipal(long id, String login, String passwordHash, String role, boolean enabled,
                            boolean mustChange, long authVersion) implements UserDetails {
    @Override public String getUsername() { return login; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public String toString() { return "ShopPrincipal[id=" + id + "]"; }
    @Override public List<SimpleGrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
