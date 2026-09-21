# UPI-Easy: Multi-Firm Staff, Invitation, Device Notification & Background Sync Implementation

You are implementing a production-grade organization/multi-firm collaboration system for **UPI-Easy**, an Android Jetpack Compose merchant UPI management application with a TypeScript/Hono backend deployed on Cloudflare Workers and PostgreSQL/Neon using Drizzle ORM.

## PRIMARY OBJECTIVE

Implement and repair the complete flow:

```text
Owner/Manager enters:
    mobile number
    optional name
    optional email
    role

        ↓

Server finds existing UPI-Easy user by normalized mobile number

        ↓

Create organization invitation

        ↓

Recipient receives:
    in-app invitation
    push notification

        ↓

Recipient opens:
Staff → Invitations

        ↓

Accept

        ↓

organization_members becomes ACTIVE

        ↓

Firm immediately appears in recipient's firm switcher

        ↓

Recipient can switch between multiple firms

        ↓

Authorized users/devices belonging to the organization
receive payment/service notifications

        ↓

FCM wakes/alerts device

        ↓

WorkManager performs authoritative sync

        ↓

Room database updates

        ↓

All authorized phones converge on the same server state
```

Do NOT implement this as separate user accounts per firm.

A single UPI-Easy user account may belong to multiple organizations/firms.

---

# 1. FIRST: INSPECT THE EXISTING CODEBASE

Before modifying anything, inspect the repository.

Find:

```text
Android:
    app module
    authentication
    ViewModels
    Compose screens
    Navigation
    Room
    DataStore
    Retrofit/OkHttp
    FCM
    WorkManager
    Hilt
    existing Staff screens
    existing Settings
    existing Dashboard/top bar

Server:
    Hono app/router
    auth routes
    organization routes
    staff routes
    notification routes
    transaction routes
    sync routes
    Drizzle schema
    migrations
    repositories/services
    middleware
    RBAC
    FCM implementation
    Cloudflare bindings
    worker entrypoint
```

Also search for existing implementations of:

```text
organization
organizations
firm
staff
member
invite
invitation
notification
device
fcm
sync
cursor
transaction
outbox
role
permission
```

Do not duplicate existing tables, routes, repositories, services, models, or utilities.

Reuse the existing architecture where possible.

Do not replace working Google authentication, OTP authentication, transaction processing, or organization logic unless required.

---

# 2. ARCHITECTURE

Target architecture:

```text
                         UPI-EASY

                         User
                          │
             ┌────────────┴────────────┐
             │                         │
          Profile                   Devices
                                      │
                         ┌────────────┼────────────┐
                         │            │            │
                      Phone A      Phone B      Phone C
                         │            │            │
                         └────────────┼────────────┘
                                      │
                                      ▼
                                Organization
                                      │
                 ┌────────────────────┼────────────────────┐
                 │                    │                    │
              Members               UPI IDs            Bank Accounts
                 │                    │                    │
        ┌────────┼────────┐           │                    │
        │        │        │           ▼                    │
      Owner   Manager   Cashier    Transactions             │
                                      │                    │
                                      ▼                    │
                                Outbox Events                │
                                      │                    │
                                      ▼                    │
                              Notification Worker             │
                                      │                    │
                                      ▼                    │
                                     FCM                     │
                                      │
                         ┌────────────┼────────────┐
                         ▼            ▼            ▼
                       Phone A      Phone B      Phone C
                         │            │            │
                         └────────────┼────────────┘
                                      ▼
                                  WorkManager
                                      ▼
                                    Sync API
                                      ▼
                                     Room
```

The backend is authoritative.

Android local state is a cache/offline representation.

Never treat local Android transaction state as authoritative financial state.

---

# 3. DATABASE MODEL

Use existing naming conventions if the project already has them.

If missing, implement the following.

## organization_members

```ts
export const organizationMembers = pgTable(
  "organization_members",
  {
    id: uuid("id").defaultRandom().primaryKey(),

    organizationId: uuid("organization_id")
      .notNull()
      .references(() => organizations.id, {
        onDelete: "cascade",
      }),

    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, {
        onDelete: "cascade",
      }),

    role: varchar("role", {
      length: 32,
    }).notNull(),

    status: varchar("status", {
      length: 32,
    })
      .notNull()
      .default("ACTIVE"),

    invitedBy: uuid("invited_by")
      .references(() => users.id),

    joinedAt: timestamp("joined_at"),

    createdAt: timestamp("created_at")
      .defaultNow()
      .notNull(),

    updatedAt: timestamp("updated_at")
      .defaultNow()
      .notNull(),
  },
  (table) => ({
    uniqueOrganizationUser: uniqueIndex(
      "organization_members_org_user_unique"
    ).on(
      table.organizationId,
      table.userId
    ),
  })
);
```

Use an enum or centralized constants if the existing project uses enums.

Roles:

```ts
export const ORGANIZATION_ROLES = [
  "OWNER",
  "MANAGER",
  "ACCOUNTANT",
  "CASHIER",
] as const;
```

Membership status:

```ts
export const MEMBERSHIP_STATUS = [
  "ACTIVE",
  "SUSPENDED",
  "REMOVED",
] as const;
```

---

# 4. ORGANIZATION INVITATIONS

Create:

```ts
export const organizationInvites = pgTable(
  "organization_invites",
  {
    id: uuid("id").defaultRandom().primaryKey(),

    organizationId: uuid("organization_id")
      .notNull()
      .references(() => organizations.id, {
        onDelete: "cascade",
      }),

    invitedUserId: uuid("invited_user_id")
      .references(() => users.id, {
        onDelete: "cascade",
      }),

    invitedMobile: varchar("invited_mobile", {
      length: 32,
    }).notNull(),

    invitedEmail: varchar("invited_email", {
      length: 320,
    }),

    invitedName: varchar("invited_name", {
      length: 160,
    }),

    role: varchar("role", {
      length: 32,
    }).notNull(),

    invitedBy: uuid("invited_by")
      .notNull()
      .references(() => users.id),

    status: varchar("status", {
      length: 32,
    })
      .notNull()
      .default("PENDING"),

    expiresAt: timestamp("expires_at")
      .notNull(),

    acceptedAt: timestamp("accepted_at"),

    rejectedAt: timestamp("rejected_at"),

    cancelledAt: timestamp("cancelled_at"),

    createdAt: timestamp("created_at")
      .defaultNow()
      .notNull(),

    updatedAt: timestamp("updated_at")
      .defaultNow()
      .notNull(),
  }
);
```

Statuses:

```ts
export const INVITE_STATUS = [
  "PENDING",
  "ACCEPTED",
  "REJECTED",
  "EXPIRED",
  "CANCELLED",
] as const;
```

---

# 5. MOBILE NUMBER NORMALIZATION

Do not perform:

```ts
users.mobile === input.mobile
```

directly.

Create one shared server utility:

```ts
normalizeIndianMobileNumber(
  mobile: string
): string
```

It should normalize valid Indian numbers consistently.

Examples:

```text
9876543210
+919876543210
919876543210
```

should resolve to the same canonical representation.

Prefer storing:

```text
+919876543210
```

if that is the existing project's convention.

Never log raw mobile numbers unnecessarily.

---

# 6. INVITE API

Implement:

```http
POST /api/v1/organizations/:organizationId/invites
```

Request:

```json
{
  "mobileNumber": "+919876543210",
  "name": "Rahul",
  "email": "rahul@example.com",
  "role": "CASHIER"
}
```

Response:

```json
{
  "success": true,
  "invite": {
    "id": "invite_id",
    "organizationId": "org_id",
    "organizationName": "Sandesh Collection",
    "role": "CASHIER",
    "status": "PENDING",
    "expiresAt": "..."
  }
}
```

Server validation:

```text
authenticated user
        ↓
organization exists
        ↓
authenticated user is active member
        ↓
permission = staff.manage
        ↓
validate role
        ↓
normalize mobile
        ↓
find existing user
        ↓
prevent inviting yourself
        ↓
prevent duplicate active membership
        ↓
prevent duplicate pending invitation
        ↓
create invitation
        ↓
create notification
        ↓
enqueue push notification
```

Do not allow the Android client to choose arbitrary `organizationId` without server-side membership validation.

---

# 7. INVITATION RECIPIENT

If the mobile number belongs to an existing UPI-Easy user:

```text
invitedUserId = existingUser.id
```

This is critical.

The invitation must be associated with the actual recipient user.

If the user does not yet exist, retain:

```text
invitedMobile
invitedEmail
invitedName
```

and allow the invitation to be claimed after registration, subject to secure verification of the invited mobile number.

Do not automatically create an account solely because someone entered a phone number.

---

# 8. INVITATION APIs

Implement:

```http
GET /api/v1/me/invitations
```

Return only invitations belonging to the authenticated user.

Also:

```http
POST /api/v1/invitations/:inviteId/accept
```

```http
POST /api/v1/invitations/:inviteId/reject
```

Optional:

```http
POST /api/v1/invitations/:inviteId/cancel
```

Cancellation should require organization permission.

---

# 9. ACCEPT INVITATION TRANSACTION

Acceptance must be atomic.

Pseudo-code:

```ts
await db.transaction(async (tx) => {
  const invite = await getInviteForUpdate(
    tx,
    inviteId
  );

  if (!invite) {
    throw new NotFoundError();
  }

  if (
    invite.invitedUserId !== authenticatedUser.id
  ) {
    throw new ForbiddenError();
  }

  if (invite.status !== "PENDING") {
    throw new ConflictError(
      "Invitation is no longer pending"
    );
  }

  if (invite.expiresAt < new Date()) {
    await markExpired(tx, invite.id);
    throw new ConflictError(
      "Invitation has expired"
    );
  }

  await createOrganizationMembership(
    tx,
    invite.organizationId,
    authenticatedUser.id,
    invite.role,
    invite.invitedBy
  );

  await markInviteAccepted(
    tx,
    invite.id
  );

  await createOutboxEvent(
    tx,
    "staff.joined",
    invite.organizationId,
    {
      userId: authenticatedUser.id,
      role: invite.role,
    }
  );
});
```

Use proper database constraints to prevent duplicate memberships.

---

# 10. `/me` ORGANIZATION RESPONSE

Update:

```http
GET /api/v1/me
```

or the existing equivalent.

Return:

```json
{
  "user": {
    "id": "...",
    "name": "...",
    "email": "..."
  },
  "organizations": [
    {
      "id": "org_1",
      "name": "Sandesh Collection",
      "role": "OWNER",
      "status": "ACTIVE"
    },
    {
      "id": "org_2",
      "name": "Another Firm",
      "role": "ACCOUNTANT",
      "status": "ACTIVE"
    }
  ]
}
```

This becomes the source for the Android firm switcher.

---

# 11. MULTI-FIRM ANDROID STATE

Create:

```text
data/
├── local/
│   ├── room/
│   └── datastore/
│
├── remote/
│   ├── api/
│   └── dto/
│
└── repository/
```

Store active organization in DataStore:

```kotlin
data class ActiveOrganization(
    val organizationId: String
)
```

Use:

```kotlin
DataStore<Preferences>
```

for the selected organization ID.

Do NOT use this local value as authorization.

The server must verify membership on every organization-scoped request.

---

# 12. TOP DASHBOARD FIRM SWITCHER

Implement a reusable Compose component:

```kotlin
@Composable
fun OrganizationSwitcher(
    organizations: List<OrganizationUiModel>,
    activeOrganizationId: String?,
    onOrganizationSelected: (String) -> Unit
)
```

Dashboard:

```text
┌─────────────────────────────────────────┐
│ 🏢 Sandesh Collection             ▼     │
│    Owner                                │
└─────────────────────────────────────────┘
```

Clicking opens:

```text
Select Firm

✓ Sandesh Collection
  Another Firm
  Third Firm

+ Create / Join Firm
```

When selected:

```text
DataStore.activeOrganizationId = id
```

Then:

```text
refresh organization-scoped data
sync organization
update dashboard
update transactions
update staff
update UPI accounts
update notifications
```

Do not recreate the user's authentication session.

---

# 13. SETTINGS → FIRMS

Add:

```text
Settings
 └── Firms & Organizations
```

Screen:

```text
Firms & Organizations

┌─────────────────────────────┐
│ Sandesh Collection           │
│ Owner                        │
│ ✓ Active                     │
└─────────────────────────────┘

┌─────────────────────────────┐
│ Another Firm                 │
│ Accountant                   │
│                             │
│ [Switch]                    │
└─────────────────────────────┘

[ + Create Firm ]
[ + Join Firm ]
```

Allow users to switch firms without logging out.

---

# 14. STAFF UI

Owner/Manager:

```text
Staff

Members
────────────────────

Rahul
Cashier
Active

Amit
Accountant
Active

Invitations
────────────────────

Pending
Priya
Manager
Waiting for response

[ Invite Staff ]
```

Invite form:

```text
Invite Staff

Mobile number *
Name
Email
Role *

[ Send Invitation ]
```

After successful request:

```text
Invitation sent
```

Do not claim that the recipient has accepted.

---

# 15. RECIPIENT INVITATION UI

The receiving user should see:

```text
Staff

Invitations

┌──────────────────────────────────┐
│ Sandesh Collection               │
│                                  │
│ Invited by Shubham               │
│ Role: Cashier                    │
│                                  │
│ [ Reject ]        [ Accept ]     │
└──────────────────────────────────┘
```

After accepting:

```text
You're now a member of
Sandesh Collection

Role: Cashier
```

Then refresh organizations.

---

# 16. NOTIFICATION DATABASE

If not already implemented, create:

```ts
notifications
```

with:

```text
id
userId
organizationId
type
title
body
dataJson
readAt
createdAt
```

Types:

```ts
export const NOTIFICATION_TYPES = [
  "STAFF_INVITATION",
  "STAFF_JOINED",
  "STAFF_REMOVED",
  "PAYMENT_RECEIVED",
  "PAYMENT_SENT",
  "PAYMENT_FAILED",
  "PAYMENT_REVERSED",
  "TRANSACTION_RECONCILED",
  "SECURITY_ALERT",
  "ACCOUNT_CONNECTED",
  "SYNC_COMPLETED",
] as const;
```

---

# 17. DEVICE REGISTRATION

Create/use:

```ts
devices
```

Fields:

```text
id
userId
deviceId
platform
deviceModel
osVersion
appVersion
fcmToken
isActive
lastSeenAt
lastSyncAt
createdAt
updatedAt
```

Unique:

```text
(userId, deviceId)
```

DO NOT store one FCM token directly on `users`.

A user can have:

```text
Phone A → token A
Phone B → token B
Phone C → token C
```

All three must remain registered.

---

# 18. DEVICE API

Implement:

```http
POST /api/v1/devices/register
```

Request:

```json
{
  "deviceId": "...",
  "platform": "ANDROID",
  "deviceModel": "...",
  "osVersion": "...",
  "appVersion": "...",
  "fcmToken": "..."
}
```

Also:

```http
POST /api/v1/devices/unregister
```

Call unregister on logout where appropriate.

Do not permanently delete audit history.

---

# 19. FCM NOTIFICATION FLOW

When a payment event is confirmed by an authoritative provider:

```text
transaction = SUCCESS
        ↓
database transaction
        ↓
outbox_events
        ↓
notification worker
        ↓
find organization members
        ↓
check notification preferences
        ↓
find active devices
        ↓
FCM
```

Do not generate a "payment received" notification merely because:

```text
UPI intent returned
```

or:

```text
client says payment succeeded
```

Only use authoritative transaction/provider state.

---

# 20. NOTIFICATION PREFERENCES

Implement:

```ts
notificationPreferences
```

with organization-specific settings:

```text
userId
organizationId
paymentReceived
paymentSent
paymentFailed
paymentReversed
staffActivity
securityAlerts
syncStatus
voiceEnabled
updatedAt
```

Default:

```text
paymentReceived = true
paymentSent = true
paymentFailed = true
paymentReversed = true
staffActivity = true
securityAlerts = true
syncStatus = false
voiceEnabled = false
```

Users can change preferences.

---

# 21. PAYMENT NOTIFICATION TO ALL LINKED PHONES

Example:

```text
Organization:
Sandesh Collection

Members:
Shubham - Owner
Rahul - Manager
Amit - Accountant

Devices:
Shubham Phone A
Shubham Phone B
Rahul Phone A
Amit Phone A
```

Payment:

```text
₹5,000 received
UPI account X
```

Server determines authorized recipients based on:

```text
organization membership
+
permission
+
notification preference
+
active device
```

Then sends to every applicable device.

Do not send organization payment information to devices belonging to another organization.

---

# 22. BACKGROUND SYNC

Android must use:

```text
WorkManager
+
FCM
+
Room
```

Create:

```text
sync/
├── SyncWorker.kt
├── SyncScheduler.kt
├── SyncRepository.kt
├── SyncState.kt
└── SyncCoordinator.kt
```

Worker:

```kotlin
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {

    override suspend fun doWork(): Result {
        return try {
            syncRepository.syncAllOrganizations()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
```

Use network constraints:

```kotlin
val constraints =
    Constraints.Builder()
        .setRequiredNetworkType(
            NetworkType.CONNECTED
        )
        .build()
```

Use exponential backoff for failures.

Do not create an always-running foreground service merely to poll the API.

---

# 23. PERIODIC SYNC

Schedule periodic WorkManager sync.

Use the minimum interval permitted by Android/WorkManager rather than attempting an unsupported custom interval.

Also trigger synchronization:

```text
app startup
app foreground
network becomes available
FCM event
manual pull-to-refresh
organization switch
after important local operation
```

Background execution is best-effort on Android.

Never promise users that Android will execute arbitrary code continuously in the background.

---

# 24. FCM → SYNC

When FCM receives an event:

```json
{
  "type": "payment.received",
  "organizationId": "org_123",
  "sequence": 1860
}
```

Do not trust this payload as authoritative transaction data.

Use it as a synchronization trigger:

```text
FCM
 ↓
check organization
 ↓
enqueue WorkManager
 ↓
Sync API
 ↓
server returns authoritative changes
 ↓
Room
```

This protects against stale, duplicated, reordered, or missing notifications.

---

# 25. INCREMENTAL SYNC API

Implement:

```http
POST /api/v1/sync
```

Request:

```json
{
  "organizations": [
    {
      "organizationId": "org_123",
      "cursor": 1858
    },
    {
      "organizationId": "org_456",
      "cursor": 91
    }
  ]
}
```

Response:

```json
{
  "organizations": [
    {
      "organizationId": "org_123",
      "nextCursor": 1861,
      "hasMore": false,
      "changes": [
        {
          "sequence": 1859,
          "type": "payment.received",
          "entityId": "txn_1"
        },
        {
          "sequence": 1860,
          "type": "staff.joined",
          "entityId": "member_1"
        },
        {
          "sequence": 1861,
          "type": "payment.received",
          "entityId": "txn_2"
        }
      ]
    }
  ]
}
```

Never allow the client to retrieve an organization it does not belong to.

---

# 26. SERVER EVENT SEQUENCE

Each organization should have a monotonic event sequence.

Example:

```text
organization 123

1857
1858
1859
1860
1861
```

Store:

```text
organizationId
sequence
eventType
entityType
entityId
payload
createdAt
```

Unique:

```text
(organizationId, sequence)
```

This enables reliable synchronization.

---

# 27. OUTBOX PATTERN

For important events:

```text
DB transaction
    │
    ├── update transaction
    ├── create notification/event
    └── create outbox event
             │
             ▼
        transaction commits
             │
             ▼
       worker processes
             │
       ┌─────┴─────┐
       ▼           ▼
      FCM        sync event
```

Never send FCM first and then save the transaction.

Otherwise:

```text
FCM sent
DB transaction failed
```

creates a notification for something that does not exist.

---

# 28. ROOM DATABASE

Create local entities for:

```text
OrganizationEntity
OrganizationMemberEntity
OrganizationInviteEntity
DeviceEntity
NotificationEntity
TransactionEntity
UpiAccountEntity
SyncCursorEntity
```

Example:

```kotlin
@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["organizationId"]
)
data class SyncCursorEntity(
    val organizationId: String,
    val sequence: Long,
    val lastSuccessfulSync: Long?
)
```

Use organization ID as part of local data identity where appropriate.

---

# 29. ROOM QUERY EXAMPLES

Active organizations:

```kotlin
@Query("""
    SELECT * FROM organizations
    WHERE membershipStatus = 'ACTIVE'
    ORDER BY name
""")
fun observeOrganizations():
    Flow<List<OrganizationEntity>>
```

Pending invitations:

```kotlin
@Query("""
    SELECT * FROM organization_invites
    WHERE status = 'PENDING'
    ORDER BY createdAt DESC
""")
fun observePendingInvites():
    Flow<List<OrganizationInviteEntity>>
```

Organization transactions:

```kotlin
@Query("""
    SELECT * FROM transactions
    WHERE organizationId = :organizationId
    ORDER BY occurredAt DESC
""")
fun observeTransactions(
    organizationId: String
): Flow<List<TransactionEntity>>
```

---

# 30. REPOSITORY PATTERN

Android:

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
Room + API
```

Example:

```kotlin
class OrganizationRepository(
    private val api: OrganizationApi,
    private val dao: OrganizationDao,
    private val dataStore: UserPreferences
) {

    fun observeOrganizations():
        Flow<List<OrganizationEntity>> =
        dao.observeOrganizations()

    suspend fun switchOrganization(
        organizationId: String
    ) {
        dataStore.setActiveOrganization(
            organizationId
        )
    }

    suspend fun acceptInvitation(
        inviteId: String
    ) {
        api.acceptInvitation(inviteId)
        refreshOrganizations()
    }
}
```

---

# 31. VIEWMODEL

Create/use:

```kotlin
class OrganizationViewModel(
    private val repository: OrganizationRepository
) : ViewModel() {

    val organizations =
        repository.observeOrganizations()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    val invitations =
        repository.observePendingInvites()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun acceptInvite(id: String) {
        viewModelScope.launch {
            repository.acceptInvitation(id)
        }
    }

    fun switchOrganization(id: String) {
        viewModelScope.launch {
            repository.switchOrganization(id)
        }
    }
}
```

Adapt this to the project's existing architecture instead of duplicating ViewModels.

---

# 32. RBAC

Server-side permissions are mandatory.

Example:

```ts
const PERMISSIONS = {
  STAFF_READ: "staff.read",
  STAFF_MANAGE: "staff.manage",

  ORGANIZATION_READ: "organization.read",
  ORGANIZATION_MANAGE: "organization.manage",

  TRANSACTIONS_READ: "transactions.read",
  TRANSACTIONS_EXPORT: "transactions.export",

  ACCOUNTS_READ: "accounts.read",
  ACCOUNTS_MANAGE: "accounts.manage",

  UPI_READ: "upi.read",
  UPI_MANAGE: "upi.manage",

  REPORTS_READ: "reports.read",
} as const;
```

Example:

```text
OWNER
    all organization permissions

MANAGER
    staff.read
    staff.manage
    transactions.read
    reports.read
    accounts.read
    upi.read

ACCOUNTANT
    transactions.read
    reports.read

CASHIER
    transactions.read
```

Use the existing RBAC model if one already exists.

UI hiding is not security.

---

# 33. ORGANIZATION SECURITY

Every organization-scoped server operation must verify:

```text
JWT
 ↓
user
 ↓
organization membership
 ↓
membership ACTIVE
 ↓
permission
 ↓
resource belongs to organization
 ↓
operation
```

Never authorize using:

```text
organizationId supplied by client
```

alone.

---

# 34. NOTIFICATION SECURITY

Do not put sensitive information into FCM payloads unnecessarily.

Prefer:

```json
{
  "type": "payment.received",
  "organizationId": "org_123",
  "eventSequence": 1861
}
```

Then fetch authoritative information through authenticated API sync.

Do not send:

```text
UPI PIN
OTP
password
JWT
refresh token
bank password
API key
full sensitive account credentials
```

through FCM.

---

# 35. LOGOUT

When a user logs out:

```text
revoke session
```

and appropriately deactivate/unregister the device session.

If Google Credential Manager is being used, integrate the appropriate credential-state clearing behavior on sign-out according to the Android Credential Manager implementation.

Do not delete the device record merely because the user logs out if you need historical device/audit information. Mark it inactive instead.

---

# 36. MULTIPLE DEVICES

Test:

```text
User A
 ├── Nothing Phone
 ├── another Android phone
 └── tablet
```

All devices must:

```text
login
register device
receive notifications
sync independently
store their own sync cursor
```

If Phone A is offline:

```text
Phone B receives event
Server remains authoritative
Phone A later reconnects
Phone A syncs missing sequence numbers
```

The system must converge.

---

# 37. OFFLINE BEHAVIOR

The app must remain useful offline.

Allowed offline:

```text
view cached organizations
view cached transactions
view cached staff
view cached UPI accounts
view cached reports
view cached notifications
```

Safe queued operations may be supported.

Never manufacture financial success offline.

For example:

```text
UPI payment initiated
```

can be:

```text
PENDING
```

until authoritative confirmation exists.

---

# 38. CONFLICT HANDLING

When the server has newer information:

```text
SERVER_WINS
```

for authoritative financial state.

For user preferences:

```text
last-write-wins
```

may be appropriate.

For membership/role/security changes:

```text
server authoritative
```

Always document the conflict strategy in code.

---

# 39. API RATE LIMITING

Add rate limits to:

```text
invite creation
invite acceptance
invite rejection
device registration
sync
notification registration
```

Example:

```text
staff invites:
20/hour/user/organization
```

Use the existing rate-limiter implementation and make limits configurable.

---

# 40. AUDIT LOGGING

Create audit events:

```text
staff.invited
staff.invite.accepted
staff.invite.rejected
staff.invite.cancelled
staff.joined
staff.removed
staff.role.changed
organization.switched
device.registered
device.removed
notification.preference.changed
```

Do not store secrets in audit metadata.

---

# 41. TRANSACTIONAL INTEGRITY

Invitation acceptance must not partially succeed.

Bad:

```text
membership created
invite update failed
```

Good:

```text
DB transaction:
    create membership
    mark invite accepted
    create event
    commit
```

All or nothing.

---

# 42. IDEMPOTENCY

Accepting the same invitation twice should not create duplicate memberships.

Inviting the same person twice should detect an existing active invitation.

Use idempotency where appropriate:

```http
Idempotency-Key: <uuid>
```

for important mutating requests.

---

# 43. ERROR STATES

Android must handle:

```text
INVITE_NOT_FOUND
INVITE_EXPIRED
INVITE_ALREADY_ACCEPTED
INVITE_ALREADY_REJECTED
ALREADY_MEMBER
INSUFFICIENT_PERMISSION
ORGANIZATION_NOT_FOUND
USER_NOT_FOUND
RATE_LIMITED
NETWORK_ERROR
SYNC_CONFLICT
SESSION_EXPIRED
```

Provide useful UI messages.

Do not expose raw server stack traces.

---

# 44. PUSH NOTIFICATION FAILURE

FCM delivery isn't guaranteed.

Therefore:

```text
FCM = wake/notification mechanism
API sync = source of truth
```

If FCM fails:

```text
periodic WorkManager
+
app startup
+
manual sync
```

still recovers the state.

Remove stale FCM tokens when the provider indicates they are invalid.

---

# 45. SYNC RECOVERY

If:

```text
client cursor = 100
server earliest available event = 500
```

because old events were compacted, return:

```json
{
  "requiresFullSync": true
}
```

Android then:

```text
clear organization cache
↓
fetch current organization snapshot
↓
store current cursor
```

Do not attempt to replay unavailable events forever.

---

# 46. ORGANIZATION SWITCH SYNC

When the user switches:

```text
Firm A → Firm B
```

perform:

```text
save active organization
       ↓
load cached Firm B
       ↓
start sync Firm B
       ↓
refresh dashboard
       ↓
refresh notifications
       ↓
refresh staff
       ↓
refresh UPI accounts
       ↓
refresh transactions
```

Never mix Firm A and Firm B transaction lists.

---

# 47. DASHBOARD

The dashboard top bar should observe:

```kotlin
activeOrganization
```

and render:

```text
[ Firm Name ▼ ]
```

All dashboard repositories should receive:

```text
activeOrganizationId
```

rather than maintaining independent copies of the active organization.

Use one source of truth.

---

# 48. NOTIFICATION SCREEN

Create:

```text
Notifications
```

grouped by:

```text
Today
Yesterday
Earlier
```

Types:

```text
Payment received
Payment failed
Payment reversed
Staff invitation
Staff joined
Security
Sync
```

Tapping:

```text
staff invitation
```

opens invitation.

Tapping:

```text
payment
```

opens transaction detail.

---

# 49. STAFF INVITE NOTIFICATION

Push:

```text
New staff invitation

Sandesh Collection
Role: Cashier

Tap to review
```

Deep link:

```text
upieasy://organization/{organizationId}/invitation/{inviteId}
```

If the app isn't installed/open, the normal notification should route to the appropriate screen after authentication.

Never put authorization information in the deep-link URL itself.

---

# 50. PAYMENT NOTIFICATION

Example:

```text
Payment received

₹2,500
Sandesh Collection

Tap to view
```

Deep link:

```text
upieasy://organization/{organizationId}/transaction/{transactionId}
```

Server authorization must still happen when opening the transaction.

A deep link is not proof of permission.

---

# 51. ANDROID NAVIGATION

Add routes such as:

```text
dashboard
staff
staff/invitations
staff/invite
organizations
organizations/{organizationId}
notifications
transactions/{transactionId}
settings
settings/organizations
settings/notifications
```

Protect organization routes using the active authenticated session and server authorization.

---

# 52. HILT

If Hilt is already used, register:

```text
Credential/Api services
OrganizationRepository
NotificationRepository
SyncRepository
WorkManager worker
Room DAOs
FCM service
DataStore
```

Do not introduce another dependency injection framework.

---

# 53. FCM SERVICE

Use:

```kotlin
class UpiEasyFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Persist locally.
        // Register/update with backend when authenticated.
    }

    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        // Parse event metadata.
        // Schedule WorkManager sync.
        // Display notification if appropriate.
    }
}
```

Do not perform large database synchronization directly inside `onMessageReceived`.

Schedule WorkManager.

---

# 54. BATTERY OPTIMIZATION

Do not request exemption from battery optimization by default.

The app should work correctly under normal Android background restrictions.

Use:

```text
FCM
+
WorkManager
+
network constraints
+
backoff
+
foreground sync when app is active
```

Only show battery-optimization guidance if a device manufacturer or Android configuration is materially preventing expected notification/sync behavior.

Never claim guaranteed real-time delivery when Android cannot guarantee it.

---

# 55. SERVER WORKERS

Use the existing Cloudflare Worker architecture.

Prefer modular workers/services:

```text
src/
├── modules/
│   ├── organizations/
│   ├── staff/
│   ├── notifications/
│   ├── devices/
│   ├── sync/
│   └── transactions/
│
├── workers/
│   ├── notification.worker.ts
│   ├── sync.worker.ts
│   └── reconciliation.worker.ts
│
├── db/
├── middleware/
├── lib/
└── app.ts
```

Do not create microservices unnecessarily.

A modular monolith + queues is sufficient at this stage.

---

# 56. CLOUDFLARE QUEUES

If the existing project uses Cloudflare Queues, create queues/bindings for:

```text
notification-events
sync-events
reconciliation-events
```

Otherwise use the project's existing async mechanism.

Don't introduce another queue provider merely for this feature.

---

# 57. SERVER API LIST

Implement or repair:

```http
POST /api/v1/organizations/:organizationId/invites

GET /api/v1/me/invitations

POST /api/v1/invitations/:inviteId/accept

POST /api/v1/invitations/:inviteId/reject

POST /api/v1/invitations/:inviteId/cancel

GET /api/v1/me/organizations

POST /api/v1/devices/register

POST /api/v1/devices/unregister

GET /api/v1/organizations/:organizationId/notifications

POST /api/v1/organizations/:organizationId/notifications/:id/read

GET /api/v1/organizations/:organizationId/sync

POST /api/v1/sync
```

Adapt routes if the existing project has a different convention.

Do not create duplicate endpoints.

---

# 58. API RESPONSE FORMAT

Follow the existing API response convention.

If none exists, standardize:

Success:

```json
{
  "success": true,
  "data": {}
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "INVITE_EXPIRED",
    "message": "This invitation has expired."
  }
}
```

Never expose database errors to clients.

---

# 59. TESTS

Implement server tests for:

```text
invite existing user
invite non-existing user
invite self
duplicate invitation
duplicate active membership
accept invitation
reject invitation
expired invitation
cancel invitation
wrong recipient attempts acceptance
unauthorized manager attempts restricted operation
multiple organization membership
organization switch
device registration
multiple devices
notification preferences
payment notification routing
sync cursor
missing events
full resync
session expiration
```

Android tests:

```text
organization list
active organization persistence
switch organization
invite screen
pending invitations
accept invitation
reject invitation
notification deep link
Room sync
WorkManager retry
FCM token registration
multiple-device behavior
offline behavior
```

---

# 60. IMPORTANT SECURITY TEST

Attempt:

```http
POST /api/v1/invitations/{inviteId}/accept
```

as a different authenticated user.

Expected:

```text
403
```

Attempt:

```http
GET /api/v1/organizations/{otherOrg}/transactions
```

without membership.

Expected:

```text
403
```

Attempt:

```http
POST /api/v1/organizations/{otherOrg}/invites
```

without permission.

Expected:

```text
403
```

Never rely on Android UI hiding buttons.

---

# 61. MIGRATION

Generate Drizzle migration.

Before applying:

```text
inspect existing schema
check foreign keys
check existing organization/member tables
check existing indexes
```

Do not destroy existing production data.

If an existing staff/member system exists, migrate it into the new membership model rather than duplicating it.

Run:

```text
pnpm db:generate
pnpm db:migrate
```

or the repository's existing equivalents.

---

# 62. ANDROID BUILD VALIDATION

Run:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

If configured:

```bash
./gradlew installDebug
```

Do not require Android Studio.

The project must work with:

```text
JDK 17
Gradle Wrapper
Android SDK
ADB
VS Code
```

---

# 63. SERVER VALIDATION

Run:

```bash
pnpm install
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

If applicable:

```bash
npx wrangler deploy
```

Do not use a development-only configuration in production.

Verify Cloudflare bindings/secrets exist.

---

# 64. PRODUCTION CONFIGURATION

Verify:

```text
DATABASE_URL
JWT_SECRET
FCM credentials
Google configuration
CORS
API_BASE_URL
Cloudflare bindings
Queue bindings
```

Never expose secrets to Android.

---

# 65. LOGGING

Server logs should include:

```text
requestId
userId
organizationId
eventType
deviceId where appropriate
status
latency
```

Do NOT log:

```text
UPI PIN
OTP
JWT
refresh token
Google ID token
FCM token unnecessarily
bank password
card CVV
```

Avoid logging full mobile numbers unless operationally required.

---

# 66. OBSERVABILITY

Use the project's existing Sentry/Pino integration.

Track:

```text
invite creation failure
invite acceptance failure
FCM registration failure
notification worker failure
sync failure
database failure
organization authorization failure
```

Add useful context:

```text
organizationId
userId
deviceId
requestId
```

without secrets.

---

# 67. PERFORMANCE

Do not query every user's entire organization list for every payment.

For payment notification:

```text
payment
 ↓
organizationId
 ↓
active members
 ↓
notification preferences
 ↓
active devices
```

Use indexed columns:

```text
organization_id
user_id
invited_user_id
status
fcm_token
```

Recommended indexes:

```text
organization_members(organization_id)
organization_members(user_id)
organization_members(organization_id, user_id)
organization_invites(invited_user_id, status)
organization_invites(organization_id, status)
devices(user_id)
devices(user_id, is_active)
notifications(user_id, created_at)
notifications(organization_id, created_at)
```

---

# 68. UX REQUIREMENT

Use Material 3 / existing UPI-Easy design system.

Staff invitation should have:

```text
large touch targets
clear role selection
validation
loading state
success state
error state
empty state
offline state
```

Firm switcher should feel like a lightweight account/workspace switcher.

Do not make switching firms feel like logging out.

---

# 69. ACCEPTANCE CRITERIA

The implementation is complete only when this exact scenario works:

### Device A

User A logs in.

Creates:

```text
Sandesh Collection
```

User A is:

```text
OWNER
```

### Device B

User B logs in using their own UPI-Easy account.

### Device A

User A opens:

```text
Staff
→ Invite Staff
```

Enters:

```text
Mobile: User B's registered mobile
Name: Rahul
Email: optional
Role: CASHIER
```

Presses:

```text
Send Invitation
```

### Server

Creates:

```text
organization_invites
```

with:

```text
invitedUserId = User B
status = PENDING
```

and creates:

```text
notification
```

Then sends FCM to User B's active devices.

### Device B

User B receives:

```text
New staff invitation
Sandesh Collection
Role: Cashier
```

Opening the notification navigates to:

```text
Staff → Invitations
```

User B presses:

```text
Accept
```

### Server

Atomically:

```text
organization_members
    User B
    Sandesh Collection
    CASHIER
    ACTIVE

organization_invites
    ACCEPTED
```

Creates:

```text
staff.joined
```

event.

### Device B

`GET /me` / sync returns:

```text
Sandesh Collection
CASHIER
```

Firm appears in:

```text
Dashboard top switcher
Settings → Firms & Organizations
```

### Device A

Owner receives:

```text
Rahul joined Sandesh Collection
```

### Payment

A confirmed payment arrives for a UPI account belonging to:

```text
Sandesh Collection
```

Server creates:

```text
payment.received
```

event.

Every authorized active device with:

```text
paymentReceived = true
```

receives notification.

### Offline test

Device B is offline.

Payment occurs.

Device B later reconnects.

WorkManager syncs.

Device B receives the missing transaction through incremental sync.

No duplicate transaction is created.

### Multi-firm test

User B also belongs to:

```text
Firm X
Firm Y
Firm Z
```

Switching:

```text
Firm X → Firm Y
```

changes all organization-scoped dashboard data.

Firm X data must never appear under Firm Y.

---

# 70. DO NOT DO THESE THINGS

Never:

```text
create duplicate user accounts for each firm
```

Never:

```text
trust client-supplied organization membership
```

Never:

```text
trust client-supplied role
```

Never:

```text
trust client-supplied payment success
```

Never:

```text
store one FCM token per user
```

Never:

```text
use FCM as the authoritative database
```

Never:

```text
depend exclusively on background execution
```

Never:

```text
store UPI PIN
```

Never:

```text
store bank password
```

Never:

```text
send authentication secrets through notifications
```

Never:

```text
mix cached data between organizations
```

Never:

```text
delete financial records simply because the user changes firms
```

---

# 71. DELIVERABLES

After implementation, provide:

```text
1. Files created
2. Files modified
3. Database migration
4. API routes
5. Android screens/components
6. Room entities/DAOs
7. WorkManager implementation
8. FCM implementation
9. Notification flow
10. Organization switcher
11. Staff invitation flow
12. RBAC changes
13. Tests added
14. Commands executed
15. Build/test results
16. Any unresolved issues
```

Also provide a final architecture diagram showing:

```text
Android
 ↓
Hono
 ↓
Neon
 ↓
Outbox
 ↓
Cloudflare Worker/Queue
 ↓
FCM
 ↓
Android
 ↓
WorkManager
 ↓
Sync API
 ↓
Room
```

## FINAL ENGINEERING PRINCIPLE

The source of truth is:

```text
SERVER + DATABASE
```

Android is:

```text
LOCAL CACHE + OFFLINE UI + SYNC CLIENT
```

FCM is:

```text
NOTIFICATION / SYNC TRIGGER
```

WorkManager is:

```text
RELIABLE BACKGROUND SYNC MECHANISM
```

Organization membership is:

```text
USER ↔ ORGANIZATION
```

not:

```text
USER = ORGANIZATION
```

A user can belong to multiple organizations, have different roles in each organization, use multiple devices, and receive organization-scoped notifications according to permissions and notification preferences.

Implement the feature end-to-end, preserve existing working functionality, use the existing project conventions, and do not leave mock implementations or TODO placeholders where production code is expected.
