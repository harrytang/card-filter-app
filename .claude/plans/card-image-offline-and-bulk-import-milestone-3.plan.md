# Plan: Bulk Import (Milestone 3 of Card Images and Bulk Import)

**Source PRD**: .claude/prds/card-image-offline-and-bulk-import.prd.md
**Selected Milestone**: 3 — Bulk import ("The user imports a valid JSON file and gets the cards (with images); an invalid file shows an error and imports nothing; matching cards are overwritten")
**Complexity**: Large (first file access in the app, a JSON parser, a matching and overwrite rule, a multi-step import flow with progress and cancel; no schema change, and image download already exists from M2)
**Depends on**: Milestone 1 (`card_image` table, `CardImage`) and Milestone 2 (`FetchCardImage`, `ImageDownloader`, `MaxImageBytes`, Ktor client), both complete.

## Summary
The card list of a topic gets an "Import" action. The user picks a JSON file; the whole file is parsed and validated first, and the first problem stops the import with an error and creates nothing. A valid file is matched against the topic's existing cards (same name, trimmed, case-insensitive). If any card would be overwritten, the user confirms first. Images are then downloaded (a few at a time, with progress and Cancel) through the existing `FetchCardImage`, and **all cards are written in one database transaction at the end**. Cancelling, or the app being killed at any point before that write, leaves the topic untouched, so there is no partially imported topic. A failed image never fails the import: the card is saved without it, and the final summary says how many images failed.

## Decisions Made in This Plan (confirm or override)
| Decision | Choice | Why |
|---|---|---|
| Where import goes | The **currently open topic**, started from the card list. No "create a topic from the file" | PRD open question 3. Smallest flow, and the card list already knows the topic. **Alternative**: name a new topic after the file |
| How the file is chosen | A system **file picker** (FileKit, Compose Multiplatform wrapper over `OpenDocument` and `UIDocumentPicker`). Parsing takes a `String`, so the picker is only the outermost edge | PRD says "JSON file". **Fallback if the Task 1 spike fails**: a paste-JSON text box in a dialog, which needs no platform code and still feeds the same parser. The library is a new dependency and is unverified against Kotlin 2.4.21, Compose 1.12.1 and AGP 9.4.1; the spike decides |
| JSON library | `kotlinx-serialization-json`, used only through `parseToJsonElement` (no compiler plugin, no `@Serializable` classes) | The parser must give a specific error per entry, which a data-class decoder cannot do cleanly. Version chosen in Task 1 (latest stable at least two weeks old that supports Kotlin 2.4.21) |
| Field mapping (PRD open question 4) | `name` → the card **title**. `ingredients` → an attribute named `ingredients`. `month` → an attribute named `month`, stored as text (`7` and `"7"` both become `7`). Attribute names are the JSON key as written | The card model is a title plus name/value attributes. **The PRD's sample JSON is not in the repo**, so the real shape of `ingredients` (string or list?) and `month` (number or string?) is unconfirmed. The parser accepts a string or a list of strings for `ingredients` (joined with `, `) and a string or number for `month`; please add the sample so Task 2 can use it |
| Which fields are required | Only `name` (non-blank string). `image`, `ingredients`, `month` are optional; `null` or blank means absent. A present field of the wrong type (a number for `name`, an object for `image`) is **invalid** | Reuses `validateCard` for the title and attribute rules, so an imported card follows the same rules as a typed one |
| Unknown extra keys (PRD open question 6) | **Ignored** | Importing unknown keys as attributes would silently add junk to every card. **Alternative**: import every scalar key as an attribute |
| Same card (PRD open question 2) | Same **title within the same topic**, compared after trimming and ignoring case. Diacritics are *not* folded (`Cá` and `Ca` are different cards) | PRD working assumption. SQLite `lower()` only handles ASCII, so matching is done in Kotlin, not SQL |
| Duplicate names **inside the file** | **Invalid**: the error names both entries. Nothing imported | "Stop on first problem" and no silent data loss. Two products that really share a name must be renamed in the file. **Alternative**: later entry wins |
| Several existing cards with the same title | Overwrite the **oldest** (lowest id); leave the others alone | The card table has no uniqueness rule, so it can happen after manual entry |
| What overwrite replaces | Title and the **whole attribute list**, in place (same id, same position in the list). The image is replaced **only when a new image downloaded**; if the entry has no `image`, or its download failed, the old image is **kept** | PRD open question 10. Deleting a good offline image because of one flaky download would be the worst outcome. **Alternative**: always clear the image on overwrite |
| Confirm before overwrite (PRD risk 1) | After validation, **only if** at least one card would be overwritten: a `ConfirmDeleteDialog` ("N cards in this topic will be replaced", up to 5 names, "Overwrite and import" / "Cancel"). No overwrite means no extra step | Design rule 2: destructive actions are confirmed. Cheap, because matching happens before any download |
| Atomicity (PRD open question 4) | Validate → download every image → **one** `importCards` transaction. Nothing is written before the transaction | A kill or Cancel mid-import cannot leave half a topic. Cost: downloaded images are held in memory until the write (see the limit below) |
| Limits | File at most **1,000,000 bytes** and **100 cards**; over either is an error and nothing is imported | Bounds memory (100 cards × the 1 MB image cap is the 100 MB worst case; real product photos are far smaller) and total download time. **Confirm** 100, or raise it |
| Download concurrency | **4 at a time** (`Semaphore`), reusing `FetchCardImage` and the M2 timeouts (10 s connect, 30 s request) | Offline fails fast, so 30 images take seconds on a normal link. Sequential would take minutes on a slow one |
| Offline during import (PRD open question 8) | Import proceeds; every card is saved without an image; the summary reports the failures | The PRD's "failed download means no image" rule, as M2 |
| Progress and cancel (PRD open question 7) | A progress dialog "Importing 12 of 30…" with a Cancel button; back also cancels. Cancel leaves the topic unchanged | Cancel is safe because of the atomic write |
| Tell the user about failed images (PRD risk 2) | **Yes, for import only**: the result dialog says "Imported 30 cards (4 replaced). 3 images could not be downloaded." A single add stays silent as M2 decided | After a bulk import the user cannot otherwise tell which of 30 cards lost its image. **Confirm**; it is a product choice |
| Error wording | One message naming the entry number ("Card 3: the name is missing") or the file-level problem. Only the first problem is shown | PRD: stop on first problem, no per-entry report |
| Schema | **No change.** One new query (`replace` on `card_image`) and one new repository method. No migration | Storage exists from M1 |
| `http://` image URLs | Unchanged from M2: blocked by Android 9+ and iOS, so the card is saved without an image and counted as a failed image in the summary | The summary now makes the M2 follow-up visible |

## Open items this plan cannot resolve
- **The sample JSON is still not in the repo.** Please add it under `composeApp/src/commonTest/resources/` (or paste it). It fixes the `ingredients` and `month` shapes and shows whether its image URLs are `https`, which M2 left unverified.
- **Duplicate and overwrite semantics are working assumptions** (PRD open question 2). The table above is a proposal; the PRD itself is unvalidated.

## Patterns to Mirror
| Category | Source | Pattern |
|---|---|---|
| Naming | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt:16-33` | One small class per use case with `operator fun invoke`; new use cases go in a new `ImportUseCases.kt` beside it |
| Reuse of the image path | `CardUseCases.kt:28-47` (`FetchCardImage`) | Returns `null` for any failure and rethrows cancellation; import calls it per entry and counts the nulls. No new download code |
| Validate then act | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/validation/CardValidation.kt:26-60` | `validateCard` trims, drops blank rows and rejects half rows; the parser calls it per entry rather than re-implementing the rules |
| Errors | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/Attempt.kt:6-13` and `Strings.kt:60-67` | `attempt` for use-case calls in ViewModels; errors are a sealed type in `domain`, mapped to text in `Strings.errorFor` |
| Sealed outcome | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/validation/CardValidation.kt:10-17` | `Saved` / `Rejected` style sealed interface; `ImportParse` follows it |
| Data access | `composeApp/src/commonMain/kotlin/app/cardfilter/data/SqlCardRepository.kt:39-62` | One `database.transaction` per operation, `withContext(ioDispatcher)`; `importCards` is one transaction over many cards |
| Interface | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/repository/CardRepository.kt:9-29` | New method with a KDoc line saying what it does not check |
| Wiring | `composeApp/src/commonMain/kotlin/app/cardfilter/AppContainer.kt:33-60` | Hand-wired, one `val` per use case; `fetchCardImage` is already a private `val` |
| ViewModel state | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/cards/CardListViewModel.kt:23-70` | `data class` UI state in a `MutableStateFlow`, handlers named `onXxx` |
| Cancelling a job explicitly | `composeApp/src/commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorViewModel.kt:111-116` | M2 found that clearing the ViewModel does not cancel at once; keep the `Job` and cancel it in `onCancel` |
| Dialogs | `composeApp/src/commonMain/kotlin/app/cardfilter/designsystem/Components.kt:81-101` | `ConfirmDeleteDialog` for the overwrite confirmation (DESIGN rule 2) |
| Screen wiring | `composeApp/src/commonMain/kotlin/app/cardfilter/App.kt:59-78` and `CardListScreen.kt:68-87` | A ViewModel built in `App` with `viewModel(key = ...)` and passed to the screen; actions in `TopAppBar` |
| Tests | `composeApp/src/commonTest/kotlin/app/cardfilter/testutil/FakeRepositories.kt:39-73`, `FakeImageDownloader.kt`, `.../domain/usecase/CardUseCasesTest.kt`, `composeApp/src/androidHostTest/kotlin/app/cardfilter/data/SqlRepositoriesTest.kt:26-61` | Fakes in `testutil`, `runTest`, `UnconfinedTestDispatcher` as Main; SQL behaviour tested on in-memory SQLite in `androidHostTest` |

## Files to Change
Paths relative to `composeApp/src` unless noted.

| File | Action | Why |
|---|---|---|
| `gradle/libs.versions.toml` (repo root) | UPDATE | `kotlinx-serialization-json` and the file-picker library entries |
| `composeApp/build.gradle.kts` | UPDATE | `commonMain` dependencies for both |
| `commonMain/kotlin/app/cardfilter/domain/importing/CardImportParser.kt` | CREATE | `ImportEntry`, `ImportError`, `ImportParse`, `parseCardImport(text)`, `MaxImportFileBytes`, `MaxImportCards` |
| `commonMain/kotlin/app/cardfilter/domain/importing/CardImportPlan.kt` | CREATE | Matching: `ImportPlan`, `CardWrite` (create or overwrite), `planImport(existing, entries)` including the in-file duplicate check |
| `commonMain/kotlin/app/cardfilter/domain/repository/CardRepository.kt` | UPDATE | `importCards(topicId, writes)` |
| `commonMain/sqldelight/app/cardfilter/data/db/CardImage.sq` | UPDATE | `replace` query (`INSERT OR REPLACE`) for overwrite |
| `commonMain/kotlin/app/cardfilter/data/SqlCardRepository.kt` | UPDATE | `importCards` in one transaction |
| `commonMain/kotlin/app/cardfilter/domain/usecase/ImportUseCases.kt` | CREATE | `PrepareCardImport` (parse + match, no side effects) and `ImportCards` (download with concurrency and progress, then one write, returns `ImportSummary`) |
| `commonMain/kotlin/app/cardfilter/AppContainer.kt` | UPDATE | `prepareCardImport`, `importCards` vals |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardImportViewModel.kt` | CREATE | State machine: Idle, Reading, Confirm, Importing(done, total), Done(summary), Failed(error) |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardListScreen.kt` | UPDATE | "Import" action, file picker launch, the four dialogs |
| `commonMain/kotlin/app/cardfilter/App.kt` | UPDATE | Build `CardImportViewModel` for the cards route and pass it in |
| `commonMain/kotlin/app/cardfilter/presentation/Strings.kt` | UPDATE | Import strings and `errorFor(ImportError)` |
| `commonTest/kotlin/app/cardfilter/testutil/FakeRepositories.kt` | UPDATE | `FakeCardRepository.importCards` |
| `commonTest/kotlin/app/cardfilter/domain/importing/CardImportParserTest.kt` | CREATE | Validation cases |
| `commonTest/kotlin/app/cardfilter/domain/importing/CardImportPlanTest.kt` | CREATE | Matching cases |
| `commonTest/kotlin/app/cardfilter/domain/usecase/ImportUseCasesTest.kt` | CREATE | Download, progress, cancel, summary, image rules |
| `commonTest/kotlin/app/cardfilter/presentation/cards/CardImportViewModelTest.kt` | CREATE | Flow and cancel |
| `androidHostTest/kotlin/app/cardfilter/data/SqlRepositoriesTest.kt` | UPDATE | `importCards` on real SQLite, including rollback |
| `DESIGN.md` (repo root) | UPDATE | Import action, dialogs, rule 8 note on import summary |
| `.claude/tdd/card-image-offline-and-bulk-import-milestone-3.tdd.md` | CREATE | RED/GREEN evidence and manual results |
| `.claude/prds/card-image-offline-and-bulk-import.prd.md` | UPDATE | M3 row → `in-progress`, Plan cell → this file (done at plan creation) |

No change to the schema, `FetchCardImage`, `KtorImageDownloader`, `AddCard`, `CardImage` or the card editor. No `Info.plist` or manifest change is expected for the picker (`OpenDocument` needs no permission); Task 1 confirms.

## Tasks
Set `JAVA_HOME` to Android Studio's bundled JDK as in earlier milestones before running Gradle. Every logic task is test-first: write the tests, see them fail, then implement.

### Task 1: Spikes — file picker and JSON library on both platforms
- **Action**: Pick versions and add the two dependency sets. In a throwaway composable: (a) open the system picker restricted to JSON, read a small file, and show its text on the Android emulator **and** the iOS simulator; record what a cancelled pick returns, how to get the file size before reading it, and that reading happens off the main thread; (b) call `Json.parseToJsonElement` on a few inputs on both targets and record: malformed text throws a catchable exception; a leading BOM (U+FEFF) is or is not rejected; duplicate keys in one object (last wins or error); a 1 MB file parses fast. If the picker fails to compile, link or run on either platform, **stop and use the paste-JSON dialog fallback** (and say so in the plan's status). The JSON library is not optional.
- **Mirror**: M1 and M2 Task 1 (spike with a stop-and-fall-back rule).
- **Validate**: `./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64` passes, and both behaviours are seen working on both platforms. Delete the throwaway code, keep the dependencies.

### Task 2: Parser and validation (tests first)
- **Action**: Write `CardImportParserTest` first, then `parseCardImport(text): ImportParse` (`Parsed(entries)` or `Invalid(error)`), reusing `validateCard` for title and attributes. Cases: the PRD sample parses to the expected entries; not JSON → `NotJson`; valid JSON that is not an array (an object, a string) → `NotAnArray`; empty array → `Empty`; more than `MaxImportCards` → `TooMany`; an entry that is not an object (a string, `null`) → `EntryNotObject(index)`; missing, blank or non-string `name` → `MissingName(index)`; `image` / `ingredients` / `month` of the wrong type (object, boolean, nested list) → `WrongType(index, field)`; `null` or blank optional fields are absent (no attribute); `ingredients` as a list joins with `, `; `month` `7` and `"7"` both give `7`; unknown keys ignored; the **first** problem in a file with several is the one reported, by entry order; whitespace around values is trimmed; Vietnamese and Japanese text survives unchanged; a BOM is handled as Task 1 found. `Invalid` carries a 1-based entry number for the messages. The file-size check is a separate function (`checkImportSize(byteCount)`) so the parser stays pure.
- **Mirror**: `CardValidation.kt:26-60` and the `TextFoldingTest.kt` table style.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`.

### Task 3: Matching and the in-file duplicate check (tests first)
- **Action**: Write `CardImportPlanTest` first, then `planImport(existing: List<Card>, entries: List<ImportEntry>): ImportPlanResult`. Cases: no existing cards → all `Create`; an entry matching an existing title → `Overwrite(id)`; match ignores case and surrounding spaces (`" wakodo A "` matches `"Wakodo a"`), does **not** fold diacritics; two existing cards with the same title → the lower id is overwritten; two entries with the same name in the file (any case) → `DuplicateInFile(index, firstIndex)`; an entry that overwrites keeps its attributes and image URL from the file; counts of creates and overwrites and the first five overwritten titles are available for the confirmation.
- **Mirror**: `CardSearchTest.kt` (pure-function tests, no coroutines).
- **Validate**: both test tasks above.

### Task 4: Repository `importCards` (tests first)
- **Action**: Add `importCards(topicId, writes: List<CardWrite>)` to `CardRepository` with a KDoc line that it stores what it is given and does not validate. In `SqlRepositoriesTest` write first, on in-memory SQLite: creates are inserted with attributes and images in one call; an overwrite keeps the id and `created_at` (list position unchanged), replaces title and the whole attribute list, and leaves no orphan attribute rows; an overwrite with an image replaces the old image (one `card_image` row); an overwrite with `image = null` **keeps** the old image; a mix of creates and overwrites in one call; **rollback**: install a SQLite trigger through the driver (`BEFORE INSERT ON card_attribute WHEN NEW.value = 'boom'` raising ABORT), call `importCards` with a write whose attribute value is `boom` as the last item, and assert that earlier creates and overwrites in the same call are **not** persisted. Then add the `replace` query, implement `SqlCardRepository.importCards` in one `database.transaction`, and add `FakeCardRepository.importCards`.
- **Mirror**: `SqlCardRepository.kt:39-62` and `SqlRepositoriesTest.kt:26-61` (the `imageRowCount` / `attributeRowCount` orphan checks).
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`. No migration: `./gradlew :composeApp:verifySqlDelightMigration` still passes with no new `.db` snapshot.

### Task 5: Import use cases (tests first)
- **Action**: Write `ImportUseCasesTest` first, then `PrepareCardImport(repository)` (reads the topic's cards once, parses, plans; no download, no write) and `ImportCards(repository, fetchCardImage)`. Cases for `ImportCards` with `FakeImageDownloader`: every entry with an image URL is downloaded once and its bytes stored; entries without a URL cause no request; a failed or non-image download saves the card without an image and counts in `imagesFailed`; a blank URL is not counted as failed; an overwrite whose download failed keeps the old image (checked through the fake repository); at most 4 downloads run at once (a downloader that suspends and records the peak); progress is reported once per entry in the range `0..total`; the summary reports created, overwritten and failed-image counts; **cancelling during the downloads writes nothing** (assert the repository is unchanged) and rethrows `CancellationException`; the write happens exactly once, after the last download; a repository failure propagates (the ViewModel turns it into `failure`).
- **Mirror**: `CardUseCasesTest.kt` fixtures, `FakeImageDownloader` (`respond` that suspends on a `CompletableDeferred`), `CardUseCases.kt:28-47`.
- **Validate**: both test tasks above.

### Task 6: ViewModel (tests first)
- **Action**: Write `CardImportViewModelTest` first, then `CardImportViewModel` taking `topicId`, `prepareCardImport`, `importCards`. `onFileChosen(read: suspend () -> String)` takes a reader so the ViewModel never sees the picker type. Cases: a valid file with no overwrites goes straight through to `Importing`, then `Done` with the summary; with overwrites it stops at `Confirm` first, and `onConfirmOverwrite` continues while `onCancel` returns to `Idle` having written nothing; an invalid file goes to `Failed(error)` and writes nothing; a reader that throws (unreadable file) goes to `Failed(Unreadable)`; a file over the size limit fails before parsing; progress states are published while a download is suspended; `onCancel` during `Importing` cancels the job, returns to `Idle` and leaves the repository unchanged; a second `onFileChosen` while busy is ignored; `onDismissResult` returns to `Idle`; a repository failure ends in `Failed(Storage)`; the state survives a new collector (it is a `StateFlow`).
- **Mirror**: `CardEditorViewModelTest.kt` (`CompletableDeferred` for suspended work) and `CardEditorViewModel.kt:111-116, 129-146` for the job and cancel handling.
- **Validate**: both test tasks above.

### Task 7: Screen, strings, wiring
- **Action**: In `App.kt` build `CardImportViewModel` for the cards route (`viewModel(key = "import-${topicId.value}")`) and pass it to `CardListScreen`. In the `TopAppBar` add an "Import" `TextButton` action (disabled while busy) that launches the picker restricted to JSON. The reader passed to `onFileChosen` checks the file size before reading. Show, by state: a progress dialog ("Importing 12 of 30…", determinate bar, Cancel, back cancels); the overwrite confirmation with `ConfirmDeleteDialog`; an error dialog with the message and one OK button; a result dialog with the summary and OK. Add the strings and `Strings.errorFor(ImportError)`. If Task 1 chose the fallback, replace the picker with a dialog holding a multi-line `DraftTextField` (paste JSON, Import button). Update `DESIGN.md` (new action, dialogs, "the import summary reports failed images; a single add stays silent").
- **Mirror**: `CardListScreen.kt:68-87, 150-160`, `Components.kt:81-101`, `App.kt:59-78`, DESIGN rules 1, 2, 3 and 8.
- **Validate**: `./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`.

### Task 8: Manual verification on both platforms
- **Action**: On the Android emulator **and** the iOS simulator, with the real sample file:
  1. Import the sample into an empty topic online: all cards appear with their thumbnails; open one and see the image in the editor.
  2. **Offline display**: network off, kill and relaunch: every imported card still shows its image.
  3. Invalid files, each ends in the matching error and an unchanged topic: not JSON, a JSON object instead of an array, an empty array, one entry with no `name` in the middle of otherwise valid entries (**none** of the valid ones appear), a wrong type, duplicate names in the file, more than 100 cards, a file over 1 MB.
  4. Import the same file again: the confirmation names the overwritten cards; Cancel changes nothing; confirming leaves one card per name (no duplicates), images still shown, list order unchanged.
  5. Edit one imported card's attributes, import again with overwrite: the edit is replaced, the image stays.
  6. Overwrite with an entry whose image URL is dead: the card text is replaced, the **old image stays**.
  7. Network off during a fresh import: all cards import without images; the summary says N images could not be downloaded.
  8. Cancel in the middle of a slow import (a URL that hangs): the dialog closes and the topic is unchanged. Kill the app mid-import: after relaunch the topic is unchanged.
  9. Rotate (Android) during the import: it continues and finishes once, not twice.
  10. Cancel the picker without choosing: nothing happens, no error.
  11. Time a 30-card import and record peak memory (Android profiler / Xcode) with the real images, against the 100 MB worst-case assumption. Record the PRD's "under 2 minutes" candidate result.
  12. Vietnamese and Japanese names and ingredients survive the round trip; dark mode for all four dialogs.
- **Mirror**: manual tables in `.claude/tdd/card-image-offline-and-bulk-import-milestone-2.tdd.md`.
- **Validate**: every step passes on both platforms. Record any platform-specific failure before closing.

### Task 9: Close out
- **Action**: Write `.claude/tdd/card-image-offline-and-bulk-import-milestone-3.tdd.md` (RED/GREEN per cycle, test counts, coverage, spike results, Task 8 results). Re-run the coverage report. Set the PRD M3 row to `complete` only after Task 8 passes and you have accepted the open decisions above. Add an "Implementation Status" section to this plan.
- **Validate**: PRD row matches reality.

## Validation
```bash
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:verifySqlDelightMigration
./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:coverageReport
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```
The `-destination ... ARCHS=arm64` flags are required; without them Xcode tries `x86_64` and fails.

## Implementation Status
**Complete (2026-11-05), set by the user.** Built test-first; Android host 280 and iOS simulator 243 tests pass. Verified with the real Wakodo sample and the real file picker on the Android emulator (13 checks) and the iOS simulator (2 UI tests). Evidence: [.claude/tdd/card-image-offline-and-bulk-import-milestone-3.tdd.md](../tdd/card-image-offline-and-bulk-import-milestone-3.tdd.md).
- The plan's decisions were confirmed as written (open topic, same trimmed title in the topic, unknown keys ignored, `month` as text, confirm before overwrite). The file picker library (FileKit) works on both platforms; a paste-JSON option was added later.
- **Follow-ups, not done:** see "Closed" in the evidence report (device checks of the size and count limits, offline overwrite keeping the image, 30-card timing, paste dialog, dark mode, rotation, iOS cancel/kill).

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| The file-picker library does not work with Kotlin 2.4.21 / Compose 1.12.1 / AGP 9.4.1 (the Android KMP library plugin) | Medium | Task 1 spike first; fallback is the paste-JSON dialog feeding the same parser |
| The real sample differs from the assumed shape (`ingredients` as a list, `month` as a number, other key names), so the real file is rejected | Medium | The parser accepts the likely variants; Task 2 uses the real file once supplied; Task 8 step 1 imports it for real |
| Overwrite silently destroys edits the user made to existing cards | Medium (PRD: High impact) | Confirmation naming the cards before any overwrite; image kept when the new one fails; the match rule is strict (same trimmed title, no fuzzy matching) |
| "Same name" is wrong for this data (two real products share a name) | Medium | In-file duplicates are an explicit error rather than a silent merge; against existing cards it overwrites one card only; the rule is in one function and easy to change |
| Holding all downloaded images in memory until the single write exhausts memory | Low | 100-card and 1 MB-per-image limits; measured in Task 8 step 11; if it is too high, lower the card limit before moving to per-batch writes (which gives up atomicity) |
| A slow host or captive portal makes a 100-image import take many minutes | Low | 4 at a time, Cancel always available and safe, M2 timeouts; a "stop after N consecutive failures" shortcut is possible if Task 8 shows it is needed |
| iOS suspends the app mid-import, downloads fail, and cards are saved without images | Medium | The summary reports the failed images; the cards are complete and can be re-imported (overwrite keeps the rest). No retry feature (out of scope) |
| `http://` image URLs in the sample all fail (cleartext blocked), so every card imports without images | Medium | Task 8 step 1 reveals it; the summary makes it visible; allowing cleartext is a security decision left to you, as in M2 |
| A third-party host forbids or blocks bulk hotlinking | Medium | Downloads are one request per image, 4 at a time, stored once; the PRD risk about acceptable use of the source images stays open |
| `parseToJsonElement` behaves differently on iOS (Kotlin/Native) for BOM, duplicate keys or very large input | Low | Task 1 records it on both platforms; Task 2 tests run on both test tasks |
| The rollback test relies on a SQLite trigger and so exercises the driver, not the app's own error path | Low | It is the only way to force a mid-transaction failure on real SQLite without a fault-injecting driver; the transaction boundary is the thing under test |
| Matching is read before the write, so a card added in between is missed | Very low | Single user, single device, the editor cannot run during the import dialog; accepted |
| PRD requirements are unvalidated (users, need and metrics are TBD) | High (inherited) | After M3, put the app in front of one real user with the Wakodo sample before further work |

## Acceptance
- [ ] All tasks complete
- [ ] `testAndroidHostTest`, `iosSimulatorArm64Test`, `verifySqlDelightMigration`, `assembleDebug`, iOS framework link and `xcodebuild` pass
- [ ] A valid file imports every card into the open topic, with images, and the images show with the network off after a restart, on Android and iOS
- [ ] Any malformed file or invalid entry shows an error and creates and changes nothing, including when the invalid entry is not the first
- [ ] A matching card is overwritten (title and attributes), in place, only after the user confirms; its image is replaced only by a newly downloaded one
- [ ] A failed image download never fails the import; the summary reports how many failed
- [ ] Cancel and a killed app leave the topic unchanged
- [ ] No database schema change and no migration
- [ ] Logic-layer coverage ≥ 80%
- [ ] The open decisions (picker library, overwrite and duplicate rules, the 100-card limit, the import summary) are confirmed or changed by you
