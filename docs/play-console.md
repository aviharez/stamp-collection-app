# Play Console checklist

Everything to fill in once the Google Play developer account exists. Answers below match what the app
does as of 2026-11-05 (see the Decisions log in CLAUDE.md). Re-check them if a feature changes.

Package: `com.goomistudio.postmory` · version 0.0.1 (versionCode 1; semantic versioning, `AppVersion` in `app/build.gradle.kts`) · targetSdk 37

---

## 0. Before the account (ready now)

- [x] Upload key: `~/keystores/stamp-upload.jks` (PKCS12, alias `upload`, RSA 4096).
      Password is in `keystore.properties` (project root, git-ignored).
      Certificate SHA-256: `c5:37:ab:cf:ee:bc:1c:42:8d:05:a5:a6:bc:9a:4d:5a:b8:5e:86:93:cd:76:86:f1:26:78:be:9a:69:dd:c4:61`
      **Back up the .jks and the password** (password manager plus one offline copy). If the upload key is
      lost, Play can reset it, but that takes a support request and days.
- [x] Signed release bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
- [x] Privacy policy draft: `docs/privacy.html` (contact: hello.goomistudio@gmail.com)
- [x] App name: **Postmory** (`app_name` in `strings.xml`)
- [x] Support email: **hello.goomistudio@gmail.com** (default in `app/build.gradle.kts`; a `supportEmail=` gradle property overrides it). Developer name on Play: **Goomi Studio**
- [x] Firebase: console.firebase.google.com → Add project "Postmory" (Google Analytics on) → Add Android app
  `com.goomistudio.postmory` → download `google-services.json` into `app/` (git-ignored) and paste it into the
  `GOOGLE_SERVICES_JSON` secret (§11). In Analytics settings, leave Google signals and ads personalisation off.
- [ ] Publish the privacy page: GitHub repo → Settings → Pages → "Deploy from a branch", `main`, `/docs`.
      URL becomes `https://aviharez.github.io/stamp-collection-app/privacy.html` →
      `privacyPolicyUrl=` in `gradle.properties` (shows "Privacy policy" on Profile)
- [x] Store graphics in `store/` (see §8)

## 1. Create the app

- App or game: **App** · Free · Default language: English (United States)
- Category: **Photography**
- Contact email: the support email · Privacy policy URL: the Pages URL above

## 2. App signing

- Choose **Play App Signing** (Google holds the app signing key, you keep the upload key above).
- The first upload of `app-release.aab` registers the upload key automatically.
- After that, copy the **app signing key** SHA-256 from Play Console. You only need it if a Google API
  is ever restricted by certificate (none today).

## 3. In-app products (Monetize → Products)

| Product ID | Type | Notes |
|---|---|---|
| `pro_lifetime` | One-time product | ~US$14.99, main offer. Title "Pro (lifetime)" |
| `pro_yearly` | Subscription | One auto-renewing base plan, 1 year, ~US$9.99. **No free trial** at launch |

- Product IDs must match exactly (`BillingRepository.kt`). Prices come from Play, never from code.
- Billing only works on a build installed from a Play testing track, by a tester account. Add your own
  account under Settings → **License testing** to buy without being charged.
- Purchases are acknowledged in the app (no server). Turn on Real-time developer notifications later
  only if a backend is added.

## 4. AdMob

1. Create an AdMob account and add the app (Android, after it's on Play, or "not listed yet").
2. Create **one Rewarded** ad unit.
3. Put the IDs in `~/.gradle/gradle.properties` (not in the repo):
   ```
   admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   admobRewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
   ```
   **Without them a release build falls back to Google's test IDs** (no revenue). Check before uploading.
4. AdMob → Privacy & messaging: create the **GDPR (EEA/UK)** message and the **US states** message.
   The app shows them through UMP the first time someone taps a "short ad" button.
5. Blocking controls → max ad content rating is already set to **PG** in code. Match it in AdMob.
6. Add `app-ads.txt` on a developer website once you have one (optional, improves fill).

## 5. App content (Policy → App content)

**Privacy policy:** the Pages URL.

**Ads:** **Yes, my app contains ads.**

**App access:** All functionality is available without special access (no login).

**Advertising ID:** **Yes**, used for **Advertising or marketing** (Mobile Ads SDK, permission
`com.google.android.gms.permission.AD_ID`).

**Target audience and content:** **13–15, 16–17 and 18+** (not under 13: AdMob would need the
Families policy setup). "Could unintentionally appeal to children": No.

**Content rating (IARC questionnaire):** category *Utility, productivity, communication or other*.
No violence, sexuality, language, drugs or gambling. "Users can interact or exchange content": **No**
(sharing goes through Android's share sheet, nothing is hosted). "Shares the user's location with other
users": **No**. "Digital purchases": **Yes**. Expected result: Everyone / PEGI 3.

**Government apps / Financial features / Health / News:** No / None / No / No.

**Foreground service, exact alarm, background location:** none needed. The reminder and widget use
inexact alarms, and location is approximate and in-use only.

## 6. Data safety

General answers:
- Does the app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes**
- Way for users to request deletion? No account exists. Say users delete everything in the app or by
  uninstalling. No deletion URL is needed without accounts.

| Data type | Collected | Shared | Optional? | Purposes | Who |
|---|---|---|---|---|---|
| Location → Approximate location | Yes, processed ephemerally | No | Optional | App functionality (postmark city) | Android Geocoder → Google |
| Location → Approximate location (from IP) | Yes | Yes | Optional (only if the user watches an ad) | Advertising, Analytics, Fraud prevention | Mobile Ads SDK |
| Device or other IDs | Yes | Yes | Optional | Advertising, Analytics, Fraud prevention, security | Mobile Ads SDK |
| App activity → App interactions | Yes | Yes | Optional | Advertising, Analytics | Mobile Ads SDK |
| App info and performance → Crash logs, Diagnostics | Yes | Yes | Optional | Analytics, Fraud prevention | Mobile Ads SDK |
| App info and performance → Crash logs, Diagnostics | Yes | No | Optional (Profile → Privacy) | Analytics (app stability) | Firebase Crashlytics |
| App activity → App interactions | Yes | No | Optional (Profile → Privacy) | Analytics | Google Analytics for Firebase |
| Device or other IDs (Firebase installation ID, not the advertising ID) | Yes | No | Optional (Profile → Privacy) | Analytics | Firebase |
| Photos and videos | **No**: stay on the device | | | | |
| Purchase history | **No**: handled by Google Play itself | | | | |

Notes:
- Data processed only on the device (photos, albums, captions, reminders, widgets, backups) is **not**
  "collected" in Play's definition, because it never leaves the phone through the app.
- Firebase counts as a service provider (processing on our behalf), so its rows are "collected", not
  "shared". Usage counts never include photos, words, places or ids of stamps; the advertising ID and ad
  signals are off (AndroidManifest.xml).
- Before submitting, compare the Mobile Ads SDK rows with Google's current page
  ("Mobile Ads SDK: data disclosure" in the AdMob help). Google updates that list.

## 7. Store listing drafts

**App name** (max 30): `Postmory: Photo Stamp Album` (27)

**Short description** (max 80):
> Turn every day's photo into a postage stamp, postmarked with where and when.

**Full description** (max 4000):
> Make a stamp of today.
>
> Snap a photo and it becomes a real-looking postage stamp, with perforated edges and a postmark
> printed with your city, country and date. Stick it into albums like a paper stamp collection,
> watch your calendar fill up and keep your daily streak going.
>
> A CAMERA MADE FOR STAMPS
> • Frame the shot in a stamp-shaped viewfinder: portrait, landscape, square or round
> • Exposure control, tap to focus, 0.5× / 1× / 2× lenses where your phone has them
> • Landscape and portrait stamps always come out the right way up
> • Import from your gallery too, always free
>
> A REAL STAMP ALBUM
> • Kraft paper pages, washi tape and handwritten dates
> • Albums for trips, pets, everyday life
> • Write on the back of every stamp
> • A calendar of stamped days and a streak to keep
> • A world map of every country you've stamped
>
> SHARE IT, PRINT IT
> • Export stamps at full resolution, transparent or on paper
> • Send a stamp as an envelope, postcard or card
> • Share a whole album page
> • Print your stamps as sticker sheets (A4 or Letter)
>
> FAIR, NOT PUSHY
> • No forced ads. Ever. A short ad plays only if you ask for one (for example to remove the
>   watermark from one picture).
> • Your photos stay on your phone. No account, no cloud, no tracking.
> • Back up everything to a single file you keep.
>
> PRO (one-time or yearly)
> Unlimited albums, no watermark, every filter and edge style, scrapbook pages, postcard looks,
> sticker sheets and home-screen widgets.

**Release notes v1.0:**
> First release. Make your first stamp in three taps.

**Translations** (id, es, pt, fr, de, ja): `docs/store-listing-translations.md`.

## 8. Graphics (ready in `store/`)

| Asset | File | Size |
|---|---|---|
| App icon | `store/icon-512.png` | 512 × 512, no alpha |
| Feature graphic | `store/feature-graphic.png` | 1024 × 500: cobalt, airmail stripes, "Postmory", three tilted stamps + a Kyoto postmark |
| Phone screenshots | `store/screenshots/screenshot-1…8-*.png` | 1080 × 1920, captioned, upload in number order |

Screenshots, in order: Home · Frame it (import screen; the emulator camera is fake) · Stamped! · Album page ·
Calendar · Map · Share as envelope · Collections. Sample photos are from Unsplash (credits in
`store/PHOTO-CREDITS.md`); no real user data.

To redo them: `python3 -I tools/store/seedgen.py <seeddir> Postmory-sample.zip` builds a sample backup
(6 albums, 52 stamps, a 12-day streak) from `<seeddir>/full/<picsum id>.jpg`; restore it on the emulator
(Profile → Restore), set demo mode and a location (Bandung), screencap each screen as `raw-<name>.png`, then
`python3 -I tools/store/compose.py app/src/main/res/font <rawdir> <seeddir> <outdir>`. Keep place names in the
pictures to well-known cities (no districts or home towns).

## 9. Testing tracks (new personal accounts)

1. **Internal testing** first: upload the AAB, add your own account, and check billing with license testing
   plus a real rewarded ad (consent form shows in the EEA via a VPN or the UMP debug geography).
2. **Closed testing**: **at least 12 testers opted in for 14 continuous days** before you can apply for
   production. Recruit 15–20 to be safe. Testers join through the opt-in link.
3. Apply for production access in the Dashboard (questions about the test) → production release.

## 10. Before each upload

- [ ] Bump `AppVersion` in `app/build.gradle.kts` (MAJOR.MINOR.PATCH: PATCH for fixes, MINOR for features, MAJOR for big changes; versionCode follows). Play refuses a version it has already seen
- [ ] `admobAppId` / `admobRewardedId` set (not the test IDs)
- [ ] `./gradlew :app:generateBaselineProfile` if screens changed a lot (emulator with stamps restored)
- [ ] `app/google-services.json` present (else the build has no crash reports / usage stats)
- [ ] `./gradlew lint bundleRelease` (or push a `v*` tag, §11)
- [ ] Smoke test the release build on a real phone via internal testing

## 11. GitHub Actions builds

- **CI** (`.github/workflows/ci.yml`): every push to `main` and every pull request runs unit tests, lint and
  the debug build. The debug APK and the lint/test reports are kept as workflow artifacts for 14 days.
- **Release** (`.github/workflows/release.yml`): runs on a `v*` tag (`git tag v0.0.1 && git push origin v0.0.1`, matching `AppVersion`; a mismatched tag fails the run)
  or by hand (Actions → Release → Run workflow). Builds the signed `.aab` (for Play) + APK + R8 mapping, kept as
  workflow artifacts for 30 days. The repo is public, so nothing is published as a GitHub Release.
- Secrets (repo → Settings → Secrets and variables → Actions → New repository secret):

  | Secret | Value |
  |---|---|
  | `KEYSTORE_BASE64` | `base64 -w0 ~/keystores/stamp-upload.jks` (the whole output, one line) |
  | `KEYSTORE_PASSWORD` | storePassword from `keystore.properties` |
  | `KEY_ALIAS` | `upload` |
  | `KEY_PASSWORD` | keyPassword from `keystore.properties` |
  | `ADMOB_APP_ID` / `ADMOB_REWARDED_ID` | real AdMob IDs (empty → Google's test IDs) |
  | `SUPPORT_EMAIL` / `PRIVACY_POLICY_URL` | optional: overrides the built-in support email / shows Privacy policy on Profile |
  | `GOOGLE_SERVICES_JSON` | the whole `app/google-services.json` from the Firebase console (without it: no crash reports / usage stats) |

  Without `KEYSTORE_BASE64` the release build is unsigned (a warning shows in the run). The job runs in the
  `release` environment: add yourself as a required reviewer there if you want to approve each run.
- Download the `.aab` from the run's Artifacts and upload it in Play Console (or keep uploading the one you
  build locally; both are signed with the same upload key).
