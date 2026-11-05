# Plan: Offline Image (Milestone 2 of Card Images and Bulk Import)

**Source PRD**: .claude/prds/card-image-offline-and-bulk-import.prd.md
**Selected Milestone**: 2 — Offline image ("An image is downloaded when the card is added and shows with no network; a failed download leaves a normal card without an image")
**Complexity**: Medium (first network code in the app: one new dependency family, a manifest permission, one new use case; storage and display already exist from M1)
**Depends on**: Milestone 1 (complete): `card_image` table, `CardRepository.create(..., image)`, `getImage`, `CardImage` composable.

## Summary
The new-card editor gets an optional "Image URL" field. On Save, the card is validated first; if it is valid and a URL was given, the image is downloaded once (with timeouts and a size cap), checked to be a real image, and stored together with the card in the existing single transaction. Any download problem (bad URL, offline, HTTP error, too big, not an image) saves the card without an image and does not fail the save. Display needs no new work: M1 already reads the image from the database, so an image that was stored shows with no network by construction. The download-and-check logic is one reusable use case (`FetchCardImage`) so Milestone 3 imports go through the same path.

## Decisions Made in This Plan (confirm or override)
| Decision | Choice | Why |
|---|---|---|
| Where the user supplies a URL | An "Image URL" text field in the **new-card** editor only; hidden when editing an existing card | PRD: no editing or replacing images, images come only from a URL |
| HTTP client | **Ktor client** in `commonMain` (`ktor-client-core` + `HttpTimeout`), engine `okhttp` on Android and `darwin` on iOS. `HttpClient()` picks the engine per platform, so there is no platform wiring in `AppContainer` | Carried from the M1 plan. **Alternative**: `expect/actual` over `HttpURLConnection` (Android) and `NSURLSession` (iOS). No new dependency, but K/N `NSURLSession` interop is callback-heavy and untested in this repo |
| Download seam | `interface ImageDownloader { suspend fun download(url: String, maxBytes: Int): ByteArray }` in `domain`, **throws on any failure**. `FetchCardImage` turns every failure into `null` | One place decides "failure means no image" (the PRD rule), tests use a fake downloader as they do with the repositories. `CancellationException` is rethrown, never swallowed (same rule as `attempt`) |
| Validation order | Title and attributes are validated first; the download starts only for a card that will be saved | A rejected form must not cost a download |
| No DB work during the download | Download first, then one `repository.create(..., image)` call | `create` is already one transaction; nothing holds the database open while the network is slow |
| Bad URL | Not a validation error. Blank = no image. Anything else is attempted; non-`http(s)` schemes are skipped without a request and count as a failed download | PRD scope: "(bad URL, offline, error) the card is saved as if it had no image" |
| Cleartext `http://` | **No exceptions added.** Android 9+ and iOS block cleartext by default, so an `http://` URL fails and the card has no image | The sample JSON is not in the repo, so whether its URLs are `https` is unknown. Check in Task 8; opening ATS / `usesCleartextTraffic` is a security decision for you |
| Size cap | **1,000,000 bytes**, applied while reading (stop at cap + 1, never download the whole body of a huge file) and again in `FetchCardImage`. Carried from M1 (PRD open question 9) | M1 verified ~1 MB reads back on Android; the cursor window is ~2 MB. Larger images are saved without an image. **Alternative**: downscale before storing (needs platform code) |
| "Is it an image" | Pure-Kotlin magic-byte check (PNG, JPEG, GIF, WebP) in `domain`; no decoding in the use case | JVM host tests cannot decode, and the usual bad result is an HTML error page saved by a wrong URL. `CardImage` still survives bytes that pass the check but do not decode. Formats not listed (SVG, AVIF, HEIC) are saved without an image |
| Timeouts | Connect 10 s, request 30 s in total | Offline fails fast on a normal device; the 30 s only bites on a captive portal or a very slow link. Save is disabled and a progress bar shows meanwhile. **Yours to confirm** |
| User feedback on a failed image | **None in M2** (card is saved "as if it had no image", as the PRD says) | PRD risk row says "decide whether to tell the user". Adding a one-line notice on the card list is small (carry a result from the editor), but it is a product choice. **Confirm** |
| Leaving during a download | Back while saving follows the existing flow (discard dialog). Confirming discard clears the ViewModel, which cancels the download; no card is created | No new state machine; checked manually (Task 8) |
| Schema | **No change.** No migration, no new SQL | M1 built storage |
| `DraftTextField` keyboard | New optional `keyboardOptions` parameter; the URL field uses `KeyboardType.Uri`, no auto-capitalisation | Today the field forces `Sentences` capitalisation, which would turn `https://…` into `Https://…` |

## Open items this plan cannot resolve
- **The PRD's sample JSON is not in the repo** (M1 plan asked for it under `composeApp/src/commonTest/resources/`). Real Wakodo image URLs are needed in Task 8 to learn whether the source site blocks hotlinking, redirects, serves `https` and stays under 1 MB. Please add the file or paste two or three URLs.
- **Ktor version**: not cached locally, so it is chosen in Task 1 (latest stable at least two weeks old that supports Kotlin 2.4.21), not guessed here.

## Patterns to Mirror
| Category | Source | Pattern |
|---|---|---|
| Naming | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt:16-33` | One small class per use case with `operator fun invoke`; `FetchCardImage` lives in this file; `AddCard` gains a constructor dependency and a parameter |
| Validate then act | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/validation/CardValidation.kt:52-60` | `saveCard(title, attributes) { ... }` runs the lambda only after validation passes; the download goes inside it |
| Errors | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/Attempt.kt:6-13` | Rethrow `CancellationException`, convert other `Exception`s. `FetchCardImage` does the same locally (domain must not import `presentation`) |
| Missing image is normal | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorViewModel.kt:147-149` and `DESIGN.md` rule 8 | No failure flag and no snackbar for an absent image |
| Data access | `composeApp/src/commonMain/kotlin/app/cardfilter/data/SqlCardRepository.kt:39-52` | The data layer implements a `domain` interface; `KtorImageDownloader` sits beside the SQL repositories in `data` |
| Wiring | `composeApp/src/commonMain/kotlin/app/cardfilter/AppContainer.kt:25-43` | Hand-wired; one new `val` for the use case |
| ViewModel draft | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorViewModel.kt:58-66, 163-166` | Everything that counts as "unsaved changes" goes through `content()` and `edit`; the URL must be in `content()` so a typed URL triggers the discard dialog |
| UI | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorScreen.kt:129-184` | Stateless composable items in the form `LazyColumn`, strings from `Strings`, `Spacing` tokens |
| Tests | `composeApp/src/commonTest/kotlin/app/cardfilter/testutil/FakeRepositories.kt`, `.../domain/usecase/CardUseCasesTest.kt`, `.../presentation/cards/CardEditorViewModelTest.kt` | Fakes in `testutil`, `runTest`, `UnconfinedTestDispatcher` as Main, behaviour-named test methods |

## Files to Change
Paths relative to `composeApp/src` unless noted.

| File | Action | Why |
|---|---|---|
| `gradle/libs.versions.toml` (repo root) | UPDATE | `ktor` version and `ktor-client-core`, `-okhttp`, `-darwin`, `-mock` entries |
| `composeApp/build.gradle.kts` | UPDATE | `commonMain` core, `androidMain` okhttp, `iosMain` darwin, `commonTest` mock |
| `androidApp/src/main/AndroidManifest.xml` (repo root) | UPDATE | `android.permission.INTERNET` (none today) |
| `commonMain/kotlin/app/cardfilter/domain/image/ImageDownloader.kt` | CREATE | The `ImageDownloader` interface and `MaxImageBytes` |
| `commonMain/kotlin/app/cardfilter/domain/image/ImageFormat.kt` | CREATE | `looksLikeImage(bytes)`: PNG / JPEG / GIF / WebP signatures |
| `commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt` | UPDATE | Add `FetchCardImage`; `AddCard(repository, fetchCardImage)` takes `imageUrl: String? = null` |
| `commonMain/kotlin/app/cardfilter/data/KtorImageDownloader.kt` | CREATE | Ktor implementation: status check, `Content-Length` pre-check, capped streaming read |
| `commonMain/kotlin/app/cardfilter/AppContainer.kt` | UPDATE | Build the `HttpClient` (with `HttpTimeout`), `KtorImageDownloader`, `FetchCardImage`; pass to `AddCard` |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorViewModel.kt` | UPDATE | `imageUrl` in state and in `content()`, `onImageUrlChanged`, pass it to `addCard` for a new card |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorScreen.kt` | UPDATE | URL field (new card only), disabled while saving, progress bar while a URL is being downloaded |
| `commonMain/kotlin/app/cardfilter/designsystem/Components.kt` | UPDATE | `DraftTextField(keyboardOptions = ...)` with the current value as default so existing callers are unchanged |
| `commonMain/kotlin/app/cardfilter/presentation/Strings.kt` | UPDATE | "Image URL", helper text, "Downloading image…" |
| `commonTest/kotlin/app/cardfilter/testutil/FakeImageDownloader.kt` | CREATE | Programmable fake: returns bytes, throws, or suspends; records requested URLs |
| `commonTest/kotlin/app/cardfilter/domain/image/ImageFormatTest.kt` | CREATE | Signature cases |
| `commonTest/kotlin/app/cardfilter/data/KtorImageDownloaderTest.kt` | CREATE | `MockEngine` cases: success, non-2xx, oversize by header, oversize without header, timeout |
| `commonTest/kotlin/app/cardfilter/domain/usecase/CardUseCasesTest.kt` | UPDATE | `FetchCardImage` and `AddCard`-with-URL cases; existing `AddCard(repository)` call updated |
| `commonTest/kotlin/app/cardfilter/presentation/cards/CardEditorViewModelTest.kt` | UPDATE | URL draft, dirty, saving states; existing `AddCard(source)` call updated |
| `DESIGN.md` (repo root) | UPDATE | New-card image URL field, progress state, "failed image is silent" |
| `.claude/tdd/card-image-offline-and-bulk-import-milestone-2.tdd.md` | CREATE | RED/GREEN evidence and manual results, as for earlier milestones |
| `.claude/prds/card-image-offline-and-bulk-import.prd.md` | UPDATE | M2 row → `in-progress`, Plan cell → this file (done at plan creation) |

No change to the database schema, `SqlCardRepository`, `CardImage` or the card list: stored images already display. No `Info.plist` change unless Task 8 finds `http://` sample URLs and you decide to allow them.

## Tasks
Set `JAVA_HOME` to Android Studio's bundled JDK as in earlier milestones before running Gradle.

### Task 1: Ktor spike on both platforms
- **Action**: Pick the Ktor version, add the four catalog entries and dependencies, and in a throwaway composable or test download one small public `https` image on the Android emulator **and** the iOS simulator. Record: it compiles on both targets with `HttpClient()` in `commonMain` (engine auto-selected); `HttpTimeout` is honoured; the exact API for a capped streaming read in this Ktor version (`prepareGet(...).execute { ... bodyAsChannel() ... }`); what offline (airplane mode) throws on each platform and that it is an `Exception`, not an `Error`. If the iOS Darwin engine fails to link or run, stop and fall back to the `expect/actual` alternative before going on.
- **Mirror**: M1 Task 1 (spike with a stop-and-fall-back rule).
- **Validate**: `./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64` passes and the image downloads on both platforms. Delete the throwaway code.

### Task 2: Image format check (tests first)
- **Action**: Write `ImageFormatTest` first: a minimal PNG, JPEG, GIF87a, GIF89a and WebP (`RIFF` + size + `WEBP`) header each pass; HTML (`<!DOCTYPE html>`), an empty array, one byte, a 3-byte array and random bytes fail; a `RIFF` header that is not `WEBP` (a WAV) fails. See them fail, then implement `looksLikeImage`.
- **Mirror**: `TextFoldingTest.kt` (small table-style pure-function tests).
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`.

### Task 3: `FetchCardImage` use case (tests first)
- **Action**: Create `FakeImageDownloader`. Write `FetchCardImage` cases first: valid image bytes returned; `null` and blank URL return `null` and make **no** download call; a non-`http(s)` URL (`ftp://`, `file:///`, `javascript:`, a bare word) returns `null` with no call; the URL is trimmed before the request; a downloader that throws returns `null`; bytes that are not an image return `null`; bytes over `MaxImageBytes` return `null`; a zero-byte result returns `null`; **cancellation is rethrown, not turned into `null`**. Then implement `ImageDownloader`, `MaxImageBytes` and `FetchCardImage`.
- **Mirror**: `CardUseCases.kt` class shape; `Attempt.kt` for the cancellation rule.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`.

### Task 4: `AddCard` with an image (tests first)
- **Action**: Extend `CardUseCasesTest` first: a valid card with a URL is saved with the image and `hasImage = true`; a card with no URL is unchanged (no download call); **a rejected card (blank title) makes no download call**; a failing downloader still saves the card with `hasImage = false` (PRD rule); a non-image response saves the card without an image; the stored bytes equal the downloaded bytes. Update the existing `AddCard(repository)` line to the new constructor. Then change `AddCard` and wire `FetchCardImage` in `AppContainer` (with `KtorImageDownloader` from Task 5).
- **Mirror**: `CardUseCasesTest.kt` fixture style; `CardValidation.kt:52-60`.
- **Validate**: both test tasks above.

### Task 5: `KtorImageDownloader` (tests first)
- **Action**: Write the `MockEngine` tests first, building the client the same way `AppContainer` does (with `HttpTimeout`, short values in the test): 200 returns the exact bytes; 404 and 500 throw; `Content-Length` above the cap throws **without reading the body**; a body above the cap with no `Content-Length` (chunked) throws after reading at most cap + 1 bytes; a handler that never answers times out with an exception; a redirect is followed. Then implement it using the API found in Task 1.
- **Mirror**: `SqlCardRepository.kt` (data-layer class implementing a domain interface, no logic the interface does not need).
- **Validate**: both test tasks above. The timeout test uses a real short timeout; if it proves flaky, give it a looser bound rather than removing it.

### Task 6: ViewModel (tests first)
- **Action**: Extend `CardEditorViewModelTest` first: typing a URL makes the draft dirty and `onBackClick` shows the discard dialog; a new card saved with a URL calls the downloader with that URL and stores the bytes; `isSaving` is true while the download is suspended and Save is ignored a second time (no second card, one download); a failing downloader still finishes the editor with a saved card and **no** `failure` flag; a rejected form (blank title) does not download and keeps the typed URL; editing an existing card never downloads and never changes its image (`hasImage` stays as loaded). Then implement `imageUrl`, `onImageUrlChanged`, and the `addCard(topicId, title, attributes, imageUrl)` call for new cards.
- **Mirror**: `CardEditorViewModelTest.kt` (`CompletableDeferred` for suspended calls, as already used there).
- **Validate**: both test tasks above.

### Task 7: Screen
- **Action**: In `CardEditorScreen` add the "Image URL" `DraftTextField` (single line, `KeyboardType.Uri`, no capitalisation, helper text "Optional. The image is saved on this device.") only when `isNewCard`; place it after the title. Disable it while `isSaving`. Show a `LinearProgressIndicator` under the top bar while `isSaving` with a non-blank URL. Add `keyboardOptions` to `DraftTextField` with the current behaviour as default. Add strings and update `DESIGN.md`.
- **Mirror**: `CardEditorScreen.kt:129-184`, `Components.kt` conventions, DESIGN.md rules.
- **Validate**: `./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`.

### Task 8: Manual verification on both platforms
- **Action**: On the Android emulator **and** the iOS simulator, with real sample URLs:
  1. Online, add a card with a valid image URL: the card appears with its thumbnail and the editor shows the image.
  2. **Offline display**: with the network switched off (airplane mode), kill and relaunch the app: the card from step 1 still shows its image in the list and the editor. This is the PRD's 100% offline check.
  3. Offline add: with the network off, add a card with a valid URL. Save returns within the timeout, the card exists with no image, no error is shown.
  4. Bad inputs, each saved as a normal card without an image and no snackbar: a malformed URL (`not a url`), a 404 URL, a URL to an HTML page, an `ftp://` URL, an `http://` URL, a URL to an image larger than 1 MB, a URL to an SVG.
  5. A rejected form (blank title) with a URL typed: errors show, the URL is kept, nothing is downloaded, no card created.
  6. Slow link / captive portal: use a URL that hangs (a local server that never answers). Save is disabled with the progress bar, then completes at about 30 s with no image. Back during the wait asks to discard; confirming leaves no card.
  7. Rotate (Android) during a download: the download continues and the card is saved once, not twice.
  8. Edit a card that has an image: no URL field is shown, the image is unchanged after saving a title edit.
  9. Real Wakodo sample URLs: record, per URL, whether the image downloaded and why not if it did not (hotlink block, redirect, size, `http`). If the site rejects the default client user agent, decide on a `User-Agent` header before closing.
  10. Dark mode; the field and the progress bar follow the theme.
- **Mirror**: manual tables in `.claude/tdd/card-image-offline-and-bulk-import-milestone-1.tdd.md`.
- **Validate**: every step passes on both platforms. Record any platform-specific failure before closing.

### Task 9: Close out
- **Action**: Write `.claude/tdd/card-image-offline-and-bulk-import-milestone-2.tdd.md` (RED/GREEN per cycle, test counts, coverage, spike result). Re-run the coverage report. Set the PRD M2 row to `complete` only after Task 8 passes and you have accepted the open decisions above.
- **Validate**: PRD row matches reality.

## Validation
```bash
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:coverageReport
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```
The `-destination ... ARCHS=arm64` flags are required; without them Xcode tries `x86_64` and fails. No schema change, so the migration tasks are not needed.

## Implementation Status
**Complete (2026-11-01).** Built test-first; Android host 188 and iOS simulator 158 tests pass; verified on the Android emulator and the iOS simulator. Evidence: [.claude/tdd/card-image-offline-and-bulk-import-milestone-2.tdd.md](../tdd/card-image-offline-and-bulk-import-milestone-2.tdd.md).
- The decisions above were confirmed as written (no `http://` exception, 1,000,000-byte cap, 10 s / 30 s timeouts, no notice on a failed image).
- **Deviation:** the plan assumed that leaving the editor clears the ViewModel and so cancels a download. On Android with system animations off it did not, and a discarded card was still created. `onDiscardConfirmed` now cancels the save job explicitly.
- **Follow-up, not done:** real Wakodo image URLs were never tried (the sample JSON is not in the repo). A host that blocks by user agent would drop images silently; revisit the cap and the "no notice" choice if that happens.

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| Ktor Darwin or OkHttp engine does not work with this Kotlin / Compose / AGP combination (Kotlin 2.4.21, AGP 9.4.1) | Medium | Task 1 spike first with a defined fallback (`expect/actual`) |
| The image host blocks the default client (403, hotlink protection, missing user agent) so Wakodo images never download, silently | Medium | Task 8 step 9 uses real sample URLs; add a `User-Agent` header if needed. The sample JSON must be supplied |
| Image URLs are `http://`, blocked by Android 9+ and iOS ATS, so cards silently get no image | Medium | Task 8 steps 4 and 9 find out; allowing cleartext is a security decision left to you |
| A failed download is invisible; the user cannot tell why a card has no image (PRD risk, still open) | Medium | Deliberate in M2 per the PRD wording; a one-line notice on the card list is the small follow-up if you want it |
| Save blocks up to 30 s on a captive portal or a very slow link | Low | Save disabled with a progress bar, discard dialog cancels the download, timeouts are constants in one place |
| A hostile or huge response exhausts memory | Low | Streaming read stops at cap + 1 bytes; `Content-Length` pre-check; 1 MB cap |
| Bytes pass the signature check but do not decode (truncated file) | Low | `CardImage` already shows nothing for undecodable bytes (M1); the card stays usable |
| Images over 1 MB, which many product photos are, are dropped silently | Medium | Cap confirmed or raised after Task 8 step 9 shows real sizes; downscaling is the alternative and needs platform code |
| Download finishes after the user left the screen and the card is created twice or not at all | Low | `isSaving` guard exists; the ViewModel scope cancels the download on leave; tested in Task 6 and Task 8 steps 6 and 7 |
| `AddCard` constructor change breaks existing tests | Low | Only two call sites (`CardUseCasesTest`, `CardEditorViewModelTest`), both updated in Tasks 4 and 6 |
| Network code is not coverable by Compose UI tests; overall coverage stays below 80% | High (known) | Keep logic in the use case, downloader and ViewModel (target ≥ 80%); UI covered by Task 8 |
| PRD requirements are unvalidated (users, need and metrics are TBD) | High (inherited) | After M2, put the app in front of one real user with the Wakodo sample before building M3 |

## Acceptance
- [ ] All tasks complete
- [ ] `testAndroidHostTest`, `iosSimulatorArm64Test`, `assembleDebug`, iOS framework link and `xcodebuild` pass
- [ ] A card added with a valid image URL stores the image once and shows it with the network off, on Android and iOS, after an app restart
- [ ] Offline, malformed, 404, non-image, oversized and non-`http(s)` URLs each produce a normal saved card without an image, with no error shown
- [ ] A rejected form downloads nothing and creates nothing
- [ ] Editing an existing card shows no URL field and never changes its image
- [ ] No database schema change; no import (M3)
- [ ] Logic-layer coverage ≥ 80%
- [ ] The open decisions (cleartext, 1 MB cap, timeouts, no failure notice) are confirmed or changed by you
