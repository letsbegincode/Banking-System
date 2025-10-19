package banking.security;

/**
 * Permissions represent fine-grained capabilities that roles can grant.
 */
public enum Permission {
    ACCOUNT_READ,
    ACCOUNT_CREATE,
    FUNDS_DEPOSIT,
    FUNDS_WITHDRAW,
    FUNDS_TRANSFER,
    HEALTH_READ
}
