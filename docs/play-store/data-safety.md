# Data safety form — answers

Based on what the code does today. Re-check if analytics, crash reporting or ads are added (none are now).

**Does the app collect or share user data?** Yes, collects. **Shared with third parties?** Only to
complete the booking the user asks for (bus operator / railway / airline: passenger names, gender,
age, NID or passport for that trip; the payment gateway: amount and booking reference). Play treats
transfers the user initiates for the service as *not sharing*, but list them in the privacy policy.

**Encrypted in transit:** Yes (HTTPS only; cleartext is blocked in release builds).
**Users can request deletion:** Yes (Account → Delete account; `DELETE /me`).

| Data type | Collected | Purpose | Optional? | Notes |
|---|---|---|---|---|
| Phone number | Yes | Account management, app functionality | Required | Login by OTP; tickets by SMS |
| Name | Yes | App functionality | Required for booking | Passenger names, profile name |
| Email address | Yes | App functionality | Optional | E-ticket by e-mail |
| Other info: gender, age / date of birth | Yes | App functionality | Required for booking | Operators and railways require them |
| Government ID (NID, passport number and expiry) | Yes | App functionality, fraud prevention | Required for train / international flights | Encrypted at rest on the server; masked in tickets; saved-traveller screen blocks screenshots |
| Purchase history | Yes | App functionality | Required | Bookings, payments, refunds |
| Payment info | **No** | — | — | Card / wallet details are entered on the gateway's page, never in the app or our server |
| Device or other IDs | Yes (push token) | App functionality | Optional | Only if push is enabled (Firebase) |
| Location | No | — | — | |
| App activity, crash logs, diagnostics | No | — | — | No analytics SDK |

**Stored on the phone only:** session tokens (Android Keystore, AES-GCM), cached tickets for offline
use (app-private storage, excluded from backups, cleared on logout).
