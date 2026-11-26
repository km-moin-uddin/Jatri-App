# Jatri (যাত্রী) — Air, Bus & Train Tickets

One Android app to search, compare, book and pay for **flight, bus and train tickets in Bangladesh**.
"Jatri" is a working name.

> **Status: Phase 1 (Foundation) is done.** The project structure, design system, navigation for all
> screens, Bangla/English and light/dark themes are in place. Search results, seat maps and payments
> show **sample data** until their phases are built (see [What is mocked](#what-is-mocked)).

| Bangla · light | English · light | Bangla · dark |
|---|---|---|
| ![](docs/screenshots/phase1/bn_light_01_home.png) | ![](docs/screenshots/phase1/en_light_01_home.png) | ![](docs/screenshots/phase1/bn_dark_01_home.png) |
| ![](docs/screenshots/phase1/bn_light_05_seat_map.png) | ![](docs/screenshots/phase1/en_light_04_flight_train_cards.png) | ![](docs/screenshots/phase1/bn_dark_06_review_pay.png) |

All 36 screenshots are in [`docs/screenshots/phase1`](docs/screenshots/phase1). They are rendered on the
JVM by Roborazzi, not on a phone.

## Repository layout

```
Jatri-App/
├── android/                 # Kotlin + Jetpack Compose app
│   ├── app/                 # MainActivity, splash, NavHost, bottom navigation, launcher icon
│   ├── build-logic/         # Gradle convention plugins (jatri.android.library, .feature, .hilt …)
│   ├── core/
│   │   ├── model/           # Pure-Kotlin domain types (Location, SearchQuery, Money in paisa …)
│   │   ├── common/          # BDT/Bangla-digit/date formatters, dispatchers, network monitor
│   │   ├── designsystem/    # Theme (colours, type, shapes, spacing), fonts, generic components
│   │   ├── ui/              # Travel UI: mode tabs, search card, pickers, result cards, seat map
│   │   ├── network/         # Retrofit/OkHttp/Kotlinx Serialization setup, API envelope
│   │   ├── database/        # Room (recent searches; tickets & travellers later)
│   │   ├── datastore/       # Settings: language, theme, onboarding
│   │   └── data/            # Repositories + seed locations + clearly-marked mock content
│   └── feature/             # auth, home, flight, bus, train, booking, payment,
│                            # trips, offers, account, notifications
├── backend/                 # FastAPI — starts in Phase 2
├── design/                  # tokens.json, app icon SVG/PNG sources, font licences
└── docs/screenshots/
```

## Requirements

- **Android Studio** (a version that supports AGP 9.5) or the command line with **JDK 17+**
- Android SDK **platform 37.1** and **build-tools 37** (Android Studio installs them when you sync)
- Min SDK 24 (Android 7.0), target SDK 37

Toolchain: Gradle 9.9 · AGP 9.5 · Kotlin 2.4 · Compose BOM 2026.11 · Hilt 2.60 · Room 2.8 · Navigation 2.10.

## Run it

Open the `android/` folder in Android Studio and press **Run**, or:

```bash
cd android
./gradlew :app:assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest           # unit tests + screenshot tests
./gradlew :app:recordRoborazziDebug   # re-render screenshots → app/build/outputs/roborazzi/
./gradlew :app:lintDebug              # lint for all modules (missing translations are errors)
./gradlew :app:assembleRelease        # R8-minified release APK (about 3.6 MB, unsigned)
```

The first launch shows onboarding (choose Bangla or English), then Home. Walk through it: pick
cities, then Search, a result, a seat or class, passenger details, Review & pay, and the success
screen. Every step in that flow can be reached.

## Phase 1 — what's built

- **Design system**: Material 3 light/dark colour schemes from the brand palette (dynamic colour off),
  Hind Siliguri for Bangla and Inter for English (bundled, OFL), a 4 dp spacing grid, and the
  12/16/20/24 dp corner radii.
- **Components**: CTA/primary/outlined buttons, card, status & tag chips, empty/error/loading states,
  shimmer skeletons, offline banner, `TicketShape` + `TicketCard`, `PriceSummaryBar`,
  `PassengerCounter`, top bar, text field, bottom sheet, `SearchCard`, `TravelModeTabs`,
  `CityPickerSheet`, `DatePickerSheet`, `PassengerClassSheet`, flight/bus/train `ResultCard`s, and `SeatMap`.
- **Navigation**: type-safe Navigation Compose routes for all 25 screens. The bottom bar has Home · My Trips ·
  Offers · Account.
- **Language**: everything is in `strings.xml` (`values` = English, `values-bn` = Bangla). Bangla is
  the default. Language can be switched in onboarding and in Account, using the per-app locale API.
  Bangla digits appear in prices, dates and counts.
- **Money** is stored as whole paisa (`Money(paisa: Long)`) and formatted with lakh grouping:
  `৳ 1,25,000` / `৳ ১,২৫,০০০`.
- **App icon**: an adaptive icon plus a monochrome version (Android 13+ themed icons), and the Android 12+ splash screen.
- **Local data**: recent searches saved in Room, settings in DataStore.
- **Tests**: formatters, phone validation, seed data, seat-selection rules (max 4, booked seats
  blocked), and 36 screenshot tests (Bangla light, English light, Bangla dark).

## What is mocked

| Area | Phase 1 behaviour | Becomes real in |
|---|---|---|
| Login / OTP | UI only. No SMS is sent and any 6-digit code works | Phase 2 |
| Locations | Bundled seed list (15 airports, 20 bus cities, 19 stations) | Phase 2 (`GET /locations`) |
| Bus results, seat map | `MockContent` with placeholder operators ("Sample Paribahan") | Phase 3 |
| Passenger form | Layout preview with disabled fields | Phase 3 |
| Review & pay, payment result | Sample price breakdown, working 10:00 hold timer, no real payment | Phase 4 |
| Flight results, fare rules | `MockContent` with placeholder airlines ("Sample Air") | Phase 5 |
| Train results | `MockContent` ("Sample Express") | Phase 6 |
| My Trips, ticket detail | One sample ticket, clearly labelled | Phase 7 |
| Offers, popular routes | Sample promo codes | Phase 8 |
| Hotline number | Placeholder `+8809600000000` in `HelpScreen` | Before release |

Every placeholder screen shows a "Coming in Phase N" banner, so sample data can't be mistaken for
real data.

## Design decisions worth knowing

- **Dark text on orange buttons.** White on `#FF7A45` is only 2.6:1 contrast and fails WCAG AA.
  Ink `#1A1F24` on orange is 6.5:1, so CTA labels use Ink.
- **Warning text** uses a darker amber (`#B45309`) on light backgrounds for the same reason.
- **Seat codes (A1, B2) stay in Latin letters and ASCII digits** in both languages, because they are
  printed on the bus that way.
- **Dark mode top bar** is deep teal `#0B4A50`, so it stays on-brand and separates the header from dark cards.
- **No backups**: `allowBackup=false` and data-extraction rules exclude everything, so tokens and
  tickets never leave the device.

---

## সংক্ষেপে (বাংলা)

**যাত্রী** একটি অ্যান্ড্রয়েড অ্যাপ। এতে বাংলাদেশের ফ্লাইট, বাস ও ট্রেনের টিকিট এক জায়গায় খোঁজা, তুলনা করা, বুক করা ও পেমেন্ট করা যাবে।

**এখনকার অবস্থা: Phase 1 (ভিত্তি) সম্পন্ন।**
- প্রজেক্টের কাঠামো আর মডিউলগুলো তৈরি হয়েছে।
- ডিজাইন সিস্টেম তৈরি: কালার, ফন্ট (হিন্দ শিলিগুড়ি ও ইন্টার) আর কম্পোনেন্ট।
- সব স্ক্রিনে navigation দিয়ে যাওয়া যায়।
- বাংলা ও ইংরেজি দুই ভাষাই আছে। বাংলা ডিফল্ট, আর বাংলায় সংখ্যাও বাংলায় দেখায়।
- লাইট ও ডার্ক থিম আছে।
- অ্যাপ আইকন আর স্প্ল্যাশ স্ক্রিন তৈরি।

সার্চের ফলাফল, সিট ম্যাপ, পেমেন্ট আর আমার যাত্রা এখন **নমুনা তথ্য** দেখায়। প্রতিটি স্ক্রিনে "Phase N-এ আসছে" লেখা থাকে।

**চালানোর নিয়ম:** Android Studio-তে `android/` ফোল্ডার খুলে Run চাপুন। অথবা কমান্ড লাইনে `cd android && ./gradlew :app:assembleDebug` চালান।

**পরের ধাপ:** Phase 2। এতে ফোন নম্বর ও OTP দিয়ে লগইন, প্রোফাইল, টোকেন সংরক্ষণ আর FastAPI ব্যাকএন্ডের শুরু হবে।
