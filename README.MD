# UPI-Easy: Complete Application UI & Screen Showcase

UPI-Easy is a production-grade multi-bank UPI management and point-of-sale (POS) terminal application designed for Indian merchants and retail businesses.

---

## 1. App Launcher Icon (UPI Style)
The home screen launcher icon features official UPI branding aesthetics on a deep midnight navy slate card:
- Indian Rupee (`₹`) symbol in bold white
- Forward dual speed chevrons in **Saffron Orange (`#FF7A00`)** and **Emerald Green (`#00C853`)**

<p align="center">
  <img src="./docs/images/upi_easy_app_icon_1789754493481.jpg" width="180" alt="UPI-Easy App Icon" />
</p>

---

## 2. All Application Screens

### Screen 1: Google One-Tap Authentication (`GoogleSignInScreen.kt`)
*First screen displayed to merchants on app launch.*
- **Header**: Safe vault branding with the bold title **UPI-Easy: Business UPI & Multi-Bank Manager**.
- **One-Tap Card**: Automatically identifies active merchant account pill (`merchant.business@gmail.com`) with a verified green indicator.
- **Action**: One-click **Continue with Google →** button.
- **Footer**: Encrypted session badge ensuring bank credentials are never stored.

<p align="center">
  <img src="./docs/images/upi_easy_signin_ui_1789755925480.jpg" width="360" alt="Google Sign-In Screen" />
</p>

---

### Screen 2: Merchant Home Dashboard (`DashboardScreen.kt`)
*The central control hub for daily store operations.*
- **Top App Bar**: Store identity (`Sri Krishna Supermarket`) with notification alert bell.
- **Hero Collections Card**: Displays **Today's Collections (`₹48,250.00`)**, total transaction count (**142 Transactions**), and percentage growth pill.
- **Quick Action Pills**: Direct shortcuts to **Scan QR**, **Generate QR**, and **UPI Accounts**.
- **Active UPI Banner**: Displays primary verified VPA (`storename@okhdfcbank`) with verified badge.
- **Live Transactions Feed**: Real-time incoming payment cards showing customer name, timestamp, amount in green (`₹2,850.00`), and `SUCCESS` pills.
- **5-Tab Bottom Navigation**: `Home`, `Ledger`, `UPI`, `Staff`, and `More`.

<p align="center">
  <img src="./docs/images/upi_easy_dashboard_ui_1789755905175.jpg" width="360" alt="Dashboard Screen" />
</p>

---

### Screen 3: Dynamic Payment QR Generator (`QrGeneratorScreen.kt`)
*Point-of-Sale (POS) dynamic billing terminal.*
- **Merchant Card**: Displays store title and active UPI VPA.
- **Dynamic QR Code**: High-contrast QR code generated in real-time with embedded UPI intent parameters (`upi://pay?pa=...`).
- **Live Amount Tag**: Highlights the billing amount (`Amount: ₹750.00`).
- **Configurable Inputs**: Custom amount field (`₹ 750`) and invoice note field (`Invoice #4891`).
- **Share Button**: **Share UPI Payment Link** button to send instant payment requests via WhatsApp, SMS, or email.

<p align="center">
  <img src="./docs/images/upi_easy_qr_ui_1789755946322.jpg" width="360" alt="QR Generator Screen" />
</p>

---

### Screen 4: Transaction Ledger & Details Sheet (`TransactionsScreen.kt`)
*Complete searchable audit trail of all store revenue.*
- **Search Bar**: Instant filter by VPA, Bank RRN, customer name, or billing note.
- **Filter Chips**: Filter by payment status (`ALL`, `SUCCESS`, `PENDING`, `FAILED`).
- **Transaction Cards**: Chronological list showing timestamp, bank reference, and amount.
- **Modal Bottom Sheet**: Tapping any transaction slides up an inspection sheet with full details:
  - Amount: `₹1,450.00`
  - Status: `SUCCESS`
  - Payee VPA & Payer VPA
  - Bank RRN: `334201948123`

<p align="center">
  <img src="./docs/images/upi_easy_ledger_ui_1789755973109.jpg" width="360" alt="Transaction Ledger Screen" />
</p>

---

### Additional Core Features
- **UPI Accounts (`UpiScreen.kt`)**: Multi-bank UPI ID manager allowing merchants to add secondary accounts (e.g. HDFC, ICICI, SBI) and route incoming revenue.
- **Staff Management (`StaffScreen.kt`)**: Manage billing clerks and store cashiers with granular access permissions.
- **Settings & Auditing (`SettingsScreen.kt`)**: Biometric authentication switch, Soundbox voice alert test, and secure session logout.
