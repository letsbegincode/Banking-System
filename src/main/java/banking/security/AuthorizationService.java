package banking.security;

import java.util.Objects;
import java.util.Set;

/**
 * Evaluates whether a token has the required permission.
 */
public class AuthorizationService {
    public void ensureAuthorized(AuthenticationToken token, Permission permission) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(permission, "permission");
        if (!hasPermission(token, permission)) {
            throw new ForbiddenException("Operator lacks permission: " + permission);
        }
    }

    public boolean hasPermission(AuthenticationToken token, Permission permission) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(permission, "permission");
        Set<Role> roles = token.roles();
        return roles.stream().anyMatch(role -> Role.permissionsFor(role).contains(permission));
    }
}
