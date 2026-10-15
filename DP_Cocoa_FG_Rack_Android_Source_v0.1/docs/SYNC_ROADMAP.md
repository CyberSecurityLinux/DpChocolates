# Multi-Mobile Offline Sync — Architecture for Next Release

## Requirement

- More than one Android device operates at the same time.
- Data entry must be possible when mobile data/Wi-Fi is unavailable.
- Devices automatically converge when they reconnect; dispatch must never double-consume stock.
- Users have server-side Admin, Manager, Operator, Checker, Viewer permissions, and Admin can revoke access.

## Technical plan (NOT yet implemented)

1. Cloud or on-prem central PostgreSQL store of users, products, racks, lot balances, movements, cycle count assignments, permissions, revisions, audited changes. Encrypted at rest and backed up.
2. API over HTTPS with short-lived access tokens and refresh token rotation, password reset and admin recovery protected by server-side procedures; disallow hard-coded universal passwords.
3. Each mobile event gets an immutable random UUID, device id, actor, timestamp, current stock revision and a command (`RECEIVE`, `DISPATCH`, `TRANSFER`, `QA_CHANGE`, etc.). Locally append it to the SQLite outbox transactionally with the stock update.
4. On reconnect, upload pending events. Server validates actor role, warehouse ownership, QA state, FIFO policy, lot-balance revision and uniqueness of event UUID. On stock mismatch, **reject conflicts** and require human reconciliation; don't silently overwrite remote changes.
5. Download the server change log in monotonic sequence, show last sync status, rejected events and per-row conflict notices.
6. Offline FIFO dispatch from multiple devices requires allocated stock reservations per device/shift **before disconnect**, or an explicit 'provisional only' workflow. Without reservations, do not finalize cross-device stock dispatch until the server verifies it.
7. Security review and tests: user revocation, inventory uniqueness, transfer rollbacks, account lockout, backups, migration tests, and offline conflicts. Server-side audit logs must be non-editable.

## Cost note

ChatGPT subscription fees do **not** include unlimited lifetime private database hosting, server operation, SMS/OTP or Play Store publication. Choose hosting based on company data/privacy policy and expected usage.
