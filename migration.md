# UPI-Easy: Multi-QR + PhonePe/GPay Notification Detection

## Full Agentic Implementation Specification

You are modifying the existing **UPI-Easy** application.

Do not create a separate demo application.

Implement this feature end-to-end across:

```text
Android Jetpack Compose
        ↓
NotificationListenerService
        ↓
Payment App Detection
        ↓
Payment Notification Parser
        ↓
Room
        ↓
Sync Engine
        ↓
Hono API
        ↓
Neon/Postgres
        ↓
Outbox / Cloudflare Worker
        ↓
FCM
        ↓
All authorized organization devices
```

The feature must support:

* Multiple payment accounts
* Multiple QR codes
* PhonePe notification detection
* Google Pay notification detection
* Installed-app detection
* App selector when adding a UPI/payment account
* Saving selected payment app package information
* Android notification-access onboarding
* Local notification parsing
* Duplicate protection
* Offline-first event storage
* Backend synchronization
* Organization-level payment event distribution
* Multi-device notifications
* Audit trail
* Explicit `OBSERVED` vs `VERIFIED` transaction status
* No private API access
* No accessibility-service abuse
* No SMS scraping
* No reading another application's private database
* No pretending a notification is a bank-confirmed transaction

---

# 1. PRODUCT MODEL

Do NOT model the feature as:

```text
QR → Notification
```

Model it as:

```text
Organization
    ↓
Payment Account
    ↓
UPI Account
    ↓
QR Codes
    ↓
Payment Detection Source
    ↓
Observed Payment Events
```

A payment account represents the receiving identity.

Example:

```text
Organization:
Sandesh Collection

Payment Account:
Main PhonePe

UPI ID:
9827743085@ybl

Payment App:
PhonePe

Package:
com.phonepe.app

QRs:
    Counter 1
    Counter 2
    Billing Desk
```

Another:

```text
Payment Account:
Main Google Pay

UPI ID:
shop@okaxis

Payment App:
Google Pay

Package:
com.google.android.apps.nbu.paisa.user

QRs:
    Counter 3
    Wholesale Desk
```

---

# 2. IMPORTANT TRANSACTION SEMANTICS

Notification detection is NOT an official payment provider webhook.

Therefore:

```text
PhonePe/GPay notification detected
        ↓
OBSERVED PAYMENT
```

not:

```text
PhonePe/GPay notification detected
        ↓
BANK VERIFIED SUCCESS
```

Use these statuses:

```text
CREATED
PAYMENT_INITIATED
PENDING
SUCCESS
FAILED
REVERSED
REFUNDED
UNKNOWN
RECONCILIATION_REQUIRED
```

And independently track:

```text
eventSource:

NOTIFICATION_PHONEPE
NOTIFICATION_GPAY
PROVIDER_WEBHOOK
BANK_SYNC
MANUAL
```

and:

```text
verificationStatus:

OBSERVED
VERIFIED
UNVERIFIED
CONFLICT
```

For this feature:

```text
PhonePe notification
    → eventSource = NOTIFICATION_PHONEPE
    → verificationStatus = OBSERVED
```

```text
Google Pay notification
    → eventSource = NOTIFICATION_GPAY
    → verificationStatus = OBSERVED
```

Never automatically upgrade an observed notification to `VERIFIED`.

---

# 3. SUPPORTED PAYMENT APPS

For this first implementation, support exactly:

### PhonePe

```text
displayName:
PhonePe

packageName:
com.phonepe.app
```

### Google Pay

```text
displayName:
Google Pay

packageName:
com.google.android.apps.nbu.paisa.user
```

Do not implement BHIM, Paytm, Amazon Pay, WhatsApp Pay, etc. yet.

However, architecture must be extensible.

Create:

```text
PaymentAppDefinition
```

with:

```kotlin
data class PaymentAppDefinition(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supported: Boolean,
    val parserKey: String
)
```

Example:

```kotlin
val supportedPaymentApps = listOf(
    PaymentAppDefinition(
        id = "phonepe",
        displayName = "PhonePe",
        packageName = "com.phonepe.app",
        supported = true,
        parserKey = "phonepe"
    ),
    PaymentAppDefinition(
        id = "google_pay",
        displayName = "Google Pay",
        packageName = "com.google.android.apps.nbu.paisa.user",
        supported = true,
        parserKey = "google_pay"
    )
)
```

Keep this registry centralized.

---

# 4. INSTALLED APP DETECTION

When the user opens:

```text
Add Payment Account
```

show:

```text
Payment App

[ Select payment app ▼ ]
```

The selector should only display supported apps that are actually installed.

Do NOT scan every installed application.

Use Android package visibility with explicit package declarations.

Manifest:

```xml
<manifest ...>

    <queries>

        <package
            android:name="com.phonepe.app" />

        <package
            android:name="com.google.android.apps.nbu.paisa.user" />

    </queries>

</manifest>
```

Android package visibility is restricted on Android 11+ and explicit package declarations are the correct mechanism when the application needs to check known packages. Avoid `QUERY_ALL_PACKAGES`.

Create:

```kotlin
interface PaymentAppDetector {

    fun getInstalledSupportedApps(): List<PaymentAppDefinition>

    fun isInstalled(packageName: String): Boolean
}
```

Implementation:

```kotlin
class AndroidPaymentAppDetector(
    private val context: Context
) : PaymentAppDetector {

    override fun getInstalledSupportedApps():
        List<PaymentAppDefinition> {

        return supportedPaymentApps.filter {
            isInstalled(it.packageName)
        }
    }

    override fun isInstalled(
        packageName: String
    ): Boolean {

        return try {
            context.packageManager
                .getApplicationInfo(packageName, 0)

            true
        } catch (
            _: PackageManager.NameNotFoundException
        ) {
            false
        }
    }
}
```

Also obtain the application label/icon through `PackageManager` for UI display.

---

# 5. ADD PAYMENT ACCOUNT UI

When the user selects:

```text
UPI
→ Add Payment Account
```

show:

```text
Add Payment Account

Payment App
┌───────────────────────────┐
│ PhonePe                ▼  │
└───────────────────────────┘

UPI ID
┌───────────────────────────┐
│ 9827743085@ybl            │
└───────────────────────────┘

Account Name
┌───────────────────────────┐
│ Main PhonePe              │
└───────────────────────────┘

[ Generate QR ]

[ Save Payment Account ]
```

For Google Pay:

```text
Payment App
Google Pay
```

The selected app must be persisted.

Store:

```text
paymentAppId
packageName
```

Do NOT store only the display name.

---

# 6. PAYMENT ACCOUNT DATABASE

Add/update:

```sql
payment_accounts
```

Fields:

```text
id
organization_id

label

upi_id

payment_app_id
payment_app_package

status

detection_enabled

notification_access_required

last_notification_detected_at

created_at
updated_at
```

Example:

```text
id:
pa_01

organization_id:
org_01

label:
Main PhonePe

upi_id:
9827743085@ybl

payment_app_id:
phonepe

payment_app_package:
com.phonepe.app

status:
ACTIVE

detection_enabled:
true
```

Never use the package name as the primary identifier.

---

# 7. QR DATABASE

Existing QR architecture should become:

```sql
qr_codes
```

Fields:

```text
id
organization_id
payment_account_id

label

upi_id
payload

qr_type

amount
transaction_reference

is_active

created_at
updated_at
```

Example:

```text
Payment Account:
Main PhonePe

QR:
Counter 1

UPI:
9827743085@ybl
```

Another:

```text
Payment Account:
Main PhonePe

QR:
Counter 2

UPI:
9827743085@ybl
```

Multiple QR codes may belong to one payment account.

Important:

If two QR codes use the same UPI ID, a generic payment notification may NOT tell us which physical QR was scanned.

Therefore:

```text
payment_account_id
```

is authoritative for notification association.

`qr_id` is optional.

Never fabricate `qr_id`.

---

# 8. NOTIFICATION LISTENER SERVICE

Create:

```text
notification/
    PaymentNotificationListenerService.kt
    PaymentNotificationProcessor.kt
    PaymentNotificationParser.kt
    PhonePeNotificationParser.kt
    GooglePayNotificationParser.kt
    NotificationEventDeduplicator.kt
```

Declare:

```xml
<service
    android:name=".notification.PaymentNotificationListenerService"
    android:exported="false"
    android:label="@string/payment_notification_listener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">

    <intent-filter>
        <action
            android:name=
            "android.service.notification.NotificationListenerService" />
    </intent-filter>

</service>
```

This is the Android-supported notification listener mechanism. Android calls the service when notifications are posted/removed, and the `StatusBarNotification` identifies the originating package.

---

# 9. WAIT FOR LISTENER CONNECTION

Do not immediately perform notification operations from service initialization.

Use:

```kotlin
override fun onListenerConnected() {
    super.onListenerConnected()

    // Initialize processing pipeline
}
```

Android documents `onListenerConnected()` as the point after which notification-listener operations should be performed.

---

# 10. NOTIFICATION FILTER

The service must immediately ignore unsupported applications.

```kotlin
private val supportedPackages = setOf(
    "com.phonepe.app",
    "com.google.android.apps.nbu.paisa.user"
)
```

Implementation:

```kotlin
override fun onNotificationPosted(
    sbn: StatusBarNotification
) {

    val packageName = sbn.packageName

    if (packageName !in supportedPackages) {
        return
    }

    processor.process(sbn)
}
```

Do not upload arbitrary notifications.

Do not store arbitrary notifications.

Only inspect notifications from configured supported payment apps.

---

# 11. NOTIFICATION DATA EXTRACTION

Create a normalized internal model:

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
    val category: String?
)
```

Extract:

```kotlin
val extras = sbn.notification.extras

val title =
    extras.getString(Notification.EXTRA_TITLE)

val text =
    extras.getCharSequence(
        Notification.EXTRA_TEXT
    )?.toString()

val bigText =
    extras.getCharSequence(
        Notification.EXTRA_BIG_TEXT
    )?.toString()
```

Also store:

```kotlin
sbn.key
sbn.id
sbn.postTime
sbn.notification.category
```

Do NOT store icons, images, RemoteViews, or unnecessary notification payloads.

---

# 12. PARSER ARCHITECTURE

Do not make one giant parser.

Create:

```kotlin
interface PaymentNotificationParser {

    fun supports(
        packageName: String
    ): Boolean

    fun parse(
        notification: RawPaymentNotification
    ): ParsedPaymentEvent?
}
```

PhonePe:

```kotlin
class PhonePeNotificationParser :
    PaymentNotificationParser
```

Google Pay:

```kotlin
class GooglePayNotificationParser :
    PaymentNotificationParser
```

---

# 13. NORMALIZED PAYMENT EVENT

Create:

```kotlin
data class ParsedPaymentEvent(
    val sourcePackage: String,
    val sourceApp: String,

    val direction: PaymentDirection,

    val amount: BigDecimal?,

    val payerName: String?,
    val payerVpa: String?,

    val reference: String?,

    val rawTitle: String?,
    val rawText: String?,

    val observedAt: Instant,

    val confidence: ParseConfidence
)
```

Enum:

```kotlin
enum class PaymentDirection {
    RECEIVED,
    SENT,
    UNKNOWN
}
```

Confidence:

```kotlin
enum class ParseConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

---

# 14. PARSER RULES

Do not hard-code one exact notification sentence.

UPI apps can change notification wording.

Use a rule-based parser.

Example concepts:

Received keywords:

```text
received
credited
payment received
money received
₹
INR
```

Sent keywords:

```text
paid
payment sent
debited
sent
```

But do NOT classify solely because a notification contains `₹`.

Require multiple signals.

For example:

```text
amount detected
+
received/credited semantic signal
```

for RECEIVED.

If uncertain:

```text
direction = UNKNOWN
confidence = LOW
```

Then do not create a successful payment transaction.

Create an observed event requiring review.

---

# 15. AMOUNT PARSER

Support:

```text
₹500
₹500.00
INR 500
Rs 500
Rs. 500
```

Normalize:

```text
500
500.00
```

Use `BigDecimal`.

Never use:

```kotlin
Double
Float
```

for money.

---

# 16. VPA/PAYER EXTRACTION

If notification text contains a VPA, parse it.

Example:

```text
rahul@ybl
```

Normalize:

```text
rahul@ybl
```

If unavailable:

```text
payerVpa = null
```

Never infer a VPA from a person's name.

---

# 17. PAYMENT ACCOUNT MATCHING

After parsing:

```text
Observed event
       ↓
source package
       ↓
find enabled payment accounts
       ↓
paymentAppPackage matches
       ↓
UPI account association
```

Query:

```sql
SELECT *
FROM payment_accounts
WHERE organization_id IN (...)
AND payment_app_package = ?
AND detection_enabled = true;
```

If only one enabled account exists for that package:

```text
paymentAccountId = that account
```

If multiple accounts exist:

```text
paymentAccountId = unresolved
```

Do NOT randomly assign.

---

# 18. IMPORTANT MULTI-ACCOUNT LIMITATION

Suppose the user has:

```text
PhonePe account A
VPA A

PhonePe account B
VPA B
```

and PhonePe posts:

```text
₹500 received
```

If the notification does not expose the receiving VPA, the app cannot safely determine which VPA received it.

Therefore:

```text
MATCHED
UNMATCHED
AMBIGUOUS
```

must exist as event-resolution states.

```kotlin
enum class PaymentMatchStatus {
    MATCHED,
    UNMATCHED,
    AMBIGUOUS
}
```

Never guess.

---

# 19. EVENT DEDUPLICATION

Notifications can be posted/reposted.

Create:

```kotlin
NotificationEventDeduplicator
```

Generate:

```text
eventFingerprint =
SHA-256(
    packageName +
    notificationKey +
    title +
    text +
    postTimeBucket
)
```

Also maintain:

```text
sourcePackage
notificationKey
amount
direction
payer
observedAt
```

in the database.

Use a unique constraint where possible.

Example:

```sql
UNIQUE(
    organization_id,
    source_package,
    source_notification_key
)
```

If notification keys are unstable across reposts, use a secondary normalized fingerprint.

---

# 20. ROOM ENTITY

Create:

```kotlin
@Entity(
    tableName = "observed_payment_events",
    indices = [
        Index(
            value = [
                "organizationId",
                "eventFingerprint"
            ],
            unique = true
        )
    ]
)
data class ObservedPaymentEventEntity(

    @PrimaryKey
    val id: String,

    val organizationId: String?,

    val paymentAccountId: String?,
    val qrId: String?,

    val sourcePackage: String,
    val sourceApp: String,

    val direction: String,

    val amountMinor: Long?,

    val currency: String,

    val payerName: String?,
    val payerVpa: String?,

    val reference: String?,

    val notificationTitle: String?,
    val notificationText: String?,

    val eventFingerprint: String,

    val matchStatus: String,
    val verificationStatus: String,

    val observedAt: Long,

    val syncState: String
)
```

Use minor units:

```text
₹500
→ 50000 paise
```

if your existing money architecture uses integer minor units.

---

# 21. DO NOT TRUST CLIENT-SUPPLIED ORGANIZATION

The Android app must NOT simply say:

```json
{
  "organizationId": "org_123"
}
```

and have the server trust it.

The server determines:

```text
authenticated user
        ↓
device
        ↓
organization membership
        ↓
authorized organization
```

Then accepts the event.

---

# 22. API

Create:

```text
POST /v1/payment-events/observed
```

Request:

```json
{
  "clientEventId": "evt_local_123",
  "source": {
    "type": "NOTIFICATION_PHONEPE",
    "packageName": "com.phonepe.app"
  },
  "paymentAccountId": "pa_123",
  "qrId": null,
  "amountMinor": 50000,
  "currency": "INR",
  "direction": "RECEIVED",
  "payerName": "Rahul",
  "payerVpa": "rahul@ybl",
  "reference": null,
  "observedAt": "2026-09-21T10:42:00Z",
  "verificationStatus": "OBSERVED",
  "matchStatus": "MATCHED",
  "fingerprint": "..."
}
```

Server response:

```json
{
  "accepted": true,
  "eventId": "evt_server_123",
  "transactionId": "txn_123",
  "status": "OBSERVED"
}
```

---

# 23. SERVER VALIDATION

Use Zod.

```ts
const observedPaymentEventSchema = z.object({
  clientEventId: z.string().min(1).max(128),

  source: z.object({
    type: z.enum([
      "NOTIFICATION_PHONEPE",
      "NOTIFICATION_GPAY"
    ]),
    packageName: z.string()
  }),

  paymentAccountId: z.string().nullable(),

  qrId: z.string().nullable(),

  amountMinor: z.number().int().positive().nullable(),

  currency: z.literal("INR"),

  direction: z.enum([
    "RECEIVED",
    "SENT",
    "UNKNOWN"
  ]),

  payerName: z.string().max(200).nullable(),

  payerVpa: z.string().max(255).nullable(),

  reference: z.string().max(255).nullable(),

  observedAt: z.string().datetime(),

  verificationStatus:
    z.literal("OBSERVED"),

  matchStatus: z.enum([
    "MATCHED",
    "UNMATCHED",
    "AMBIGUOUS"
  ]),

  fingerprint: z.string().min(32).max(128)
});
```

---

# 24. SERVER AUTHORIZATION

Processing order:

```text
Authentication
      ↓
Device session
      ↓
Organization membership
      ↓
Permission
      ↓
Payment account ownership
      ↓
Idempotency
      ↓
Validation
      ↓
Insert event
      ↓
Create transaction
      ↓
Outbox event
```

Required permission:

```text
transactions.create
```

or create a dedicated:

```text
payment_events.ingest
```

permission for detection devices.

Recommended:

```text
payment_events.ingest
```

---

# 25. TRANSACTION RECORD

When an observed RECEIVED event is accepted:

Create:

```text
transaction
```

with:

```text
status:
PENDING
```

or:

```text
UNKNOWN
```

depending on existing transaction semantics.

Do NOT use:

```text
SUCCESS
```

for notification-only events.

Recommended:

```text
transaction.status = UNKNOWN

transaction.verificationStatus = OBSERVED
```

This prevents the dashboard from presenting an unverified notification as a confirmed bank settlement.

---

# 26. OUTBOX

Inside the same DB transaction:

```text
INSERT observed_payment_event
INSERT transaction
INSERT outbox_event
```

Outbox:

```text
PAYMENT_OBSERVED
```

Payload:

```json
{
  "transactionId": "txn_123",
  "organizationId": "org_123",
  "paymentAccountId": "pa_123",
  "amountMinor": 50000,
  "source": "NOTIFICATION_PHONEPE"
}
```

---

# 27. NOTIFICATION WORKER

Existing worker architecture should process:

```text
PAYMENT_OBSERVED
```

Flow:

```text
Outbox
   ↓
Notification Worker
   ↓
Find organization members
   ↓
Check role
   ↓
Check notification preferences
   ↓
Find active devices
   ↓
Send FCM
```

Roles:

```text
OWNER
MANAGER
ACCOUNTANT
CASHIER
```

Use the existing role/permission system.

---

# 28. FCM PAYLOAD

Do not send sensitive full notification text unnecessarily.

Use:

```json
{
  "type": "PAYMENT_OBSERVED",
  "transactionId": "txn_123",
  "organizationId": "org_123",
  "amountMinor": "50000",
  "currency": "INR",
  "source": "PHONEPE"
}
```

The receiving app fetches authoritative event details through the API if necessary.

---

# 29. ANDROID FCM HANDLING

FCM is a delivery/trigger mechanism.

It is NOT the database.

Flow:

```text
FCM received
      ↓
Save lightweight event
      ↓
Trigger sync
      ↓
GET /payment-events?cursor=...
      ↓
Room
      ↓
UI
```

Do not rely on FCM payload alone for accounting.

---

# 30. WORKMANAGER

Create:

```text
PaymentEventSyncWorker
```

Responsibilities:

```text
Upload pending observed events
Download server-side events
Resolve conflicts
Update sync cursor
Retry failures
```

Constraints:

```kotlin
Constraints.Builder()
    .setRequiredNetworkType(
        NetworkType.CONNECTED
    )
    .build()
```

Use exponential backoff.

Do not create an infinite foreground service.

---

# 31. OFFLINE FLOW

If payment notification arrives while offline:

```text
PhonePe
 ↓
NotificationListener
 ↓
Parser
 ↓
Room
 ↓
syncState = PENDING_UPLOAD
```

Later:

```text
Network available
 ↓
WorkManager
 ↓
Upload
```

Never lose the local event merely because the server is unavailable.

---

# 32. NOTIFICATION ACCESS UI

Create:

```text
Settings
 → Payment Detection
```

Screen:

```text
Payment Detection

Detect payments from installed UPI apps.

PhonePe
● Enabled

Google Pay
○ Not enabled

Notification Access
Connected
```

For each selected payment app:

```text
Payment detection
[ ON ]

Notification access
[ Connected ]
```

If permission is missing:

```text
Notification access required

UPI-Easy needs Android notification
access to observe payment notifications
from PhonePe.

[ Enable Access ]
```

Open:

```kotlin
startActivity(
    Intent(
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    )
)
```

Android provides this system settings action for notification-listener access.

---

# 33. VERIFY ACCESS STATE

Do not assume the user granted permission after returning from settings.

Check:

```kotlin
NotificationManager
    .isNotificationListenerAccessGranted(
        ComponentName(
            context,
            PaymentNotificationListenerService::class.java
        )
    )
```

Update UI accordingly.

---

# 34. ADD PAYMENT ACCOUNT FLOW

Exact flow:

```text
UPI
 ↓
Add Payment Account
 ↓
Detect installed supported payment apps
 ↓
Show selector
 ↓
User selects PhonePe
 ↓
Enter UPI ID
 ↓
Enter account label
 ↓
Save
 ↓
Check notification access
 ↓
If missing:
    show setup screen
 ↓
Enable notification access
 ↓
Return
 ↓
Verify access
 ↓
Payment Account = ACTIVE
```

If no supported payment app is installed:

```text
No supported UPI app found

Install PhonePe or Google Pay
to enable notification-based payment detection.
```

Do not make the feature unusable for users who only want QR generation.

Instead allow:

```text
Continue without payment detection
```

with:

```text
Detection:
Not configured
```

---

# 35. QR GENERATION

QR generation remains independent.

Example UPI URI:

```text
upi://pay?
pa=9827743085@ybl
&pn=Sandesh%20Collection
&cu=INR
```

URL-encode parameters correctly.

Do not treat the generated QR itself as payment confirmation.

QR generation:

```text
FREE
LOCAL
NO NETWORK REQUIRED
```

Payment observation:

```text
DEPENDS ON DEVICE NOTIFICATION
```

---

# 36. MULTIPLE QR SCREEN

Create:

```text
UPI
```

with:

```text
Payment Accounts

┌──────────────────────────────┐
│ PhonePe                      │
│ 9827743085@ybl               │
│ Detection ● Active           │
│                              │
│ 3 QR codes                   │
└──────────────────────────────┘

┌──────────────────────────────┐
│ Google Pay                   │
│ shop@okaxis                  │
│ Detection ● Active           │
│                              │
│ 2 QR codes                   │
└──────────────────────────────┘
```

Tap account:

```text
Main PhonePe

9827743085@ybl

Detection
● Active

Last payment observed
₹1,250
10:42 AM

QR Codes

Counter 1
Counter 2
Billing Desk

[ Add QR ]
[ Edit Account ]
```

---

# 37. QR DETAIL

Each QR:

```text
Counter 1

[ QR IMAGE ]

9827743085@ybl

Status:
Active

Payment Account:
Main PhonePe

[ Share ]
[ Save Image ]
[ Edit ]
[ Disable ]
```

If QR uses same VPA as another QR, show:

```text
QR-level payment identification:
Limited

Payments will be associated
with the payment account unless
the receiving notification contains
enough information to identify the QR.
```

Do not falsely claim exact QR attribution.

---

# 38. PAYMENT EVENT UI

Transaction list should distinguish:

```text
₹500
Rahul

Payment observed
PhonePe

10:42 AM
```

Badge:

```text
OBSERVED
```

Instead of:

```text
SUCCESS
```

If verified later:

```text
VERIFIED
```

If conflict:

```text
CONFLICT
```

---

# 39. DASHBOARD

Dashboard card:

```text
Today's Received

₹24,500

18 payments

12 Verified
6 Observed
```

Do not combine observed and verified values without clearly labeling them.

Better:

```text
Received Today

₹24,500

Verified
₹18,000

Observed
₹6,500
```

---

# 40. PAYMENT DETAILS

Payment detail:

```text
Payment

₹500

Observed
PhonePe

Payer:
Rahul

UPI:
rahul@ybl

Payment Account:
Main PhonePe

QR:
Counter 1

Observed:
10:42:11 AM

Verification:
Not verified

Source:
Android notification
```

If QR cannot be established:

```text
QR:
Not determined
```

---

# 41. SECURITY

Never collect:

```text
UPI PIN
Bank password
Debit card PIN
CVV
Payment authorization OTP
Net banking password
```

Notification listener should only process payment-related notification content.

Do not store arbitrary notification history.

Do not upload the entire notification stream.

---

# 42. LOCAL DATA RETENTION

For raw notification text:

```text
Keep minimum necessary data.
```

Prefer:

```text
parsed amount
payer
source
timestamp
reference
```

instead of permanently storing raw notification content.

If raw text is retained for debugging:

```text
local only
limited retention
encrypted where appropriate
user-controlled diagnostics
```

Production logging must never print full notification contents.

---

# 43. LOGGING

Bad:

```kotlin
Log.d(
    "PaymentListener",
    "Notification = $text"
)
```

Do not do this.

Good:

```kotlin
Log.d(
    "PaymentListener",
    "Payment notification detected: " +
    "package=$packageName"
)
```

For debug builds only, optionally expose redacted parser diagnostics.

---

# 44. NOTIFICATION PARSER TESTING

Create fixture tests.

Example:

```text
PhonePeFixtures/
    received_500.txt
    received_1250.txt
    payment_sent.txt
    unrelated_notification.txt
    malformed_notification.txt
```

Tests:

```kotlin
@Test
fun phonePeReceivedPaymentIsParsed()
```

```kotlin
@Test
fun unrelatedPhonePeNotificationIsIgnored()
```

```kotlin
@Test
fun duplicateNotificationIsIgnored()
```

```kotlin
@Test
fun missingAmountDoesNotCreatePayment()
```

```kotlin
@Test
fun ambiguousAccountDoesNotGuess()
```

Google Pay equivalents:

```text
GooglePayFixtures/
```

IMPORTANT:

Do not assume exact current PhonePe/GPay wording forever.

Parser tests should be versioned and easy to update.

---

# 45. END-TO-END TEST

Test:

```text
PhonePe notification
      ↓
NotificationListenerService
      ↓
PhonePeParser
      ↓
ParsedPaymentEvent
      ↓
Room
      ↓
WorkManager
      ↓
POST /payment-events/observed
      ↓
Cloudflare Worker
      ↓
Neon
      ↓
Outbox
      ↓
Notification Worker
      ↓
FCM
      ↓
Second Android device
      ↓
Room
      ↓
Transaction UI
```

---

# 46. SERVER TABLES

Ensure these exist:

```text
payment_accounts
qr_codes
transactions
observed_payment_events
transaction_events
notification_devices
notifications
notification_preferences
outbox_events
sync_cursors
audit_logs
```

Add relationships:

```text
organization
    ↓
payment_account
    ↓
qr_code

payment_account
    ↓
observed_payment_event
    ↓
transaction
```

---

# 47. OBSERVED PAYMENT EVENT SCHEMA

Recommended Drizzle shape:

```ts
export const observedPaymentEvents =
  pgTable(
    "observed_payment_events",
    {
      id: text("id").primaryKey(),

      organizationId:
        text("organization_id")
          .notNull()
          .references(
            () => organizations.id
          ),

      paymentAccountId:
        text("payment_account_id")
          .references(
            () => paymentAccounts.id
          ),

      qrId:
        text("qr_id")
          .references(
            () => qrCodes.id
          ),

      sourceType:
        text("source_type")
          .notNull(),

      sourcePackage:
        text("source_package")
          .notNull(),

      amountMinor:
        bigint(
          "amount_minor",
          { mode: "number" }
        ),

      currency:
        text("currency")
          .notNull()
          .default("INR"),

      direction:
        text("direction")
          .notNull(),

      payerName:
        text("payer_name"),

      payerVpa:
        text("payer_vpa"),

      reference:
        text("reference"),

      eventFingerprint:
        text("event_fingerprint")
          .notNull(),

      matchStatus:
        text("match_status")
          .notNull(),

      verificationStatus:
        text("verification_status")
          .notNull(),

      observedAt:
        timestamp(
          "observed_at",
          { withTimezone: true }
        ).notNull(),

      createdAt:
        timestamp(
          "created_at",
          { withTimezone: true }
        )
        .defaultNow()
        .notNull()
    },
    table => ({
      fingerprintUnique:
        uniqueIndex(
          "observed_payment_event_fingerprint_idx"
        )
        .on(
          table.organizationId,
          table.eventFingerprint
        )
    })
  );
```

Adapt names/types to the existing schema rather than blindly duplicating existing tables.

---

# 48. API ROUTES

Implement:

```text
GET /v1/payment-apps/supported
```

Returns:

```json
[
  {
    "id": "phonepe",
    "displayName": "PhonePe",
    "packageName": "com.phonepe.app"
  },
  {
    "id": "google_pay",
    "displayName": "Google Pay",
    "packageName": "com.google.android.apps.nbu.paisa.user"
  }
]
```

This endpoint is optional because the initial supported list can remain Android-side.

Prefer keeping package definitions Android-side because installed-state detection happens locally.

---

Implement:

```text
GET /v1/payment-accounts
POST /v1/payment-accounts
PATCH /v1/payment-accounts/:id
DELETE /v1/payment-accounts/:id
```

QR:

```text
GET /v1/payment-accounts/:id/qr
POST /v1/payment-accounts/:id/qr
PATCH /v1/qr/:id
DELETE /v1/qr/:id
```

Observed events:

```text
POST /v1/payment-events/observed
GET /v1/payment-events
GET /v1/payment-events/:id
```

---

# 49. IDEMPOTENCY

Client sends:

```text
clientEventId
```

Server requires:

```text
Idempotency-Key
```

or uses:

```text
clientEventId + deviceId
```

to prevent duplicates.

Example:

```text
device_123:event_456
```

must only create one server event.

---

# 50. AUDIT LOG

Record:

```text
PAYMENT_EVENT_OBSERVED
```

with:

```text
actorUserId
deviceId
organizationId
paymentEventId
source
timestamp
```

Do not store raw notification text in audit logs.

---

# 51. DEVICE REGISTRATION

Every Android installation already participating in UPI-Easy should have:

```text
deviceId
userId
organizationId
fcmToken
platform
appVersion
lastSeenAt
```

Add:

```text
paymentDetectionEnabled
notificationListenerEnabled
```

or derive these from device configuration.

---

# 52. DEVICE CAPABILITY

Expose:

```json
{
  "notificationListener": true,
  "supportedPaymentApps": [
    "phonepe",
    "google_pay"
  ],
  "installedPaymentApps": [
    "phonepe"
  ]
}
```

This lets the server know:

```text
Owner's phone:
PhonePe detection available

Manager's phone:
No payment detection
```

---

# 53. ORGANIZATION RULE

Payment detection belongs to a device.

Payment events belong to an organization.

Therefore:

```text
Device
   ↓
Detection
   ↓
Organization
   ↓
Payment Account
```

Do NOT make the listener itself belong globally to one company.

The user may switch companies.

When active organization changes:

```text
listener remains active
```

but the processor resolves the payment event against configured payment accounts belonging to the appropriate organization.

If the same device is authorized for multiple organizations and an event cannot safely be mapped:

```text
AMBIGUOUS
```

Do not randomly select the active organization.

Prefer requiring an explicit detection-device/account assignment for production.

---

# 54. RECOMMENDED DEVICE ASSIGNMENT

Add:

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

Example:

```text
Owner Phone
     ↓
Payment Account:
Main PhonePe
```

This solves much of the multi-company ambiguity.

---

# 55. EVENT RESOLUTION

When notification arrives:

```text
source package
      ↓
device
      ↓
payment detection device assignments
      ↓
payment account
      ↓
parse event
```

If exactly one payment account is assigned:

```text
MATCHED
```

If zero:

```text
UNMATCHED
```

If more than one:

```text
AMBIGUOUS
```

Never guess.

---

# 56. ANDROID REPOSITORY STRUCTURE

Integrate into the existing project.

Recommended:

```text
app/
└── src/main/java/.../

    data/
        local/
            dao/
                PaymentAccountDao.kt
                QrCodeDao.kt
                ObservedPaymentEventDao.kt

            entity/
                PaymentAccountEntity.kt
                QrCodeEntity.kt
                ObservedPaymentEventEntity.kt

        remote/
            api/
                PaymentAccountApi.kt
                PaymentEventApi.kt

            dto/
                PaymentAccountDto.kt
                ObservedPaymentEventDto.kt

        repository/
            PaymentAccountRepository.kt
            PaymentEventRepository.kt

    payment/
        apps/
            PaymentAppDefinition.kt
            PaymentAppDetector.kt
            AndroidPaymentAppDetector.kt

        notification/
            PaymentNotificationListenerService.kt
            PaymentNotificationProcessor.kt
            PaymentNotificationParser.kt
            PhonePeNotificationParser.kt
            GooglePayNotificationParser.kt
            NotificationFingerprint.kt

        sync/
            PaymentEventSyncWorker.kt

    ui/
        payment/
            PaymentAccountsScreen.kt
            AddPaymentAccountScreen.kt
            PaymentAccountDetailScreen.kt
            AddQrScreen.kt
            PaymentDetectionScreen.kt

            components/
                PaymentAppSelector.kt
                PaymentAccountCard.kt
                QrCodeCard.kt
                DetectionStatusCard.kt
                ObservedPaymentBadge.kt
```

Adapt this to the actual existing package/folder structure.

Do not duplicate repositories, Room databases, Retrofit clients, Hilt modules, or navigation graphs already present.

---

# 57. HILT

Create bindings for:

```text
PaymentAppDetector
PaymentNotificationProcessor
PhonePeNotificationParser
GooglePayNotificationParser
PaymentEventRepository
```

Example:

```kotlin
@Provides
@Singleton
fun providePaymentAppDetector(
    @ApplicationContext context: Context
): PaymentAppDetector =
    AndroidPaymentAppDetector(context)
```

---

# 58. COMPOSE APP SELECTOR

Use Material 3.

Component:

```kotlin
@Composable
fun PaymentAppSelector(
    apps: List<PaymentAppDefinition>,
    selected: PaymentAppDefinition?,
    onSelected: (PaymentAppDefinition) -> Unit
)
```

Display:

```text
PhonePe
Installed
```

```text
Google Pay
Installed
```

Do not show unsupported/uninstalled apps.

If app is not installed, it should not appear in the selector.

---

# 59. DETECTION STATUS

Create:

```kotlin
data class NotificationAccessState(
    val enabled: Boolean,
    val supportedAppInstalled: Boolean
)
```

UI states:

```text
● Active
○ Permission required
○ App not installed
○ Detection disabled
```

---

# 60. ADD ACCOUNT VALIDATION

Before saving:

```text
Payment app selected
+
UPI ID non-empty
+
UPI format valid
+
label non-empty
```

Example VPA validation:

```kotlin
private val vpaRegex =
    Regex(
        "^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$"
    )
```

This is basic validation only.

Do not claim it proves the VPA exists.

---

# 61. DO NOT VERIFY VPA BY SENDING MONEY

The app must never automatically initiate a real payment just to test a VPA.

---

# 62. NOTIFICATION ACCESS EXPLANATION

Before opening system settings, clearly explain:

```text
Why is notification access required?

UPI-Easy uses Android's notification listener
to detect payment notifications from the selected
UPI app.

UPI-Easy does not read the UPI app's private
database or private APIs.

Only supported payment-app notifications are
processed.
```

This must be visible before requesting access.

---

# 63. SYSTEM ACCESS CHECK

Create:

```kotlin
fun isNotificationListenerEnabled(
    context: Context
): Boolean {

    val manager =
        context.getSystemService(
            NotificationManager::class.java
        )

    val component =
        ComponentName(
            context,
            PaymentNotificationListenerService::class.java
        )

    return manager
        .isNotificationListenerAccessGranted(component)
}
```

Use this whenever the app resumes from settings.

---

# 64. IMPORTANT SERVICE BEHAVIOR

Do not start the listener manually.

Android binds the `NotificationListenerService`.

Your app should:

```text
Register service
        ↓
User grants access
        ↓
Android binds service
        ↓
onListenerConnected()
        ↓
Process notifications
```

---

# 65. NOTIFICATION REMOVAL

Implement:

```kotlin
override fun onNotificationRemoved(
    sbn: StatusBarNotification
) {
    // Do not delete payment records.
    // Notification removal does not mean payment reversal.
}
```

This is extremely important.

If PhonePe notification disappears:

```text
NOT:
payment reversed
```

It means:

```text
notification removed
```

Nothing more.

---

# 66. PAYMENT REVERSAL

Never derive:

```text
NotificationRemoved
→ REVERSED
```

Reversal requires a separate verified source or explicit reconciliation process.

---

# 67. PAYMENT SENT

Initially support:

```text
RECEIVED
```

as the primary accounting event.

For:

```text
SENT
```

you may detect and store it as:

```text
direction = SENT
```

but do not include it in received collections.

Dashboard:

```text
Received
Sent
```

must remain separate.

---

# 68. EVENT PIPELINE

Final Android pipeline:

```text
Android System
      ↓
NotificationListenerService
      ↓
package filter
      ↓
RawNotification
      ↓
ParserRegistry
      ↓
ParsedPaymentEvent
      ↓
PaymentAccountResolver
      ↓
MATCHED / UNMATCHED / AMBIGUOUS
      ↓
Fingerprint
      ↓
Room
      ↓
WorkManager
      ↓
Hono API
```

---

# 69. FINAL SERVER PIPELINE

```text
POST /payment-events/observed
      ↓
Auth
      ↓
Device authorization
      ↓
Organization authorization
      ↓
Zod validation
      ↓
Payment account validation
      ↓
Idempotency
      ↓
Observed Event INSERT
      ↓
Transaction INSERT
      ↓
Outbox INSERT
      ↓
Commit
      ↓
Queue
      ↓
Notification Worker
      ↓
FCM
```

---

# 70. MULTI-DEVICE NOTIFICATION

Suppose:

```text
Owner phone
PhonePe notification detected
```

Then:

```text
Owner phone
      ↓
Server
      ↓
Organization members
      ↓
OWNER
MANAGER
ACCOUNTANT
CASHIER
      ↓
notification preference check
      ↓
active devices
      ↓
FCM
```

All authorized devices receive the event according to their notification permissions.

---

# 71. ROLE DEFAULTS

Recommended defaults:

```text
OWNER:
payment.received = ON

MANAGER:
payment.received = ON

ACCOUNTANT:
payment.received = ON

CASHIER:
payment.received = ON
```

But preserve user notification preferences.

---

# 72. BACKGROUND REQUIREMENTS

Do not use:

```text
infinite polling
```

Do not use:

```text
background timer every few seconds
```

Do not use:

```text
AccessibilityService
```

Do not use:

```text
root
```

The Android notification listener is event-driven.

That is the correct mechanism.

---

# 73. BATTERY

The listener must remain lightweight.

On notification:

```text
parse
↓
persist
↓
return
```

Do not perform:

```text
large network request
complex DB query
long-running work
```

directly in the notification callback.

Instead:

```text
notification callback
      ↓
Room
      ↓
WorkManager
```

---

# 74. NETWORK FAILURE

If API upload fails:

```text
syncState = FAILED
retryCount++
```

WorkManager retries.

If permanent validation failure:

```text
syncState = REJECTED
```

and surface diagnostic information locally.

---

# 75. CONFLICT HANDLING

If same fingerprint arrives twice:

```text
ignore duplicate
```

If same amount/payer/time but different fingerprints:

```text
do not automatically merge
```

Store both and let reconciliation determine whether they are separate payments.

---

# 76. RECONCILIATION

Add:

```text
Observed payment
      ↓
Possible matching transaction
      ↓
Verified source later
      ↓
Reconcile
```

If verified source says:

```text
₹500 SUCCESS
```

then:

```text
verificationStatus = VERIFIED
status = SUCCESS
```

If no verified source exists:

```text
verificationStatus = OBSERVED
```

---

# 77. API RESPONSE TO UI

Transaction response:

```json
{
  "id": "txn_123",
  "amountMinor": 50000,
  "currency": "INR",
  "direction": "RECEIVED",
  "status": "UNKNOWN",
  "verificationStatus": "OBSERVED",
  "eventSource": "NOTIFICATION_PHONEPE",
  "paymentAccount": {
    "id": "pa_123",
    "label": "Main PhonePe",
    "upiId": "9827743085@ybl"
  },
  "qr": null,
  "observedAt": "2026-09-21T10:42:11Z"
}
```

---

# 78. ERROR STATES

Implement:

```text
NO_PAYMENT_APP_INSTALLED

NOTIFICATION_ACCESS_REQUIRED

PAYMENT_DETECTION_DISABLED

PAYMENT_ACCOUNT_NOT_FOUND

AMBIGUOUS_PAYMENT_ACCOUNT

PARSER_UNABLE_TO_IDENTIFY_PAYMENT

DUPLICATE_EVENT

SERVER_REJECTED

NETWORK_ERROR

SYNC_PENDING
```

---

# 79. USER-FACING WARNING

In Payment Detection settings:

```text
Important

Payment detection is based on notifications
generated by the selected UPI app.

Notifications can be delayed, changed, disabled,
or unavailable.

UPI-Easy treats these events as "Observed" until
they are independently verified.

Do not rely on an observed notification alone
for disputes or final settlement reconciliation.
```

---

# 80. DEVELOPMENT DEBUG SCREEN

Add debug-only screen:

```text
Payment Detection Debug

Listener:
CONNECTED

PhonePe:
Installed ✓
Enabled ✓

Google Pay:
Installed ✓
Disabled

Last notification:
PhonePe

Last parsed event:
₹500

Parser:
PhonePeParser

Confidence:
HIGH

Match:
Main PhonePe

Sync:
UPLOADED
```

Do not show raw sensitive notification text in production.

---

# 81. TESTING MATRIX

Test these cases:

```text
1. PhonePe installed
2. Google Pay installed
3. Neither installed
4. PhonePe selected
5. Google Pay selected
6. Notification access denied
7. Notification access granted
8. Notification access revoked
9. PhonePe payment received
10. Google Pay payment received
11. Payment sent
12. Unrelated notification
13. Duplicate notification
14. Missing amount
15. Multiple payment accounts
16. Multiple QR codes
17. Same VPA across multiple QR codes
18. Offline notification
19. Server unavailable
20. FCM delivery
21. Multiple authorized devices
22. Company switch
23. Device removed
24. Staff removed
25. Permission revoked
26. App uninstalled
27. Phone restarted
28. Notification listener reconnect
29. Notification removed
30. Parser format changes
```

---

# 82. REBOOT TEST

After Android reboot:

```text
device starts
 ↓
Android notification listener reconnects
 ↓
onListenerConnected()
 ↓
payment detection continues
```

Do not assume the app's Activity must be opened first.

Test this on the real target device.

---

# 83. NOTHING PHONE TESTING

Because the development device is a Nothing Phone, test:

```text
Android 16
Nothing OS
battery optimization
notification permission
notification listener access
PhonePe
Google Pay
screen locked
screen unlocked
device reboot
network disconnected
network restored
```

Do not rely only on emulator behavior.

---

# 84. IMPORTANT PLAY STORE CONSIDERATION

The implementation must use Android's notification-listener mechanism legitimately.

Do not describe it as:

```text
Read PhonePe database
```

or:

```text
Intercept PhonePe
```

The actual mechanism is:

```text
Android notification access
```

The application should clearly explain why notification access is required and only process supported payment-app notifications.

Android's notification listener is an OS-level API, and package visibility should be restricted to the known supported applications rather than using broad installed-app access.

Before Play Store release, separately review current Google Play policy requirements for notification access and financial/payment-related functionality. Do not assume technical feasibility automatically means store-policy approval.

---

# 85. DO NOT IMPLEMENT

The agent must NOT implement:

```text
❌ AccessibilityService
❌ Root access
❌ Reading /data/data/com.phonepe.app
❌ Reading PhonePe SQLite database
❌ Private PhonePe APIs
❌ Private Google Pay APIs
❌ Intent interception of another app's private internals
❌ SMS interception
❌ OTP interception
❌ UPI PIN collection
❌ Bank password collection
❌ Fake SUCCESS
❌ Automatic payment verification
❌ Notification removal → reversal
❌ Guessing QR attribution
❌ QUERY_ALL_PACKAGES
```

---

# 86. IMPLEMENTATION ORDER

Execute in this order.

## Phase 1

Inspect existing repository.

Identify:

```text
Android module
Gradle
Compose navigation
Hilt
Room
Retrofit/OkHttp
DataStore
WorkManager
FCM
authentication
organization context
API client
transaction model
QR model
```

Do not create duplicates.

---

## Phase 2

Implement:

```text
PaymentAppDefinition
PaymentAppDetector
supported app registry
package visibility
installed-app detection
```

---

## Phase 3

Implement database:

```text
payment_accounts
qr_codes
observed_payment_events
payment_detection_devices
```

using the existing Room architecture.

---

## Phase 4

Implement Compose:

```text
Add Payment Account
PaymentAppSelector
Payment Account Card
Payment Account Details
QR management
Payment Detection settings
```

---

## Phase 5

Implement:

```text
NotificationListenerService
```

and notification-access UI.

---

## Phase 6

Implement:

```text
PaymentNotificationParser
PhonePeNotificationParser
GooglePayNotificationParser
```

---

## Phase 7

Implement:

```text
fingerprinting
deduplication
payment-account resolution
match status
```

---

## Phase 8

Implement:

```text
POST /v1/payment-events/observed
```

on Hono.

---

## Phase 9

Implement:

```text
Neon persistence
Drizzle schema
idempotency
authorization
outbox
```

---

## Phase 10

Implement:

```text
notification worker
FCM distribution
multi-device sync
```

---

## Phase 11

Implement:

```text
WorkManager
offline queue
sync cursor
retry
```

---

## Phase 12

Implement:

```text
transaction UI
observed badge
verification state
payment-account association
```

---

## Phase 13

Run:

```text
unit tests
repository tests
API tests
Room tests
parser tests
instrumentation tests
end-to-end tests
```

---

# 87. DEFINITION OF DONE

The feature is complete only when this exact flow works:

```text
1. Install UPI-Easy
        ↓
2. Open UPI
        ↓
3. Add Payment Account
        ↓
4. App detects installed PhonePe/Google Pay
        ↓
5. User selects PhonePe
        ↓
6. User enters VPA
        ↓
7. User enters account label
        ↓
8. UPI-Easy saves payment account
        ↓
9. UPI-Easy detects missing notification access
        ↓
10. User grants Android notification access
        ↓
11. UPI-Easy verifies permission
        ↓
12. User creates QR
        ↓
13. Customer scans QR using a UPI app
        ↓
14. Selected payment app receives payment
        ↓
15. Payment app posts notification
        ↓
16. NotificationListenerService receives it
        ↓
17. Package is identified
        ↓
18. Parser extracts payment information
        ↓
19. Payment account is resolved
        ↓
20. Event is deduplicated
        ↓
21. Event is stored in Room
        ↓
22. WorkManager uploads it
        ↓
23. Hono validates it
        ↓
24. Neon stores observed event
        ↓
25. Transaction is created as OBSERVED/UNKNOWN
        ↓
26. Outbox event created
        ↓
27. Worker sends FCM
        ↓
28. Owner receives UPI-Easy notification
        ↓
29. Manager receives notification
        ↓
30. Accountant receives notification
        ↓
31. Authorized devices sync
        ↓
32. Transaction appears in ledger
```

---

# 88. FINAL ARCHITECTURAL RULE

The implementation must preserve this distinction permanently:

```text
QR CODE
    =
payment destination

NOTIFICATION
    =
observed payment signal

PROVIDER/BANK VERIFICATION
    =
authoritative payment confirmation
```

UPI-Easy can fully implement the first two without becoming a payment provider.

It must not pretend the second is the third.

---

# 89. EXPECTED FINAL RESULT

UPI-Easy will provide:

```text
                  UPI-EASY
                      │
             ┌────────┴────────┐
             │                 │
       Payment Accounts       QR Codes
             │                 │
       ┌─────┴─────┐      ┌────┴─────┐
       │           │      │          │
    PhonePe      GPay   Counter 1  Counter 2
       │           │
       └─────┬─────┘
             │
      Android Notification
          Listener
             │
       ┌─────┴─────┐
       │           │
    PhonePe      GPay
       │           │
       └─────┬─────┘
             │
       Parsed Event
             │
          Room DB
             │
        WorkManager
             │
        Cloudflare
             │
           Neon
             │
          Outbox
             │
           FCM
             │
     ┌───────┼────────┐
     │       │        │
   Owner   Manager  Accountant
```

The architecture must remain modular so that a future legitimate provider/bank integration can add:

```text
PROVIDER_WEBHOOK
```

without replacing:

```text
NOTIFICATION_PHONEPE
NOTIFICATION_GPAY
```

The two systems can coexist.

---

# AGENT EXECUTION RULES

1. Inspect the existing code before changing anything.
2. Reuse existing architecture.
3. Do not duplicate services.
4. Do not create mock payment success.
5. Do not hard-code transaction success.
6. Do not guess QR attribution.
7. Do not trust client organization IDs.
8. Do not trust notification text as authoritative settlement.
9. Use Room as the local source of truth.
10. Use Neon as the server source of truth.
11. Use FCM as delivery/trigger only.
12. Use WorkManager for deferred synchronization.
13. Use Hilt for dependency injection.
14. Use Kotlin coroutines/Flow.
15. Keep Compose UI state driven by ViewModels.
16. Keep notification parsing independent from Compose.
17. Keep server validation independent from Android parsing.
18. Add unit tests for every parser.
19. Add idempotency for every observed event.
20. Never store UPI PIN, bank password, CVV, or payment authorization OTP.
21. Never implement accessibility/root/private-app-database techniques.
22. Never use `QUERY_ALL_PACKAGES` for this feature.
23. Support only PhonePe and Google Pay in v1.
24. Make adding future payment apps possible through `PaymentAppDefinition + PaymentNotificationParser`.
25. At completion, provide a changed-files summary, database migration summary, Android permission/setup summary, API summary, and test results.

# END SPECIFICATION
One important correction to the earlier idea: don't make the app selector merely a hard-coded list and don't try to discover every installed app. For your v1, declare PhonePe and Google Pay as the only package-visible targets, then use PackageManager to show whichever of those two are actually installed. Android specifically recommends limiting package visibility to the apps your feature needs.

The resulting feature is essentially a local payment-notification bridge. The QR management remains completely yours, while the Android device that actually receives the PhonePe/GPay notification becomes the observation device. That is the cleanest way to get the multi-QR + multi-device behavior