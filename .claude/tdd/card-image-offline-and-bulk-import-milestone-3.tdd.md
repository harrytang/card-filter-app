# TDD Evidence: Bulk Import (Milestone 3)

**Source plan**: [.claude/plans/card-image-offline-and-bulk-import-milestone-3.plan.md](../plans/card-image-offline-and-bulk-import-milestone-3.plan.md)
**Status**: Tasks 2-7 built test-first and green on Android host and iOS simulator. **Task 8 (manual verification) is partly done: the import flow was driven on an Android emulator; nothing was run on iOS.** The PRD M3 row stays `in-progress`: it is set to `complete` only after the remaining Task 8 steps and your decisions (see the end).

Gradle KMP project (not npm). Tests: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`. The folder is not a Git repository, so no checkpoint commits were made. RED in every cycle was a compile failure on the new symbols (the tests reference code that did not exist), then GREEN after the minimal implementation.

## User journeys (from the plan)
1. As a person building a topic from a product list, I pick a JSON file and get all its cards, with images, in the open topic.
2. As that person, if the file is wrong anywhere, I see what is wrong and nothing is imported.
3. As that person, importing a list again replaces the cards with the same name after I confirm, and never loses a good image to a failed download.
4. As that person, I can cancel an import at any point before it finishes and the topic stays as it was.

## Task report
| Task | Summary | RED | GREEN |
|---|---|---|---|
| 1 Spikes | Added `kotlinx-serialization-json` 1.11.0 and FileKit 0.16.0 (newest versions older than two weeks; 1.12.0 and 0.17.0 were 13 and 4 days old). Both targets compile and link. | n/a | `compileAndroidMain compileKotlinIosSimulatorArm64` passed; picker runs on Android (see manual table) |
| 2 Parser | `parseCardImport`, `checkImportSize`, error types | compile errors: `Unresolved reference 'ImportEntry'`, `'parseCardImport'` | `CardImportParserTest` 23/23 on both targets |
| 3 Matching | `planImport`, `ImportPlan`, `PlannedCard`, `CardWrite` | `Unresolved reference 'planImport'`, `'ImportPlan'` | `CardImportPlanTest` 10/10 on both |
| 4 Repository | `CardRepository.importCards`, `card_image.replace`, `SqlCardRepository.importCards`, fake | `Unresolved reference 'importCards' on receiver of type 'SqlCardRepository'` | 7 new `importCards_*` tests pass (`SqlCardRepositoryTest` 24/24); `verifySqlDelightMigration` passes with no new snapshot |
| 5 Use cases | `PrepareCardImport`, `ImportCards`, `ImportSummary` | `Unresolved reference 'PrepareCardImport'`, `'ImportCards'` | `ImportUseCasesTest` 19/19 on both |
| 6 ViewModel | `CardImportViewModel`, `CardImportState`, `ImportFailure` | `Unresolved reference 'CardImportState'` | `CardImportViewModelTest` 14/14 on both |
| 7 Screen | Strings (tested), dialogs, Import action, picker, wiring, DESIGN.md | `Unresolved reference 'importSummary'` (strings tests) | `ImportStringsTest` 5/5 on both; `assembleDebug`, iOS framework link and `xcodebuild` pass |

Totals after the milestone: **Android host 266 tests, iOS simulator 229 tests, 0 failures, 0 skipped** (M2 ended at 188 and 158; 78 and 71 new).

## Test specification
| # | What is guaranteed | Test | Type | Result |
|---|---|---|---|---|
| 1 | A Wakodo-style entry parses to title, `ingredients` and `month` attributes and an image URL | `CardImportParserTest.parses_aWakodoStyleEntry` | unit | PASS |
| 2 | Not JSON, not an array, empty and over 100 cards each give their own error | `notJson_isReported`, `jsonThatIsNotAnArray_isReported`, `emptyArray_isReported`, `moreThanTheLimit_isReported`, `exactlyTheLimit_isAccepted` | unit | PASS |
| 3 | A bad entry is reported with its 1-based number; the first problem wins; name is checked before other fields | `entryThatIsNotAnObject_...`, `missingBlankOrNonTextName_...`, `firstProblem_isTheOneReported` | unit | PASS |
| 4 | Wrong-typed `image`, `ingredients` or `month` is an error, not a guess | `imageOfTheWrongType_...`, `ingredientsOfTheWrongType_...`, `monthOfTheWrongType_...` | unit | PASS |
| 5 | `null`/blank optional fields are absent; list ingredients join with `, `; `7` and `"7"` give `7`; unknown keys ignored; values trimmed | `nullAndBlankOptionalFields_areAbsent`, `ingredientsGivenAsAList_...`, `monthAsNumberAndAsText_...`, `unknownKeys_areIgnored`, `valuesAreTrimmed` | unit | PASS |
| 6 | Vietnamese and Japanese text survive; a leading BOM is ignored; duplicate keys: the last wins (identical on Android and iOS) | `vietnameseAndJapaneseText_...`, `leadingByteOrderMark_isIgnored`, `duplicateKeysInOneEntry_lastOneWins` | unit | PASS |
| 7 | Matching is by trimmed title, ignoring case (including non-ASCII), not folding diacritics; oldest wins among existing duplicates | `CardImportPlanTest.matching_*`, `withSeveralExistingCardsOfTheSameTitle_theOldestIsOverwritten` | unit | PASS |
| 8 | Two entries with the same name in the file are rejected, naming both | `twoEntriesWithTheSameName_areRejectedNamingBoth`, `theFirstInFileDuplicate_isTheOneReported` | unit | PASS |
| 9 | Overwrite keeps the id and list position, replaces title and all attributes, leaves no orphan rows | `SqlCardRepositoryTest.importCards_overwrite_keepsIdAndListPosition...` | integration (SQLite) | PASS |
| 10 | An overwrite replaces the image only when it carries one; otherwise the old image is kept | `importCards_overwriteWithAnImage_*`, `importCards_overwriteWithoutAnImage_keepsTheOldImage` | integration | PASS |
| 11 | **Atomic write**: a failure on the last item (forced by a SQLite trigger) leaves earlier creates, overwrites and images unstored | `importCards_whenAWriteFails_noneOfTheCallIsStored` | integration | PASS |
| 12 | Preparing an import downloads and writes nothing; an invalid file writes nothing | `ImportUseCasesTest.prepare_*` | unit | PASS |
| 13 | Each image is downloaded once; entries with no URL make no request; a failed, non-image or non-http image saves the card without it and is counted | `import_storesEveryImageOnce...`, `import_anEntryWithoutAnImageUrl_...`, `import_aFailedDownload_...`, `import_bytesThatAreNotAnImage_...`, `import_anAddressThatIsNotHttp_...` | unit | PASS |
| 14 | An overwrite whose download failed, or whose entry has no image, keeps the old image; a new image replaces it | `import_anOverwriteWhoseDownloadFailed_...`, `import_anOverwriteWithAnEntryWithoutAnImage_...`, `import_anOverwriteWithANewImage_...` | unit | PASS |
| 15 | At most 4 downloads at once; one write for the whole import after the last download, in file order; progress runs 0..total | `import_runsAtMostFourDownloadsAtOnce`, `import_writesAllCardsInOneCall...`, `import_reportsProgress...` | unit | PASS |
| 16 | **Cancelling during the downloads writes nothing**; a failing write propagates | `import_cancelledDuringTheDownloads_writesNothing`, `import_aFailingWrite_propagates` | unit | PASS |
| 17 | The flow: straight through without overwrites; a confirmation first with overwrites (nothing written until confirmed); Cancel at the confirmation, while reading or while importing leaves the topic unchanged | `CardImportViewModelTest.*` | unit | PASS |
| 18 | Invalid file, unreadable file, over-size file and storage failure end in the matching `Failed` state with nothing written | `anInvalidFile_...`, `aFileThatCannotBeRead_...`, `aFileTheReaderRejectsAsTooLarge_...`, `aFailingWrite_...` | unit | PASS |
| 19 | A second file while busy is ignored; dismissing a result returns to idle | `aSecondFileWhileBusy_isIgnored`, `dismissingAResult_returnsToIdle` | unit | PASS |
| 20 | Summary and error wording: pluralisation, "(N replaced)", failed-image line, "and N more", every error has a message | `ImportStringsTest.*` | unit | PASS |

## Coverage
`./gradlew :composeApp:coverageReport` (Android host): `CardImportParserKt` 98% lines / 97% branches, `CardImportPlanKt` 100% / 100%, `ImportPlan` 100%, `SqlCardRepository.importCards` 100% / 100%, `CardImportViewModel` 97% lines / 68% branches, `Strings` 71% lines. Overall 45% lines (the Compose UI has no automated tests, a known gap since M1). The logic layers are above the 80% target.

## Manual verification (Task 8)
Android emulator (API 36, AVD `booklet-api36`), driven with `adb` and `uiautomator` (throwaway scripts in the session scratchpad). Sample files: 3 cards, two with real `https` images from gstatic (45 KB and 88 KB), one with a dead image URL, `ingredients` once as text and once as a list, `month` once as text and once as a number, Vietnamese and Japanese text.

| Plan step | Result |
|---|---|
| Picker opens and returns a file (Task 1a) | PASS on Android: the system picker opens with no extra `FileKit.init`, `.json` files are selectable with no type filter |
| 1 Import into an empty topic online | PASS: 3 cards; the two valid images show as thumbnails; Vietnamese/Japanese text intact; list `ingredients` joined; numeric `month` shown as text; summary "Imported 3 cards. 1 image could not be downloaded." |
| 3 Invalid file (valid first entry, second has no name) | PASS: dialog "Card 2 has no name. Nothing was imported."; the topic stayed empty (the valid first entry was not imported) |
| 4 Import the same file again | PASS: confirmation names the 3 cards; Cancel changed nothing; confirming gave "Imported 3 cards (3 replaced)." and still exactly 3 cards (no duplicates), same order |
| 6/7 Overwrite with the network off (airplane mode, ping unreachable) | PASS: all 3 downloads failed ("3 images could not be downloaded") and the two cards that had images **kept** them |
| 2 Offline display after restart | PARTLY: the images displayed with airplane mode on; the app was not killed and relaunched |
| Not run | iOS simulator (the picker, the whole flow); cancel mid-import and killing the app mid-import (needs a request that hangs; cleartext `http://` is blocked so a local server cannot be used, and the https images answer in milliseconds); rotation during import; more than 100 cards and over 1 MB files on a device (covered by unit tests only); 30-card timing and peak memory; dark mode; real Wakodo sample (not in the repo, so `ingredients`/`month` shapes are still assumed) |

## Deviations and notes
- **Picker has no file type filter** (plan said "restricted to JSON"): some Android file managers label `.json` as plain text and would grey it out. A non-JSON file ends in "This file is not valid JSON."
- Added `ImportFailure` (thrown by the file reader for the over-size case) so the ViewModel can show the exact error and stay testable without the picker type.
- `FakeCardRepository.importCalls` was added so tests can tell "written once" from "never written".
- Plan Task 4 rollback uses a SQLite trigger through the test driver; it proves the transaction boundary, not the app's own error path.
- The spike behaviours are pinned as tests on both platforms: BOM stripped by our code, duplicate keys: last wins.
- Ktor was already in place from M2; no change there.

## Known gaps and decisions still open
- iOS is untested beyond compile, framework link and `xcodebuild`. The FileKit picker on iOS has not been seen working; this was the main risk in the plan.
- Cancel and kill mid-import are covered only by unit tests (`import_cancelledDuringTheDownloads_writesNothing`, `cancellingWhileImporting_...`).
- The decisions in the plan (picker library, same-name rule, in-file duplicates are an error, 100 cards / 1 MB, import summary shows failed images) are implemented as proposed; they still need your confirmation. The real sample JSON is still needed to confirm field shapes.

## Addendum: the real Wakodo sample (2026-11-05)
Source: the user pasted the PRD's sample file (6 products) and confirmed the plan's defaults (open topic, same trimmed title in the topic is the same card, unknown keys ignored, `month` stored as text, confirm before overwrite). Saved byte for byte as `composeApp/src/commonTest/kotlin/app/cardfilter/testutil/WakodoSample.kt` (common tests cannot read resource files). Not a Git repo, so no checkpoint commits.

**Real image URLs, checked with `curl` (Ktor, OkHttp and CFNetwork user agents give identical results):** all 6 are `https`, HTTP 200, `image/jpeg`, no redirect, 143,291 to 175,976 bytes (about 0.94 MB in total). So the M2 follow-up risks did not materialise for this host: no `http://`, no user-agent block, no image near the 1,000,000-byte cap, and no hotlink block from a non-browser client. This was `curl` only; the app has not yet downloaded these images itself.

**Tests added (characterization, not RED: the parser already handled this shape):**
- `CardImportParserTest`: the sample parses to 6 cards; image URLs, months `7,7,9,9,12,12` and the `ingredients`/`month` attributes are exact; the Japanese and English title and ingredients text are unchanged; the 6 names are distinct (so no in-file duplicate error).
- `ImportUseCasesTest`: importing the sample stores 6 cards each with its own image in file order (each fake image carries its URL); importing it twice leaves the same 6 ids in the same order, reports 6 overwritten, and keeps all 6 images when every download fails.
- First run of the end-to-end test failed because of a bug in the test (all six URLs have the same length, so a size-based lookup matched six images); fixed in the test, production code unchanged.
- **Mutation checks:** dropping the numeric `month` branch fails the sample parser test; renaming the `image` key fails the parser test and both end-to-end tests. The parser was restored byte for byte.
- Totals: Android host 277 and iOS simulator 240 tests, 0 failing (was 272 and 235).

**Findings from the sample:** `ingredients` is a single bilingual string (not a list) and `month` is a number, so the assumed shapes were right. Titles are long (about 110 characters, Japanese then an English translation in brackets), so a card list row will show them truncated or wrapped; this has not been looked at on a device.

## Addendum: Android emulator, real Wakodo sample (2026-11-05)
Emulator API 36 (`booklet-api36`), driven with `adb` and `uiautomator` (throwaway scripts in the session scratchpad). The sample was pushed to `/sdcard/Download` and chosen through the real system file picker ("From file"); the picker only lists a pushed file after a media scan.

| Check | Result |
|---|---|
| Import the real 6-product sample into a new topic, online, from the file picker | PASS: "Imported 6 cards." with no failed-image line (all six real `wakodo.co.jp` images downloaded), about 3 s; the list shows the six real product photos as thumbnails and the Japanese and English text unchanged (screenshot checked) |
| Network off (failed ping), app killed and relaunched | PASS: six cards listed; the first card's editor shows its image (offline display) |
| Editor shows the full title | PASS: the whole bilingual title; in the list it is clamped to three lines, so the English part is cut with an ellipsis |
| Import the same file again | PASS: "Replace existing cards?" naming the 6 cards, button "Overwrite and import"; Cancel leaves six cards; confirming gives "Imported 6 cards (6 replaced)." and still exactly six cards |
| Invalid file (valid first entry, second has no name) | PASS: "Card 2 has no name. Nothing was imported."; the valid first entry was not imported |
| Cancel during a slow import (two image URLs that never answer) | PASS: "Importing 0 of 2…" appears; Cancel, and 14 s later (past the 10 s connect timeout) the topic is unchanged |
| Kill the app during a slow import | PASS: after relaunch the topic is unchanged |

Not run on Android: rotation during an import, a file over 1 MB or over 100 cards on a device (unit tests only), the paste-JSON dialog, a 30-card timing and peak memory, dark mode of the four dialogs, and the old image being kept when an overwrite's download fails (unit-tested; on the device the re-import had working downloads). A real 6-card import takes about 3 s, well inside the PRD's "under 2 minutes" candidate for 30 cards, but 30 cards were not timed.

## Addendum: iOS simulator, real file picker and real Wakodo sample (2026-11-05)
iPhone 18 Pro simulator, real app, real FileKit document picker. The sample files were copied into the simulator's "On My iPhone" storage (the app group `group.com.apple.FileProvider.LocalStorage`, folder `File Provider Storage`), so the picker lists them. Two UI tests were added to `CardFilterUITests.swift`; both pass on a clean install:
- `testImportsTheWakodoSampleFromTheFilePicker` (47 s): Import > From file > `wakodo.json`; result "Imported 6 cards." with no failed-image line; the list shows the six real product photos (screenshot checked) and the editor shows "Card image"; importing it again shows "Replace existing cards?" naming the cards (screenshot checked), and Cancel leaves the list unchanged.
- `testAnInvalidFileFromThePickerImportsNothing` (28 s): a file whose second entry has no name shows "Nothing was imported." and the topic still says "No cards yet".

This closes the plan's main risk: the picker library works on iOS with Kotlin 2.4.21 and Compose 1.12.1, and the whole flow runs there.

Two bugs in my own tests while writing these (production code unchanged): the first version looked for any element whose label contained "wakodo", which matched the topic named "Wakodo import" behind the picker, so the file was never tapped (topic renamed "Sample products"); and one assertion expected cards 5 and 6 on screen in a lazy list.

**Seen in the screenshots:** the overwrite confirmation lists up to five full titles, and each Wakodo title is about 110 characters, so the dialog fills most of the screen. It works, but listing shortened titles would be easier to read. Not changed; a design choice for you.

Not run on iOS: overwriting with the network off (image kept), cancel and kill mid-import, a file over 1 MB or over 100 cards, the paste-JSON dialog, dark mode of the import dialogs, and rotation.

## Closed (2026-11-05)
The PRD M3 row was set to `complete` by the user with these gaps still open and recorded as follow-ups: overwriting with the network off on a device, a file over 1 MB or over 100 cards on a device, timing a 30-card import, the paste-JSON dialog on a device, dark mode of the import dialogs, rotation (Android), and cancel/kill mid-import on iOS.

**Wording fix after closing:** the overwrite confirmation printed up to five full titles (about 110 characters each) and filled most of the screen. `Strings.overwriteMessage` now shortens each listed title to 30 UTF-16 units including "…" and never splits a character made of two units.
- RED: `overwriteMessage_shortensLongTitlesSoTheDialogStaysReadable` and `overwriteMessage_neverSplitsACharacterMadeOfTwoUnits` failed at runtime (the exact-limit test already passed).
- GREEN: Android host 280 and iOS simulator 243 tests, 0 failing; APK builds, iOS framework links; the iOS import UI test passes and the dialog is about half its previous height (screenshot checked).
- Not changed: the card list still clamps each title to three lines, so the bracketed English part is cut with an ellipsis there (the editor shows it whole).

## Addendum: paste JSON (2026-11-05)
Source: `/ecc:plan import feature support paste json file`. Not a Git repo, so no checkpoint commits.
- RED: new tests in `CardImportViewModelTest` (`onTextPasted`: valid, not JSON, over the limit counted in bytes, overwrite confirm) and `ImportStringsTest` (menu labels, no "file" in shared errors) failed to compile on the missing `onTextPasted` and `Strings.importFromFile`.
- GREEN: `./gradlew :composeApp:testAndroidHostTest :composeApp:compileKotlinIosSimulatorArm64` passes.
- Changes: `CardImportViewModel.onTextPasted`, `PasteJsonDialog`, Import dropdown in `CardListScreen`, neutral error wording (file -> JSON).
- Gaps: the dropdown and dialog have no automated UI test and were not run on a device. Dialog closing on error and the 1 MB cap on pasted text are as planned; a very large paste in the text field is untested for lag.

