# database structure.md - Database Schemas & Storage Design

## 1. Overview
UPI-Easy utilizes a two-tier database architecture:
1. **Client Tier**: Local Android SQLite database managed via Android Jetpack Room with full offline support and indexing.
2. **Server Tier**: Central relational database managed via Drizzle ORM (SQLite / Neon Postgres) for multi-tenant synchronization and team-wide reconciliation.

---

## 2. Android Client Database (Room)

### A. Table: `observed_payment_events`
Stores normalized raw observation records extracted from payment application notifications.

```sql
CREATE TABLE IF NOT EXISTS `observed_payment_events` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `organizationId` TEXT,
    `paymentAccountId` TEXT,
    `qrId` TEXT,
    `sourcePackage` TEXT NOT NULL,
    `sourceApp` TEXT NOT NULL,
    `direction` TEXT NOT NULL,           -- RECEIVED, SENT, UNKNOWN
    `amountMinor` INTEGER,               -- Amount in paise (integer)
    `currency` TEXT NOT NULL DEFAULT 'INR',
    `payerName` TEXT,
    `payerVpa` TEXT,
    `reference` TEXT,                   -- 12-digit UTR/RRN
    `notificationTitle` TEXT,
    `notificationText` TEXT,
    `eventFingerprint` TEXT NOT NULL,    -- Deterministic SHA-256
    `matchStatus` TEXT NOT NULL,         -- MATCHED, UNMATCHED, AMBIGUOUS
    `verificationStatus` TEXT NOT NULL DEFAULT 'OBSERVED', -- OBSERVED, VERIFIED, CONFLICT
    `observedAt` INTEGER NOT NULL,      -- Post timestamp (millis)
    `syncState` TEXT NOT NULL DEFAULT 'PENDING_UPLOAD', -- PENDING_UPLOAD, SYNCED, FAILED
    `createdAt` INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_observed_payment_events_eventFingerprint` 
ON `observed_payment_events` (`eventFingerprint`);

CREATE INDEX IF NOT EXISTS `index_observed_payment_events_org_account` 
ON `observed_payment_events` (`organizationId`, `paymentAccountId`);
```

### B. Table: `local_transactions`
The merchant's ledger of incoming and outgoing financial transactions.

```sql
CREATE TABLE IF NOT EXISTS `local_transactions` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `organizationId` TEXT NOT NULL,
    `bankAccountId` TEXT,
    `upiAccountId` TEXT,
    `paymentAccountId` TEXT,
    `type` TEXT NOT NULL,                -- PAYMENT, REFUND, REVERSAL
    `direction` TEXT NOT NULL,           -- RECEIVED, SENT
    `amount` REAL NOT NULL,              -- Double in rupees
    `currency` TEXT NOT NULL DEFAULT 'INR',
    `status` TEXT NOT NULL,              -- UNKNOWN, SUCCESS, PENDING, FAILED
    `verificationStatus` TEXT NOT NULL DEFAULT 'UNVERIFIED', -- OBSERVED, VERIFIED, UNVERIFIED
    `eventSource` TEXT NOT NULL,         -- NOTIFICATION_PHONEPE, NOTIFICATION_GOOGLE_PAY, NOTIFICATION_BHIM, NOTIFICATION_PAYTM, UPI_INTENT, MANUAL
    `paymentMethod` TEXT NOT NULL DEFAULT 'UPI',
    `referenceNumber` TEXT,
    `payerName` TEXT,
    `payerVpa` TEXT,
    `payeeName` TEXT NOT NULL,
    `payeeVpa` TEXT NOT NULL,
    `note` TEXT,
    `occurredAt` INTEGER NOT NULL,
    `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED'
);

CREATE INDEX IF NOT EXISTS `index_local_transactions_org_occurred` 
ON `local_transactions` (`organizationId`, `occurredAt`);
```

### C. Table: `payment_accounts`
Configured UPI payment observation accounts assigned to specific installed payment apps.

```sql
CREATE TABLE IF NOT EXISTS `payment_accounts` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `organizationId` TEXT NOT NULL,
    `label` TEXT NOT NULL,
    `upiId` TEXT NOT NULL,
    `paymentAppId` TEXT NOT NULL,        -- phonepe, google_pay, bhim, paytm
    `paymentAppPackage` TEXT NOT NULL,   -- com.phonepe.app, com.google.android.apps.nbu.paisa.user, in.org.npci.upiapp, net.one97.paytm
    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
    `detectionEnabled` INTEGER NOT NULL DEFAULT 1,
    `notificationAccessRequired` INTEGER NOT NULL DEFAULT 1,
    `lastNotificationDetectedAt` INTEGER,
    `createdAt` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL
);
```

---

## 3. Server Database (Drizzle ORM / SQLite / Postgres)

### A. Table: `observed_payment_events`
Ingested notification events synchronized from merchant devices.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `text` | PRIMARY KEY | Unique event ID (`evt_obs_...`) |
| `organization_id` | `text` | NOT NULL, REFERENCES organizations(id) | Tenant Organization |
| `payment_account_id`| `text` | REFERENCES payment_accounts(id) | Linked Payment Account |
| `qr_id` | `text` | REFERENCES qr_codes(id) | Linked QR code (if attributable) |
| `source_type` | `text` | NOT NULL | `NOTIFICATION_PHONEPE`, `NOTIFICATION_GPAY`, `NOTIFICATION_BHIM`, `NOTIFICATION_PAYTM` |
| `source_package` | `text` | NOT NULL | Package identifier of payment application |
| `amount_minor` | `integer` | | Amount in paise (e.g. 25000 = ₹250.00) |
| `currency` | `text` | NOT NULL DEFAULT 'INR' | Currency ISO code |
| `direction` | `text` | NOT NULL DEFAULT 'RECEIVED' | `RECEIVED`, `SENT`, `UNKNOWN` |
| `payer_name` | `text` | | Name of paying customer |
| `payer_vpa` | `text` | | Customer UPI VPA |
| `reference` | `text` | | 12-digit UTR/RRN reference number |
| `event_fingerprint` | `text` | NOT NULL | SHA-256 Idempotency hash |
| `match_status` | `text` | NOT NULL DEFAULT 'MATCHED' | `MATCHED`, `UNMATCHED`, `AMBIGUOUS` |
| `verification_status`| `text` | NOT NULL DEFAULT 'OBSERVED' | Always forced to `OBSERVED` on ingest |
| `observed_at` | `integer` | NOT NULL | Timestamp when notification was posted |
| `created_at` | `integer` | NOT NULL | Ingestion timestamp |

### B. Table: `transactions`
The authoritative centralized transaction ledger.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `text` | PRIMARY KEY | Unique transaction ID (`txn_...`) |
| `organization_id` | `text` | NOT NULL, REFERENCES organizations(id) | Tenant Organization |
| `payment_account_id`| `text` | REFERENCES payment_accounts(id) | Associated Payment Account |
| `amount` | `real` | NOT NULL | Amount in Rupees |
| `status` | `text` | NOT NULL | `UNKNOWN`, `SUCCESS`, `PENDING`, `FAILED`, `RECONCILIATION_REQUIRED` |
| `verification_status`| `text` | NOT NULL DEFAULT 'UNVERIFIED' | `OBSERVED`, `VERIFIED`, `UNVERIFIED`, `CONFLICT` |
| `event_source` | `text` | NOT NULL | Source identifier |
| `reference_number` | `text` | | RRN / UTR reference |
| `occurred_at` | `integer` | NOT NULL | Date & time of transaction occurrence |

### C. Table: `payment_accounts`
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `text` PRIMARY KEY | Unique ID (`pa_...`) |
| `organization_id` | `text` NOT NULL | Tenant Organization |
| `label` | `text` NOT NULL | Friendly label (e.g. Counter 1 PhonePe) |
| `upi_id` | `text` NOT NULL | UPI VPA address |
| `payment_app_id` | `text` NOT NULL | `phonepe`, `google_pay`, `bhim`, `paytm` |
| `payment_app_package` | `text` NOT NULL | Supported package name |
| `detection_enabled` | `integer` NOT NULL | Boolean flag |
| `last_notification_detected_at` | `integer` | Timestamp of latest detected payment |

---

## 4. Idempotency & Deduplication Indexes
```sql
-- Client Room Deduplication:
CREATE UNIQUE INDEX idx_observed_events_fingerprint ON observed_payment_events(eventFingerprint);

-- Server Schema Deduplication:
CREATE UNIQUE INDEX idx_srv_observed_events_org_fp ON observed_payment_events(organization_id, event_fingerprint);
```
These indexes guarantee that duplicate notifications or retry syncs never cause duplicate ledger entries or duplicate balance updates.
