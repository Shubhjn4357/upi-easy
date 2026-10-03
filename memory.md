# memory.md - System Context, Decisions & Operational Memory

## 1. Supported Applications Registry
The application recognizes exactly four payment applications for notification-based observation:

| Application | ID | Package Identifier | Supported Parser Key | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Google Pay** | `google_pay` | `com.google.android.apps.nbu.paisa.user` | `google_pay` | Production |
| **PhonePe** | `phonepe` | `com.phonepe.app` | `phonepe` | Production |
| **BHIM UPI** | `bhim` | `in.org.npci.upiapp` | `bhim` | Production |
| **Paytm** | `paytm` | `net.one97.paytm` | `paytm` | Production |

Package names must remain exact constants. Do not use wildcards, regex packages, or prefix searches.

---

## 2. Core Enums & Data Contracts

### A. PaymentDirection
- `RECEIVED`: Payment credited to merchant.
- `SENT`: Payment debited from device.
- `UNKNOWN`: Payment direction cannot be unambiguously determined.

### B. ParseConfidence
- `HIGH`: Received/sent indicators + validated amount + 12-digit UTR/RRN reference.
- `MEDIUM`: Received/sent indicators + validated amount.
- `LOW`: Weak payment indicator or ambiguous formatting. LOW events must not trigger soundbox announcements or customer confirmation.

### C. VerificationStatus
- `OBSERVED`: Produced exclusively by notification parsers. (Never treated as settled or bank-confirmed).
- `VERIFIED`: Confirmed by an authoritative financial provider (bank statement API, PSP webhook, or bank integration).
- `UNVERIFIED`: Manual or unconfirmed entries.
- `CONFLICT`: Discrepancy between observed notification and authoritative record.

### D. MatchStatus
- `MATCHED`: Unambiguously mapped to an active `PaymentAccount` on the device.
- `UNMATCHED`: No active `PaymentAccount` configured for this package.
- `AMBIGUOUS`: Multiple active accounts match the package, but no account-specific identifier (e.g. VPA) is present to resolve between them.

### E. EventSource
- `NOTIFICATION_GOOGLE_PAY`
- `NOTIFICATION_PHONEPE`
- `NOTIFICATION_BHIM`
- `NOTIFICATION_PAYTM`
- `PROVIDER_WEBHOOK`
- `BANK_API`
- `MANUAL`

---

## 3. Idempotency & Deduplication
To prevent duplicate records from notification re-posts, sticky notifications, or screen unlock triggers:
1. **Fingerprint Construction**:
   ```kotlin
   val rawString = "$packageName|$notificationKey|$title|$text|$postTimeBucket"
   val fingerprint = MessageDigest.getInstance("SHA-256")
       .digest(rawString.toByteArray())
       .joinToString("") { "%02x".format(it) }
   ```
2. **Two-Tier Checking**:
   - In-Memory LRU cache (`NotificationEventDeduplicator`) handles rapid successive notification updates within milliseconds.
   - Database unique index on `(organizationId, eventFingerprint)` in Room and SQLite/Neon prevents duplicate ledger records.

---

## 4. Notification Listener Reliability & Strengthening
- Android kills or unbinds background services under heavy memory pressure or after app updates.
- **Rebind Hook**: In `PaymentNotificationListenerService`:
  ```kotlin
  override fun onListenerDisconnected() {
      super.onListenerDisconnected()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          requestRebind(ComponentName(this, PaymentNotificationListenerService::class.java))
      }
  }
  ```
- **Lifecycle Rebind Checks**: On application boot (`UPIEasyApp`), `MainActivity.onResume()`, and `PaymentDetectionSettingsScreen`.
- **Battery Optimization Exemption**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` guidance ensures the OS Doze mechanism does not suppress notification listener events.

---

## 5. Architectural Invariants
- Room database is the offline single source of truth for the Android client.
- WorkManager (`PaymentEventSyncWorker`) guarantees eventual consistency with exponential backoff.
- Hono backend rejects any client attempt to self-declare `verificationStatus = "VERIFIED"` on the `/payment-events/observed` endpoint.
- Soundbox alerts only trigger for `RECEIVED` payments with `HIGH` or `MEDIUM` confidence.
