# Data Retention & Account Deletion Policy — UPI-Easy

**Effective Date:** September 20, 2026  
**Compliance Standard:** Digital Personal Data Protection Act, 2023 & Google Play Developer Policy (Data Safety & Account Deletion)

---

### 1. In-App Account Deletion Workflow
Any registered merchant or user may permanently delete their account and associated profile data directly within the application:
1. Open **UPI-Easy** mobile app.
2. Tap the **Settings** tab.
3. Navigate to **Account & Security** → **Delete Account & Data**.
4. Read the statutory retention disclosure and tap **Permanently Delete**.
5. Once confirmed, all authentication sessions are invalidated, tokens are revoked, and local offline databases are wiped immediately.

### 2. Immediate Data Erasure vs. Statutory Retention

When an account is deleted, the data lifecycle operates as follows:

| Data Type | Deletion Timeline | Action Taken | Legal Rationale |
| :--- | :--- | :--- | :--- |
| **User Profile (Name, Email, Mobile, Google ID)** | Immediate (0 days) | Permanently purged from active databases and authentication directories | DPDP Act Section 12 (Right to Erasure) |
| **Device Tokens & Push FCM Tokens** | Immediate (0 days) | Revoked and deleted | Session termination |
| **Local Room SQLite Database** | Immediate (0 days) | `database.clearAllTables()` executed on user device | Complete local wipe |
| **Commercial Transaction Ledger Records** | Mandated Retention (up to 7 years) | Anonymized (personal identifiers detached); stored in secure compliance archive | Section 128 of the Companies Act, 2013 & Goods and Services Tax (GST) Act record-keeping requirements |
| **Security Audit Logs (Actor Actions)** | 2 years | Retained in write-only audit trail | Information Technology (Intermediary Guidelines) Rules, 2021 |

### 3. Deletion Request via Web / Email
If you have uninstalled the application or cannot access your device, you may submit an account deletion request by emailing `support@upieasy.com` with the subject *"Account Deletion Request"* from your registered email address. Requests are verified and processed within 7 business days.
