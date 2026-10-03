# Android Permissions & Security Guide - UPI-Easy

This document outlines all system permissions requested by UPI-Easy on Android devices, explains why each permission is essential to operations, and details our privacy guarantees.

---

## 1. Permission Matrix

| Permission Name | Android API Constant | Type | Purpose & Scope |
| :--- | :--- | :--- | :--- |
| **Notification Listener Access** | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | Special Access | Observes payment arrival alerts posted by supported UPI applications (**Google Pay**, **PhonePe**, **BHIM UPI**, and **Paytm**). Strictly filtered to supported apps only. |
| **Post Notifications** | `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Displays immediate payment receipt notices, staff alerts, and diagnostic status cards on merchant devices. |
| **Vibration** | `android.permission.VIBRATE` | Normal | Provides haptic feedback upon payment detection and soundbox voice announcements. |
| **Boot Completed** | `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Reconnects and rebinds the payment observation listener immediately after the device is restarted. |
| **Ignore Battery Optimizations** | `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special Access | Prevents Android OS Doze mode and OEM battery sweeps from terminating the notification listener service when the screen is turned off. |
| **Camera** | `android.permission.CAMERA` | Runtime | Scans merchant or customer UPI QR codes to initiate dynamic collection requests or link payment accounts. Optional. |
| **Biometric / Fingerprint** | `android.permission.USE_BIOMETRIC`, `USE_FINGERPRINT` | Normal | Secures organizational access, preventing unauthorized cashier or staff access to financial ledgers. |
| **Internet Access** | `android.permission.INTERNET`, `ACCESS_NETWORK_STATE` | Normal | Synchronizes observed payments with your organization's private backend ledger and checks cloud reconciliation status. |

---

## 2. Notification Listener Service Security & Guarantees
The Notification Listener is the core of UPI-Easy's software soundbox technology:
1. **Targeted Package Inspection Only**: UPI-Easy inspects incoming notifications exclusively from four verified UPI application package IDs:
   - `com.google.android.apps.nbu.paisa.user` (Google Pay)
   - `com.phonepe.app` (PhonePe)
   - `in.org.npci.upiapp` (BHIM UPI)
   - `net.one97.paytm` (Paytm)
2. **Immediate Discard of Unrelated Notifications**: All personal messages (WhatsApp, SMS, banking apps, emails, social media) are dropped synchronously without reading, parsing, logging, or storing their contents.
3. **No Credential Scraping**: UPI-Easy does not collect or inspect bank passwords, UPI PINs, CVV codes, debit card PINs, or OTPs.
4. **No Raw Data Upload**: Android `Notification` parcels and raw system objects are never uploaded to the cloud. Only normalized financial amounts, direction, UTR references, and timestamps are retained.

---

## 3. Battery Optimization Guidance
Modern Android versions (especially on Xiaomi/MIUI, Samsung OneUI, Vivo/Funtouch, and Oppo/ColorOS) aggressively kill background services when the device is locked. To ensure UPI-Easy never misses an incoming customer payment:
- Merchants can navigate to **Settings > Payment Detection > Listener Health**.
- Tap **Battery Settings** and select **Don't Optimize / Unrestricted** for UPI-Easy.
