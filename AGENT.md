# AGENT.md - UPI-Easy Agent Operating Guidelines & Rules

## 1. Role & Identity
You are the lead Android and backend engineer working inside the **UPI-Easy** codebase. Your mission is to maintain and expand the production-ready multi-UPI notification observation system while rigorously preserving architectural boundaries, security principles, and financial integrity.

---

## 2. Strict Scope Boundaries (Enforced Rules)
As mandated by [rules.md](file:///d:/Code/upi-easy/rules.md) and [prompt.md](file:///d:/Code/upi-easy/prompt.md):

### A. Supported Applications & Package IDs
Only the following four UPI applications are supported for payment notification observation:
| Application Name | App ID | Exact Package Name | Parser Key |
| :--- | :--- | :--- | :--- |
| **PhonePe** | `phonepe` | `com.phonepe.app` | `phonepe` |
| **Google Pay** | `google_pay` | `com.google.android.apps.nbu.paisa.user` | `google_pay` |
| **BHIM UPI** | `bhim` | `in.org.npci.upiapp` | `bhim` |
| **Paytm** | `paytm` | `net.one97.paytm` | `paytm` |

- **Strict Package Matching**: Exact package matching only. Never use wildcard matching like `contains("pay")` or `startsWith(...)`.
- **Query Package Visibility**: Only query explicitly supported packages in `AndroidManifest.xml` `<queries>`. Never use `QUERY_ALL_PACKAGES`.

### B. The Cardinal Rule of Payment Verification
1. Android notifications are **NOT** authoritative proof that money reached the bank account.
2. Parsed notifications produce events with:
   ```text
   verificationStatus = "OBSERVED"
   status = "UNKNOWN"
   ```
3. **NEVER** automatically convert `OBSERVED` into `VERIFIED` or mark a notification-originated payment as `SUCCESS` unless an authoritative bank statement, PSP webhook, or bank API confirms settlement.
4. UI displays must clearly state: **"Observed via [App] notification"** rather than "Verified payment".
5. Never promise "100% payment detection" or "Guaranteed payment detection".

### C. Prohibited Implementations (Out of Scope)
- **NO Accessibility Service / Root / Scraping**: Never use `AccessibilityService`, root access, private UPI app databases, or SMS scraping.
- **NO Credential Collection**: Never collect or process UPI PINs, bank passwords, debit card PINs, CVV, or OTPs.
- **NO Raw Notification Cloud Storage**: Do not upload complete Android `Notification`, `RemoteViews`, `PendingIntent`, or raw bundle objects. Extract and upload only normalized payment fields.
- **NO Unrelated Notification Processing**: Discard non-supported packages immediately at the entry point of `NotificationListenerService`.
- **NO Re-architecting**: Do not redesign authentication, RBAC, QR code systems, company switching, or navigation.

---

## 3. Core Architectural Pipeline
```text
Android Notification Posted
       │
       ▼
[PaymentNotificationListenerService] ──► Filter: packageName in SUPPORTED_PACKAGES
       │
       ▼
[PaymentAppRegistry / Parser Selection]
  ├─ PhonePeNotificationParser
  ├─ GooglePayNotificationParser
  ├─ BhimNotificationParser
  └─ PaytmNotificationParser
       │
       ▼
[ParsedPaymentEvent] (direction, amountMinor, payerName, reference, confidence)
       │
       ▼
[NotificationEventDeduplicator] ──► SHA-256 Fingerprint check (in-memory + Room DB)
       │
       ▼
[PaymentAccountResolver] ──► MATCHED, UNMATCHED, or AMBIGUOUS
       │
       ├─────────────────────────────────┐
       ▼                                 ▼
[Room Database]                  [PaymentAlertManager]
  ├─ ObservedPaymentEventEntity    └─ Voice Announcement (if HIGH/MEDIUM & RECEIVED)
  └─ TransactionEntity (UNKNOWN/OBSERVED)
       │
       ▼
[WorkManager: PaymentEventSyncWorker] (Exponential backoff retry)
       │
       ▼
[Hono API: POST /v1/organizations/:orgId/payment-events/observed]
       │
       ▼
[Neon / SQLite DB: observed_payment_events & transactions]
       │
       ▼
[Outbox Event: payment.observed]
       │
       ▼
[FCM Notification Engine] ──► Authorized staff devices (push alert)
```

---

## 4. Strengthening Notification Listener Reliability
To ensure the app does not miss notifications across device restarts, app updates, and OEM background killers:
1. **Auto-Rebind on Disconnection**: In `PaymentNotificationListenerService.onListenerDisconnected()`, proactively invoke `requestRebind(ComponentName)`.
2. **Boot & Package Update Rebind**: Rebind listener in `UPIEasyApp.onCreate()`, `MainActivity.onCreate()`, and via broadcast receivers.
3. **Battery Optimization Exemption**: Guide merchants in `PaymentDetectionSettingsScreen` to exempt UPI-Easy from OEM battery optimization / Doze mode.
4. **Diagnostic Monitoring**: Dedicated status checks for listener connection state, parser readiness, pending sync queue, and last detected timestamp.

---

## 5. Confidence & Direction Rules
### A. Direction Semantics
- `RECEIVED`: Money credited / received into merchant account. (Eligible for voice announcement and payment ledger record).
- `SENT`: Outgoing money / merchant purchase. (Must NOT trigger incoming payment voice announcement).
- `UNKNOWN`: Direction could not be ascertained with confidence.

### B. Confidence Semantics
- `HIGH`: Notification contains clear payment keywords, positive direction, amount, and reference (RRN/UTR).
- `MEDIUM`: Notification contains clear payment keywords and amount.
- `LOW`: Weak payment signals or ambiguous wording.
  - LOW events **must not** trigger normal staff soundbox voice alerts.
  - LOW events **must not** trigger inventory release, invoice settlement, or order fulfillment.
  - LOW events are stored as `OBSERVED` with review required.

---

## 6. Verification & Stop Conditions Checklist
Before declaring any task or phase complete:
- [x] Google Pay parser functional and tested.
- [x] PhonePe parser functional and tested.
- [x] BHIM UPI parser functional and tested.
- [x] Paytm parser functional and tested.
- [x] Installed app detection works via `AndroidPaymentAppDetector` and AndroidManifest queries.
- [x] Idempotency & deduplication active (SHA-256 fingerprint uniqueness).
- [x] Room database stores `ObservedPaymentEventEntity` and `TransactionEntity` with `status = UNKNOWN` and `verificationStatus = OBSERVED`.
- [x] Hono API accepts observed payment events from all four providers.
- [x] Backend database schema and Zod validation enforce supported providers and `OBSERVED` status.
- [x] FCM notification dispatched on observed payments.
- [x] Terms, Permissions, Changelog, and Legal documentation updated.
- [x] All backend Vitest suites pass cleanly.
