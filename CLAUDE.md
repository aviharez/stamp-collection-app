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

Working app name: TBD (keep the string in `strings.xml` as `app_name` so it is easy to change).

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
