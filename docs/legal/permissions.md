# Android Permissions & Security Guide - UPI-Easy

This document outlines all system permissions requested by UPI-Easy on Android devices, explains why each permission is essential to operations, and details our privacy guarantees.

---

## 1. Permission Matrix

| Permission Name | Android API Constant | Type | Purpose & Scope |
| :--- | :--- | :--- | :--- |
| **Notification Listener Access** | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | Special Access | Observes payment arrival alerts posted by supported UPI applications (**Google Pay**, **PhonePe**, **BHIM UPI**, and **Paytm**). Strictly filtered to supported apps only. |
| **Call Phone** | `android.permission.CALL_PHONE` | Runtime | Initiates automated telephone calls for NPCI UPI 123Pay (`tel:08045163666`) and dials `*99#` USSD codes without requiring internet access. |
| **Read Phone State** | `android.permission.READ_PHONE_STATE` | Runtime | Detects active cellular subscriptions (SIM 1 / SIM 2), carrier capabilities (VoLTE vs GSM USSD), and call connection duration for offline payment progress tracking. |
| **Answer Phone Calls** | `android.permission.ANSWER_PHONE_CALLS` | Runtime (API 26+) | Programmatically terminates active UPI 123Pay payment calls after the user enters their PIN on the dialpad or taps End Call. |
| **Display Overlay** | `android.permission.SYSTEM_ALERT_WINDOW` | Special Access | Shows a floating in-call guidance overlay during the 123Pay payment call so the merchant can keep payment amount and payee details visible over the system keypad. |
| **Receive SMS** | `android.permission.RECEIVE_SMS` | Runtime | Captures bank debit/credit confirmation SMS locally while an offline payment is pending, confirming the transaction and triggering the soundbox alert without internet. |
| **Read Contacts** | `android.permission.READ_CONTACTS` | Runtime | Allows selecting a recipient's phone number directly from the address book for UPI 123Pay transfers. |
| **Modify Audio Settings** | `android.permission.MODIFY_AUDIO_SETTINGS` | Normal | Manages call audio states and ensures local soundbox voice announcements play clearly. |
| **Post Notifications** | `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Displays immediate payment receipt notices, staff alerts, and diagnostic status cards on merchant devices. |
| **Vibration** | `android.permission.VIBRATE` | Normal | Provides haptic feedback upon payment detection and soundbox voice announcements. |
| **Boot Completed** | `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Reconnects and rebinds the payment observation listener immediately after the device is restarted. |
| **Ignore Battery Optimizations** | `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special Access | Prevents Android OS Doze mode and OEM battery sweeps from terminating background services when the screen is turned off. |
| **Camera** | `android.permission.CAMERA` | Runtime | Scans merchant or customer UPI QR codes to initiate collection requests, offline USSD payments, or link payment accounts. Optional. |
| **Biometric / Fingerprint** | `android.permission.USE_BIOMETRIC`, `USE_FINGERPRINT` | Normal | Secures organizational access, preventing unauthorized cashier or staff access to financial ledgers. |
| **Internet Access** | `android.permission.INTERNET`, `ACCESS_NETWORK_STATE` | Normal | Synchronizes observed and queued offline payments with your organization's backend ledger and cloud reconciliation status when online. |

---

## 2. Notification Listener Service Security & Guarantees
The Notification Listener is the core of UPI-Easy's software soundbox technology:
1. **Targeted Package Inspection Only**: UPI-Easy inspects incoming notifications exclusively from four verified UPI application package IDs:
   - `com.google.android.apps.nbu.paisa.user` (Google Pay)
   - `com.phonepe.app` (PhonePe)
   - `in.org.npci.upiapp` (BHIM UPI)
   - `net.one97.paytm` (Paytm)
2. **Immediate Discard of Unrelated Notifications**: All personal messages (WhatsApp, personal SMS, chat apps, emails, social media) are dropped synchronously without reading, parsing, logging, or storing their contents.
3. **No Credential Scraping**: UPI-Easy does not collect or inspect bank passwords, UPI PINs, CVV codes, debit card PINs, or OTPs.
4. **No Raw Data Upload**: Android `Notification` parcels and raw system objects are never uploaded to the cloud. Only normalized financial amounts, direction, UTR references, and timestamps are retained.

---

## 3. Offline Payment & Telephony Privacy Guarantees
1. **Local Processing of SMS**: `RECEIVE_SMS` is utilized solely while a payment session is active. Bank confirmation messages from recognized Indian bank sender codes (e.g. `HDFCBK`, `SBIINB`, `ICICIB`, etc.) are parsed locally on the device. Raw SMS bodies are never exfiltrated to any remote server.
2. **Keypad Security & Zero PIN Interception**: During UPI 123Pay calls, the user enters their secure 4- or 6-digit UPI PIN directly into their phone's native telephony dialer keypad. UPI-Easy does not log, record, or intercept DTMF tones or user PIN entries.
3. **Contacts Privacy**: The `READ_CONTACTS` permission is used strictly to populate the recipient phone number when the user explicitly opens the contact picker. Address books are never synchronized or uploaded.

---

## 4. Battery Optimization Guidance
Modern Android versions (especially on Xiaomi/MIUI, Samsung OneUI, Vivo/Funtouch, and Oppo/ColorOS) aggressively kill background services when the device is locked. To ensure UPI-Easy never misses an incoming customer payment:
- Merchants can navigate to **Settings > Payment Detection > Listener Health**.
- Tap **Battery Settings** and select **Don't Optimize / Unrestricted** for UPI-Easy.
