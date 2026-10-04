# Legal & Regulatory Disclaimer - UPI-Easy

**Read this before using, building, distributing, or interacting with UPI-Easy. By doing any of those, you accept these terms.**

---

## 1. No Warranty

UPI-Easy is provided under the terms of the Apache License 2.0 **"AS IS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,"** including but not limited to warranties of merchantability, fitness for a particular purpose, non-infringement, title, data accuracy, uninterrupted service, or system reliability. You assume total responsibility and risk for your use of the application and platform.

---

## 2. No Affiliation with NPCI, RBI, Banks, or Telecom Operators

UPI-Easy is an independent commercial and open-source payment software platform developed by Aerotech Innovations. It is **not affiliated with, endorsed by, sponsored by, certified by, or connected to**:
- National Payments Corporation of India (NPCI)
- Reserve Bank of India (RBI)
- Any commercial bank, payment bank, or cooperative bank in India (including SBI, HDFC, ICICI, Axis, PNB, etc.)
- Any telecommunications operator (including Reliance Jio, Bharti Airtel, Vodafone Idea, BSNL)
- Any third-party UPI application (including Google Pay, PhonePe, Paytm, BHIM)

References to public shortcodes (such as `*99#`), national IVR numbers (`08045163666`), UPI URI schemes (`upi://pay`), and payment trademarks are made strictly for **descriptive, technical, and nominative purposes**.

---

## 3. Financial Transactions are Between You, Your Customer, and Your Bank

When using UPI-Easy to display QR codes, observe notifications, or initiate offline payments via UPI 123Pay or `*99#`:
1. **You are interacting directly with your banking institution and cellular operator.**
2. UPI-Easy **does not see, hold, custody, pool, transmit, intermediate, or modify** your transaction funds or your UPI PIN.
3. UPI-Easy only:
   - Formats legitimate NPCI-compliant payment strings and intent URIs.
   - Triggers the phone dialer with public IVR shortcodes for offline payments.
   - Observes incoming payment notification signals and parses bank confirmation SMS **locally on your device** for merchant bookkeeping.

Any transaction outcome — including success, pending states, delays, double debits, or banking failures — is **strictly between you and your bank**, governed by your bank's terms of service and applicable NPCI payment scheme rules. Aerotech Innovations and contributors accept no liability whatsoever for transaction disputes, ledger reconciliation discrepancies, or fund settlements.

---

## 4. NPCI UPI 123Pay & USSD Regulations

1. **Transaction Limit**: Under NPCI circulars and RBI regulations, payments made via UPI 123Pay are restricted to a statutory cap of **₹4,999** per transaction and must be in whole rupees.
2. **Carrier Tariffs**: Initiating voice calls to `08045163666` or dialing USSD `*99#` utilizes cellular voice and signaling networks. Telecom providers may levy airtime or USSD session charges according to your tariff plan. UPI-Easy does not charge, control, or subsidize these fees.
3. **PIN Confidentiality**: When prompted by the IVR, you enter your confidential UPI PIN on your phone's dialpad. UPI-Easy does not listen to, record, decrypt, or intercept DTMF tones or user PIN entries.

---

## 5. Permissions and Device Privacy

- **SMS Ingestion**: `RECEIVE_SMS` is utilized solely while a payment session is active. Bank confirmation messages from recognized Indian bank sender codes are parsed locally on the device. Raw SMS bodies are never exfiltrated to any remote server.
- **Notification Listener**: Inbound notification listener access is strictly restricted to supported UPI apps (Google Pay, PhonePe, BHIM, Paytm). All other notifications are discarded immediately.
- **Overlay Window**: `SYSTEM_ALERT_WINDOW` is used solely to render a live payment anchor during voice calls so merchants can view payee details and amount while entering their PIN.

---

## 6. Limitation of Liability

To the maximum extent permitted by applicable law, in no event shall Aerotech Innovations, its directors, developers, or contributors be liable for any direct, indirect, incidental, special, consequential, or exemplary damages — including but not limited to loss of funds, profits, business interruption, transaction failures, data loss, telecom fees, regulatory penalties, or reputational harm — arising out of or related to your use of or inability to use UPI-Easy.

---

## 7. Trademarks

All product names, logos, brands, and registered trademarks (including "UPI", "NPCI", "Google Pay", "PhonePe", "Paytm", "BHIM", "Jio", "Airtel", "Vi") are property of their respective owners. Their mention here is purely nominative and does not imply endorsement or partnership.

---

## 8. Grievances and Compliance Inquiries

For legal, regulatory, or privacy grievances, please contact:
- **Designated Grievance Officer**: Legal & Compliance Operations, Aerotech Innovations
- **Email**: `compliance@upieasy.com` / `grievance@upieasy.com`
- **Jurisdiction**: Courts of New Delhi, India
