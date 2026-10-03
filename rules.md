Yes. The prompt needs a hard scope boundary so the agent doesn't decide that adding BHIM and Paytm somehow requires rebuilding half the financial system. Humanity has suffered enough from “while I’m here, I’ll refactor everything.” 😌

Add this section near the **top of the agentic prompt**, before the implementation phases:

# STRICT IMPLEMENTATION SCOPE

## Objective

This task is **strictly limited** to extending the existing UPI-Easy notification-based payment detection system from:

* Google Pay
* PhonePe

to additionally support:

* BHIM UPI
* Paytm

The implementation must preserve all existing UPI-Easy behavior and architecture unless a change is directly required to support these two additional payment applications.

---

# 1. IN SCOPE

The following work is explicitly authorized.

### Android

Implement or modify only the components necessary for:

* Detecting installed supported UPI applications.
* Adding BHIM UPI to the supported-app registry.
* Adding Paytm to the supported-app registry.
* Detecting notifications from BHIM UPI.
* Detecting notifications from Paytm.
* Parsing BHIM UPI payment notifications.
* Parsing Paytm payment notifications.
* Normalizing parsed payment events.
* Deduplicating notification events.
* Mapping notifications to the existing Payment Account.
* Storing observed events in the existing Room architecture.
* Synchronizing observed events using the existing sync system.
* Displaying BHIM and Paytm in the existing Payment Account UI.
* Showing notification-access status using the existing permission flow.
* Using the existing voice-alert mechanism if already implemented.

### Backend

Only modify backend components required to:

* Accept BHIM notification events.
* Accept Paytm notification events.
* Persist their source information.
* Preserve `OBSERVED` verification semantics.
* Maintain existing idempotency.
* Maintain existing organization/device/payment-account authorization.
* Deliver existing FCM payment notifications.
* Include BHIM/Paytm events in existing transaction/history/reconciliation flows.

### Database

Only modify database schemas/migrations where required for:

* BHIM source identification.
* Paytm source identification.
* Parser/source metadata if the existing schema requires it.
* Existing event/transaction relationships.

### UI

Only modify existing screens/components required to:

* Display BHIM UPI as a detection app.
* Display Paytm as a detection app.
* Select BHIM or Paytm for a Payment Account.
* Display their detection status.
* Display their observed payment source.
* Display relevant diagnostics.

---

# 2. OUT OF SCOPE

The agent MUST NOT implement any of the following as part of this task.

## Banking / Payment Provider Integration

Do NOT add:

* Bank APIs.
* PSP APIs.
* TPAP integrations.
* Payment gateway integrations.
* Payment provider onboarding.
* Bank account authentication.
* Account aggregation.
* UPI Autopay APIs.
* UPI Collect APIs.
* UPI mandate APIs.
* Payment initiation APIs.
* Payment settlement APIs.

This task is about **notification observation only**.

---

## Payment Verification

Do NOT implement a new verification mechanism.

Do NOT:

* Treat a notification as bank confirmation.
* Mark notification-originated payments as `SUCCESS`.
* Mark notification-originated payments as `VERIFIED`.
* Claim settlement confirmation.
* Invent transaction confirmation logic.
* Change the existing `OBSERVED` vs `VERIFIED` model.

Notification-originated events must remain:

```text
verificationStatus = OBSERVED
```

unless an already-existing authoritative provider mechanism independently verifies them.

Do not create such a provider mechanism in this task.

---

# 3. NO NEW PAYMENT SYSTEM

Do NOT create:

* A new payment-processing engine.
* A new payment gateway.
* A new UPI transaction protocol.
* A new payment-account architecture.
* A second transaction ledger.
* A second reconciliation system.
* A second synchronization system.

Reuse the existing UPI-Easy architecture.

The new implementation is an extension of the existing notification-detection layer.

---

# 4. NO ARCHITECTURAL REWRITE

Do NOT rewrite:

* Authentication.
* Authorization.
* Organization management.
* Staff management.
* Company switching.
* Device registration.
* Session management.
* Existing Payment Account architecture.
* Existing QR architecture.
* Existing transaction architecture.
* Existing FCM architecture.
* Existing WorkManager architecture.
* Existing Room architecture.
* Existing Hono architecture.
* Existing Drizzle architecture.
* Existing navigation architecture.
* Existing Material 3 design system.

Refactor only when necessary to introduce the provider/parser abstraction required for BHIM and Paytm.

If the existing implementation already has a suitable abstraction, extend it instead of replacing it.

---

# 5. NO NEW UPI QR LOGIC

Do NOT change:

* QR generation.
* QR parsing.
* QR storage.
* QR rendering.
* QR sharing.
* QR scanning.
* Static QR behavior.
* Dynamic QR behavior.

The relationship remains:

```text
Organization
    ↓
Payment Account
    ↓
QR Codes
```

The detection application belongs to the Payment Account.

Do not create a separate BHIM QR subsystem or Paytm QR subsystem.

---

# 6. NO NEW USER ROLES

Do NOT create or modify:

```text
OWNER
MANAGER
ACCOUNTANT
CASHIER
```

permissions unless an existing permission is genuinely required for the new detection-app selection.

Do not redesign RBAC.

---

# 7. NO NEW CLOUD ARCHITECTURE

Do NOT introduce:

* New backend services.
* New Workers.
* New queues.
* New databases.
* New message brokers.
* New notification infrastructure.
* New authentication services.

Use the existing:

```text
Android
    ↓
Room
    ↓
WorkManager
    ↓
Hono
    ↓
Drizzle
    ↓
Neon/Postgres
    ↓
Outbox
    ↓
FCM
```

architecture.

---

# 8. NO NEW EXTERNAL DEPENDENCIES UNLESS REQUIRED

Do not add a new library merely for convenience.

Before adding a dependency:

1. Check whether the existing project already provides the capability.
2. Prefer Android/Kotlin standard APIs.
3. Prefer existing project dependencies.
4. Add a new dependency only if technically necessary.
5. Document the reason.

Do not add:

* Third-party notification scraping libraries.
* Accessibility libraries.
* unofficial UPI SDKs.
* reverse-engineering libraries.
* private payment-app APIs.

---

# 9. NO ACCESSIBILITY / ROOT / PRIVATE API IMPLEMENTATION

The implementation MUST use Android's supported notification-listener mechanism.

Do NOT use:

* `AccessibilityService`
* root access
* private Android APIs
* private UPI application databases
* private IPC
* reverse-engineered internal APIs
* SMS scraping
* filesystem scraping of payment apps

The detection pipeline must remain:

```text
Android Notification
        ↓
NotificationListenerService
        ↓
Provider Parser
```

---

# 10. NO PROCESSING OF UNRELATED NOTIFICATIONS

The listener must not become a general notification collector.

Only process notifications from explicitly supported and configured payment applications:

```text
Google Pay
PhonePe
BHIM UPI
Paytm
```

Do not:

* store all device notifications.
* upload all device notifications.
* log all notification contents.
* build a general notification history.
* inspect unrelated applications.

---

# 11. NO RAW NOTIFICATION CLOUD STORAGE

Do not upload complete Android notification payloads.

Do not persist unnecessary:

```text
Notification
Bundle
RemoteViews
Icon
PendingIntent
Parcelable
```

objects.

Extract only the normalized payment information required by UPI-Easy.

---

# 12. NO CREDENTIAL COLLECTION

Absolutely no implementation may collect or process:

* UPI PIN.
* Bank password.
* Card PIN.
* CVV.
* OTP.
* Authentication tokens belonging to payment applications.
* Bank credentials.

The feature must remain notification-based.

---

# 13. NO "100% DETECTION" CLAIM

Do not add product copy claiming:

```text
100% payment detection
Never miss a payment
Guaranteed payment detection
Bank-confirmed payment
Instant guaranteed payment
```

Notification delivery is dependent on:

* payment-app behavior,
* Android notification behavior,
* device settings,
* notification access,
* battery/background restrictions.

The UI should describe these events as **observed payment notifications**.

---

# 14. NO NEW SOUNDbox PRODUCT

A local voice announcement may use the existing UPI-Easy notification/voice mechanism.

However, this task does NOT include building a separate:

* Soundbox product.
* Soundbox subscription.
* Soundbox hardware integration.
* Bluetooth speaker system.
* Audio-device management system.
* Commercial soundbox marketplace.

The goal is payment detection expansion, not creation of a separate product.

---

# 15. NO NEW ANALYTICS PLATFORM

Do not add:

* Mixpanel.
* Firebase Analytics migration.
* Amplitude.
* PostHog.
* custom analytics infrastructure.

Use the existing analytics/logging infrastructure if one already exists.

Only add technical events necessary to diagnose BHIM/Paytm detection.

---

# 16. NO UI REDESIGN

Do not redesign the application.

Do not modify:

* navigation structure,
* color system,
* typography,
* global spacing,
* dashboard layout,
* authentication screens,
* organization screens,
* existing QR screens.

Only update existing Payment Account / Payment Detection screens where required.

---

# 17. NO CHANGE TO EXISTING SUPPORTED APPS

Google Pay and PhonePe are existing supported providers.

Their current behavior must remain functional.

Do not intentionally alter their parser behavior.

If refactoring is required to move them into the new registry/parser architecture:

```text
Before
Google Pay → existing parser
PhonePe → existing parser

After
Google Pay → registry → existing parser
PhonePe → registry → existing parser
BHIM → registry → new parser
Paytm → registry → new parser
```

The migration must preserve existing behavior.

---

# 18. BACKWARD COMPATIBILITY

Existing data must continue to work.

Existing:

* Payment Accounts
* UPI IDs
* QR Codes
* Transactions
* Observed events
* Devices
* Organizations
* Staff assignments

must remain readable.

Database migrations must be backward-safe.

Do not rename or delete existing columns unless absolutely necessary.

If a migration is required, prefer:

```text
ADD
```

over destructive schema changes.

---

# 19. NO DATA DELETION

This task must not delete:

* existing transactions,
* existing observed events,
* payment accounts,
* QR codes,
* organization data,
* staff/device data.

Do not perform destructive migrations.

---

# 20. STRICT PROVIDER SCOPE

Only these four apps are in scope:

```text
Google Pay
PhonePe
BHIM UPI
Paytm
```

Do not implement:

* Amazon Pay
* WhatsApp
* CRED
* MobiKwik
* Freecharge
* Airtel Thanks
* Jio
* other bank UPI applications

unless they already exist in the current codebase.

The architecture may be extensible, but implementation must stop at the four supported providers.

---

# 21. STRICT PACKAGE SCOPE

Use only the explicitly supported package identifiers:

```text
Google Pay:
com.google.android.apps.nbu.paisa.user

PhonePe:
com.phonepe.app

BHIM UPI:
in.org.npci.upiapp

Paytm:
net.one97.paytm
```

Do not use wildcard package matching.

Do not use:

```text
contains("pay")
contains("upi")
startsWith(...)
```

for identifying payment applications.

Package matching must be exact.

---

# 22. STRICT PARSER SCOPE

Each parser is responsible only for:

```text
Raw notification
      ↓
Payment event extraction
```

It must NOT:

* access the database directly,
* perform network requests,
* call Hono,
* send FCM,
* modify transactions,
* resolve organization membership,
* modify QR records.

The parser should remain a pure/testable transformation:

```text
RawPaymentNotification
        ↓
ParsedPaymentEvent?
```

---

# 23. STRICT PROCESSOR SCOPE

The notification processor is responsible for orchestration:

```text
notification
    ↓
provider lookup
    ↓
parser
    ↓
validation
    ↓
payment-account resolution
    ↓
deduplication
    ↓
Room
    ↓
sync queue
```

It must not contain provider-specific parsing rules.

---

# 24. STRICT BACKEND SCOPE

The backend changes are limited to accepting and processing the new observed-event sources.

Do not change:

* authentication protocol,
* session architecture,
* organization architecture,
* FCM infrastructure,
* role architecture,
* unrelated API endpoints.

Only extend the existing payment-event functionality.

---

# 25. STRICT TEST SCOPE

Required tests:

### Android

* app detection
* package matching
* BHIM parser
* Paytm parser
* existing GPay regression
* existing PhonePe regression
* duplicate detection
* malformed notification
* received payment
* sent payment
* amount parsing
* reference parsing
* offline storage

### Backend

* BHIM event validation
* Paytm event validation
* authorization
* idempotency
* transaction creation
* outbox creation

Do not build a new end-to-end testing framework.

Use the existing test infrastructure.

---

# 26. CHANGE BUDGET

Prefer the smallest change set that satisfies the requirements.

Before modifying a file:

1. Determine whether the file actually requires modification.
2. Reuse existing abstractions where possible.
3. Avoid unrelated cleanup.
4. Avoid formatting entire files unnecessarily.
5. Avoid renaming unrelated classes.
6. Avoid opportunistic refactoring.

The final pull request should be explainable as:

> "Added BHIM UPI and Paytm support to the existing notification-based payment detection architecture."

It should NOT become:

> "Rearchitected UPI-Easy while adding two parsers."

---

# 27. REQUIRED IMPLEMENTATION CHECKPOINT

Before coding, the agent must produce:

```text
CURRENT ARCHITECTURE
--------------------
Notification listener:
Parser architecture:
Payment Account:
Room event:
Sync:
Backend endpoint:
Drizzle table:
FCM:
UI:

REQUIRED CHANGES
----------------
Files to modify:
Files to create:
Database migration:
API changes:
UI changes:
Tests:

OUT OF SCOPE
-------------
List any requested-looking changes that are intentionally not being implemented.
```

Then implement only the identified changes.

---

# 28. STOP CONDITION

The agent must stop implementation once all of these are true:

```text
✓ Google Pay still works
✓ PhonePe still works
✓ BHIM UPI works
✓ Paytm works
✓ Installed-app selection works
✓ Notification access works
✓ Payment Account mapping works
✓ Room persistence works
✓ Offline sync works
✓ Hono accepts events
✓ Database persistence works
✓ FCM propagation works
✓ Existing transaction flow works
✓ Existing QR flow remains unchanged
✓ OBSERVED/VERIFIED semantics remain intact
✓ Tests pass
```

Do NOT continue into unrelated improvements after this point.

---

# 29. Definition of a Successful Change

The final implementation should conceptually be:

```text
             Payment App
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
    Google      PhonePe     BHIM       Paytm
       │          │          │          │
       └──────────┴──────────┴──────────┘
                         │
                         ▼
              Existing Notification
                    Listener
                         │
                         ▼
                 Parser Registry
                         │
                         ▼
                 Existing Payment
                    Pipeline
                         │
                         ▼
                    Existing
                 UPI-Easy System
```

The task is therefore an **extension**, not a replacement.

The agent must preserve the existing architecture, data, security model, payment semantics, and user experience while adding exactly two new notification providers: **BHIM UPI and Paytm**.
