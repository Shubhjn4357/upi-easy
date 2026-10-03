# Product Changelog - UPI-Easy

All notable changes to the UPI-Easy platform (Android App & Cloud API) are documented in this log.

---

## [Version 2.0.0] - 2026-10-03
### Multi-UPI Notification Detection Expansion & System Strengthening

#### Added
- **BHIM UPI Integration**: Full notification observation and parsing support for BHIM UPI (`in.org.npci.upiapp`).
  - Contextual pattern recognition for credits, debits, Hindi transliterations, UTR extraction, and promotional filtering.
- **Paytm Integration**: Full notification observation and parsing support for Paytm (`net.one97.paytm`).
  - Contextual pattern recognition for wallet/bank credits, Paytm Payments Bank alerts, RRN extraction, and cashback notification suppression.
- **Auto-Rebind Listener Architecture**:
  - Implemented `onListenerDisconnected()` hook in `PaymentNotificationListenerService` calling Android 7+ `requestRebind()`.
  - Added `BootAndPackageRebindReceiver` to restore notification observation immediately after device boot and app updates.
  - Added proactive rebind calls in `UPIEasyApp.onCreate()` and `MainActivity`.
- **Payment App Registry**:
  - Centralized `SupportedPaymentApps` catalog defining PhonePe, Google Pay, BHIM UPI, and Paytm.
  - Backend API endpoint `GET /api/v1/payment-apps/supported` exposing all 4 active providers.
  - Dynamic `AndroidPaymentAppDetector` checking exact package visibility in `AndroidManifest.xml`.
- **Listener Health & Diagnostics UI**:
  - New diagnostic card in **Payment Detection Settings** displaying real-time listener online/offline status, auto-rebind action, and battery optimization exemption shortcuts.
- **Backend Ingestion Expansion**:
  - Ingestion endpoint `POST /api/v1/organizations/:orgId/payment-events/observed` updated to accept `NOTIFICATION_BHIM` and `NOTIFICATION_PAYTM`.
  - Payment account creation and updates accept `bhim` and `paytm` app identifiers.
- **Documentation & Legal Disclosures**:
  - Created `AGENT.md`, `memory.md`, `architecture.md`, and `database structure.md`.
  - Added Permissions Guide, Platform Changelog, and System Sitemap.

#### Changed
- **Strict Verification Semantics**:
  - Ensured that notification-originated payments recorded in `local_transactions` are assigned `status = UNKNOWN` and `verificationStatus = OBSERVED`, strictly preventing false "SUCCESS" claims without authoritative bank reconciliation.
- **Idempotency Fingerprinting**:
  - Enhanced deterministic SHA-256 fingerprint generation to guarantee zero duplicate ledger entries on notification updates or re-posts.

---

## [Version 1.0.0] - Initial Release
- Notification-based payment observation for Google Pay and PhonePe.
- Offline-first local SQLite Room storage and WorkManager sync.
- Multi-tenant organization architecture with role-based access control (OWNER, MANAGER, CASHIER, ACCOUNTANT).
- Hono API backend with Drizzle ORM and Neon Postgres support.
- Local soundbox voice announcements for received UPI credits.
- Static and dynamic UPI QR code generator and counter management.
