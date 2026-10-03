Below is a **copy-paste agentic prompt** for updating UPI-Easy. I’ve kept the architecture extensible so adding BHIM and Paytm does not turn the notification parser into the usual spaghetti creature.

The package IDs are currently documented as `in.org.npci.upiapp` for BHIM and `net.one97.paytm` for Paytm. Paytm itself publishes these package identifiers in its UPI integration documentation, and the current Play Store listings confirm the apps. ([Paytm Payments Services Limited][1])

# UPI-Easy: Multi-UPI Notification Detection Expansion

## Role

You are the lead Android + backend engineer working inside the existing **UPI-Easy** codebase.

Your job is to inspect the existing implementation first, understand the current architecture, and then implement a production-ready expansion of the UPI notification detection system.

Do NOT rewrite working parts of the application unnecessarily.

The current application already supports notification-based payment observation for:

* Google Pay
* PhonePe

Now extend the same architecture to support:

* BHIM UPI
* Paytm

The architecture must also make it easy to add additional UPI applications later without modifying the core notification listener.

---

# 1. Product Goal

UPI-Easy should allow a business/payment account to select the UPI application used on the Android device for payment-notification detection.

Supported applications for this release:

| App        | Package                                  |
| ---------- | ---------------------------------------- |
| Google Pay | `com.google.android.apps.nbu.paisa.user` |
| PhonePe    | `com.phonepe.app`                        |
| BHIM UPI   | `in.org.npci.upiapp`                     |
| Paytm      | `net.one97.paytm`                        |

Paytm's own integration documentation lists these package identifiers for Paytm, Google Pay, PhonePe and BHIM.

BHIM's current Android package is `in.org.npci.upiapp`.

Paytm's current Android package is `net.one97.paytm`.

---

# 2. IMPORTANT PAYMENT-VERIFICATION RULE

This feature uses Android notification observation.

A notification is NOT authoritative proof that money reached the bank account.

Therefore:

```text
Notification received
        ↓
Parser recognizes payment
        ↓
Create OBSERVED event
        ↓
Store locally
        ↓
Sync to backend
        ↓
Notify authorized staff
```

Never automatically convert:

```text
OBSERVED
```

into:

```text
VERIFIED
```

unless an authoritative provider/bank integration later confirms the transaction.

The system must preserve this distinction everywhere.

---

# 3. Existing Architecture To Preserve

First inspect the existing codebase.

Find:

* NotificationListenerService
* payment notification parser
* payment account model
* UPI account model
* QR model
* Room database
* observed payment event entity
* transaction entity
* WorkManager sync
* Hono API
* Drizzle schema
* FCM notification system
* organization/member permissions
* payment account UI
* QR management UI
* existing PhonePe parser
* existing Google Pay parser

Do not create duplicate implementations if these already exist.

Reuse existing:

* repositories
* ViewModels
* use cases
* DAOs
* API clients
* authentication
* organization context
* permission system
* sync engine
* event/outbox architecture
* logging
* error handling
* dependency injection

---

# 4. Target Architecture

Refactor the notification system into a provider/parser registry.

The architecture should become:

```text
Android NotificationListenerService
              │
              ▼
      PaymentNotificationProcessor
              │
              ▼
      PaymentAppRegistry
              │
      ┌───────┼────────┬────────┐
      ▼       ▼        ▼        ▼
   GPay    PhonePe    BHIM    Paytm
  Parser    Parser    Parser   Parser
      │       │        │        │
      └───────┴────────┴────────┘
                  │
                  ▼
         ParsedPaymentEvent
                  │
                  ▼
       PaymentAccountResolver
                  │
                  ▼
         ObservedPaymentEvent
                  │
        ┌─────────┴──────────┐
        ▼                    ▼
     Room DB              Sync Queue
                              │
                              ▼
                        Hono API
                              │
                              ▼
                         Neon/Postgres
                              │
                              ▼
                          Outbox Event
                              │
                              ▼
                             FCM
                              │
                              ▼
                    Authorized Staff Devices
```

---

# 5. Create Payment App Registry

Create a central registry instead of hardcoding package-name checks throughout the application.

Example:

```kotlin
data class PaymentAppDefinition(
    val id: String,
    val displayName: String,
    val packageName: String,
    val parserKey: String,
    val enabled: Boolean = true
)
```

Create:

```kotlin
object SupportedPaymentApps {

    val GOOGLE_PAY = PaymentAppDefinition(
        id = "google_pay",
        displayName = "Google Pay",
        packageName = "com.google.android.apps.nbu.paisa.user",
        parserKey = "google_pay"
    )

    val PHONEPE = PaymentAppDefinition(
        id = "phonepe",
        displayName = "PhonePe",
        packageName = "com.phonepe.app",
        parserKey = "phonepe"
    )

    val BHIM = PaymentAppDefinition(
        id = "bhim",
        displayName = "BHIM UPI",
        packageName = "in.org.npci.upiapp",
        parserKey = "bhim"
    )

    val PAYTM = PaymentAppDefinition(
        id = "paytm",
        displayName = "Paytm",
        packageName = "net.one97.paytm",
        parserKey = "paytm"
    )

    val ALL = listOf(
        GOOGLE_PAY,
        PHONEPE,
        BHIM,
        PAYTM
    )
}
```

Do not scatter package strings throughout the application.

---

# 6. Android Package Visibility

Update AndroidManifest.xml.

Add:

```xml
<queries>

    <package android:name="com.google.android.apps.nbu.paisa.user" />

    <package android:name="com.phonepe.app" />

    <package android:name="in.org.npci.upiapp" />

    <package android:name="net.one97.paytm" />

</queries>
```

Do NOT request:

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

The application only needs visibility for the supported payment applications.

---

# 7. PaymentAppDetector

Create or extend:

```kotlin
interface PaymentAppDetector {

    fun getInstalledSupportedApps(): List<PaymentAppDefinition>

    fun isInstalled(packageName: String): Boolean
}
```

Implementation should use:

```kotlin
PackageManager
```

and the supported registry.

The UI should dynamically display only applications actually installed on the device.

Example:

```text
Payment Detection App

Choose the UPI app used on this device

✓ Google Pay
  Installed

✓ PhonePe
  Installed

✓ BHIM UPI
  Installed

✗ Paytm
  Not installed
```

Do not show unsupported applications.

---

# 8. Notification Listener

Keep ONE notification listener.

Do not create separate services for each UPI application.

Example:

```kotlin
class PaymentNotificationListenerService :
    NotificationListenerService() {

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        paymentNotificationProcessor.process(sbn)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        paymentNotificationProcessor.onConnected()
    }
}
```

The listener should:

1. Receive notification.
2. Check package.
3. Ignore unsupported packages.
4. Find the corresponding parser.
5. Parse notification.
6. Validate parsed result.
7. Resolve payment account.
8. Generate deterministic event fingerprint.
9. Store locally.
10. Queue synchronization.

---

# 9. Parser Interface

Use a common interface:

```kotlin
interface PaymentNotificationParser {

    fun supports(packageName: String): Boolean

    fun parse(
        notification: RawPaymentNotification
    ): ParsedPaymentEvent?
}
```

Create:

```text
GooglePayNotificationParser
PhonePeNotificationParser
BhimNotificationParser
PaytmNotificationParser
```

Do not put all parsing logic into one giant:

```kotlin
when(packageName)
```

block.

---

# 10. Raw Notification Model

Normalize Android notification data before parsing.

Create:

```kotlin
data class RawPaymentNotification(
    val packageName: String,
    val notificationKey: String,
    val notificationId: Int,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val category: String?,
    val extras: Bundle?
)
```

Extract:

```kotlin
Notification.EXTRA_TITLE
Notification.EXTRA_TEXT
Notification.EXTRA_BIG_TEXT
Notification.EXTRA_SUB_TEXT
```

Do not upload the entire Android Notification object.

---

# 11. Parsed Payment Event

Create/extend:

```kotlin
data class ParsedPaymentEvent(
    val sourceAppId: String,
    val direction: PaymentDirection,
    val amountMinor: Long,
    val currency: String,
    val payerName: String?,
    val payerVpa: String?,
    val payeeName: String?,
    val payeeVpa: String?,
    val transactionReference: String?,
    val observedAt: Instant,
    val confidence: ParseConfidence
)
```

Use integer minor units.

For INR:

```text
₹250.00 -> 25000 paise
₹50.00  -> 5000 paise
```

Never use:

```kotlin
Double
Float
```

for money.

---

# 12. Parser Requirements

Each parser must recognize only payment notifications belonging to its application.

Do not assume notification formats are identical.

The parsers should support:

### Google Pay

Detect likely:

```text
Money received
Payment received
Received ₹...
```

and equivalent current notification formats.

### PhonePe

Detect likely:

```text
Payment received
Received ₹...
Money received
```

and equivalent current notification formats.

### BHIM UPI

Create parser logic for BHIM-specific notification titles/text.

The parser should be tolerant of:

* Hindi
* English
* localized number formatting
* currency symbols
* punctuation changes
* notification title/text placement

### Paytm

Create parser logic for Paytm-specific notification titles/text.

Paytm currently advertises a "Receive Money" widget and payment notification functionality, so notification-based observation should be treated as an application-specific parser rather than assuming a universal UPI notification format.

---

# 13. Do NOT Hardcode One Notification Format

Bad:

```kotlin
if (text == "Payment received ₹100")
```

Good:

```kotlin
PaymentNotificationParser
    ↓
candidate extraction
    ↓
amount extraction
    ↓
direction detection
    ↓
reference extraction
    ↓
confidence calculation
```

Use multiple patterns per provider.

Example:

```kotlin
private val amountPatterns = listOf(
    Regex("""₹\s*([\d,]+(?:\.\d{1,2})?)"""),
    Regex("""INR\s*([\d,]+(?:\.\d{1,2})?)""")
)
```

Normalize:

```text
₹1,250.50
1,250.50
INR 1250.50
₹ 1250
```

into:

```text
125050 paise
125050 paise
125050 paise
125000 paise
```

---

# 14. Parse Confidence

Add:

```kotlin
enum class ParseConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

Example:

### HIGH

Notification clearly contains:

* received/payment language
* amount
* reference

### MEDIUM

Contains:

* received/payment language
* amount

### LOW

Contains:

* ambiguous payment-related language

Low-confidence events should not silently appear as successful transactions.

---

# 15. Direction

Support:

```kotlin
enum class PaymentDirection {
    RECEIVED,
    SENT,
    UNKNOWN
}
```

For merchant/business detection, the primary use case is:

```text
RECEIVED
```

Do not assume every UPI notification means money was received.

For example:

```text
You paid ₹500
```

must not become:

```text
Incoming ₹500
```

---

# 16. Payment Account Mapping

The existing architecture should continue using:

```text
Organization
   ↓
Payment Account
   ↓
UPI ID
   ↓
QR Codes
```

A payment account should contain:

```text
paymentAccountId
organizationId
label
upiId
paymentAppId
paymentAppPackage
detectionEnabled
```

Example:

```text
Payment Account
-----------------------------
Main Business UPI

UPI ID:
business@upi

Detection App:
PhonePe

Detection:
Enabled

QR Codes:
- Counter 1
- Counter 2
- Counter 3
```

Do NOT assign the payment app directly to each QR unless the existing architecture absolutely requires it.

---

# 17. Multiple Payment Accounts

Support:

```text
Organization
│
├── Payment Account A
│     ├── PhonePe
│     ├── business@upi
│     ├── QR Counter 1
│     └── QR Counter 2
│
├── Payment Account B
│     ├── Google Pay
│     ├── shop@upi
│     └── QR Counter 3
│
├── Payment Account C
│     ├── BHIM
│     └── company@upi
│
└── Payment Account D
      ├── Paytm
      └── store@paytm
```

---

# 18. QR Attribution Rule

Do NOT assume:

```text
Notification received
        ↓
QR #3 received it
```

unless the notification/reference contains enough information to establish that relationship.

A notification may only identify:

```text
Payment Account
```

and not the physical QR.

Therefore:

```text
paymentAccountId = known
qrId = null
```

is valid.

Never fabricate QR attribution.

---

# 19. Device-to-Payment-Account Assignment

Preserve/add:

```text
payment_detection_devices
```

Fields:

```text
id
organization_id
device_id
payment_account_id
enabled
created_at
updated_at
```

This solves multi-company and multi-account ambiguity.

Example:

```text
Device A
    ↓
Organization Shop A
    ↓
Payment Account PhonePe

Device B
    ↓
Organization Shop A
    ↓
Payment Account BHIM
```

A notification received on Device B must not accidentally become a transaction for Device A's payment account.

---

# 20. Room Database

Extend the existing observed event entity if required.

Recommended structure:

```kotlin
@Entity(
    tableName = "observed_payment_events",
    indices = [
        Index(value = ["eventFingerprint"], unique = true),
        Index(value = ["paymentAccountId"]),
        Index(value = ["organizationId"]),
        Index(value = ["sourceAppId"]),
        Index(value = ["observedAt"])
    ]
)
data class ObservedPaymentEventEntity(
    @PrimaryKey
    val id: String,

    val organizationId: String,

    val paymentAccountId: String?,

    val qrId: String?,

    val sourceAppId: String,

    val sourcePackage: String,

    val direction: String,

    val amountMinor: Long,

    val currency: String,

    val payerName: String?,

    val payerVpa: String?,

    val transactionReference: String?,

    val parseConfidence: String,

    val verificationStatus: String,

    val matchStatus: String,

    val eventFingerprint: String,

    val observedAt: Long,

    val syncState: String,

    val createdAt: Long
)
```

---

# 21. Verification Status

Use:

```kotlin
enum class VerificationStatus {
    OBSERVED,
    VERIFIED,
    UNVERIFIED,
    CONFLICT
}
```

Notification parsers must produce:

```text
OBSERVED
```

only.

Provider/bank integrations may later produce:

```text
VERIFIED
```

If notification and provider information disagree:

```text
CONFLICT
```

---

# 22. Match Status

Use:

```kotlin
enum class PaymentMatchStatus {
    MATCHED,
    UNMATCHED,
    AMBIGUOUS
}
```

Example:

```text
PhonePe notification
        ↓
Device assigned to Payment Account A
        ↓
MATCHED
```

If no payment account can be confidently identified:

```text
UNMATCHED
```

If multiple accounts could match:

```text
AMBIGUOUS
```

Do not randomly select one.

---

# 23. Idempotency

Notifications can be delivered multiple times.

The same payment may produce:

* notification update
* expanded notification
* duplicate notification
* repeated notification after device restart
* notification re-post

Create deterministic fingerprint.

Example inputs:

```text
sourcePackage
notificationKey
amountMinor
transactionReference
direction
normalizedTimestampBucket
```

Hash them:

```text
SHA-256
```

Store:

```text
eventFingerprint
```

with a unique database constraint.

---

# 24. Notification Updates

Do not create a new payment event simply because:

```text
same notificationKey
```

was updated.

Instead:

```text
notificationKey + reference
```

should be considered when determining whether an event is an update.

The processor must be idempotent.

---

# 25. Backend API

Extend the existing API.

Keep:

```http
POST /v1/payment-events/observed
```

Request:

```json
{
  "organizationId": "...",
  "paymentAccountId": "...",
  "qrId": null,
  "sourceAppId": "bhim",
  "sourcePackage": "in.org.npci.upiapp",
  "direction": "RECEIVED",
  "amountMinor": 25000,
  "currency": "INR",
  "payerName": "Rahul",
  "payerVpa": "rahul@upi",
  "transactionReference": "123456789",
  "parseConfidence": "HIGH",
  "verificationStatus": "OBSERVED",
  "observedAt": "2026-10-03T10:20:00Z",
  "eventFingerprint": "..."
}
```

---

# 26. Hono Validation

Use Zod.

Example:

```typescript
const observedPaymentEventSchema = z.object({
  organizationId: z.string(),
  paymentAccountId: z.string().nullable(),
  qrId: z.string().nullable(),
  sourceAppId: z.enum([
    "google_pay",
    "phonepe",
    "bhim",
    "paytm"
  ]),
  sourcePackage: z.string(),
  direction: z.enum([
    "RECEIVED",
    "SENT",
    "UNKNOWN"
  ]),
  amountMinor: z.number().int().positive(),
  currency: z.string().length(3),
  payerName: z.string().nullable(),
  payerVpa: z.string().nullable(),
  transactionReference: z.string().nullable(),
  parseConfidence: z.enum([
    "HIGH",
    "MEDIUM",
    "LOW"
  ]),
  verificationStatus: z.literal("OBSERVED"),
  observedAt: z.string(),
  eventFingerprint: z.string()
});
```

Server must NOT trust the Android client to declare:

```text
VERIFIED
```

for notification-originated events.

Force:

```text
verificationStatus = OBSERVED
```

for this endpoint.

---

# 27. Organization Authorization

Before accepting an event:

1. Authenticate device/user.
2. Find organization membership.
3. Verify device belongs to organization.
4. Verify payment account belongs to organization.
5. Verify device is assigned to payment account.
6. Verify payment account is active.
7. Validate event.
8. Check idempotency.
9. Insert event.
10. Create transaction.
11. Create outbox event.

Reject unauthorized combinations.

---

# 28. Transaction Creation

When an observed payment is received:

```text
ObservedPaymentEvent
        ↓
Transaction
```

Example:

```text
transaction.status = UNKNOWN
transaction.verificationStatus = OBSERVED
transaction.source = NOTIFICATION_BHIM
```

Do NOT set:

```text
SUCCESS
```

just because a notification exists.

---

# 29. Source Enum

Extend:

```kotlin
enum class PaymentEventSource {
    NOTIFICATION_GOOGLE_PAY,
    NOTIFICATION_PHONEPE,
    NOTIFICATION_BHIM,
    NOTIFICATION_PAYTM,
    PROVIDER_WEBHOOK,
    BANK_API,
    MANUAL
}
```

Backend equivalent:

```typescript
type PaymentEventSource =
  | "NOTIFICATION_GOOGLE_PAY"
  | "NOTIFICATION_PHONEPE"
  | "NOTIFICATION_BHIM"
  | "NOTIFICATION_PAYTM"
  | "PROVIDER_WEBHOOK"
  | "BANK_API"
  | "MANUAL";
```

---

# 30. FCM Notification

After backend accepts an observed payment:

```text
Observed event
      ↓
Outbox
      ↓
PAYMENT_OBSERVED
      ↓
FCM worker
      ↓
Authorized organization devices
```

FCM should contain minimal data.

Example:

```json
{
  "type": "PAYMENT_OBSERVED",
  "eventId": "...",
  "organizationId": "...",
  "paymentAccountId": "...",
  "amountMinor": 25000,
  "sourceApp": "bhim"
}
```

The receiving application should fetch/sync the complete event.

Do not place unnecessary sensitive payment information in FCM.

---

# 31. Local Soundbox Mode

Add an optional local notification announcement feature.

Settings:

```text
Payment Detection

☑ Detect payments

☑ Voice announcement

Language
English

Announcement
"Payment received ₹250"

Volume
System volume

Vibrate
On

Repeat protection
On
```

This makes UPI-Easy capable of behaving similarly to a software soundbox while still retaining its larger business-management architecture.

---

# 32. Voice Announcement Rules

Only announce:

```text
RECEIVED
```

events.

Do not announce:

```text
SENT
UNKNOWN
FAILED
```

unless explicitly configured.

Example:

```text
₹250 received
```

or:

```text
Payment received ₹250
```

Prevent duplicate announcements using the event fingerprint.

---

# 33. Supported-App UI

Update Add Payment Account screen.

Flow:

```text
Add Payment Account
        ↓
Payment Account Name
        ↓
UPI ID
        ↓
Select Detection App
        ↓
Installed Apps
```

Display:

```text
Choose payment app

┌────────────────────────────┐
│ Google Pay                 │
│ ● Installed                │
└────────────────────────────┘

┌────────────────────────────┐
│ PhonePe                    │
│ ● Installed                │
└────────────────────────────┘

┌────────────────────────────┐
│ BHIM UPI                   │
│ ● Installed                │
└────────────────────────────┘

┌────────────────────────────┐
│ Paytm                      │
│ ○ Not installed            │
└────────────────────────────┘
```

If an app is not installed:

```text
Not installed
```

Do not allow selection.

---

# 34. Notification Access UI

If NotificationListener access is disabled:

```text
Payment detection requires
notification access.

UPI-Easy only reads notifications
from the payment apps you selected.

[Open Notification Access]
```

Open:

```kotlin
Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
```

After returning to the application:

```text
Check access again
```

Do not assume the user enabled it.

---

# 35. Privacy UX

Clearly explain:

```text
UPI-Easy uses Android notification access
to detect payment notifications from
the selected UPI applications.

Only supported payment-app notifications
are processed for payment detection.

UPI-Easy does not read:
- UPI PIN
- bank password
- CVV
- card PIN
- authorization OTP
```

Do not process or upload unrelated notifications.

---

# 36. Notification Filtering

At the very beginning:

```kotlin
if (
    sbn.packageName !in supportedPackageNames
) {
    return
}
```

Then:

```text
Supported package
       ↓
Selected/assigned payment app?
       ↓
Yes
       ↓
Parser
```

If the user has not selected that app for any active payment account:

```text
Ignore
```

This reduces unnecessary processing and privacy exposure.

---

# 37. Parser Test Architecture

Create unit tests for every parser.

Structure:

```text
src/test/
    payment/
        parser/
            GooglePayNotificationParserTest
            PhonePeNotificationParserTest
            BhimNotificationParserTest
            PaytmNotificationParserTest
```

Each test should contain:

```text
Raw notification
       ↓
Expected ParsedPaymentEvent
```

Test:

* received payment
* sent payment
* failed payment
* pending payment
* refund
* amount formatting
* commas
* decimals
* ₹ symbol
* INR text
* Hindi/localized text where available
* payer name
* payer VPA
* reference number
* notification update
* malformed notification
* unrelated notification
* duplicate notification

---

# 38. Golden Notification Fixtures

Create test fixtures:

```text
fixtures/
    google_pay/
    phonepe/
    bhim/
    paytm/
```

Store sanitized notification examples.

Never commit real:

* phone numbers
* UPI IDs
* transaction IDs
* names
* financial information

Use:

```text
testuser@upi
TEST123456
₹250.00
```

---

# 39. Parser Safety

A parser returning:

```kotlin
null
```

is better than creating a false payment.

When uncertain:

```text
Do not create transaction.
```

False positives are worse than missed notifications for financial records.

---

# 40. Backend Database Migration

If existing Drizzle schema contains source enums, extend them.

Add:

```text
NOTIFICATION_BHIM
NOTIFICATION_PAYTM
```

If `sourceAppId` is stored as a string rather than enum, ensure Zod validation still restricts supported values.

Add indexes:

```text
organization_id
payment_account_id
source_app_id
observed_at
event_fingerprint
```

Ensure:

```text
event_fingerprint UNIQUE
```

where appropriate.

---

# 41. API Response

Observed events should expose:

```json
{
  "id": "...",
  "source": "NOTIFICATION_BHIM",
  "status": "UNKNOWN",
  "verificationStatus": "OBSERVED",
  "matchStatus": "MATCHED",
  "amountMinor": 25000,
  "currency": "INR",
  "observedAt": "...",
  "transactionReference": "..."
}
```

UI should clearly display:

```text
Observed via BHIM notification
```

rather than:

```text
Verified payment
```

---

# 42. Transaction UI

Add source badge:

```text
₹250
Received

BHIM UPI
Observed

10:32 AM
```

For Paytm:

```text
₹1,000
Received

Paytm
Observed

10:35 AM
```

For provider-verified transactions:

```text
₹1,000
Received

Verified
```

This distinction must remain visually obvious.

---

# 43. Payment Detection Dashboard

Create/update:

```text
Settings
   ↓
Payment Detection
```

Show:

```text
Detection Status
────────────────────

Notification Access
● Enabled

Apps

Google Pay       ●
PhonePe          ●
BHIM UPI         ●
Paytm            ●

Active Accounts

Main Business
PhonePe
business@upi

Counter Account
BHIM
shop@upi
```

Also show:

```text
Last detected payment

₹500
PhonePe
2 minutes ago
Observed
```

---

# 44. Diagnostics

Add:

```text
Payment Detection Diagnostics
```

Display:

```text
Notification access     Enabled
Processor               Running
Google Pay parser       Ready
PhonePe parser          Ready
BHIM parser             Ready
Paytm parser            Ready

Last notification       10:35:21
Last parsed event       10:35:20
Last sync                10:35:24
Pending uploads         0
```

Do not expose raw notification text by default.

Provide a developer/debug mode for sanitized diagnostic output only.

---

# 45. Error Handling

Handle:

```text
Notification listener disabled
Payment app uninstalled
Payment account deleted
Payment account disabled
Device unassigned
Parser failure
Malformed notification
Duplicate notification
Backend unavailable
FCM unavailable
Database failure
```

The local event should survive temporary backend failure.

Use:

```text
Room
  ↓
syncState = PENDING
  ↓
WorkManager
  ↓
retry
```

---

# 46. Offline Behavior

Detection must work offline.

Architecture:

```text
Payment App
      ↓
Android Notification
      ↓
Parser
      ↓
Room
```

No internet required for:

* detection
* parsing
* local history
* voice announcement

Internet is required only for:

```text
Room
 ↓
Hono API
 ↓
Neon
 ↓
FCM
```

---

# 47. WorkManager

Use existing WorkManager infrastructure.

Create/extend:

```kotlin
ObservedPaymentSyncWorker
```

Responsibilities:

1. Find pending events.
2. Upload batches.
3. Handle idempotent server response.
4. Mark uploaded events as synced.
5. Retry transient failures.
6. Back off.
7. Preserve events on permanent failure for inspection.

Use network constraints.

---

# 48. Multi-Device Behavior

Example:

```text
Shop Organization

Owner Phone
    ↓
PhonePe notification
    ↓
UPI-Easy
    ↓
Cloud
    ↓
FCM
    ├── Manager Phone
    ├── Accountant Phone
    └── Cashier Phone
```

Only authorized users/devices receive the event.

Respect:

```text
organization membership
role
permission
notification preferences
device status
```

---

# 49. Permissions

Example permissions:

```text
payment.read
payment.observe
payment.manage
payment.reconcile
payment.notifications
```

Suggested access:

### OWNER

All.

### MANAGER

Observe/read/payment notifications.

### ACCOUNTANT

Read/reconcile.

### CASHIER

Only the permissions already defined by the existing application.

Do not accidentally broaden role permissions.

---

# 50. Security Requirements

Never collect:

```text
UPI PIN
Bank password
CVV
Debit card PIN
OTP
Authentication secrets
```

Never use:

```text
AccessibilityService
root
private UPI databases
private IPC
reverse-engineered payment APIs
SMS scraping
```

for this feature.

Use only the Android notification listener mechanism and officially available application information.

---

# 51. App-Removal Handling

If the selected app is uninstalled:

```text
Payment Account
        ↓
Detection App
        ↓
Paytm
        ↓
Not installed
```

Display:

```text
Paytm is no longer installed.

Payment detection is paused.
```

Do not automatically delete the payment account.

---

# 52. App Update Resilience

Do not depend excessively on exact notification wording.

The parser architecture should support:

```text
parser version
```

Example:

```kotlin
data class PaymentParserMetadata(
    val parserKey: String,
    val version: Int
)
```

Start with:

```text
Google Pay parser v1
PhonePe parser v1
BHIM parser v1
Paytm parser v1
```

If a provider changes its notification format:

```text
Update parser
```

without changing the entire payment architecture.

---

# 53. Future Provider Registry

The final registry must make adding another application simple.

Example:

```kotlin
SupportedPaymentApps.ALL
```

should be enough to expose the app.

Future examples:

```text
Amazon Pay
WhatsApp
CRED
Freecharge
Airtel Thanks
Mobikwik
Jio
Bank UPI applications
```

Do not implement these now unless already present.

The architecture must support them later.

---

# 54. Feature Flag

Add:

```text
multi_upi_notification_detection
```

If the existing project has a feature flag system, use it.

Otherwise define a simple internal configuration.

The feature should be possible to disable without removing code.

---

# 55. Logging

Use structured logging.

Example:

```text
payment_notification_received
payment_notification_parsed
payment_notification_ignored
payment_notification_duplicate
payment_notification_unmatched
payment_notification_sync_failed
```

Never log:

```text
UPI PIN
OTP
full notification payload
bank credentials
unnecessary personal data
```

Mask:

```text
UPI IDs
phone numbers
transaction references
```

where appropriate.

---

# 56. Analytics

If the existing app has analytics, track only technical events:

```text
payment_detection_enabled
payment_detection_disabled
parser_success
parser_failure
notification_ignored
sync_success
sync_failure
```

Do not send raw financial notification content to analytics.

---

# 57. UI/UX Requirements

Use the existing UPI-Easy design system.

Do not introduce a separate visual language.

Maintain:

* Jetpack Compose
* Material 3
* adaptive colors
* existing typography
* existing spacing
* existing cards
* existing bottom navigation
* existing animations
* existing icons
* existing dark/light theme

Use the existing design tokens.

---

# 58. Add Payment Account Flow

Final flow:

```text
UPI
 ↓
Payment Accounts
 ↓
Add Payment Account
 ↓
Account Name
 ↓
UPI ID
 ↓
Choose Detection App
 ↓
Installed UPI Apps
 ↓
Google Pay / PhonePe / BHIM / Paytm
 ↓
Save
 ↓
Check Notification Access
 ↓
Open Settings if necessary
 ↓
Return
 ↓
Verify Access
 ↓
Detection Active
```

If no supported UPI application is installed:

```text
No supported payment app detected.

You can still create this payment account
and generate QR codes.
```

---

# 59. QR Creation

QR functionality must remain independent.

Flow:

```text
Payment Account
      ↓
QR Codes
      ↓
Create QR
```

Multiple QR codes may belong to one payment account.

Example:

```text
Main PhonePe Account

QR:
- Main Counter
- Counter 2
- Counter 3
- Delivery Desk
```

Notification detection belongs to the payment account.

---

# 60. Reconciliation

Observed notification events should appear in reconciliation.

Example:

```text
Observed:
₹500 via BHIM

Bank/provider:
No matching record

Result:
RECONCILIATION_REQUIRED
```

Do not mark as verified merely because the amount exists.

---

# 61. Transaction Matching

Future provider data may contain:

```text
amount
reference
timestamp
payer VPA
payee VPA
```

Create matching logic:

```text
Observed notification
        +
Provider transaction
        ↓
Matching engine
```

Strong match:

```text
reference + amount
```

Medium:

```text
amount + VPA + time window
```

Weak:

```text
amount only
```

Weak matches must not automatically become VERIFIED.

---

# 62. Testing Matrix

Test every provider:

| Test               | GPay | PhonePe | BHIM | Paytm |
| ------------------ | ---: | ------: | ---: | ----: |
| App detection      |    ✓ |       ✓ |    ✓ |     ✓ |
| Package detection  |    ✓ |       ✓ |    ✓ |     ✓ |
| Parser             |    ✓ |       ✓ |    ✓ |     ✓ |
| Received payment   |    ✓ |       ✓ |    ✓ |     ✓ |
| Sent payment       |    ✓ |       ✓ |    ✓ |     ✓ |
| Amount parsing     |    ✓ |       ✓ |    ✓ |     ✓ |
| Reference parsing  |    ✓ |       ✓ |    ✓ |     ✓ |
| Duplicate handling |    ✓ |       ✓ |    ✓ |     ✓ |
| Offline storage    |    ✓ |       ✓ |    ✓ |     ✓ |
| Backend sync       |    ✓ |       ✓ |    ✓ |     ✓ |
| FCM                |    ✓ |       ✓ |    ✓ |     ✓ |
| Voice alert        |    ✓ |       ✓ |    ✓ |     ✓ |

---

# 63. Integration Tests

Test:

```text
Notification
 ↓
Parser
 ↓
Room
 ↓
WorkManager
 ↓
Hono
 ↓
Drizzle
 ↓
Neon
 ↓
Outbox
 ↓
FCM
```

Verify the event ID remains consistent.

---

# 64. Important Edge Cases

Handle:

### Same amount multiple times

```text
₹500
₹500
₹500
```

They must not automatically collapse into one transaction.

### Same reference

Should deduplicate.

### Notification update

Should update/reconcile existing observation rather than blindly create another.

### Same notification from two devices

Do not assume they are the same physical observation unless the fingerprint/event identity proves it.

### App notification disabled

Show diagnostic warning.

### Battery optimization

Do not claim guaranteed detection.

### User clears notification

Do not interpret notification removal as payment reversal.

### Payment app changes notification wording

Parser should fail safely.

### Parser cannot confidently understand notification

Return:

```text
null
```

instead of creating a false payment.

---

# 65. Do Not Promise "100% Detection"

The UI/documentation must say:

```text
Payment detection depends on notifications generated
by the selected UPI application and Android device settings.

Notification-based events are observations and are not
independent proof of bank settlement.
```

Avoid marketing claims such as:

```text
Never miss a payment
100% guaranteed
Bank-confirmed
Instant guaranteed payment
```

unless a real authoritative provider integration supports those claims.

---

# 66. Implementation Order

Implement in this order.

## Phase 1

Inspect existing architecture.

Produce:

```text
ARCHITECTURE_FINDINGS.md
```

containing:

* current notification flow
* current parser architecture
* current Room schema
* current API
* current sync
* current FCM
* files that need modification
* files that should not be modified

Do not code before understanding the current implementation.

---

## Phase 2

Create/refactor:

```text
PaymentAppDefinition
PaymentAppRegistry
PaymentAppDetector
PaymentNotificationParser
PaymentNotificationProcessor
```

Make existing GPay and PhonePe use the new registry.

Do not break existing functionality.

---

## Phase 3

Add:

```text
BhimNotificationParser
PaytmNotificationParser
```

Add sanitized fixtures and tests.

---

## Phase 4

Add package visibility:

```text
com.google.android.apps.nbu.paisa.user
com.phonepe.app
in.org.npci.upiapp
net.one97.paytm
```

---

## Phase 5

Update Add Payment Account UI.

Show dynamically installed supported apps.

---

## Phase 6

Update Room schema and migrations.

Add source-app fields if missing.

---

## Phase 7

Update Hono + Zod + Drizzle.

Add BHIM/Paytm source values.

---

## Phase 8

Update WorkManager synchronization.

---

## Phase 9

Update FCM/outbox events.

---

## Phase 10

Add voice announcement support using the same parsed event.

---

## Phase 11

Add diagnostics.

---

## Phase 12

Run complete test suite.

---

# 67. Required Deliverables

At the end, provide:

```text
1. Architecture changes
2. Files created
3. Files modified
4. Database migrations
5. Android manifest changes
6. Parser implementation
7. Parser test fixtures
8. API changes
9. Drizzle changes
10. Room changes
11. WorkManager changes
12. FCM changes
13. UI changes
14. Permission changes
15. Security review
16. Offline behavior
17. Multi-device behavior
18. Known limitations
19. Test results
20. Remaining TODOs
```

---

# 68. Definition of Done

The feature is complete only when:

* Google Pay detection still works.
* PhonePe detection still works.
* BHIM detection works.
* Paytm detection works.
* Installed-app detection works.
* Package visibility works.
* Notification access status works.
* Payment accounts can select a detection app.
* Multiple payment accounts work.
* Multiple QR codes work.
* Device-to-account assignment works.
* Notification events are stored locally.
* Duplicate notifications are idempotent.
* Events sync offline-first.
* Hono validates events.
* Backend authorization works.
* Drizzle stores events correctly.
* Outbox events are generated.
* FCM reaches authorized devices.
* Voice announcements work.
* Observed events remain `OBSERVED`.
* Notification events never automatically become `VERIFIED`.
* Parser failures do not create fake transactions.
* Unrelated notifications are ignored.
* Sensitive credentials are never collected.
* No `QUERY_ALL_PACKAGES`.
* No AccessibilityService.
* No root/private database access.
* No SMS scraping.
* No private UPI APIs.
* Existing UPI-Easy functionality remains intact.

---

# 69. Final Architecture

The finished UPI-Easy payment detection system should look like:

```text
                    UPI-EASY
                       │
              ┌────────┴────────┐
              │ Payment Account  │
              └────────┬────────┘
                       │
                Detection App
                       │
       ┌───────────────┼────────────────┐
       │               │                │
       ▼               ▼                ▼
   Google Pay       PhonePe           BHIM
       │               │                │
       └───────────────┼────────────────┘
                       │
                     Paytm
                       │
                       ▼
          Android NotificationListener
                       │
                       ▼
             Notification Processor
                       │
                       ▼
                Parser Registry
                       │
                       ▼
             Parsed Payment Event
                       │
                       ▼
             Payment Account Resolver
                       │
                       ▼
                 Room Database
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
       Voice Alert          Sync Queue
                                 │
                                 ▼
                           Hono Worker
                                 │
                                 ▼
                            Drizzle/DB
                                 │
                        ┌────────┴────────┐
                        ▼                 ▼
                    Transaction        Outbox
                                          │
                                          ▼
                                         FCM
                                          │
                    ┌─────────────────────┼──────────────────┐
                    ▼                     ▼                  ▼
                  Owner                Manager           Accountant
```

The important architectural principle is:

```text
UPI app support = parser plugin
```

not:

```text
UPI app support = new payment system
```

That means adding another UPI application later should require approximately:

```text
1 PaymentAppDefinition
+
1 Parser
+
Parser tests
```

rather than rewriting the payment system.

---

# 70. First Action

Before making modifications:

1. Scan the complete UPI-Easy repository.
2. Identify the existing notification detection implementation.
3. Identify current PhonePe and Google Pay parsers.
4. Identify current payment-account schema.
5. Identify current Room entities.
6. Identify current Hono routes.
7. Identify Drizzle tables.
8. Identify sync/outbox/FCM implementation.
9. Identify existing UI flow.
10. Produce a concise architecture map.
11. Then implement the changes incrementally.

Never overwrite existing working architecture merely to match this prompt.

Adapt the implementation to the actual repository.

[1]: https://www.paytmpayments.com/docs/upi-smart-intent?utm_source=chatgpt.com "UPI Smart Intent"
Add the following section immediately after **Section 13: Do NOT Hardcode One Notification Format**. It gives the agent a strict fallback hierarchy without allowing weak heuristics to manufacture payments.

# 13A. NOTIFICATION-PARSER FALLBACK RULES

Notification formats are controlled by third-party UPI applications and may change without notice.

Parsers MUST therefore use a controlled fallback strategy.

The fallback strategy must improve compatibility **without increasing false-positive payment creation**.

---

## 13A.1 Provider-First Parsing

Every notification must first be routed using the exact package name.

```text
Notification package
        ↓
Exact package match
        ↓
PaymentAppDefinition
        ↓
Provider-specific parser
```

Example:

```text
com.phonepe.app
        ↓
PhonePeNotificationParser

in.org.npci.upiapp
        ↓
BhimNotificationParser

net.one97.paytm
        ↓
PaytmNotificationParser
```

Never use a generic parser before provider-specific parsing.

---

## 13A.2 Parser Fallback Levels

Each provider parser must use the following ordered levels:

```text
Level 1
Exact known notification pattern

        ↓ if no match

Level 2
Known provider-specific flexible patterns

        ↓ if no match

Level 3
Provider-specific semantic extraction

        ↓ if insufficient confidence

Level 4
Return null
```

Never skip directly from Level 1 to a generic "contains payment" heuristic.

---

## 13A.3 Level 1: Exact Known Patterns

The first parser layer should recognize known notification formats.

Example:

```kotlin
private val knownReceivedPatterns = listOf(
    Regex("""payment\s+received""", RegexOption.IGNORE_CASE),
    Regex("""money\s+received""", RegexOption.IGNORE_CASE),
    Regex("""received\s+₹""", RegexOption.IGNORE_CASE)
)
```

These patterns must be provider-specific.

Do not assume that a pattern valid for PhonePe is automatically valid for BHIM or Paytm.

---

## 13A.4 Level 2: Flexible Provider Patterns

If an exact pattern fails, use normalized provider-specific patterns.

Before matching:

1. Combine title/text/bigText where appropriate.
2. Normalize whitespace.
3. Normalize Unicode whitespace.
4. Normalize common currency formatting.
5. Normalize case.
6. Preserve the original text separately for diagnostics if required.
7. Do not destroy information needed for reference extraction.

Example:

```kotlin
fun normalizeNotificationText(value: String): String {
    return value
        .replace('\u00A0', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
}
```

Do not aggressively remove punctuation because punctuation may separate:

```text
amount
reference
VPA
name
```

---

## 13A.5 Level 3: Semantic Extraction

If exact wording changes, attempt independent extraction of:

```text
Amount
Direction
Reference
Payer/payee information
Payment-related intent
```

Do not require the entire notification to match one regular expression.

For example:

```text
Title:
Payment received

Text:
₹1,250 from Rahul
Ref: TEST123456
```

should be decomposed into:

```text
direction = RECEIVED
amount = 125000
payerName = Rahul
reference = TEST123456
```

---

## 13A.6 Required Minimum Evidence

A notification must contain enough evidence before it can become a `ParsedPaymentEvent`.

For a `RECEIVED` event, require:

```text
Provider identity
+
Payment/received semantic evidence
+
Valid amount
```

At minimum:

```text
payment signal + amount
```

must be present.

A notification containing only:

```text
₹500
```

must NOT be treated as a received payment.

A notification containing only:

```text
Payment
```

must NOT be treated as a received payment.

A notification containing:

```text
Your payment was successful
```

without a valid amount must NOT create a payment event.

---

## 13A.7 Reference Is Optional

A transaction reference is highly useful but must not be mandatory for parsing.

Valid:

```text
Payment received
₹500
```

may produce:

```text
amount = 50000
reference = null
confidence = MEDIUM
```

while:

```text
Payment received
₹500
Ref: TEST123456
```

may produce:

```text
amount = 50000
reference = TEST123456
confidence = HIGH
```

Do not reject otherwise valid payment notifications solely because the provider does not expose a reference.

---

## 13A.8 Direction Must Be Explicit

The parser must distinguish:

```text
RECEIVED
SENT
UNKNOWN
```

Do not infer `RECEIVED` merely because an amount exists.

Examples that must NOT become received payments:

```text
You paid ₹500
Payment sent ₹500
₹500 paid
You transferred ₹500
Payment successful: ₹500
```

unless the provider-specific context clearly establishes that the notification represents money received by the configured merchant/payment account.

When direction cannot be established:

```kotlin
direction = PaymentDirection.UNKNOWN
```

Then follow the existing policy for whether `UNKNOWN` events are stored or discarded.

Do not silently convert `UNKNOWN` into `RECEIVED`.

---

## 13A.9 Received-Payment Keyword Groups

Provider parsers may maintain keyword groups.

Example:

```kotlin
private val receivedSignals = setOf(
    "received",
    "money received",
    "payment received",
    "credited",
    "amount received"
)
```

However, keyword presence alone is insufficient.

The parser must also validate:

```text
amount
+
provider context
+
direction
```

before producing a received event.

---

## 13A.10 Sent-Payment Exclusion

Each parser must maintain explicit negative patterns for outgoing payments.

Examples:

```kotlin
private val sentSignals = setOf(
    "paid",
    "payment sent",
    "sent",
    "transferred",
    "debited",
    "you paid"
)
```

If both received and sent signals are detected:

```text
direction = UNKNOWN
```

unless a provider-specific rule unambiguously resolves the conflict.

Never choose `RECEIVED` simply because that is the application's primary merchant use case.

---

## 13A.11 Failed/Pending/Refunded Exclusion

The parser must recognize non-success payment states.

Examples:

```text
failed
declined
cancelled
pending
processing
refunded
reversed
```

These must not be interpreted as a successful received payment.

Map them to the existing transaction/event model.

For example:

```text
Payment failed
₹500
```

must not produce:

```text
RECEIVED
SUCCESS
```

---

## 13A.12 Notification Title/Text Fallback

Providers may place useful information in different notification fields.

The parser must inspect, in controlled order:

```text
EXTRA_TITLE
EXTRA_TEXT
EXTRA_BIG_TEXT
EXTRA_SUB_TEXT
```

Recommended normalized input:

```kotlin
data class NotificationTextSet(
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?
)
```

Do not assume:

```text
title = payment information
```

or:

```text
text = payment information
```

for every provider.

---

## 13A.13 BigText Fallback

If title/text does not contain enough information:

```text
title + text
```

then inspect:

```text
bigText
```

Example:

```text
Title:
Payment received

Text:
Tap to view

BigText:
₹1,250 received from Rahul
Reference TEST123456
```

The parser should use the more informative representation.

Do not create duplicate events simply because the same information exists in both `text` and `bigText`.

---

## 13A.14 Amount Extraction Fallback

Amount extraction should support:

```text
₹1,250
₹ 1,250
₹1,250.00
INR 1250
INR 1,250.50
Rs 1250
Rs. 1,250
```

Use a controlled amount extractor.

Do not accept arbitrary numbers as payment amounts.

For example:

```text
Reference: 123456789
```

must not be interpreted as:

```text
₹123,456,789
```

The amount extractor should consider:

* currency marker
* currency position
* decimal structure
* nearby payment semantics
* provider-specific notification layout

before accepting a number.

---

## 13A.15 Multiple Amounts

A notification may contain more than one number.

Example:

```text
Payment received ₹500
Available balance ₹2,500
```

The parser must select the payment amount:

```text
₹500
```

not:

```text
₹2,500
```

Do not simply select:

```kotlin
numbers.first()
```

or:

```kotlin
numbers.last()
```

unless a provider-specific rule establishes that behavior.

If multiple plausible payment amounts exist and the parser cannot determine which is the transaction amount:

```text
confidence = LOW
```

and do not create a normal received-payment transaction.

---

## 13A.16 Reference Extraction Fallback

References may appear as:

```text
Ref
Reference
Txn
Transaction ID
UTR
UPI Ref
UPI Transaction ID
```

Normalize labels but preserve the original reference value.

Example:

```text
Ref: TEST123456
```

becomes:

```text
transactionReference = TEST123456
```

Do not confuse:

```text
phone number
UPI ID
order number
notification ID
```

with a transaction reference unless provider-specific rules identify it as such.

---

## 13A.17 VPA Extraction Fallback

If the notification exposes a UPI ID:

```text
rahul@upi
```

extract it only if it matches a valid UPI/VPA-like structure.

Do not infer a VPA from arbitrary text.

If payer VPA is ambiguous:

```text
payerVpa = null
```

Do not invent it.

---

## 13A.18 Name Extraction

Names are optional.

If the parser cannot confidently distinguish:

```text
payerName
```

from other notification text:

```text
payerName = null
```

Do not use a generic token as a person's name merely because it appears next to the amount.

---

## 13A.19 Language Fallback

Parsers should support known localized notification wording where practical.

Initial support should prioritize:

```text
English
Hindi
```

Do not implement a full translation engine.

Use provider-specific keyword dictionaries.

Example:

```kotlin
data class PaymentLanguagePatterns(
    val received: Set<String>,
    val sent: Set<String>,
    val failed: Set<String>,
    val pending: Set<String>
)
```

Additional languages may be added later without changing parser architecture.

---

## 13A.20 Unicode Normalization

Normalize Unicode before matching.

Handle:

* non-breaking spaces
* different whitespace characters
* common Unicode punctuation
* currency symbols
* localized digit representations where safely supported

Do not blindly transliterate all text.

Preserve the original normalized fields needed for diagnostics.

---

## 13A.21 Generic Fallback Parser

A generic fallback parser MAY exist, but it has strict restrictions.

It may run only after:

```text
1. Exact package match
2. Provider-specific parser
3. Provider-specific flexible fallback
```

It MUST NOT independently create a `RECEIVED` payment merely because it sees:

```text
₹ + number
```

The generic fallback may only produce:

```text
UNKNOWN / LOW confidence
```

or return:

```kotlin
null
```

unless multiple independent payment signals are present.

---

## 13A.22 Generic Fallback Safety Rule

The generic fallback MUST require at least:

```text
Payment semantic signal
+
Currency/amount signal
+
No strong outgoing/failed/pending signal
```

Example:

```text
Money received ₹500
```

may qualify.

But:

```text
Your balance is ₹500
```

must not qualify.

And:

```text
You paid ₹500
```

must not qualify.

---

## 13A.23 Provider Parser Wins

If a provider-specific parser returns a valid result:

```text
Provider parser result
        ↓
Use it
```

Do not subsequently run the generic parser and overwrite it.

The generic parser is fallback only.

---

## 13A.24 Conflicting Parser Results

If multiple parser strategies produce conflicting interpretations:

```text
Parser A:
RECEIVED ₹500

Parser B:
SENT ₹500
```

do not select one arbitrarily.

Return:

```text
direction = UNKNOWN
confidence = LOW
```

or:

```text
null
```

depending on the conflict.

Never resolve financial ambiguity using "most likely."

---

## 13A.25 Confidence Rules

Use deterministic confidence scoring.

Example:

### HIGH

```text
+ provider-specific known payment pattern
+ valid amount
+ clear direction
+ valid reference
```

### MEDIUM

```text
+ provider-specific payment signal
+ valid amount
+ clear direction
```

### LOW

```text
+ generic payment signal
+ amount
```

A LOW-confidence event must not be presented as a verified/successful payment.

Follow the existing project policy for whether LOW-confidence events are:

```text
stored as UNKNOWN
```

or:

```text
discarded
```

---

## 13A.26 Fallback Must Never Upgrade Verification

Parser fallback can change:

```text
parseConfidence
```

but it must never change:

```text
verificationStatus
```

All notification-originated events remain:

```text
verificationStatus = OBSERVED
```

regardless of parser confidence.

Example:

```text
HIGH confidence
+
notification source
=
OBSERVED
```

not:

```text
HIGH confidence
=
VERIFIED
```

---

## 13A.27 Parser Versioning

Every parser result should be traceable to a parser version.

Example:

```kotlin
data class ParsedPaymentEvent(
    ...
    val parserKey: String,
    val parserVersion: Int,
    val confidence: ParseConfidence
)
```

Example:

```text
phonepe:v1
google_pay:v1
bhim:v1
paytm:v1
```

When notification formats change:

```text
paytm:v1
    ↓
paytm:v2
```

without changing the rest of the transaction pipeline.

---

## 13A.28 Parser Diagnostics

In debug builds, expose:

```text
Provider:
Paytm

Parser:
PaytmNotificationParser

Parser Version:
1

Matched Level:
2

Direction:
RECEIVED

Amount:
₹500

Reference:
TEST123456

Confidence:
HIGH
```

Do not expose raw notification content in production diagnostics unless explicitly required and sanitized.

---

## 13A.29 Parser Failure Logging

Log only structured failure information:

```text
payment_parser_no_match
payment_parser_low_confidence
payment_parser_conflict
payment_parser_invalid_amount
payment_parser_invalid_direction
```

Include:

```text
provider
parserVersion
notificationKeyHash
```

where useful.

Do not log complete notification payloads.

---

## 13A.30 Parser Failure Behavior

When all fallback levels fail:

```text
Level 1 → no match
Level 2 → no match
Level 3 → insufficient evidence
Level 4 → null
```

Return:

```kotlin
null
```

The processor must then:

```text
not create transaction
not create SUCCESS
not send PAYMENT_OBSERVED
not announce payment
```

unless the existing application explicitly supports storing unmatched diagnostic events.

---

## 13A.31 Never Guess

The parser must prefer:

```text
missed detection
```

over:

```text
false payment
```

Never infer:

* amount
* payer
* VPA
* reference
* direction
* QR
* payment account

from insufficient evidence.

The parser's job is extraction, not imagination.

---

## 13A.32 Fallback Test Matrix

Every provider parser must test:

```text
Exact known notification
Flexible wording
Title-only payment
Text-only payment
BigText-only payment
Amount with ₹
Amount with INR
Amount with commas
Amount with decimals
Hindi/localized wording where supported
Received payment
Sent payment
Failed payment
Pending payment
Refund
Multiple amounts
Missing reference
Missing payer
Malformed notification
Unrelated notification
Conflicting signals
Duplicate notification
Notification update
```

Every fallback level must have at least one positive and one negative test.

---

## 13A.33 Golden Fixture Rule

Whenever a real-world notification format is discovered to be unsupported:

1. Sanitize the notification.
2. Add it to the provider fixture set.
3. Add a regression test.
4. Update the provider parser.
5. Increment parser version if behavior changes materially.

Do not fix parser failures using broad regexes without adding regression coverage.

---

## 13A.34 Final Parser Decision Tree

The complete parser behavior must follow:

```text
Notification
     │
     ▼
Exact supported package?
     │
    NO ───────────────► IGNORE
     │
    YES
     ▼
Provider-specific parser
     │
     ├── HIGH/MEDIUM valid result ──► ACCEPT
     │
     ├── LOW result ────────────────► SAFETY CHECK
     │
     └── no result
             │
             ▼
      Provider fallback rules
             │
             ├── valid result ──────► ACCEPT
             │
             └── no result
                     │
                     ▼
             Generic fallback
                     │
              ┌──────┴──────┐
              ▼             ▼
        Strong evidence   Weak/ambiguous
              │             │
              ▼             ▼
          LOW/OBSERVED     NULL
              │
              ▼
        Existing processor
              │
              ▼
        Room / Sync / FCM
```

The critical rule is:

```text
Fallback increases compatibility.
Fallback must never increase guesswork.
```

When evidence is insufficient, return `null`.

A missed notification is recoverable through reconciliation.

A fabricated payment can corrupt the merchant's ledger, trigger false staff alerts, and create a very unpleasant accounting problem. Therefore parser conservatism is intentional.
Yes. LOW-confidence handling should be explicit because otherwise some future agent will see “₹500 + payment-ish word” and decide it has discovered a transaction. Tiny regex, enormous accounting disaster. 😑

Insert this **after Section 13A.25 (Confidence Rules)**:

# 13A.26 LOW-CONFIDENCE EVENT HANDLING

LOW-confidence payment events require special handling.

A LOW-confidence event is **not equivalent to a successful payment** and MUST NOT enter the normal successful-payment flow.

The purpose of retaining a LOW-confidence event is diagnostics and possible later reconciliation, not payment confirmation.

---

## 13A.26.1 Definition

An event is LOW confidence when:

* the notification originates from a supported payment application,
* some payment-related evidence exists,
* an amount may be present,
* but one or more critical attributes cannot be determined reliably.

Examples:

```text
Payment ₹500
```

but direction is unclear.

Or:

```text
Money received
```

but no reliable amount exists.

Or:

```text
₹500 credited
```

but the parser cannot determine whether the notification belongs to the configured Payment Account.

---

## 13A.26.2 LOW Is Not SUCCESS

A LOW-confidence event MUST NOT set:

```text
transaction.status = SUCCESS
```

It MUST NOT set:

```text
verificationStatus = VERIFIED
```

It MUST remain:

```text
verificationStatus = OBSERVED
```

If a transaction status is required, use the existing non-success state:

```text
UNKNOWN
```

or:

```text
PENDING
```

according to the existing UPI-Easy transaction-state rules.

Do not introduce a new `LOW_CONFIDENCE` transaction status merely for this feature.

---

## 13A.26.3 Recommended Representation

Prefer keeping parsing confidence separate from transaction state:

```kotlin
enum class ParseConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

And:

```kotlin
data class ParsedPaymentEvent(
    val direction: PaymentDirection,
    val amountMinor: Long?,
    val currency: String?,
    val payerName: String?,
    val payerVpa: String?,
    val reference: String?,
    val confidence: ParseConfidence,
    val parserKey: String,
    val parserVersion: Int
)
```

The existing transaction model remains responsible for payment state.

Do not overload `transaction.status` with parser confidence.

---

## 13A.26.4 LOW Event Storage Policy

A LOW-confidence event MAY be stored locally when it contains enough information to be useful for diagnostics or reconciliation.

Minimum recommended fields:

```text
organizationId
paymentAccountId, if confidently resolved
sourcePackage
sourceApp
direction
amountMinor
currency
reference
eventFingerprint
parserKey
parserVersion
confidence = LOW
verificationStatus = OBSERVED
observedAt
```

Fields that cannot be determined safely must remain:

```text
null
```

Do not invent missing values.

---

## 13A.26.5 Payment Account Resolution

LOW-confidence parsing and Payment Account resolution are separate decisions.

The system must distinguish:

```text
Parser confidence
```

from:

```text
Payment Account match confidence
```

Example:

```text
Notification:
"₹500 received"

Parser:
LOW confidence

Device:
configured for Payment Account A only
```

The event may still be associated with Payment Account A if the device-to-account assignment is authoritative.

Conversely:

```text
Notification:
"₹500 received from Rahul"

Parser:
HIGH confidence

Device:
configured for Payment Account A and Payment Account B
```

must not arbitrarily select A or B.

The event should become:

```text
matchStatus = AMBIGUOUS
```

until a deterministic account match exists.

---

## 13A.26.6 LOW Event Must Not Trigger Normal Payment Notification

By default, LOW-confidence events MUST NOT trigger the same staff-facing notification used for normal observed payment events.

Do not send:

```text
"Payment received ₹500"
```

to all staff when the system only has LOW-confidence evidence.

If the existing notification architecture supports diagnostic notifications, use clearly differentiated wording such as:

```text
"Possible payment notification detected. Review required."
```

Never phrase a LOW-confidence event as confirmed payment.

---

## 13A.26.7 LOW Event Must Not Trigger Voice Confirmation

The existing voice-alert system MUST NOT announce LOW-confidence events as:

```text
"Payment received ₹500"
```

unless an existing explicit product rule permits voice alerts for uncertain events.

Default behavior:

```text
HIGH/MEDIUM
    ↓
normal observed-payment handling

LOW
    ↓
no normal payment voice announcement
```

This prevents false payment announcements in a merchant environment.

---

## 13A.26.8 LOW Event Must Not Trigger External Actions

LOW-confidence events MUST NOT independently trigger:

* payment confirmation;
* order fulfillment;
* inventory release;
* invoice settlement;
* receipt generation;
* customer confirmation;
* accounting settlement;
* reconciliation closure;
* webhook emission claiming successful payment.

A LOW event is evidence that something payment-like was observed, not proof that money was successfully received.

---

## 13A.26.9 LOW Event Synchronization

If LOW events are persisted locally, they may be synchronized to the backend using the existing observed-event endpoint.

The backend must preserve:

```text
verificationStatus = OBSERVED
confidence = LOW
```

Do not upgrade the event merely because it reached the server.

Server persistence does not increase confidence.

---

## 13A.26.10 Backend Validation

The backend MUST validate confidence as an enum:

```text
HIGH
MEDIUM
LOW
```

Reject unknown confidence values.

The backend must not apply logic such as:

```text
if source == notification:
    confidence = HIGH
```

or:

```text
if amount exists:
    status = SUCCESS
```

---

## 13A.26.11 FCM Behavior

LOW-confidence events should not be broadcast as ordinary payment alerts.

If diagnostic propagation is required, the FCM payload should explicitly identify the event:

```json
{
  "type": "PAYMENT_OBSERVED_REVIEW_REQUIRED",
  "eventId": "...",
  "confidence": "LOW"
}
```

The client must fetch the event from the server rather than trusting FCM payload data as authoritative.

Do not send the full raw notification text through FCM.

---

## 13A.26.12 UI Representation

If LOW-confidence events appear in the transaction/activity UI, they must be visually distinguishable from normal observed payments.

Recommended information:

```text
Possible payment detected

₹500

Source:
Paytm

Status:
Review required

Verification:
Observed

Confidence:
Low
```

Do not display:

```text
SUCCESS
```

for a LOW-confidence event.

Do not use wording that implies bank confirmation.

---

## 13A.26.13 Filtering

The transaction list should support filtering LOW-confidence events separately if the existing UI architecture allows it.

Recommended filters:

```text
All
Observed
Needs Review
Verified
Failed
```

Do not redesign the transaction UI solely for this feature.

If adding a filter would require substantial UI changes, keep LOW-confidence handling in the existing event/status representation.

---

## 13A.26.14 Reconciliation

LOW-confidence events may be useful during reconciliation.

When an authoritative transaction later becomes available:

```text
LOW observed event
        +
authoritative provider/bank transaction
        ↓
existing reconciliation engine
```

The reconciliation system may then determine whether the observed event corresponds to the authoritative transaction.

If a match is established:

```text
observed event
    ↓
linked to authoritative transaction
```

Do not mutate the historical notification event into a verified event.

Instead preserve provenance:

```text
eventSource = NOTIFICATION_PAYTM
verificationStatus = OBSERVED
```

and separately associate it with the authoritative transaction.

---

## 13A.26.15 No Automatic Confidence Upgrade

The following MUST NOT automatically upgrade LOW to MEDIUM or HIGH:

* successful Room insertion;
* successful server upload;
* successful FCM delivery;
* duplicate suppression;
* device ownership;
* Payment Account association;
* QR association;
* time proximity;
* matching amount alone.

Confidence represents parser evidence.

It must only change when new payment evidence or an authoritative source justifies the change.

---

## 13A.26.16 Duplicate Handling

LOW-confidence events must participate in the same idempotency system as all other observed events.

Use the existing event fingerprint.

Example:

```text
provider
+
notification key
+
notification id
+
post time
+
normalized payment fields
```

must produce a deterministic fingerprint.

If the same LOW-confidence notification is received again:

```text
do not create another transaction
do not send another FCM notification
do not announce another voice alert
```

---

## 13A.26.17 Notification Updates

Payment applications may update an existing notification.

For example:

```text
Initial:
Payment notification

Updated:
₹500 received from Rahul
```

The listener must avoid treating every update as a new payment.

Use notification identity where available:

```text
StatusBarNotification.key
notification.id
packageName
```

combined with normalized event data.

An updated notification may replace or enrich the local observation rather than creating a duplicate transaction.

---

## 13A.26.18 Expiration / Retention

LOW-confidence events should follow the existing transaction/event retention policy.

Do not introduce a separate indefinite retention store.

If the existing application has no retention policy, use a conservative diagnostic retention period rather than storing raw notification content indefinitely.

Only normalized event fields should be retained.

---

## 13A.26.19 Metrics

Use existing logging/diagnostic infrastructure to measure:

```text
low_confidence_event_count
low_confidence_by_provider
parser_no_match_count
parser_conflict_count
parser_invalid_amount_count
payment_account_ambiguous_count
```

These metrics are for improving parser reliability.

Do not create a new analytics platform.

Do not collect unnecessary notification content.

---

## 13A.26.20 Required LOW-Confidence Tests

Every provider must have tests for:

```text
LOW: payment wording + amount, unclear direction
LOW: received wording + amount, ambiguous account
LOW: amount + weak payment signal
LOW: multiple possible amounts
LOW: conflicting received/sent signals
LOW: malformed reference
LOW: missing reference
LOW: missing payer
LOW: provider-specific fallback match
LOW: generic fallback match
```

For every LOW test, assert:

```text
verificationStatus == OBSERVED
```

and assert that the event does NOT:

```text
become SUCCESS
trigger normal payment FCM
trigger normal voice confirmation
create duplicate transactions
```

---

## 13A.26.21 LOW-Confidence State Machine

The intended behavior is:

```text
                    Notification
                         │
                         ▼
                      Parser
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
             HIGH      MEDIUM       LOW
              │          │           │
              │          │           ▼
              │          │      Store as
              │          │      OBSERVED
              │          │           │
              │          │           ▼
              │          │      Review / Reconcile
              │          │
              └────┬─────┘
                   ▼
             Existing observed
             payment pipeline
```

No branch from LOW may directly reach:

```text
SUCCESS
VERIFIED
SETTLED
```

---

## 13A.26.22 Golden Rule

The implementation must follow this rule everywhere:

```text
HIGH confidence
    ≠ VERIFIED

MEDIUM confidence
    ≠ VERIFIED

LOW confidence
    ≠ FAILED

OBSERVED
    ≠ SUCCESS
```

Confidence describes **how confidently the parser understood the notification**.

Verification describes **whether an authoritative payment source confirms the transaction**.

These are separate concepts and must remain separate throughout Android, Room, Hono, Neon/Postgres, FCM, UI, and reconciliation.
