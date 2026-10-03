# architecture.md - UPI-Easy End-to-End Architecture

## 1. System Overview
UPI-Easy is a multi-tenant business payment observation and reconciliation platform designed for Indian retail merchants. It observes incoming UPI payment notifications from installed payment applications, parses and deduplicates payment details locally, records them in an offline-first SQLite database, synchronizes them securely with a central Hono backend, and triggers local soundbox voice alerts and multi-device FCM push notifications for authorized staff.

---

## 2. End-to-End Component Architecture

```text
┌────────────────────────────────────────────────────────────────────────┐
│                          Android Client Device                         │
│                                                                        │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │           Supported Payment Apps (Installed on Device)       │     │
│   │   [Google Pay]     [PhonePe]     [BHIM UPI]     [Paytm]      │     │
│   └────────┬───────────────┬──────────────┬────────────┬─────────┘     │
│            └───────────────┼──────────────┴────────────┘               │
│                            ▼ OS Notification Dispatch                  │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │            PaymentNotificationListenerService                │     │
│   │            (Auto-Rebind on Disconnect, Strict Filter)        │     │
│   └────────────────────────┬─────────────────────────────────────┘     │
│                            ▼ StatusBarNotification                     │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │                 PaymentAppRegistry & Parsers                 │     │
│   │  ├─ GooglePayNotificationParser                              │     │
│   │  ├─ PhonePeNotificationParser                                │     │
│   │  ├─ BhimNotificationParser                                   │     │
│   │  └─ PaytmNotificationParser                                  │     │
│   └────────────────────────┬─────────────────────────────────────┘     │
│                            ▼ ParsedPaymentEvent                        │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │                 Deduplicator & Resolver                      │     │
│   │  ├─ NotificationEventDeduplicator (SHA-256 Fingerprint)      │     │
│   │  └─ PaymentAccountResolver (MATCHED / UNMATCHED / AMBIGUOUS) │     │
│   └───────────┬──────────────────────────────────┬───────────────┘     │
│               ▼                                  ▼                     │
│   ┌───────────────────────────┐     ┌────────────────────────────┐     │
│   │    Room Database          │     │    PaymentAlertManager     │     │
│   │ ├─ ObservedPaymentEvents  │     │ ├─ Local Soundbox TTS      │     │
│   │ └─ Transactions (UNKNOWN) │     │ └─ In-app HUD alerts       │     │
│   └───────────┬───────────────┘     └────────────────────────────┘     │
│               ▼                                                        │
│   ┌───────────────────────────┐                                        │
│   │ PaymentEventSyncWorker    │                                        │
│   │ (WorkManager Backoff)     │                                        │
│   └───────────┬───────────────┘                                        │
└───────────────┼────────────────────────────────────────────────────────┘
                │ HTTPS (mTLS/Bearer Auth + Tenant Headers)
                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Backend (Hono + TypeScript)                     │
│                                                                        │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ API Routing & Middleware                                     │     │
│   │ ├─ requireAuth (JWT Token Verification)                      │     │
│   │ ├─ requireTenant (Organization Isolation & Header Check)     │     │
│   │ ├─ requirePermission (RBAC: payment_events.ingest)          │     │
│   │ └─ Zod Validation (Enforces verificationStatus = OBSERVED)   │     │
│   └────────────────────────┬─────────────────────────────────────┘     │
│                            ▼                                           │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ Database Layer (Drizzle ORM + SQLite / Neon Postgres)        │     │
│   │ ├─ observed_payment_events (Idempotent by Fingerprint)       │     │
│   │ ├─ transactions (status = UNKNOWN, verification = OBSERVED)  │     │
│   │ ├─ transaction_events (Audit Trail)                          │     │
│   │ └─ outbox_events (Guaranteed Delivery)                       │     │
│   └────────────────────────┬─────────────────────────────────────┘     │
│                            ▼                                           │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ Notification Worker                                          │     │
│   │ └─ Firebase Cloud Messaging (FCM)                            │     │
│   └────────────────────────┬─────────────────────────────────────┘     │
└────────────────────────────┼───────────────────────────────────────────┘
                             │
                             ▼
     ┌───────────────────────────────────────────────────────┐
     │           Authorized Staff Android Devices            │
     │      (Owner, Manager, Cashier Real-Time Alerts)       │
     └───────────────────────────────────────────────────────┘
```

---

## 3. Detailed Component Responsibilities

### A. Android Notification Pipeline
1. **`PaymentNotificationListenerService`**:
   - Registered in `AndroidManifest.xml` with permission `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.
   - Filters incoming notifications immediately by `SUPPORTED_PACKAGES`.
   - Normalizes raw extras (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_SUB_TEXT`) into `RawPaymentNotification`.
   - Handles OS disconnection via `onListenerDisconnected()` with `requestRebind()`.

2. **Parsers (`PaymentNotificationParser`)**:
   - Pure, stateless parsing components.
   - Extracts amount into integer minor units (paise) and converts to `BigDecimal` for precision.
   - Determines direction: `RECEIVED` (credits) vs `SENT` (debits).
   - Rejects non-payment notifications (promotions, offers, recharge reminders, loan approvals).
   - Extracts 12-digit UTR/RRN references and payer VPAs.

3. **`PaymentAccountResolver`**:
   - Links the parsed event to configured `payment_accounts` for the current active organization.
   - Resolves status: `MATCHED`, `UNMATCHED`, or `AMBIGUOUS`.

4. **`NotificationEventDeduplicator`**:
   - Generates deterministic SHA-256 fingerprints across package, key, title, text, and timestamp bucket.
   - Prevents duplicate alerts from notification updates or re-posts.

5. **`PaymentAlertManager`**:
   - Speech synthesis / Soundbox voice alert ("Payment received: ₹250").
   - Triggers only for `RECEIVED` events with `HIGH` or `MEDIUM` confidence.

6. **`PaymentEventSyncWorker`**:
   - Batches unsynced observed events.
   - Calls backend `POST /api/v1/organizations/:orgId/payment-events/observed`.
   - Handles network constraints and exponential backoff retry.

---

## 4. Strengthening the Notification Service
To guarantee that the app never misses a payment notification:
1. **Android 7+ Rebind Mechanism**:
   - Whenever Android terminates or unbinds the service, `onListenerDisconnected()` triggers `requestRebind(ComponentName)`.
2. **App Startup Verification**:
   - In `UPIEasyApp.onCreate()` and `MainActivity.onResume()`, the app verifies listener access and triggers `checkAndRequestRebind()`.
3. **Battery Optimization Handling**:
   - Prompts the user to exclude UPI-Easy from battery saver / Doze mode using `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
4. **Boot Completion Receiver**:
   - Rebinds the notification listener immediately upon device boot.
5. **Diagnostics Screen**:
   - In `PaymentDetectionSettingsScreen`, merchants can view live listener status, parser readiness, last detected timestamp, and run a manual rebind test.

---

## 5. Backend Architecture (Hono + Drizzle)
1. **Tenant Isolation**:
   - Every request is authenticated with JWT Bearer tokens.
   - Every database query strictly includes `organizationId` scoping.
2. **Financial Verification Safety**:
   - Backend endpoint `/payment-events/observed` forces `verificationStatus = "OBSERVED"` and `status = "UNKNOWN"`.
   - Client is rejected if attempting to claim `VERIFIED` status.
3. **Outbox Pattern**:
   - Outbox records ensure transactions and notification events are saved atomically in the same database transaction.
4. **Multi-Role Device Propagation**:
   - FCM sends lightweight payment notices to enrolled staff devices, directing the app to sync the complete event details.
