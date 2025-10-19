package banking.security;

/**
 * Describes an authenticated user along with the resolved authorization role.
 */
public record UserPrincipal(String username, Role role) {
}
