# TDD Evidence: Offline Image (Milestone 2 of Card Images and Bulk Import)

**Source plan**: [.claude/plans/card-image-offline-and-bulk-import-milestone-2.plan.md](../plans/card-image-offline-and-bulk-import-milestone-2.plan.md)
**Status**: Tasks 1-7 built test-first and green; the download is confirmed in the real app on the iOS simulator. **Task 8 is done on Android (emulator) and mostly on iOS (UI tests); real Wakodo image URLs are still untried.** One defect was found on Android and fixed (see below). **The PRD row was set to `complete` by the user on 2026-11-01 with the Wakodo URLs still untried; trying them is a follow-up, not a closed item.**

## User journeys (from the plan)
1. As someone adding a card, I can paste an image URL, and the image is saved with the card so it shows with no network.
2. As someone adding a card while offline or with a bad link, the card is still saved, just without an image, and nothing errors.
3. As someone with a card form that is wrong (blank title), nothing is downloaded and nothing is created.
4. As someone editing a stored card, I cannot change its image and editing never loses it.

## Environment
Gradle KMP project (not npm). Tests: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`. Ktor 3.6.0 (published 2026-09-16; 3.6.1 was 3 days old and skipped). The folder is not a Git repository, so no checkpoint commits were made.

## Task report
| Task | RED (what failed first) | GREEN | What is guaranteed |
|---|---|---|---|
| 1 Ktor spike | n/a | `compileAndroidMain` and `compileKotlinIosSimulatorArm64` pass with `HttpClient()` in `commonMain` | Dependencies resolve and compile on both targets. Real-engine behaviour: see "Real-engine spike" |
| 2 `looksLikeImage` | Compile-time: `Unresolved reference 'looksLikeImage'` (11 tests) | `ImageFormatTest` 11 tests, 0 failures | PNG, JPEG, GIF87a/89a, WebP accepted; HTML, WAV-in-RIFF, empty, truncated and random bytes rejected |
| 3 `FetchCardImage` | Compile-time: 20 errors, unresolved `fetchCardImage`, `MaxImageBytes`, `FetchCardImage` | 165 Android host tests, 0 failures | No/blank/non-http(s) URL: no download. URL trimmed. Any thrown failure, non-image, empty, or over-1,000,000-byte result gives null; exactly the limit is kept. **Cancellation is rethrown** |
| 4 `AddCard` + image | Compile-time: `Too many arguments` for the new constructor and `imageUrl` | 38 `CardUseCasesTest` tests pass (6 new) | Download starts only after validation; rejected form downloads nothing and saves nothing; failed or non-image download still saves the card without an image; cancellation saves nothing |
| 5 `KtorImageDownloader` | Runtime: stub with `TODO()`; 3 of 9 tests failed (success, exact-limit, redirect) | 180 tests, 0 failures | Returns the body on 2xx; fails on 404 and 500, on a declared `Content-Length` over the limit, on an over-limit body with no `Content-Length`, on a connection error, on a 100 ms timeout; follows a redirect |
| 6 ViewModel | Compile-time: unresolved `onImageUrlChanged` and `imageUrl` | Android host 187, iOS simulator 157 tests, 0 failures | Typing a URL makes the draft dirty (discard dialog); clearing it makes it clean; save stores the downloaded image; `isSaving` during the download and a second Save does nothing (one download, one card); failed download finishes with a saved card and no `failure` flag; rejected form keeps the URL and downloads nothing; an existing card ignores the URL and keeps its image |
| 7 Screen | No unit tests (Compose UI has none in this repo) | `assembleDebug`, `linkDebugFrameworkIosSimulatorArm64` and `xcodebuild` succeed | Compiles and links only. Behaviour is **not yet seen on a device** |

Tasks 4 and 5 turned green together: `AppContainer` builds `AddCard` from the downloader, so the project did not compile between them.

## Mutation checks (downloader)
Each guard was removed in turn and the downloader tests rerun; the file was then restored byte for byte.
| Removed | Failing tests |
|---|---|
| `isSuccess()` check | `download_failsOnNotFound`, `download_failsOnServerError` |
| `Content-Length` check | `download_failsWhenContentLengthIsOverTheLimit` |
| read-size check | `download_failsWhenTheBodyIsOverTheLimitWithoutAContentLength` |

## Test counts
Before this milestone: Android host 142, iOS simulator 112 (M1 report). After: **Android host 188, iOS simulator 158**, 0 failing on both (+46 each: 11 format, 12 `FetchCardImage`, 6 `AddCard`, 9 downloader, 8 ViewModel, the last being the discard-during-download test).

## Coverage
`./gradlew :composeApp:coverageReport` (Android host): the touched logic (`ImageFormatKt`, `FetchCardImage`, `AddCard`, `KtorImageDownloader`, `CardEditorViewModel`) is at **100% line coverage**. Overall is **36.4%** because the Compose UI has no automated tests (known risk in the plan).

## Real-engine spike (throwaway test, deleted)
The unit tests use `MockEngine`, so a temporary test ran the real engines through `FetchCardImage`:
| | JVM host (OkHttp) | iOS simulator (Darwin) |
|---|---|---|
| Real HTTPS image (Google-hosted PNG, 375,229 bytes) | **Downloaded** | **Failed**: `DarwinHttpRequestException`, `NSURLErrorDomain -1202` "certificate for this server is invalid" |
| Same with a Wikimedia PNG | Downloaded (224,566 bytes) | **Failed**: `-1200` "A TLS error caused the secure connection to fail" |
| Unknown host | `UnknownHostException` (an `Exception`) | `DarwinHttpRequestException` (an `Exception`) |

- The offline path is safe on both: a failure is an `Exception`, so `FetchCardImage` turns it into "no image".
- **Resolved: the iOS TLS failures belong to the bare Kotlin/Native test runner, not to the app.** HTTPS failed for two unrelated hosts inside `iosSimulatorArm64Test`, but the same download works in the real app (see "iOS UI test" below). The certificate chain seen from the host was ordinary (Let's Encrypt, ISRG) and no proxy is configured. The cause inside the test runner was not diagnosed; it does not affect the app.

## iOS UI test (real app, real Darwin engine, iPhone 18 Pro simulator)
`CardFilterUITests.testCardImageIsDownloadedWhenTheCardIsAdded` (new, run alone on a clean install with `xcodebuild test -only-testing:...`): **passed in 52 s**.
- A card added with `https://www.gstatic.com/webp/gallery/1.png` is saved with the image; the editor shows "Card image" and the screenshot shows the photo rendered.
- A card added with `https://does-not-exist.invalid/a.png` is saved, shows no "Something went wrong", and has no image.
- A blank title with a URL typed shows "Enter some text", keeps the URL in the field, and creates no card (the topic still lists two cards after Discard).

Two more UI tests, run together with it on a clean install, **all three passed**:
- `testImageUrlsThatCannotBeUsedSaveACardWithoutAnImage` (114 s): for each of a 404 URL, a web page (`https://example.com/`), `ftp://`, plain `http://`, a 6.5 MB JPEG (Apollo 17, Wikimedia), an SVG, and the text `not a url`, the card is saved, no "Something went wrong" appears, and the editor shows no image. It does not prove *why* each was refused: the oversize case is proven by the unit tests (`Content-Length` and body limit), not by this test.
- `testDownloadedImageSurvivesRelaunchAndEditing` (34 s): after killing and relaunching the app the image is still shown; the editor of a stored card has no "Image URL" field; saving a title edit keeps the image.
- Wikimedia answered 403 to a `okhttp/…` user agent and 200 to `ktor-client` and `CFNetwork`. Hosts that filter by user agent can silently block downloads (plan risk). The Wakodo host has not been tried.

## Android emulator checks (API 36, AVD `booklet-api36`, driven with `adb` and `uiautomator`; the scripts were throwaway)
| # | Check | Result |
|---|---|---|
| 1 | Online add with a valid URL: card saved, editor shows the image | PASS |
| 2 | Airplane mode on (a ping to 8.8.8.8 fails), app force-stopped and relaunched: card still listed, editor still shows the image (**offline display**) | PASS |
| 3 | Offline add with a valid URL: card saved, no error, no image | PASS |
| 4 | 404, web page, `ftp://`, plain `http://`, 6.5 MB JPEG, SVG, `not a url`: each saved without an image and without an error | PASS (7 of 7) |
| 5 | Blank title with a URL typed: error shown, URL kept, Back asks to discard, Discard returns to the list | PASS (the first Back only closes the keyboard) |
| 6 | Black-holed address `https://10.255.255.1/a.png`: Save and the URL field are disabled, the hint reads "Downloading image…" (screenshot checked), the card is saved after the 10 s connect timeout with no error and no image | PASS (12.5 s measured, which includes about 2 s per `uiautomator` call) |
| 7 | Rotation to landscape during the download: the download continues and exactly one card is saved | PASS |
| 8 | Editing a card with an image: no "Image URL" field, the image is shown and is kept after a title edit | PASS |
| 9 | Dark mode: list, new-card editor and stored-image editor follow the theme (screenshots checked) | PASS |
| 6b | Leaving during the download (Discard 1 s and 5 s after Save, well inside the timeout) | **FAIL with system animations off, PASS with normal animations** (see below) |

### Defect found and fixed: a discarded card was still created
With system animations off (Android's "Remove animations"), confirming Discard while the image was downloading left the download running and the card was created about 10 s later, although the user had discarded it. With normal animations it did not happen. The plan had assumed that leaving the screen clears the ViewModel and so cancels the download; that is not guaranteed. The fix is to keep the save job and cancel it in `onDiscardConfirmed`.
- **RED**: `discardingWhileTheImageDownloads_savesNoCardEvenIfTheDownloadThenFinishes` failed at runtime (`AssertionError`: a card existed).
- **GREEN**: Android host 188 and iOS simulator 158 tests, 0 failing.
- **Re-checked on the emulator** with animations off: Discard 1.0 s after Save, no card 16 s later. A second timing (about 5 s) was not re-checked because the driver was too slow that run.

## Not done
- **Task 8 manual verification is mostly not done.** (`adb` exists in the Android SDK but was not on `PATH`, so Android was run later: see the section above.) On iOS the three UI tests above cover steps 1, 4, 5 and 8, and the "survives a relaunch" half of step 2 (with the network on). Step 10 (dark mode) was checked on iOS by screenshot: the new-card editor (URL field, hint, URL keyboard, no auto-capitalisation) and the stored-image editor both follow the theme in light and dark; the progress bar state was not captured. Not run on iOS: the network switched off, the connect timeout and leaving mid-download (all done on Android). Not run anywhere: real Wakodo URLs (step 9), and the 30 s total request timeout (only the 10 s connect timeout was observed). Rotation (step 7) applies to Android only. The simulator shares the Mac's network, so "offline" cannot be switched from here; the UI tests also need real internet and cannot run in CI offline.
- The PRD sample JSON (real Wakodo image URLs) is still not in the repo.
- "Download is refused before reading the body" is asserted only as "the call fails"; a test that detects a body read was not written.
- The timeout values (10 s connect, 30 s request) are constants in `AppContainer` and are not covered by a test.
- Decisions from the plan, **confirmed by the user on 2026-11-01**: no `http://` exception, 1,000,000-byte cap, 10 s / 30 s timeouts, no notice when an image fails. The cap and the "no notice" choice can be revisited after real Wakodo URLs are tried.

## Deviations from the plan
- Ktor 3.6.0 uses `readBuffer(Long)`; `readRemaining(Long)` is deprecated in it. The compiler warning was fixed.
- RED for Tasks 2-4 and 6 was compile-time (missing symbols); RED for Task 5 was runtime against a `TODO()` stub. Failure-path downloader tests passed against the stub (the assertion also catches `NotImplementedError`), which is why the mutation checks above were added.
- The real-engine spike was run as a throwaway test instead of a throwaway composable on an emulator.
