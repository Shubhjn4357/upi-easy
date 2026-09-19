# Security & Responsible Disclosure Policy — UPI-Easy

**Effective Date:** September 20, 2026  

---

### 1. Technical Security Architecture
UPI-Easy is engineered with defense-in-depth principles:
- **TLS 1.3 Strict Transport Security:** All mobile client to edge API communications are strictly encrypted with TLS 1.3 and enforced with HTTP Strict Transport Security (HSTS).
- **Keystore-Backed Hardware Security:** Session tokens and cryptographic signing keys are stored locally using Android Jetpack Security (`EncryptedSharedPreferences`) backed by the hardware Android Keystore (StrongBox where available).
- **Zero Banking Password / PIN Architecture:** UPI-Easy's software architecture contains zero input fields, database columns, or API parameters for storing UPI-PIN, net banking passwords, or card CVVs.
- **Server-Side Role-Based Authorization:** Organization membership and role permissions (`Owner`, `Manager`, `Cashier`, `Accountant`) are strictly validated server-side on every API request.
- **DDoS & WAF Protection:** Cloudflare Web Application Firewall (WAF) and automated rate-limiting protect our edge API endpoints from brute-force and volumetric attacks.

### 2. Responsible Disclosure Program
We welcome security researchers and ethical hackers to responsibly test and disclose vulnerabilities in our mobile application and backend services.

**Scope:**
- `*.upieasy.com` API endpoints
- UPI-Easy Android mobile application

**Out of Scope:**
- Social engineering (phishing) of UPI-Easy employees or users
- Denial of Service (DoS/DDoS) attacks
- NPCI, bank, or third-party UPI applications

**Reporting a Vulnerability:**
Please email detailed reproduction steps, proof-of-concept, and your contact information to:  
📧 `security@upieasy.com`  

We commit to acknowledging reports within 48 hours and keeping reporters updated during triage and remediation.
