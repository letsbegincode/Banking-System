package banking.security;

/**
 * Defines the supported authorization roles for the banking platform.
 */
public enum Role {
    CUSTOMER,
    OPERATOR;

    /**
     * Determines whether this role grants the permissions associated with the required role.
     * Operators are treated as super-users and therefore inherit all permissions.
     *
     * @param required the role required to access a resource
     * @return {@code true} if this role satisfies the requirement
     */
    public boolean grants(Role required) {
        if (this == OPERATOR) {
            return true;
        }
        return this == required;
    }
}
