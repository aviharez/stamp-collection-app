# CLAUDE.md — Stamp Collection App (Android)

Read this file fully at the start of every session. It is the source of truth for product, design and code rules.
If something here conflicts with a request in chat, ask before deviating.

---

## 1. Product in one paragraph

A camera-first app that turns everyday photos into collectible **postage stamps**. Users shoot (or import) a photo,
it becomes a stamp with perforated edges and an automatic **postmark** (city · country · date), and it gets stuck into
**albums** (collections). A calendar shows stamped days and a streak keeps the habit. The feel is a real paper stamp
album: kraft paper, airmail stripes, washi tape, handwritten notes. Target: worldwide users, **Android first**.

Why we win vs competitors (from Play Store review research):
- Best camera in the category: exposure, lens choice (0.5× / 1× / 2× / 50MP), correct orientation, full quality.
- Fair: own photos + gallery import are always free. **No forced ads, ever.**
- Unique: automatic postmark from location, album-page aesthetic.

App name: **Postmory** (`app_name` in `strings.xml`).

---

## 2. Tech stack (use latest stable versions via `gradle/libs.versions.toml`)

- Kotlin, Jetpack Compose, Material 3 (heavily themed — see §5; never ship default Material look)
- Single-activity, Navigation Compose
- Architecture: MVVM + unidirectional data flow (`UiState` data class + `ViewModel` + `StateFlow`)
- DI: Hilt
- Camera: **CameraX** (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` or `camera-compose`);
  use Camera2 interop only where CameraX lacks a control
- Storage: Room (stamps, collections), DataStore (settings, streak cache), images in app-private storage
- Images: Coil (thumbnails)
- Location: Fused Location Provider + `Geocoder` for reverse geocoding (city, country); must work if denied
- Billing: Google Play Billing Library (lifetime + yearly) — milestone 3
- Ads: Google AdMob **rewarded ads only** — milestone 3
- minSdk 26, targetSdk = latest, Kotlin DSL Gradle

Package structure:
```
app/src/main/java/<package>/
  ui/theme/        Color.kt, Type.kt, Shape.kt, Spacing.kt, Theme.kt
  ui/components/   Stamp.kt, PerforatedShape.kt, Postmark.kt, AirmailStripe.kt, WashiTape.kt,
                   PaperGrain.kt, HandNote.kt, AlbumCover.kt, StampNavBar.kt, buttons
  ui/navigation/   Routes.kt (type-safe routes), StampApp.kt (NavHost + StampNavBar + system bars)
  ui/screens/      home/, camera/, stamped/, album/, collections/, calendar/, paywall/, settings/
  data/            db/ (Room), repo/, location/, image/ (stamp rendering + export)
  domain/          models, use cases (CreateStamp, ExportStamp, StreakCalculator)
```

---

## 3. Features and scope

### V1 — launch (milestones 1–4)
- Camera opens fast; stamp frame overlay; tap to focus; exposure slider; lens switch (0.5×/1×/2×/50MP when available); zoom
- Correct orientation for portrait AND landscape stamps (known competitor bug — test both)
- Import from gallery (free)
- Stamp shapes: Portrait, Landscape, Square, Circle (+ Diamond later); 3–4 perforated edge styles
- Edit: crop, **rotate button** (not only pinch), title, caption ("write on the back")
- Automatic postmark: city · country · date; location permission asked once, optional; date-only fallback
- Collections (albums): create, rename, cover colour, category (Trips / Everyday / Pets / Other)
- Calendar of stamped days + streak
- Export at full resolution: PNG transparent or solid background
- Backup/restore to a single file; works fully offline
- Short first-run: "make your first stamp in 3 taps"

### V1.1 (after launch)
Home-screen widget (stamp of the day), scrapbook pages (stickers/text), send a stamp to a friend, streak reminders, filters.

### Later
World map "passport", printable sticker sheets (A4/Letter PDF), cloud sync, iOS, seasonal packs.

---

## 4. Monetization rules (do not violate)

- Free: camera, gallery import, basic shapes, **3 albums**, calendar, full-quality export with small watermark.
- Pro: unlimited albums, no watermark, all edges/filters, scrapbook, widgets, **no ads**.
  - Lifetime ~$14.99 (main offer), yearly ~$9.99. Prices come from Play Console, never hardcoded.
- Ads: **rewarded only, always optional, always user-initiated**:
  - remove watermark from one export · unlock a premium edge for 24h · +1 album
- NEVER: interstitials, app-open ads, banners, ads before an action, paywall on the user's own photos.

---

## 5. Design system — "Airmail"

Visual reference: screenshots of the approved mockups live in `/design/` (Home, Camera, Stamped, Album page,
Collections). Match them. When unsure, open the PNG and compare.

### Colours (`Color.kt`)
| Token | Hex | Use |
|---|---|---|
| Paper | `#F6F0E6` | default background (60%) |
| PaperWhite | `#FFFEFB` | stamp paper, cards, plates |
| Ink | `#18223F` | text, nav bar, camera background (30%) |
| AirmailRed | `#D7362B` | primary action, shutter, postmark (accent, ~10%) |
| Cobalt | `#2848C8` | celebration screen, airmail stripe |
| Kraft | `#E3CFAE` | album pages |
| KraftDark | `#C9A97C` | album covers |
| Sage | `#6E8F6A` | album covers / accents |
| Ember | `#FF6A4D` | streak flame / accents on dark |
| Surface2 | `#EBE1D0` | icon-button backgrounds on Paper |
Text hierarchy on Paper uses Ink at 100% / 80% / 64% opacity. On dark, PaperWhite at 100% / 80% / 56%.
Shadows are **tinted** (warm brown on paper `rgba(70,45,15,…)`, navy on blue), soft, never pure black.

### Typography (`Type.kt`) — bundle fonts in `res/font` (all OFL)
- **Fraunces Italic 600** — screen titles (40–56sp), album names (17–22sp), "Stamped!"
- **Plus Jakarta Sans 500/700** — all UI text: 12sp (captions/labels), 13–15sp (body/buttons)
- **Caveat 600** — handwritten notes, captions on album pages, counts (18–22sp)
Max 4 sizes per screen. Values bigger than labels.

### Spacing & shape
- 8-pt grid (4 allowed for tight gaps). Screen padding 24dp (16dp for full-bleed cards).
- Radii: buttons/pills fully rounded; cards 16dp; viewfinder 20dp; album cover 4dp spine side / 12dp open side.
- Touch targets ≥ 44dp (48dp preferred). Primary actions in the bottom third (thumb zone).

### Signature components (build these first, reuse everywhere)
- **Stamp**: image + paper border with **perforated edges**. Implement `PerforatedShape` as a Compose `Shape`
  that builds a `Path` = rounded rect minus circles along every edge (hole radius ~4dp, pitch ~16dp, holes centered
  on the edge so you see half-circles). Same path is reused for bitmap export. Slight random rotation (−6°..+6°) in
  album/collage contexts, 0° in editors.
- **Postmark**: double circle (outer dashed optional) + city (bold, letter-spaced) + date, plus 3–4 wavy cancellation
  lines to the right. AirmailRed at ~85% opacity on light and on the camera frame (as in design/camera.png). Random rotation −15..−8°.
- **AirmailStripe**: repeating diagonal stripes Red / White / Cobalt (or Ink) / White, 45°. Used as envelope border,
  top edge of camera & celebration screens, cover detail.
- **WashiTape**: semi-transparent striped strip, rotated, overlapping a stamp corner.
- **PaperGrain**: subtle noise overlay (pre-rendered tiled PNG, multiply, ~30% opacity) on kraft and cobalt surfaces.
- **HandNote**: Caveat text, slight rotation, Ink 72–100%.
- **StampNavBar** ("stamp sheet"): grained Kraft strip (card radius, warm shadow) holding 4 mini perforated stamps
  (Album, Calendar, Map, Profile) with Caveat lowercase labels, always visible. Active stamp turns AirmailRed with a
  PaperWhite icon, tilts −7° and lifts (×1.12); inactive stamps are PaperWhite with Ink icons, resting tilt ±1.5°.
  Squash on press + haptic tick on select; snaps when "reduce motion" is on.
- **AlbumCover**: cloth-textured coloured cover, darker 12dp spine, cream name plate in Fraunces italic.

### Screens (see `/design/*.png`)
1. **Home** — date + streak chip; big italic title "Your stamp album"; a kraft album page with the latest stamps
   (tape, postmark, hand notes); airmail-envelope card "Today's stamp is waiting" + red "Stamp it" button; StampNavBar.
   Empty state when nothing stamped today; if already stamped, the card shows today's stamp instead.
2. **Camera** — Ink background, airmail stripe top; close · "City, date" · flash; viewfinder with dimmed outside and
   perforated frame for the chosen shape; live postmark preview; vertical exposure slider; zoom chips; shape picker
   (4 stamp-shaped tiles); gallery (mini stamp) · red postmark-style shutter · flip.
3. **Stamped!** (peak moment) — Cobalt background + grain; stamp drops in, postmark "slams" (scale 1.4→1 + slight
   shake + haptic), tiny stamps fly out; "Stamped!" in Fraunces; "No. 129 in your Japan trip album"; streak chip;
   handwritten caption field; "Share" (outline) + "Stick in album" (white, primary).
4. **Album page** (collection detail) — kraft page, 3-column stamps with tilt, tape, Caveat dates, an empty "next
   stamp…" slot; Map + "Share this page".
5. **Collections list** — filter chips (All/Trips/Everyday/Pets); 2-column AlbumCovers with Caveat counts; dashed
   "New album".
6. Calendar, Paywall, Settings — design in the same language when we get there (ask me to review first).

Motion: springy but short (200–350ms). Haptics on shutter and postmark. Respect "reduce motion".

---

## 6. Camera & image rules

- Request camera permission with a friendly pre-prompt; handle "denied" gracefully (gallery still works).
- Capture at max resolution; crop to the stamp aspect **after** capture; store original + rendered stamp.
- Read EXIF orientation and apply it — never let landscape stamps come out rotated.
- Expose lens choice from available cameras/zoom ratios; hide 50MP chip if the device does not support it.
- Exposure via `CameraControl.setExposureCompensationIndex`; show value (e.g. −0.3).
- Rendered stamp export: long side ≥ 1600px, PNG, transparent outside perforation.
- Test on at least: one Samsung, one Xiaomi/Redmi, one Pixel/emulator (emulator camera is fake — UI only).

---

## 7. Working agreement (how Claude works in this repo)

- Work milestone by milestone (§8). Small commits with clear messages. Don't start the next milestone without my OK.
- After each screen/component:
  1. `./gradlew assembleDebug` — fix all errors and warnings you introduced.
  2. `./gradlew installDebug` with my phone connected (`adb devices`).
  3. Screenshot: `adb exec-out screencap -p > /tmp/screen.png`, look at it, compare with `/design/*.png`, fix gaps.
  4. Tell me exactly what to test on the real phone (camera features need my hands).
- Never hardcode colours, sizes or strings in screens — use theme tokens and `strings.xml`.
- Previews: every component has a `@Preview` (light paper background).
- No new dependencies without a one-line reason. No analytics/tracking SDKs without asking.
- Keep `CLAUDE.md` updated when a decision changes (add a short "Decisions log" entry at the bottom).

Useful commands:
```
./gradlew assembleDebug
./gradlew installDebug
./gradlew lint
adb devices
adb exec-out screencap -p > /tmp/screen.png
adb logcat -s AndroidRuntime:E
```

---

## 8. Milestones

1. **Foundation + Camera (week 1)** — theme tokens, fonts, signature components with previews (Stamp/PerforatedShape,
   Postmark, AirmailStripe, PaperGrain, StampNavBar); navigation skeleton; Camera screen with live preview, frame
   overlay, focus, exposure, zoom/lens, shape picker, capture → orientation-correct cropped stamp → placeholder
   Stamped screen.
2. **Albums + data (week 2)** — Room models (Stamp, Collection), save flow, Stamped! screen with animation,
   Album page, Collections list, Home, gallery import, postmark with reverse geocoding, calendar + streak.
3. **Export + money (week 3)** — PNG export & share, watermark, backup/restore, Play Billing (lifetime + yearly),
   paywall screen, AdMob rewarded (opt-in only), first-run flow.
4. **Polish + release (week 4)** — edge cases, permissions, performance (no jank on mid-range phones), accessibility
   (content descriptions, 48dp targets, font scaling), store listing assets, closed testing (12 testers / 14 days).

---

## Decisions log
- 2026-11-04: Android first, Kotlin + Compose + CameraX. Airmail design direction approved. Rewarded ads only.
- 2026-11-04: Light-only theme (no dark mode). The app is a paper album; Camera (Ink) and Stamped! (Cobalt) are drawn dark explicitly with textOnDark* tokens.
- 2026-11-04: Default perforation is Classic (hole radius 3.5dp, pitch 12dp), measured from the mockups; Fine / Bold / Scallop are the other edge styles.
- 2026-11-04: Nav bar changed from the Ink pill (InkNavBar) to the "stamp sheet" StampNavBar (concept E of 9): more tactile, labels always visible.
- 2026-11-04: Camera postmark is AirmailRed (as in design/camera.png), not PaperWhite: white disappears where it overlaps the white stamp border, and red reads as real ink.
- 2026-11-04: Front camera has a "mirror selfie" toggle (default on = saved as the preview shows it; off = true view, preview un-mirrored to match). Kept in memory for now; persisted with DataStore in milestone 2.
- 2026-11-04: Room 3 (androidx.room3) with the platform AndroidSQLiteDriver (no bundled native SQLite). Schemas exported to app/schemas.
- 2026-11-04: Every capture is saved immediately into the last-used album (first ever → auto-created "Everyday" album). "Retake" deletes it; leaving Stamped! keeps it.
- 2026-11-04: A stamp's calendar day is fixed at capture in the time zone then. Streak: today not stamped yet doesn't break it; a whole missed day does. Computed from stamp days (no separate cache needed yet).
- 2026-11-04: Postmark place = coarse fused location (play-services-location; LocationManager last-known fallback) + platform Geocoder, cached 10 min. Asked once on the camera with an in-app card ("Not now" = never asked there again; date-only). Postmark reads "CITY / dd MMM yy · CC"; header "City · d MMM". Note for the store privacy form: Geocoder sends coordinates to Google's geocoding service.
- 2026-11-04: Stamped! choreography: PNG preloaded (≤800ms) → stamp drops (spring) → postmark slams 1.4→1 (140ms) + LongPress haptic + small shake → tiny stamps fly out. Plays once per stamp; snaps on reduce motion. Album is changed by tapping "No. N in your … album" (picker sheet + "New album" form). Caption saves debounced in an app-scoped coroutine. Share is shown disabled until export (M3); the mockup's "Edit" button waits for the edit screen.
- 2026-11-04: Album pages hold 9 stamps (3 × 3), filled oldest first; the last page always keeps a "next stamp…" slot (tap = camera, and that capture goes into this album). The album opens on its newest page; swipe for older ones. One postmark per page, waves only over a neighbouring stamp.
- 2026-11-04: Home's kraft page shows the 4 newest stamps of the album the newest stamp is in ("ALBUM · P.N", tap = open it; "All albums" = Collections). Once today is stamped, the envelope card shows today's stamp + a camera button for another. Streak chip on Home is the compact Ink pill ("🔥 12 days").
- 2026-11-04: Collections sorted by most recently stamped; filters All / Trips / Everyday / Pets (+ Other once used). Albums are renamed / recoloured / recategorised from "…" on the album page (same form as "New album"). Map and "Share this page" are shown disabled until milestones 5 / 3; tapping a stamp does nothing until the stamp detail/edit screen exists.
- 2026-11-04: Gallery import uses the system photo picker (PickVisualMedia, no storage permission; Play-services backport declared in the manifest). The photo is copied into app storage, then framed on an Ink "import" screen that reuses the camera frame, shape picker and red shutter: pinch/drag to fit (photo always covers the frame), rotate in quarter turns, shape preselected from the photo's aspect. Same save flow + Stamped! ("Retake" becomes "Pick another").
- 2026-11-04: An imported stamp keeps the photo's own date (EXIF, else the gallery's date taken, else today; future dates → today): its postmark, calendar day and place in the album (albums are chronological, so an old photo can be "No. 1"). It shows on that day's calendar but only counts for the streak if made that same day (see 2026-11-04 streak entry). Place = the photo's EXIF GPS when present (the system picker usually strips it), else the current place for a photo from today (only if location is already allowed), else date-only.
- 2026-11-04: Whole app locked to portrait (manifest) until a real landscape/tablet layout exists (milestone 4 polish). Landscape squashed the album page; it also exposed a 0-size draw loop (fixed).
- 2026-11-04: Calendar = concept A "stamp sheet" + B's big streak number (3 concepts reviewed). The month is a kraft sheet: a stamped day shows its newest stamp as a mini stamp (real shape) with a Caveat date and "×N" badge; empty past days are dashed slots; today gets a red postmark ring (red dashed slot until stamped). Week starts per the phone's region. Arrows or sideways swipe change month (from the first stamp's month up to now); tapping a neighbouring month's day opens that month. Under the sheet: the selected day's stamps (tap = open its album) or "Stamp it" for today. Streak sub-line: start / keep it going today / come back tomorrow for day 2 / your best yet / best N.
- 2026-11-04: Streak counts only stamps made on their own day (camera, or an import of a photo from today): importing an old photo can't backfill a missed day. Stored as stamps.created_day (DB v2; existing stamps migrated with created_day = day so no streak changed).
- 2026-11-04: Tapping the nav-bar tab you are already on returns to that tab's first screen (Collections → Home).
- 2026-11-04: Export = the stamp PNG at full resolution + its postmark (same placement as Stamped!, drawn by the shared `drawPostmark`) on Transparent or Paper (Paper adds a mat + warm shadow); postmark can be turned off. Free tier adds a small PaperWhite "app name" label in the picture's bottom-start corner (top-centre on a Circle); Pro (ProStatus, DataStore cache that billing will write) removes it. Share sheet shows a live preview of the exact export; "Share" = system chooser via FileProvider (cache/exports), "Save" = MediaStore Pictures/<app name> (storage permission only on Android 8–9). Opened from Stamped! for now; album "Share this page" stays disabled until page export.
- 2026-11-04: Paywall = concept B "stamp sheet" (3 reviewed) + A's "Best value" tag and full perk labels. Perks are stamps on a kraft sheet; only shipped perks are real stamps (unlimited albums, no watermark), the rest are dashed "soon" slots ("Pro includes them") so we never sell what doesn't exist. The reason it opened lights its perk red and sets the overline. Plans are two big stamps picked like the nav bar (Lifetime preselected). "No ads" is not listed as a perk (free users never get forced ads).
- 2026-11-04: The paywall only opens when the user asks for something Pro covers: 4th album (Collections / album picker; free limit = ProRules.FreeAlbumLimit = 3, existing extra albums are kept), "Remove with Pro" in the share sheet, Profile "Get Pro" (premium edges later). Never on launch or after a stamp.
- 2026-11-04: Play Billing 9.1 (plain `billing`, callbacks wrapped in coroutines). Products: `pro_lifetime` (one-time) and `pro_yearly` (subscription, base plan, no free trial at launch). Prices only from Play. Purchases acknowledged in-app (no backend yet); ProStatus caches Pro in DataStore and is refreshed from Play at app start (restores on a new phone, drops refunds/expiry) and by "Restore". Debug builds have a "pretend Pro" switch on Profile, stored apart from the billing flag.
- 2026-11-04: Rewarded ads use the same row pattern as the share sheet's watermark row (shown next to "Remove with Pro" / "+1 album" / "try 24h"); they arrive with AdMob.
- 2026-11-04: AdMob rewarded ads (play-services-ads 25.5 + UMP 4.0 consent). Nothing ad-related runs at launch: the consent form (EEA/UK/US states) is shown the first time the user taps a "short ad" button, and the SDK starts after it. Two offers, each an outline chip beside the filled Pro option: share sheet "Once · short ad" (the watermark is off for that stamp while Stamped! is open, both Save and Share) and, on the album-limit paywall only, "+1 album · short ad" (a permanent extra slot, ProStatus.bonusAlbums; returns to where "New album" was tapped with the form open). No ad offers for Pro users. Max ad rating PG. Debug builds always use Google's test IDs; release reads admobAppId / admobRewardedId gradle properties. "Ad privacy choices" shows on Profile when UMP requires it. Premium-edge 24h trial waits for premium edges. Play data-safety form must declare the Mobile Ads SDK (device/advertising ID, app interactions, diagnostics).
- 2026-11-04: Backup/restore = one plain ZIP the user saves anywhere via the system file picker (no new dependency, no cloud): `backup.json` (albums, stamps, mirror-selfie, bonus album slots; format version 1) + every stamp's rendered PNG and original photo, stored uncompressed. Restore merges and never deletes: stamps already on the phone (same id) are skipped, albums with the same name + creation time are reused, empty albums come back too, so restoring twice is harmless and a failed restore can just be retried. Restored albums ignore the free 3-album limit (it's the user's own photos). Pro is not in the file (Play restores it); bonus slots restore as max(phone, file), capped at 50. Only entries named exactly `rendered/<uuid>.png` / `originals/<uuid>.<ext>` are read. Lives on Profile for now ("Back up to a file" / "Restore from a file" + last-backup note) until the Settings design is reviewed.
- 2026-11-04: First run = one cobalt welcome screen (Stamped!'s language: grain, airmail stripe, white primary pill), shown only on a fresh install (DataStore `onboarded` unset and no stamps; anyone with stamps is marked onboarded silently). Three example stamps (flat PreviewArt, not photos) with today's "HELLO" postmark, "Turn today into a stamp", the 3 taps as numbered mini stamps (tap below · snap it · stick it in), "Make my first stamp" (white) + "From my photos" (outline) stacked, "Skip" top-right. Any choice marks it done and puts Home underneath, so back from the camera lands Home. Coming from the welcome, the camera asks for the system permission straight away (the welcome is the pre-prompt); if denied, the usual in-app prompt with "Use a photo instead" shows. The location card on the camera doesn't block the shutter, so the first stamp stays 3 taps plus the system permission dialog.
- 2026-11-04: Album "Share this page" = the current kraft page as a 1440 × 1920 PNG: the on-screen AlbumSheet composable (no "next stamp…" slot) under a "PAGE N OF M" overline, the album name and counts, on Paper. Drawn by Compose off screen at a fixed design width (400dp, font scale 1) into a GraphicsLayer, after every stamp image has loaded (ImageLoads), so it always matches the screen. Free tier: a small letter-spaced app name at the foot; Pro or "Once · short ad" (for this album while it's open) removes it. Same sheet pattern as the stamp export (preview, watermark row, Save/Share). Disabled on an empty page.
- 2026-11-04: Stamp detail (tap a stamp on an album page): Paper screen, stamp straight (no tilt) and as big as fits with its postmark over the bottom-end corner; "NO. N IN <ALBUM>" header (tap = move, same picker as Stamped!); title in Fraunces written straight on the paper (shows on album pages instead of the city); date · place; "write on the back" caption; Reframe (outline) + Share (red, the stamp export sheet). "…" = Move to another album / Delete stamp (asks again inside the sheet, no system dialog; deletes row + both files). Reframe reuses the import framing screen on the stamp's original (shape, quarter turns, crop; date/place/words kept); the new PNG gets a versioned file name so image caches can't show the old one; hidden when the original is missing.
- 2026-11-04: Milestone 4 accessibility pass, checked on every screen at font scale 1.0 and 2.0 (largest) with a tree dump (every tappable thing ≥ 48dp and named). Labels on fixed-size controls (pill buttons, chips, shape tiles, plan/perk stamps, album name plates) use `FitText`: shrinks only as far as needed (to 60% of the style size) to fit without clipping or breaking a word, then "…". Big titles with a side note use `TitleWithNote` (FlowRow: the note drops below instead of squeezing the title). Option rows in share sheets wrap their chips under the label. Exports stay at font scale 1 by design. Placeholder note is "coming soon…" (Map); Profile has none.
- 2026-11-04: Milestone 4 edge cases, tested on the emulator by killing the backgrounded process on each screen, filling the disk, revoking permissions and deleting stamp files. Android closing the app in the background keeps: camera shape, the import screen's photo copy (no second copy) + shape + quarter turns + pinch/drag, share-sheet background/postmark, a watched "Once · short ad" unlock (stamp and album page), the calendar month. Before a capture/import/save we need ≥ 64 MB allocatable (StorageManager, counts clearable cache); otherwise "Your phone is full…" and nothing is half-saved (failed captures/renders delete their files). Background DB writes that hit a full disk are logged, not a crash. On start, image files no stamp uses and older than a day are deleted (left by a capture/import Android interrupted). Camera permission: "Don't allow" twice now switches to "Open settings" right away (the check was stale); prompt title is "Let's make a stamp" (people with stamps see it too). A stamp whose PNG is missing shows a faded photo mark and hides Share (Reframe was already hidden without the original). Location off or denied → date-only postmark, as before.
- 2026-11-04: Milestone 4 performance. Release now runs R8 (isMinifyEnabled + isShrinkResources; the classic flags, because the baseline profile plugin only turns isMinifyEnabled off for its nonMinifiedRelease, so AGP 9's `optimization {}` block gave an obfuscated, useless profile). Release APK 5.3 MB. Keep rules live in app/proguard-rules.pro (one so far: play-services-ads' old WorkManager builds its Room database by reflection; R8 full mode dropped the constructor and release crashed on launch). A `:baselineprofile` test module (androidx.baselineprofile + macrobenchmark + uiautomator, test-only) records Home → Collections → album pages → stamp detail → Calendar → camera into app/src/release/generated/baselineProfiles (also the startup profile); `profileinstaller` ships it for non-Play installs. Regenerate with `./gradlew :app:generateBaselineProfile` on an emulator with stamps restored (it uninstalls the app at the end). Measured on a Xiaomi 15T (60 Hz, release build with profile): cold start ≈ 270–300 ms to first frame; browsing 0.7 % janky frames (P99 21–31 ms; without the profile 1.2 %, P99 31–48 ms); album page swipes 0 janky (P50 9–10 ms); calendar/collections smooth when warm. No code change needed; re-check on a real mid-range phone during closed testing. HyperOS hides app trace sections from macrobenchmark's FrameTimingMetric, so frame numbers there come from `dumpsys gfxinfo`.
- 2026-11-04: Judge smoothness on a release-like build (`./gradlew :app:installBenchmarkRelease`: same debug key, so it installs over the debug app and keeps its data), never on debug. Album → Calendar on the Xiaomi: debug freezes ~150–170 ms on the first frame (≈38 text layouts + composition run uncompiled), benchmarkRelease 1 janky frame in 10 switches (0 missed vsync, GPU 7–9 ms). Framestats on HyperOS flag every frame 32, so never filter frames by flag.
- 2026-11-05: V1.1 home-screen widgets (Glance 1.2): all three reviewed looks ship as separate widgets: "Stamp of the day" (loose stamp, 2×2), "Airmail envelope" (4×2) and "Album strip" (kraft, last 2 stamped days + today, 4×2). Each widget is one picture drawn by WidgetArt with the app's own fonts, stamps and postmark at the widget's exact size (layouts in a 156dp-square or 336×156 wide frame, scaled to fit, background fills the whole widget; scale drops if the bitmap would pass the RemoteViews memory cap). Waiting = dashed red slot + camera; stamped = today's stamp with its postmark + streak. Free users get the real widget faded with a red "PRO" tag; tapping opens the paywall with reason Widget (perk lit, overline "Pro · home-screen widgets"). No ad offer for widgets. Pro tap: waiting → camera; stamped → today's stamp detail on top of its album (Back = album, then Home). Links are `stampwidget://` intent data; MainActivity is singleTop and ignores links while the welcome screen shows. Updates: the app process watches stamps/streak/Pro and updates all widgets (debounced 400 ms), plus an inexact midnight alarm (re-armed by every widget update, so it survives reboots) to flip "today". Picker previews are real renders of sample art; debug gallery shows every state and can pin each widget. Release needs a second WorkManager 2.7 keep rule (InputMerger constructor) or widgets stay on the loading layout.
- 2026-11-05: V1.1 streak reminder, free for everyone (not a Pro perk; it keeps people coming back). Off until turned on from Profile ("Remind me to stamp"); that tap is the pre-prompt, and Android 13+'s notification permission is asked only then. Time = one of 6–10 PM on the hour (chips, default 8 PM; no Material time picker), shown in the phone's 12/24-hour style. One inexact alarm (setAndAllowWhileIdle, no exact-alarm permission, may come up to ~1 h late); when it rings we check again: stays quiet if today is stamped, if the ring slipped past midnight, or if the reminder was turned off. Wording: "Keep your N-day streak going / One photo before midnight keeps it alive." or, with no streak, "Today's stamp is waiting / One photo turns today into a stamp." Tap = camera (same link as the widget). The note is pulled as soon as today gets a stamp. Re-armed after every ring, at app start, after a reboot, an app update and a clock/zone change (RECEIVE_BOOT_COMPLETED). If notifications are blocked, Profile says so in red with "Open settings"; coming back allowed finishes turning it on. Debug gallery can show the note and ring it now.
- 2026-11-05: V1.1 photo filters: 9 colour-matrix looks (Original, Postcard, Engraved free; Sepia, Vivid, Faded, Red/Blue/Green ink Pro). They tint only the photo, never the paper border, and the same matrix draws the renderer and the Compose thumbnails. Placement A + B (3 concepts reviewed): a row of mini stamps on Stamped! under the stamp, and a "Filter" button on the stamp page that opens a kraft tray (picks save at once; "Done" closes it). Non-destructive: each stamp stores its crop (`render_spec`: window, quarter turns, frame size) and its filter (DB v3), so a filter change re-draws from the original into a new versioned PNG; Reframe keeps the filter. Stamps made before v3 have no crop saved and show no filters until they are reframed once. A free user can try a Pro filter on: it is drawn into a cache file only (never saved), with "Keep X with Pro" opening the paywall (reason Filters, perk "All 9 photo filters" now a real stamp; "Every edge style" stays a soon slot). Getting Pro while trying one on keeps it. No ad offer for filters. No live filters in the camera (the preview surface can't take them below API 31). Backup carries filter + crop (format still 1; missing = Original / no crop).
- 2026-11-05: V1.1 send a stamp to a friend = a "Send as" row in the stamp share sheet (Stamped! and stamp page): Stamp (the plain export, as before) or one of three postcard looks, all offered (3 concepts reviewed): Envelope (airmail border, cream paper, stamp + postmark top-right, "To" line, message; 1800 × 1200), Postcard (back of a picture postcard: message left, divider, stamp + "To" + address lines right; 1800 × 1200) and Card (kraft "Greetings from <city>" or "A stamp for you" + date, big taped stamp, note card; 1440 × 1920 for chats and stories). Optional "To" (40 chars) and message (140), prefilled from the back note, else the title; written only into the picture, never saved to the stamp. Drawn by Compose off screen at a fixed design width (same `OffscreenCapture` as the album page export, waits for the stamp PNG). Same watermark rules ("made with <app>"; Pro or "Once · short ad" removes it) and Save/Share buttons. No backend: it goes out through the system share sheet.
- 2026-11-05: Postcard looks: Envelope is free, Postcard and Card are Pro. Free users see a red "PRO" tag on those chips (as on Pro filters) and can still pick one, see their stamp in it and type To/message; the watermark row becomes "this look comes with Pro · the Envelope is free" and Save/Share becomes one red "Send the <look> with Pro" (paywall reason Postcards, perk "Postcard & card looks", a real stamp). No ad offer unlocks a Pro look ("Once · short ad" still only removes the watermark from the free looks). Perk order keeps the "soon" slots last.
- 2026-11-05: V1.1 scrapbook pages = concept B started from C (3 reviewed). "Scrapbook page" pill on the album page (replaces the Map pill until the passport map; red PRO tag for free users) opens an editor: a new page starts from a layout (Diary, Collage, Postcard, Big one) filled with the album's newest stamps, then every piece can be dragged, pinched, twisted, or sized/turned/brought to front/removed from an ink tool pill; a tray adds stamps (this album), 11 paper stickers (par avion, rubber stamps, wax seal, ticket, heart, star, postmark, 3 tapes) and writing (Caveat note, Fraunces title, typed live). Pieces are stored as page fractions + scale + rotation on a 360dp-wide 3 : 4 page (max 40), so the page looks the same on every screen and in the export; new pieces land on the emptiest spot. Pages live among the album's grid pages, after the page holding the newest stamp taken before the page was made; tap = edit, delete only from inside the editor (asks again in place). Leaving with changes asks "Leave without saving?". Pro perk with try-on: a free user can build a whole page; "Save" opens the paywall (reason Scrapbook, perk now a real stamp) and the draft stays, saved as soon as Pro arrives. The draft survives Android closing the app. "Share this page" exports a scrapbook page the same way as a grid page (1440 × 1920, same watermark rules). Backup carries the pages (scrap_pages, DB v4). No ad offer.
- 2026-11-05: Edge styles: Classic is free; Fine, Bold and Scallop are Pro (Diamond later, separately). Picked with the filters: the picker on Stamped! and the stamp page's tray (button now "Look") have "filters / edges" tabs; edges show as mini stamps of the photo in the chosen filter. Re-drawn from the original like filters (a stamp's look = filter + edge). The last saved edge is what the camera frame and gallery import start with (Classic if it can't be used any more). A free user can try a Pro edge on; "Keep the X edge with Pro" opens the paywall (reason Edges, perk "Every edge style" now a real stamp), and when only the edge is locked an outline "24h · short ad" chip unlocks that edge for 24 hours (per edge, DataStore `edge_trial_ends`, not in backups) and saves it at once; stamps keep their edge after the day ends. A Pro filter + Pro edge together say "Keep this look with Pro" and get no ad offer. The paywall's "dashed ones are on their way" note hides while no perk is a "soon" slot.
- 2026-11-05: Profile/Settings = concept A's "stamp passport" on top + B's perforated stamp sections below (3 concepts reviewed: Passport, Stamp sheet, Logbook). Passport (kraft): newest stamp (dashed "your first stamp goes here" slot when empty), Stamps, Albums · places (distinct city + country), Best streak, "since" first stamped day. Free users get the Pro airmail envelope (Get Pro → paywall reason Profile; "Already bought it? Restore" asks Play in place); Pro users get a red PRO / THANK YOU postmark on the passport instead. Sections are PaperWhite perforated stamps with a slight tilt: Streak reminder (now an Airmail switch row + time chips), Backup, Camera (Mirror selfies moved here from memory-only; new "Place on postmarks"). Footer: Rate the app (Play Store app, else its web page), Ad privacy choices (only when UMP asks), version; debug builds add the gallery + pretend Pro. New `SwitchRow`/`StampSwitch` (Ink pill, red knob when on, whole row toggles, Role.Switch). Feedback and Privacy wait for a support email and a privacy-policy URL; licences later.
- 2026-11-05: "Place on postmarks" (DataStore `postmark_place`, default on). On needs location: switching it on asks Android's permission right there; refused for good → red note + "Open settings", and coming back allowed finishes turning it on. Off = date-only postmarks everywhere (camera and gallery import, the photo's own GPS too) and the camera never shows its location card. Existing stamps keep their place.
- 2026-11-05: Map tab ("passport", moved up from Later) = concept A + B's visa (3 concepts reviewed: kraft world map, passport pages, airmail arcs; arcs may come back as a "trips" view). "Your world": a kraft card with the whole world (Natural Earth 1:50m admin-0, public domain, simplified by tools/world_map.py into a 204 KB binary asset; Equal Earth projection; latitudes −56..84, no Antarctica; fully offline, no map SDK). Countries with stamps are postmark red; each located place is an ink dot; the 12 busiest countries get their newest stamp taped on as a pin, resting on the top edge of the land it marks (or hanging below it when there's no room above). Under it, Countries / Places lists (mini stamp, name, ×N), then a note on how many stamps have no place. Only stamps whose postmark has a country count; the place is its city (coordinates averaged), "Somewhere in X" without one. Country names come from the phone's language, else the postmark's own. Tapping a country or place opens the country page: a close-up of the land holding its places (all of it if none has coordinates), its passport visa (ink and round/square picked from the country code; words at font scale 1, read out as one sentence) stamped over the card's corner, then each place with its stamps (tap = stamp detail). The country page hides the nav bar like other detail pages (album page, stamp page); Back returns to the map. Empty: a note + "Stamp it" (and a hint when "Place on postmarks" is off). Free for everyone (it's the user's own photos). The album page's Map pill stays replaced by Scrapbook.
- 2026-11-05: Map share = World postcard (A) + Passport page (B) as two looks (3 concepts reviewed; the square "postmarked map" was dropped). A share button on the Map header (only when a country is on the map) opens a sheet like the album page's: World / Passport chips, live preview, watermark row, Save/Share. Both are 1440 × 1920 drawn off screen at a 400dp design width. World: Paper + airmail stripe, "MY WORLD · year(s)", "Stamped in N countries", the same kraft WorldCard as the tab (waits for the pin stamps), countries / places / stamps as big numbers, up to 8 countries handwritten "Japan ×14" + "+N more". Passport: kraft page, "N countries", "N places · N stamps", the countries' visas (the same VisaStamp as the country page) two per row, up to 6 + "+N more"; a lone first visa is drawn big. Free for everyone (it's the user's own map) with the small "made with <app>" mark; Pro or "Once · short ad" (while the Map tab is open) removes it, paywall reason Watermark.
- 2026-11-05: Printable sticker sheets (moved up from Later) = look A "Grid" + C "Mix" (3 concepts reviewed). Opened from "Print stickers" at the top of the album page's "…" sheet (now "Album options"; red PRO tag for free users). Paper screen: look chips (Grid / Mix), size (Grid only: Small 28 / Medium 38 / Large 52 mm long side), paper (A4 / Letter, Letter preselected in US, CA, MX, PH and parts of Latin America), "Postmarks on the stamps" switch, then a live preview of every page. Grid = one size in even square cells; Mix = per page one 60 mm stamp, two 42 mm, then 30 mm, tilted −3..3°, a paper sticker (par avion, heart, postmark, star, received) after every 4 stamps and in gaps beside the stamps (never rows of stickers below them). Every piece has a dashed cut line 2 mm out (round for Circle stamps); header album name + "cut along the dashed lines", page number at the foot; 12 mm side margins. Laid out in mm (StickerSheets, unit-tested: each stamp once, inside the margins, no overlapping cut lines) and drawn by Compose off screen at 1 mm = 4 dp, captured one page at a time at 200 dpi into a platform PdfDocument (no new dependency), then the system print dialog (its "Save as PDF" printer saves it). Only stamps whose PNG exists are printed, in album order. Pro perk with try-on: anyone can see every look and page; "Print with Pro" opens the paywall (reason Stickers, perk "Printable sticker sheets", a real stamp). No ad offer.
- 2026-11-05: Place names. The postmark "city" is the geocoder's locality, except in Indonesia, where the locality is the kecamatan ("Kelapa Dua") and the city people say is the kota / kabupaten ("Tangerang"): there it's the subAdminArea with "Kabupaten" / "Kota (Administrasi)" / "Regency" / "City" stripped (`PostmarkCity`, unit-tested; add more countries to `CityIsSubAdmin` when testers report them). Any place can be renamed from its country page (pencil beside the place → Paper sheet): it changes the city on all of that place's stamps (postmarks, map, exports, widgets); a name another place already has merges them; empty = "Somewhere in <country>". Coordinates are kept.
- 2026-11-05: While a photo becomes a stamp (camera capture + render + save, or the import screen's save), a `StampingOverlay` covers the screen: Ink scrim (72%), a dashed red postmark ring turning (still and tilted with reduce motion) over a thin inner ring, Caveat "making your stamp…" (polite live region). It appears 200 ms after the shutter (quick saves don't flicker, the white flash plays first), fades in/out, and swallows taps; Back still leaves (the half-made stamp is deleted, as before).
- 2026-11-05: Release prep before the Play Console account exists. Profile footer gains "Credits" (fonts, Natural Earth, libraries; each opens its full licence text from assets/licenses, reflowed for phone width; Google Play services / Billing are listed under Google's terms without a text). "Send feedback" (mailto) and "Privacy policy" (browser) only show once the `supportEmail` / `privacyPolicyUrl` gradle properties are set (resValues, empty by default). Release signing reads a git-ignored `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword); without it release builds stay unsigned. The upload key lives outside the repo (~/keystores/stamp-upload.jks, PKCS12, alias `upload`) and is meant for Play App Signing. Privacy policy draft in docs/privacy.html (GitHub Pages from /docs); Play Console answers and store-listing drafts in docs/play-console.md.
- 2026-11-05: App name = **Postmory** (post + memory; no app or brand found with it, while the category is full of "Stamp-" names). Store title "Postmory: Photo Stamp Album". Only `app_name`, the backup file name note and the docs changed; code identifiers (StampAppTheme, package com.aviharez.stamp) stay.
- 2026-11-05: App icon = concept "A + F" (4 rounds reviewed): an airmail-striped envelope on Cobalt carrying a white perforated photo stamp (sun + hills), cancelled by an Ink postmark whose waves run across the envelope. Drawn as SVG by tools/icon (icons*.py, `tovd.py` converts to VectorDrawable): ic_launcher_background (Cobalt), _foreground, _monochrome (themed icons: outlines + solid stamp). Store icon store/icon-512.png = the middle 80/108 of the canvas, no alpha.
- 2026-11-05: Store graphics live in `store/` (icon-512, feature graphic, 8 captioned 1080 × 1920 screenshots), made from a sample backup of Unsplash photos (tools/store/seedgen.py → restore on the emulator → screencap → tools/store/compose.py). Captions in Fraunces + Caveat on Paper / Ink / Cobalt / Kraft frames with the airmail stripe. Pictures only show well-known cities (Kyoto, Paris, Lisbon, Bergen, Jakarta, Bandung…), never a home town.
- 2026-11-05: Translations: Indonesian (`values-in`, casual "kamu"), Spanish (`values-es`), Portuguese (`values-pt`, Brazilian wording, also serves pt-PT), French (`values-fr`, "tu"), German (`values-de`, "du"), Japanese (`values-ja`). Machine-quality drafts by Claude: closed testers who speak them should read them. Every visible string is translated, including the date patterns (no longer translatable="false": e.g. de "d. MMM", ja "M月d日(E)", postmarks "yy.MM.dd" in ja); `app_name` and links stay untranslatable. Plurals follow each language's lint rules (es/pt/fr add "many" = "other"; word-only plurals in fr/pt carry tools:ignore ImpliedQuantity). Per-app language on Android 13+ via `generateLocaleConfig` + res/resources.properties (`unqualifiedResLocale=en-US`). The bundled fonts have no CJK glyphs, so Japanese falls back to the system font (checked: titles, calendar, nav bar fit). Data the app created in English before (the auto "Everyday" album) keeps its name. Store listing name/short description/release notes per language in docs/store-listing-translations.md.
- 2026-11-05: GitHub Actions: `ci.yml` (push to main, PRs: unit tests + lint + debug APK as artifacts) and `release.yml` (`v*` tags or manual: signed .aab + APK + mapping as workflow artifacts only, never a public Release since the repo is public). The upload key comes from secrets (KEYSTORE_BASE64 etc., docs/play-console.md §11) written to a temporary keystore.properties and deleted after; without them the build is unsigned. Blank `admobAppId` / `admobRewardedId` now count as unset (test IDs), so an empty secret can't ship an empty ad ID.
- 2026-11-05: Developer = **Goomi Studio**, support email **hello.goomistudio@gmail.com** (public: store listing, privacy policy, "Send feedback"). The email is now the default in app/build.gradle.kts (`SupportEmail`; a non-blank `supportEmail` property / secret overrides it), so "Send feedback" shows in every build. Profile footer reads "made by Goomi Studio" (Caveat, above the version); the studio name is untranslatable, "made by" is translated.
