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
