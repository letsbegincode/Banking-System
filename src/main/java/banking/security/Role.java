package banking.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Roles define collections of permissions operators can assume.
 */
public enum Role {
    ADMIN(EnumSet.allOf(Permission.class)),
    TELLER(EnumSet.of(
        Permission.ACCOUNT_READ,
        Permission.ACCOUNT_CREATE,
        Permission.FUNDS_DEPOSIT,
        Permission.FUNDS_WITHDRAW,
        Permission.FUNDS_TRANSFER
    )),
    AUDITOR(EnumSet.of(
        Permission.ACCOUNT_READ,
        Permission.HEALTH_READ
    ));

    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = buildMap();

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public static Set<Permission> permissionsFor(Role role) {
        return ROLE_PERMISSIONS.get(role);
    }

    private static Map<Role, Set<Permission>> buildMap() {
        Map<Role, Set<Permission>> map = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            map.put(role, role.permissions());
        }
        return Collections.unmodifiableMap(map);
    }
}
