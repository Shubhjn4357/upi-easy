# Privacy Policy — UPI-Easy

**Last Updated:** September 20, 2026  
**Effective Date:** September 20, 2026  

UPI-Easy ("UPI-Easy", "we", "us", or "our") is a merchant-oriented UPI management and transaction-record management software application operated by Aerotech Innovations. This Privacy Policy describes our practices regarding the collection, use, storage, protection, disclosure, and deletion of user information in compliance with the **Digital Personal Data Protection Act, 2023 (DPDP Act)**, the **Digital Personal Data Protection Rules, 2025**, the **Information Technology Act, 2000**, and **Google Play User Data & Financial Features Policies**.

---

## 1. What UPI-Easy Is (And What It Is Not)

**UPI-Easy is:**  
A merchant-oriented software tool designed for authorized UPI identification management, static and dynamic QR code generation, payment link generation, transaction record ledgering, staff access role management, and instant soundbox notifications.

**UPI-Easy is NOT:**  
- A bank, payment system operator (PSO), payment service provider (PSP) bank, payment aggregator, or prepaid payment instrument (PPI) issuer.
- An entity that holds customer funds or processes, authorizes, or settles banking transactions. All financial settlements occur exclusively between the respective customer's bank, merchant's bank, and NPCI-authorized UPI applications.

---

## 2. Information We Collect and Purpose Table

In accordance with Section 5 and Section 6 of the DPDP Act 2023, data processing is strictly limited to data necessary for specified, legitimate business and security purposes:

| Data Category | Specific Data Elements | Primary Purpose | Retention Basis |
| :--- | :--- | :--- | :--- |
| **Account Identification** | Full Name, Business Name, Mobile Number, Email Address, Google Account ID | Merchant profile creation, account recovery, multi-device authentication | Active account lifetime + statutory period |
| **Authorized UPI Metadata** | UPI VPA (e.g. `store@okhdfcbank`), Payee Name, Merchant Category Code (MCC) | Rendering dynamic/static QR codes, transaction linking, payment requests | Duration of merchant linking |
| **Transaction Records** | UPI Reference Number (RRN), Transaction Amount, Timestamp, Status (Success/Pending/Failed), Payer Name (if provided by bank notification) | Business bookkeeping, ledger reconciliation, transaction history, offline-first sync | Statutory tax & commercial accounting retention (up to 7 years) |
| **Device & Security Metadata** | Android Device ID, Model, OS Version, App Version, IP Address, Session Token | Preventing unauthorized session hijacking, fraud detection, crash diagnostics | 180 days rolling |
| **Push Notification Tokens** | Firebase Cloud Messaging (FCM) Token | Delivering real-time payment soundbox and credit alerts | Replaced on app update / session logout |
| **Organization & Staff Roles** | Staff Full Name, Assigned Role (Owner, Manager, Cashier, Accountant), Permissions | Role-Based Access Control (RBAC) within merchant business | Controlled by Organization Owner |
| **Audit Logs** | Timestamped actor ID, action type (e.g., `upi.added`, `staff.invited`, `export.ledger`) | Internal security accountability and fraud prevention | 2 years |
| **Hardware Permissions** | Camera (only when scanner is active), Storage (only when generating CSV report) | Scanning UPI QR codes for payment; exporting accounting reports | Ephemeral; no background access |

---

## 3. Strict Boundary: Information We NEVER Collect or Store

In strict adherence to NPCI UPI security specifications and RBI directions:
- **WE NEVER REQUEST, STORE, READ, INTERCEPT, OR TRANSMIT:**
  1. UPI PIN
  2. Net Banking Passwords
  3. ATM PIN
  4. Debit / Credit Card CVV or Full Card Numbers
  5. One-Time Passwords (OTPs) used for banking transaction authorization

UPI authentication and PIN verification are executed solely within authorized NPCI-certified UPI applications or bank PSP apps on your device.

---

## 4. Third-Party Service Providers and Data Processors

We transmit data only to verified cloud infrastructure providers operating under strict confidentiality and security terms:

1. **Cloudflare Workers & D1**: Serverless API routing and edge database hosting (Encrypted with TLS 1.3).
2. **Neon Serverless Postgres**: Primary multi-tenant relational persistence with role-level isolation.
3. **Google Firebase (FCM)**: Real-time notification dispatch for transaction alerts.
4. **Google Identity Services**: OAuth 2.0 single sign-on authentication.

*We do NOT sell, rent, monetize, or broker personal or financial information to data brokers or third-party advertisers.*

---

## 5. Data Security and Safeguards

- **In Transit**: All API traffic is strictly enforced over HTTPS with TLS 1.3 encryption and HSTS headers.
- **At Rest**: Secure encrypted token storage using Android Keystore-backed `EncryptedSharedPreferences`.
- **Database Isolation**: Multi-tenant schema segmentation ensuring Organization A cannot query or mutate Organization B's data under any circumstance.
- **Session Protection**: Dual-token architecture (short-lived 15-minute JWT access tokens and cryptographically random rotating refresh tokens).

---

## 6. Account and Data Deletion Policy

Under Google Play policies and Section 12 of the DPDP Act 2023, you have the absolute right to delete your account:
- You may initiate account deletion directly in the app at:  
  `Settings` → `Account & Security` → `Delete Account & Data`
- Upon confirmation:
  1. Your profile credentials, auth tokens, device tokens, and active sessions are permanently erased immediately.
  2. Local Room databases on your device are wiped clean.
  3. *Statutory Exception*: Under Indian commercial, GST, and anti-fraud regulations, certain anonymized transaction records and immutable audit logs must be retained for mandated audit retention windows (up to 7 years) and cannot be deleted prior to statutory expiry.

---

## 7. Staff and Role-Based Privacy

Organization Owners have administrative authority to invite and assign roles (Manager, Cashier, Accountant). Staff activity (such as processing payments or generating invoices) is logged in the merchant's internal audit log. Organization Owners can revoke staff access at any time.

---

## 8. Grievance Redressal and Contact

In accordance with Rule 3 of the IT (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021 and DPDP Act 2023, our designated Grievance Officer details are:

- **Grievance Officer:** Grievance Officer, UPI-Easy Legal Operations  
- **Email:** `grievance@upieasy.com`  
- **Support & Privacy Inquiries:** `support@upieasy.com`  
- **Responsible Security Disclosures:** `security@upieasy.com`  
- **Address:** Aerotech Innovations, Technology Hub, Bengaluru, Karnataka 560100, India  
