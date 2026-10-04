# System Sitemap & Navigation - UPI-Easy

This document provides a complete navigation map of all screens, legal documents, API routes, and architectural modules across UPI-Easy.

---

## 1. Android Application Sitemap

```text
UPI-Easy Android Application
├── Authentication & Setup
│   ├── Login / OTP Verification Screen
│   ├── Business Organization Setup
│   └── Multi-Firm Company Switcher
├── Dashboard (Home)
│   ├── Today's Collected Volume & Transaction Summary
│   ├── Soundbox Status Indicator
│   ├── Recent Observed Payments HUD
│   └── Quick QR Collection Action
├── Transactions & Ledger
│   ├── Transaction History List (All, Received, Sent, Observed)
│   ├── Transaction Details Modal (Status, Verification, UTR Reference)
│   └── Manual Export (CSV, Excel)
├── UPI Accounts & QR Counter Management
│   ├── Configured UPI VPAs
│   ├── Payment Accounts (Linked to PhonePe, Google Pay, BHIM, or Paytm)
│   ├── Add / Edit Payment Account Bottom Sheet
│   └── QR Generator (Static & Dynamic Counter QRs)
├── Offline Payments (Without Internet)
│   ├── UPI 123Pay Automated Telephony Dialer
│   ├── *99# USSD Scan & Pay
│   ├── Dual-SIM Carrier & VoLTE Selector
│   ├── In-Call Floating Guidance Overlay Window
│   └── Offline Bank SMS Confirmation Ingestion
├── Staff & Device Management
│   ├── Organization Members List
│   ├── Invite Staff (Cashier, Manager, Accountant)
│   └── Enrolled POS & Notification Devices
├── Settings
│   ├── Payment Detection Settings
│   │   ├── Notification Access Permission Toggle
│   │   ├── Listener Health & Auto-Rebind Diagnostic Trigger
│   │   ├── Battery Optimization Exemption Shortcut
│   │   ├── Supported UPI Apps Status (GPay, PhonePe, BHIM, Paytm)
│   │   └── Last Detected Payment Time
│   ├── Soundbox & Voice Announcement Preferences
│   └── Profile & Security
└── Legal & Regulatory Disclosures
    ├── About Architecture & System Role
    ├── Android Permissions Guide
    ├── Changelog (Release History)
    ├── Sitemap & Screen Directory
    ├── Terms of Service
    ├── Privacy Policy
    ├── UPI & NPCI Regulatory Disclaimer
    ├── Refunds & Dispute Resolution Policy
    ├── Data Retention & Account Deletion Policy
    └── Grievance Redressal
```

---

## 2. Backend Cloud API Routes (Hono)

### A. Authentication & Session
- `POST /api/v1/auth/request-otp` - Send login verification OTP
- `POST /api/v1/auth/verify-otp` - Authenticate and issue JWT tokens
- `GET /api/v1/auth/me` - Profile of authenticated user

### B. Organizations & Multi-Tenant Management
- `GET /api/v1/organizations` - List user's businesses
- `POST /api/v1/organizations` - Create new merchant organization
- `GET /api/v1/organizations/:orgId/members` - View staff members
- `POST /api/v1/organizations/:orgId/invites` - Send staff invitation
- `POST /api/v1/invitations/:inviteId/accept` - Accept staff invite

### C. Payment Accounts & Supported Applications
- `GET /api/v1/payment-apps/supported` - Catalog of supported UPI detection apps (PhonePe, GPay, BHIM, Paytm)
- `GET /api/v1/organizations/:orgId/payment-accounts` - List business payment accounts
- `POST /api/v1/organizations/:orgId/payment-accounts` - Register new payment account
- `PATCH /api/v1/organizations/:orgId/payment-accounts/:id` - Update payment account configuration

### D. Payment Events & Reconciliation
- `POST /api/v1/organizations/:orgId/payment-events/observed` - Ingest observed notifications (Idempotent SHA-256)
- `GET /api/v1/organizations/:orgId/transactions` - Synchronize merchant ledger transactions
- `GET /api/v1/organizations/:orgId/qr` - Manage counter QR codes

---

## 3. Documentation Repository
- [AGENT.md](file:///d:/Code/upi-easy/AGENT.md) - Operating guidelines, strict scope, and verification gates
- [LEGAL.md](file:///d:/Code/upi-easy/LEGAL.md) - Master legal, regulatory, and NPCI compliance disclaimer
- [memory.md](file:///d:/Code/upi-easy/memory.md) - System contracts, persistent decisions, and constants
- [architecture.md](file:///d:/Code/upi-easy/architecture.md) - End-to-end component data flow and strengthening mechanisms
- [database structure.md](file:///d:/Code/upi-easy/database%20structure.md) - Room and Drizzle ORM database schemas
- [docs/legal/](file:///d:/Code/upi-easy/docs/legal/) - Complete regulatory and compliance policies
