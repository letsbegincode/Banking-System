# Security Posture and Compliance Checklist

## Overview
The banking console application now enforces a layered security model that combines strong authentication, role-based authorization, centralized logging, and tamper-evident audit trails. These enhancements ensure that sensitive banking operations are observable, traceable, and restricted to least-privilege users.

## Authentication and Authorization
- **Identity Provider:** In-application `AuthService` issues HMAC-SHA256 signed JWT access tokens with one-hour lifetimes.
- **Credential Storage:** Passwords are hashed with SHA-256 and stored in-memory. Default administrative, teller, auditor, and customer accounts are seeded for testing.
- **Role Enforcement:** `AuthMiddleware` validates every token and enforces role checks (ADMIN, TELLER, AUDITOR, USER) before allowing service operations.
- **Session Handling:** The console UI requires successful login and supports re-authentication if tokens expire or privileges change.

## Logging and Tracing
- **Central Logger:** `LoggingConfig` standardizes Java util logging for console and rotating file outputs under `logs/`.
- **Trace Correlation:** `TraceContext` manages per-request trace identifiers that flow through asynchronous banking operations.
- **Log Hygiene:** All logging avoids sensitive data exposure and downgrades stack traces to debug files while preserving operational visibility.

## Audit Trail Controls
- **Persistent Audit Store:** `AuditTrailService` writes JSON Lines entries to `logs/audit_trail.jsonl` for every privileged action.
- **Data Masking:** Account numbers, customer names, and tokens are automatically masked before persistence to prevent sensitive data leaks.
- **Tamper Awareness:** Audit events include timestamps and trace identifiers, enabling reconciliation with application logs.

## Compliance Checklist
- [x] **Access Control:** Enforced multi-role authorization with least-privilege defaults.
- [x] **Authentication:** Strong credential hashing and signed tokens with configurable secret via `BANKING_JWT_SECRET`.
- [x] **Auditability:** Immutable, append-only audit trail with masked sensitive fields.
- [x] **Logging:** Centralized, structured logs with correlation IDs for forensic analysis.
- [x] **Data Protection:** Sensitive identifiers masked prior to storage and display in audit artifacts.
- [ ] **Encryption at Rest:** Persisted `banking_system.ser` file remains unencrypted (consider OS-level volume encryption or keystore integration).
- [ ] **Secret Management:** JWT signing key defaults to a static string; production deployments should inject a secret via environment variables or secrets manager.

## Operational Guidance
1. Set `BANKING_JWT_SECRET` to a strong, environment-specific value.
2. Periodically rotate credentials and audit log files.
3. Monitor `logs/application.log` and `logs/audit_trail.jsonl` for anomalous activity.
4. Review the compliance checklist regularly and close remaining gaps before production deployment.
