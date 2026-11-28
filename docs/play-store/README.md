# Play Store release — checklist

Everything the app needs for its first Play Store release. Items marked **(you)** need an account,
a decision or a secret only the owner has; nothing here contains real keys.

## 1. Before the first upload

| Step | Who | Notes |
|---|---|---|
| Google Play developer account | **(you)** | Organisation account if Jatri is a company (needs a D-U-N-S number). |
| Production backend on HTTPS | **(you)** | Deploy `backend/` (Dockerfile). Production refuses to start with the dev secret, a fixed OTP, the mock payment gateway, sandbox SSLCommerz or the console SMS sender. |
| Real SMS gateway | **(you)** + code | Production needs an SMS provider adapter in `app/services/sms.py` (e.g. a BD gateway). Not built yet: no provider was chosen. |
| SSLCommerz live store | **(you)** | Store id/password as deploy-platform secrets. |
| Firebase project | **(you)** | For push. Add `google-services.json` and the server's service account. Optional for launch: reminders already work on the phone. |
| Bus / train / flight providers | **(you)** | The providers are mocks. **Do not publish to production with mock providers**: use the internal testing track until real inventory is connected. |
| Upload key | **(you)** | `keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 9125`. Keep it out of the repo. Enrol in **Play App Signing** (Google keeps the app signing key). |
| GitHub secrets | **(you)** | `JATRI_UPLOAD_KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `JATRI_KEYSTORE_PASSWORD`, `JATRI_KEY_ALIAS`, `JATRI_KEY_PASSWORD`. Variables: `JATRI_API_BASE_URL` (`https://…/api/v1/`), optionally `JATRI_CERT_PINS`. |
| Certificate pins (optional) | **(you)** | `openssl s_client -connect api.host:443 </dev/null \| openssl x509 -pubkey -noout \| openssl pkey -pubin -outform der \| openssl dgst -sha256 -binary \| base64` → `sha256/<value>`. Always pin a **backup** key too, or a certificate change locks every user out. |
| Privacy policy URL | **(you)** | Host [`privacy-policy.md`](privacy-policy.md) (after filling the placeholders) on a public HTTPS page. |
| Account deletion URL | done in app | Account → Delete account (`DELETE /me`). Play also asks for a web link: a page explaining the in-app steps is enough. |

## 2. Build

Run **Actions → Release → Run workflow** with a tag (e.g. `v1.0.0`). With the secrets above set, the
run also builds a **signed `.aab`** and keeps it with the R8 `mapping.txt` as a private workflow
artifact (`jatri-<tag>-play-bundle`). Without them it only builds the demo APK.

Locally:

```bash
JATRI_KEYSTORE_FILE=/path/upload.jks JATRI_KEYSTORE_PASSWORD=… JATRI_KEY_ALIAS=upload JATRI_KEY_PASSWORD=… \
  ./gradlew :app:bundleRelease -Pjatri.useMockApi=false -Pjatri.apiBaseUrl=https://api.example.com/api/v1/ -Pjatri.versionCode=10
```

`bundleRelease` refuses to run with the demo backend, a non-HTTPS address or no upload key.
`versionCode` must grow with every upload (CI uses the run number × 10).

## 3. Play Console

1. **App content**: privacy policy URL; ads = *No*; app access = *phone login with OTP* (give reviewers a
   test number: set one up on the staging server); target audience 18+; content rating questionnaire
   (no violence, no user-generated content, handles payments); **Data safety** from
   [`data-safety.md`](data-safety.md); government apps = No; financial features = *payments* only.
2. **Store listing**: texts in [`listing.md`](listing.md) (Bangla default, English). Screenshots: take them
   from `docs/screenshots/` (phone, 1080×2400 or similar), feature graphic 1024×500, icon 512×512.
3. Upload the `.aab` to **Internal testing** first, then Closed testing (a new personal account needs
   12 testers for 14 days before Production).
4. Upload `mapping.txt` for the same version so crash reports are readable.

## 4. What Phase 9 checked

- Security review of the backend and the app (see the README's Phase 9 section).
- Minified release APK smoke-tested on an emulator in CI (onboarding → guest → search → results).
- Load test: `backend/scripts/loadtest.py`.
