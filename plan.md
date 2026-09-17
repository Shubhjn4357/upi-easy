# UPI-Easy — Master Product & Engineering Build Prompt

## 1. Product Identity

Build a production-ready Android application called **UPI-Easy**.

UPI-Easy is a business/merchant-side UPI management application designed to help a business owner manage multiple legitimate bank accounts and UPI IDs from one place.

The application should provide:

* Mobile-number + OTP authentication
* Multiple bank-account management
* Multiple UPI-ID management
* UPI QR generation
* QR scanning
* UPI payment initiation through supported UPI applications
* Complete transaction-record management
* Owner/staff management
* Role-based permissions
* Real-time notifications
* Background synchronization
* Offline-first local data
* Business dashboard
* Transaction search/filtering
* Reconciliation
* Audit logs
* Voice notifications where appropriate
* Secure API architecture
* Scalable backend architecture
* API versioning
* Rate limiting
* Worker/background-job architecture
* Webhook/event architecture
* Multi-tenant organization model
* Jetpack Compose Android application

The product must be designed so that UPI-Easy can eventually support very large scale, including tens of millions of registered users, without requiring a complete architectural rewrite.

---

# 2. Critical Regulatory / Security Boundary

UPI-Easy is NOT itself a bank, PSP, TPAP, payment processor, or UPI switch unless the appropriate regulatory approvals, partnerships and integrations are obtained.

Do not implement fake payment processing.

The application must use legitimate UPI mechanisms and authorized providers.

UPI-Easy may:

* Store business configuration
* Store UPI IDs supplied/authorized by the user
* Generate UPI-compatible QR representations
* Scan UPI QR codes
* Initiate payment through Android UPI intents/deep links where supported
* Maintain transaction records obtained through legitimate integrations
* Consume authorized provider/bank webhooks
* Maintain reconciliation records
* Notify users about transaction events
* Maintain staff permissions
* Export accounting records

UPI-Easy must NEVER:

* Store UPI PIN
* Read UPI PIN
* Request bank passwords
* Request card PIN
* Request CVV
* Capture OTPs for financial authorization
* Bypass bank authentication
* Bypass UPI authentication
* Bypass Android device security
* Bypass biometric authentication
* Bypass app authentication
* Modify another banking application's security
* Scrape private banking sessions
* Fake successful transactions
* Mark a transaction successful without authoritative evidence
* Circumvent NPCI/bank/provider restrictions
* Claim to be an authorized PSP without authorization
* Intercept or manipulate another UPI application

Actual payment authorization remains with the user's bank/authorized UPI application.

---

# 3. Primary User Model

UPI-Easy uses a multi-tenant business model.

Hierarchy:

Organization
→ Owner
→ Staff
→ Bank Accounts
→ UPI IDs
→ QR Codes
→ Transactions
→ Reconciliation
→ Notifications
→ Audit Logs

A user can belong to multiple organizations if required.

Example:

Owner
├── Business A
│   ├── Bank Account 1
│   │   ├── UPI ID 1
│   │   └── UPI ID 2
│   ├── Bank Account 2
│   │   └── UPI ID 3
│   └── Staff
│       ├── Manager
│       ├── Cashier
│       └── Accountant
│
└── Business B
└── Bank Account
└── UPI ID

---

# 4. Authentication

Use:

* Mobile number
* OTP
* Secure session
* Refresh-token rotation
* Device registration
* Optional biometric app unlock

Authentication flow:

1. User enters mobile number.
2. Backend validates request.
3. OTP provider sends OTP.
4. User enters OTP.
5. Backend verifies OTP.
6. Create/login user.
7. Register device.
8. Create secure session.
9. Fetch organizations.
10. Enter dashboard.

Never store raw OTP values permanently.

Use:

* OTP expiry
* Attempt limits
* IP rate limits
* Device rate limits
* Mobile-number rate limits
* Replay protection
* Session expiration

---

# 5. Android Technology

Build the Android application using:

* Kotlin
* Jetpack Compose
* Material 3
* Kotlin Coroutines
* Flow
* ViewModel
* Navigation Compose
* Hilt
* Room
* DataStore
* WorkManager
* Retrofit
* OkHttp
* Kotlin Serialization or Moshi
* Android Keystore
* BiometricPrompt
* CameraX
* ML Kit where appropriate
* Firebase Cloud Messaging

Architecture:

UI
↓
ViewModel
↓
Use Case
↓
Repository
↓
Local / Remote Data Source

Do not place business logic directly inside Compose UI.

---

# 6. Modern Android UI

Use a modern 2026-style business interface.

Design principles:

* Material 3
* Edge-to-edge
* Adaptive layouts
* Rounded surfaces
* Subtle glass/translucent surfaces where appropriate
* Clear hierarchy
* Large touch targets
* Smooth transitions
* Subtle motion
* Haptic feedback for important interactions
* Dark/light theme
* Dynamic color where appropriate
* Low cognitive load
* Accessibility-first typography
* Excellent empty states
* Clear transaction status indicators

Primary navigation:

Home
Transactions
UPI
Staff
More

Use a floating/rounded navigation style where it improves usability.

Home should show:

* Today's received amount
* Today's sent amount
* Transaction count
* Pending transactions
* Failed transactions
* Recent payments
* Active UPI IDs
* Account health
* Notification summary

---

# 7. UPI Management

Create a dedicated UPI section.

Features:

## UPI IDs

Display:

* UPI ID
* Linked bank
* Account label
* Status
* QR
* Default UPI ID
* Created date
* Last transaction
* Transaction count

Actions:

* Add UPI ID
* Verify/associate through supported legitimate flow
* Set default
* Generate QR
* Share QR
* View transactions
* Disable/archive

Never imply that simply typing an arbitrary UPI ID gives the application control over that account.

---

# 8. QR Management

Allow users to create QR representations for legitimate UPI payment addresses.

QR screen:

* Business name
* UPI ID
* Optional amount
* Optional transaction note
* QR preview
* Save
* Share
* Print
* Full-screen QR

Support:

Static QR

Dynamic payment-request QR where an authorized integration supports it.

QR history:

* QR ID
* UPI ID
* Created time
* Status
* Usage count

---

# 9. QR Scanner

Implement QR scanning using CameraX.

Scanner should:

1. Detect QR.
2. Parse supported UPI URI.
3. Display:

   * Payee name
   * UPI ID
   * Amount if present
   * Note if present
4. Ask user to confirm.
5. Launch supported payment flow.
6. Return to UPI-Easy.
7. Wait for authoritative result where available.
8. Never assume success merely because the external app returned.

---

# 10. Payment Flow

UPI-Easy should NOT process the payment itself.

Flow:

User
↓
UPI-Easy
↓
Create/parse payment intent
↓
Supported UPI application
↓
Bank authentication
↓
UPI network
↓
Recipient bank
↓
Result
↓
UPI-Easy transaction reconciliation

The user enters their UPI PIN only inside the authorized UPI/banking flow.

Never collect it inside UPI-Easy.

---

# 11. Transaction System

Create a robust transaction ledger.

Transaction fields:

```text
id
organizationId
bankAccountId
upiAccountId
type
direction
amount
currency
status
paymentMethod
provider
providerTransactionId
upiTransactionId
referenceNumber
payerName
payerVpa
payeeName
payeeVpa
note
invoiceId
staffId
source
occurredAt
createdAt
updatedAt
```

Transaction types:

* PAYMENT
* REFUND
* REVERSAL
* TRANSFER
* COLLECTION
* ADJUSTMENT

Direction:

* RECEIVED
* SENT

Status:

* CREATED
* PENDING
* SUCCESS
* FAILED
* REVERSED
* REFUNDED
* UNKNOWN
* RECONCILIATION_REQUIRED

Never create a SUCCESS transaction from a local assumption.

---

# 12. Transaction Sources

Support an abstraction:

```ts
interface TransactionProvider {
  getTransactions(
    accountId: string,
    range: DateRange
  ): Promise<Transaction[]>;

  getTransactionStatus(
    referenceId: string
  ): Promise<TransactionStatus>;

  verifyWebhook(
    payload: unknown
  ): Promise<WebhookEvent>;
}
```

Possible legitimate sources:

* Authorized bank APIs
* Authorized payment-provider APIs
* Account Aggregator ecosystem where applicable
* Authorized merchant/payment provider integrations
* Webhooks
* User-imported statements

Do not depend on arbitrary banking-app scraping.

SMS parsing must NOT be the authoritative financial ledger.

---

# 13. Reconciliation Engine

Build reconciliation as a first-class subsystem.

Flow:

Provider transaction
↓
Normalize
↓
Deduplicate
↓
Match existing transaction
↓
Update status
↓
Generate transaction event
↓
Notify user
↓
Audit

Use:

* Provider transaction ID
* UPI reference
* Bank reference
* Idempotency key
* Amount
* Timestamp
* Account ID

for matching.

Handle:

* Duplicate events
* Delayed events
* Reversed payments
* Missing webhooks
* Provider downtime
* Unknown status
* Manual reconciliation

---

# 14. Staff Management

Owner can:

* Invite staff
* Remove staff
* Suspend staff
* Change role
* Restrict account access
* Restrict UPI ID access
* Restrict transaction visibility

Roles:

## Owner

Full access.

## Manager

Business management + transactions + staff visibility.

## Cashier

Payment/transaction operations but limited financial configuration.

## Accountant

Transactions, exports, reconciliation and reports.

Permissions should be granular.

Example:

```text
transactions.read
transactions.export
transactions.create
transactions.refund
accounts.read
accounts.manage
upi.read
upi.manage
qr.create
staff.read
staff.manage
reports.read
organization.manage
```

Authorization must be enforced server-side.

Hiding a button is not security. Humans have discovered browser developer tools, apparently.

---

# 15. Notifications

Implement:

* Firebase Cloud Messaging
* Server-generated notification events
* Android notification channels
* Background delivery
* Notification preferences

Events:

```text
payment.received
payment.sent
payment.failed
payment.reversed
transaction.pending
transaction.reconciled
staff.invited
staff.joined
staff.removed
security.alert
account.connected
provider.error
sync.completed
```

Example:

Payment received:

₹12,500 received

UPI: business@bank

Reference: XXXXXXXX

Do not expose unnecessary sensitive information in notification previews.

---

# 16. Voice Notifications

Optional voice notification system.

Example:

"Payment received. Twelve thousand five hundred rupees."

Voice should be:

* User-controlled
* Disableable
* Configurable
* Concise

Never speak:

* OTP
* UPI PIN
* Password
* API key
* Bank credentials
* Access token
* Sensitive account secrets

Voice should never be always-on.

---

# 17. Background Sync

Use WorkManager.

Sync states:

```text
LOCAL_ONLY
QUEUED
SYNCING
SYNCED
FAILED
CONFLICT
```

Architecture:

Room
↓
Sync Engine
↓
API
↓
Server
↓
Event Log
↓
FCM/WebSocket/SSE
↓
Room

Use incremental synchronization rather than downloading the entire transaction history repeatedly.

---

# 18. Realtime Sync

Create an event-based synchronization system.

Example:

```text
transaction.created
transaction.updated
transaction.reconciled
staff.updated
upi.updated
notification.created
```

Each organization has an ordered event stream.

Client stores:

```text
lastSequence
```

When reconnecting:

```text
GET /v1/sync?afterSequence=12345
```

Server returns missing events.

This prevents the app from depending entirely on persistent WebSockets.

Use:

* WebSocket where appropriate
* SSE where appropriate
* FCM for mobile wake-up/notification
* WorkManager for guaranteed background reconciliation

---

# 19. Backend

Recommended backend:

* Node.js
* TypeScript
* Hono
* Drizzle ORM
* PostgreSQL
* Zod
* Pino
* Sentry

Database options:

Primary recommendation:

Neon PostgreSQL

Alternatives:

* Supabase PostgreSQL
* Turso where relational requirements remain compatible
* Firebase for specific event/notification requirements

For a transaction-heavy multi-tenant financial-management application, PostgreSQL should be the primary relational database.

---

# 20. Backend Architecture

Use a modular monolith initially.

```text
apps/
  api/
    src/
      modules/
        auth/
        users/
        organizations/
        members/
        roles/
        permissions/
        bank-accounts/
        upi/
        qr/
        transactions/
        reconciliation/
        notifications/
        devices/
        sync/
        audit/
        providers/
        reports/
        exports/
      middleware/
      workers/
      queues/
      lib/
      config/
      index.ts
```

Do NOT start with microservices.

Split services only when actual scale and workload justify it.

---

# 21. Database Schema

Core tables:

```text
users
organizations
organization_members
roles
permissions
role_permissions

devices
sessions

bank_accounts
upi_accounts
qr_codes

transactions
transaction_events
transaction_references

payment_requests
reconciliation_records

notifications
notification_devices
notification_preferences

audit_logs

sync_cursors
outbox_events

provider_connections
provider_webhooks

reports
exports
```

Every business-owned table must be tenant-aware.

Prefer:

```text
organization_id
```

and enforce authorization at the service/query layer.

---

# 22. API Versioning

Use:

```text
/api/v1
```

Example:

```text
POST /api/v1/auth/request-otp
POST /api/v1/auth/verify-otp

GET /api/v1/me

GET /api/v1/organizations
POST /api/v1/organizations

GET /api/v1/organizations/:id/accounts
POST /api/v1/organizations/:id/accounts

GET /api/v1/organizations/:id/upi
POST /api/v1/organizations/:id/upi

GET /api/v1/organizations/:id/qr
POST /api/v1/organizations/:id/qr

GET /api/v1/organizations/:id/transactions
GET /api/v1/transactions/:id

GET /api/v1/organizations/:id/staff
POST /api/v1/organizations/:id/staff

GET /api/v1/notifications
POST /api/v1/sync

POST /api/v1/webhooks/:provider
```

Never silently break v1.

---

# 23. Rate Limiting

Rate limit by:

* IP
* User
* Organization
* Device
* Endpoint
* API token
* OTP request
* OTP verification
* Provider
* Webhook
* Export job

Example:

OTP:

```text
5 requests / 15 minutes / mobile number
```

Authentication:

```text
strict IP + device + account limits
```

Normal APIs:

```text
user/org based quotas
```

Webhook endpoints:

Use signature verification + replay protection + idempotency.

---

# 24. Idempotency

All financial operations must support idempotency.

Example:

```http
Idempotency-Key: UUID
```

Server stores:

```text
idempotency_key
organization_id
operation
request_hash
response
created_at
```

If the same operation is retried, return the previous result instead of creating a duplicate.

---

# 25. Worker Architecture

Never perform heavy work inside the API request.

Architecture:

```text
Client
  ↓
Cloudflare/API Gateway
  ↓
Hono API
  ↓
Database / Queue
  ↓
Workers
```

Workers:

```text
notification-worker
sync-worker
reconciliation-worker
report-worker
export-worker
webhook-worker
cleanup-worker
provider-worker
```

Potential infrastructure:

* Cloudflare Workers
* Cloudflare Queues
* Neon PostgreSQL
* Cloudflare R2
* FCM
* External OTP provider

---

# 26. Outbox Pattern

Use:

```text
transaction
↓
database commit
↓
outbox event
↓
worker
↓
notification/provider/reconciliation
```

Never rely on:

```text
database write
↓
hope notification sends
```

because distributed systems enjoy turning hope into production incidents.

---

# 27. Security Architecture

Security layers:

```text
Android Authentication
        ↓
TLS
        ↓
API Authentication
        ↓
Organization Authorization
        ↓
Role Permission
        ↓
Resource Authorization
        ↓
Operation Validation
        ↓
Audit
```

Android:

* Android Keystore
* Encrypted token storage
* BiometricPrompt
* Secure session handling
* Certificate/TLS validation
* No sensitive logs

Backend:

* Argon2id where passwords exist
* Short-lived access tokens
* Refresh-token rotation
* Device sessions
* RBAC
* Audit logging
* Input validation
* SQL parameterization through ORM
* Webhook signatures
* Replay protection
* Idempotency
* Security headers
* CORS restrictions
* Secret management

---

# 28. Audit Logs

Record security-sensitive operations:

```text
user.login
user.logout
staff.invited
staff.removed
role.changed
account.added
account.removed
upi.added
upi.removed
qr.created
transaction.viewed
transaction.exported
reconciliation.modified
security.setting.changed
```

Audit record:

```text
id
organizationId
actorId
action
resourceType
resourceId
metadata
ip
deviceId
createdAt
```

Never place secrets inside metadata.

---

# 29. Offline-First Design

The application should remain useful when temporarily offline.

Available offline:

* View cached transactions
* View cached UPI IDs
* View QR codes
* View staff
* Search cached data
* Queue safe non-financial operations

Never claim:

"Payment successful"

while offline.

Financial state must be confirmed by an authoritative source.

---

# 30. Transaction Search

Support:

* Date
* Amount
* UPI ID
* Bank account
* Status
* Payer
* Payee
* Reference number
* Staff
* Invoice
* Transaction type

Use pagination.

Do not load 1 million transactions into Android memory because humans apparently enjoy watching phones become space heaters.

---

# 31. Reports

Reports:

* Daily collection
* Monthly collection
* UPI-wise collection
* Bank-account-wise collection
* Staff-wise activity
* Failed transactions
* Pending transactions
* Reversed transactions
* Reconciliation report

Export:

* CSV
* Excel-compatible format
* PDF
* JSON

Large exports must run asynchronously.

---

# 32. Business Dashboard

Dashboard cards:

```text
Today's Collection

₹1,24,850
+18 transactions
```

```text
Pending

7
```

```text
Failed

2
```

```text
Active UPI IDs

4
```

Recent transactions list:

```text
₹4,500
Received
UPI: shop@bank
10:32 AM
SUCCESS
```

Use clear visual states.

---

# 33. Multi-Bank Architecture

Do not tightly couple the app to one bank.

Create:

```ts
interface BankProvider {
  connect(): Promise<void>;

  getAccounts(): Promise<BankAccount[]>;

  getTransactions(
    accountId: string,
    range: DateRange
  ): Promise<Transaction[]>;

  getTransactionStatus(
    reference: string
  ): Promise<TransactionStatus>;
}
```

Provider registry:

```text
BankProviderRegistry
```

Example:

```text
Provider A
Provider B
Provider C
Provider D
```

Only expose providers that have legitimate APIs/integration agreements.

---

# 34. Provider Adapter

Normalize external provider formats:

```text
External Provider
       ↓
Provider Adapter
       ↓
Normalized Transaction
       ↓
Transaction Service
       ↓
Postgres
```

Never let provider-specific schemas leak throughout the application.

---

# 35. 50 Million User Architecture

Design for horizontal scaling.

Do not claim that 50 million users automatically means 50 million concurrent users.

Architecture:

```text
                 CDN
                  ↓
          API Gateway / Edge
                  ↓
             Hono API
                  ↓
       ┌──────────┴──────────┐
       ↓                     ↓
   PostgreSQL              Queue
       ↓                     ↓
   Read replicas         Workers
       ↓                     ↓
      Cache             Notifications
                             ↓
                            FCM
```

Scale using:

* Stateless APIs
* Connection pooling
* Read replicas
* Database partitioning when justified
* Proper indexes
* Queue-based processing
* Caching
* CDN
* Object storage
* Async exports
* Event-driven reconciliation
* Regional infrastructure where required

Measure actual workload before adding complexity.

---

# 36. Database Indexes

Important indexes:

```text
transactions(organization_id, occurred_at)
transactions(organization_id, status)
transactions(organization_id, upi_account_id)
transactions(provider_transaction_id)
transaction_references(reference_number)
notifications(user_id, created_at)
audit_logs(organization_id, created_at)
organization_members(organization_id, user_id)
upi_accounts(organization_id)
bank_accounts(organization_id)
```

For large transaction tables, evaluate time/tenant partitioning based on real workload.

---

# 37. API Hooks

Create application hooks:

```text
beforeRequest
afterRequest

beforeTransactionCreate
afterTransactionCreate

beforeTransactionUpdate
afterTransactionUpdate

beforeReconciliation
afterReconciliation

beforeNotification
afterNotification

beforeStaffInvite
afterStaffInvite

beforeProviderWebhook
afterProviderWebhook

onAuthenticationFailure
onSecurityViolation
onProviderFailure
```

Hooks must not allow a plugin to bypass security policy.

---

# 38. Event System

Events:

```text
user.created
user.logged_in

organization.created
member.invited
member.joined
member.removed

bank_account.connected
upi_account.created
upi_account.updated

qr.created

transaction.created
transaction.pending
transaction.success
transaction.failed
transaction.reversed
transaction.reconciled

notification.created

security.alert
provider.error
```

Events should be versioned.

---

# 39. Android Project Structure

```text
android/
└── app/
    └── src/main/java/com/upieasy/
        ├── MainActivity.kt
        │
        ├── core/
        │   ├── auth/
        │   ├── network/
        │   ├── database/
        │   ├── security/
        │   ├── sync/
        │   ├── notifications/
        │   ├── analytics/
        │   └── navigation/
        │
        ├── data/
        │   ├── local/
        │   ├── remote/
        │   ├── repository/
        │   └── mapper/
        │
        ├── domain/
        │   ├── model/
        │   ├── repository/
        │   └── usecase/
        │
        ├── feature/
        │   ├── auth/
        │   ├── dashboard/
        │   ├── transactions/
        │   ├── upi/
        │   ├── qr/
        │   ├── staff/
        │   ├── accounts/
        │   ├── reports/
        │   ├── notifications/
        │   └── settings/
        │
        └── ui/
            ├── components/
            ├── theme/
            └── animation/
```

---

# 40. Backend Project Structure

```text
api/
├── src/
│   ├── app.ts
│   ├── server.ts
│   │
│   ├── modules/
│   │   ├── auth/
│   │   ├── users/
│   │   ├── organizations/
│   │   ├── members/
│   │   ├── permissions/
│   │   ├── accounts/
│   │   ├── upi/
│   │   ├── qr/
│   │   ├── transactions/
│   │   ├── reconciliation/
│   │   ├── providers/
│   │   ├── notifications/
│   │   ├── sync/
│   │   ├── audit/
│   │   ├── reports/
│   │   └── exports/
│   │
│   ├── middleware/
│   ├── workers/
│   ├── queues/
│   ├── events/
│   ├── db/
│   ├── config/
│   └── lib/
│
├── drizzle/
└── package.json
```

---

# 41. Environment Configuration

Never hard-code credentials.

Example:

```env
DATABASE_URL=
FCM_PROJECT_ID=
FCM_CLIENT_EMAIL=
FCM_PRIVATE_KEY=
OTP_PROVIDER_URL=
OTP_PROVIDER_KEY=
SENTRY_DSN=
R2_BUCKET=
R2_ACCESS_KEY=
R2_SECRET_KEY=
```

Use environment/secret management.

Never commit `.env`.

---

# 42. Testing

Android:

* Unit tests
* ViewModel tests
* Repository tests
* Room tests
* Sync tests
* UI tests
* QR parser tests

Backend:

* Unit tests
* Integration tests
* API tests
* Permission tests
* Webhook tests
* Idempotency tests
* Reconciliation tests
* Rate-limit tests
* Multi-tenant isolation tests

Critical security test:

User A must NEVER access:

```text
Organization B
Transaction B
Account B
UPI B
Staff B
```

even if the client modifies IDs manually.

---

# 43. Failure Handling

Provider unavailable:

```text
PENDING
```

not:

```text
SUCCESS
```

Webhook duplicated:

```text
Ignore duplicate
```

Webhook delayed:

```text
Keep pending
```

Network lost:

```text
Queue safe operation
```

Database temporarily unavailable:

```text
Retry with bounded exponential backoff
```

Worker failure:

```text
Retry
↓
Dead-letter queue
↓
Alert
```

Never infinitely retry financial operations.

---

# 44. Observability

Use:

* Structured Pino logs
* Sentry
* Metrics
* Trace IDs
* Request IDs
* Organization IDs
* User IDs where safe

Never log:

* OTP
* UPI PIN
* Password
* Access token
* Refresh token
* API key
* Bank credentials
* Sensitive payment secrets

---

# 45. Deployment

Recommended initial stack:

```text
Android
    ↓
Cloudflare
    ↓
Hono API
    ↓
Neon PostgreSQL

R2
↓
Reports / exports / files

FCM
↓
Push notifications

GitHub Actions
↓
Android APK/AAB builds
```

Start with one backend deployment and a modular architecture.

Do not over-engineer the first release into twenty-seven microservices and a Kubernetes cluster maintained by one exhausted developer.

---

# 46. Development Constraints

The developer may have:

* 8 GB RAM laptop
* Windows
* No Android Studio

Therefore the project must support:

* VS Code
* JDK 17
* Gradle Wrapper
* Android SDK command-line tools
* ADB
* Physical Android device
* GitHub Actions for remote builds

Do not require Android Studio for normal development.

Local emulator is optional.

Primary development device can be a physical Android phone.

---

# 47. Development Commands

Provide:

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
./gradlew lint
```

Backend:

```bash
pnpm install
pnpm dev
pnpm test
pnpm lint
pnpm typecheck
```

Database:

```bash
pnpm db:generate
pnpm db:migrate
pnpm db:studio
```

---

# 48. MVP Release

MVP should contain:

### Authentication

* Mobile login
* OTP
* Session
* Device registration

### Business

* Create organization
* Business profile
* Owner

### UPI

* Add/manage UPI IDs
* Generate QR
* Share QR
* Scan QR
* Launch supported UPI payment flow

### Transactions

* Transaction list
* Transaction detail
* Search
* Filters
* Status

### Staff

* Invite staff
* Roles
* Permissions
* Remove staff

### Notifications

* FCM
* Payment notification
* Staff notification
* Security notification

### Sync

* Room
* WorkManager
* Background sync

### Backend

* Hono
* PostgreSQL
* Drizzle
* RBAC
* Rate limiting
* Audit logs
* API v1

---

# 49. Phase 2

Add:

* Multiple provider integrations
* Automated reconciliation
* Advanced reports
* Accounting exports
* Invoice association
* Payment requests
* Dynamic QR where legitimately supported
* Provider webhooks
* Advanced staff permissions
* Voice notifications
* Advanced offline sync
* Business analytics

---

# 50. Phase 3

Add:

* Advanced reconciliation
* Multi-location businesses
* Branch management
* Advanced reporting
* Accounting integrations
* Provider marketplace
* Enterprise controls
* Regional infrastructure
* Large-scale event processing

Only introduce features requiring regulated payment capabilities after obtaining the appropriate partnerships/authorizations.

---

# 51. AI Development Agent Instructions

The coding agent building UPI-Easy must:

1. Inspect the existing repository before modifying anything.
2. Preserve working code.
3. Never delete unrelated functionality.
4. Maintain a single source of truth for configuration.
5. Use strict TypeScript.
6. Use Kotlin best practices.
7. Keep business logic out of UI.
8. Keep financial state authoritative.
9. Never fabricate transaction success.
10. Never store financial authentication secrets.
11. Enforce authorization on the server.
12. Add tests for security-sensitive changes.
13. Use migrations for schema changes.
14. Use backward-compatible API changes.
15. Add indexes for frequently queried transaction fields.
16. Use idempotency for financial operations.
17. Use event/outbox architecture for asynchronous operations.
18. Use bounded retries.
19. Never expose secrets in logs.
20. Never bypass OS, bank, UPI, biometric, authentication, or authorization security.

When implementing a feature:

```text
Inspect
↓
Understand
↓
Plan
↓
Implement
↓
Typecheck
↓
Test
↓
Lint
↓
Security review
↓
Verify
```

---

# 52. Agent Response Format

For development tasks, report:

```text
STATUS

What changed

FILES

Files created/modified

DATABASE

Schema/migration changes

API

Endpoints added/changed

ANDROID

Screens/components/features

SECURITY

Security implications

TESTS

Tests executed

VERIFICATION

Build/typecheck/lint result

NEXT
```

Do not claim something works unless it has been verified.

---

# 53. Prime Directive

UPI-Easy should feel like a simple, trustworthy business control center for legitimate UPI and bank transaction management.

The complexity belongs in:

* provider adapters
* reconciliation
* synchronization
* security
* workers
* authorization
* database architecture

not in the user's face.

The owner should be able to open the application and immediately understand:

**What money came in?
What money went out?
Which UPI ID received it?
Which bank account is involved?
What is pending?
What needs attention?
Which staff member did what?**

Build the product around those questions.

Never compromise financial correctness, user authorization, security, or regulatory boundaries for convenience.
