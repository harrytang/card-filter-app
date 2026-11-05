# TDD Evidence: Card Image (Milestone 1 of Card Images and Bulk Import)

**Source plan**: [.claude/plans/card-image-offline-and-bulk-import-milestone-1.plan.md](../plans/card-image-offline-and-bulk-import-milestone-1.plan.md)
**Source PRD**: [.claude/prds/card-image-offline-and-bulk-import.prd.md](../prds/card-image-offline-and-bulk-import.prd.md)
**Scope**: Milestone 1 only. A card can own one stored image and the card list and editor show it. No download, no image input, no import (Milestones 2 and 3).

Commands ran with `JAVA_HOME` set to Android Studio's bundled JDK and the project wrapper (Gradle 9.8.1). The directory is not a Git repository, so there are no checkpoint commits; this report is the evidence trail. RED here is mostly compile-time RED (the new tests reference API that did not exist yet), each time with every error pointing at the intended missing piece.

## User journeys (from the plan)
1. As someone consulting a card, I see its image in the list and when I open it, so the card is recognisable at a glance.
2. As someone editing a card, I can change its text without losing its image.
3. As someone deleting a card or a topic, no image data is left behind.
4. As someone upgrading the app, every card and attribute I already have is kept.
5. As someone whose stored image is unreadable, I see a normal card, not an error.

## Task report

| Task | Summary | Validation actually run | Result |
|---|---|---|---|
| 1. Decode spike | Added `compose.components.resources`; tried `ByteArray.decodeToImageBitmap()` on a 1x1 PNG, garbage and empty bytes | `compileAndroidMain compileKotlinIosSimulatorArm64`; an iOS-only throwaway test (`iosSimulatorArm64Test`); a throwaway `Log.i` in `MainActivity` run on the emulator | Compiles on both. PNG decodes on both. Garbage and empty bytes **throw**: `IllegalArgumentException: Failed to Image::makeFromEncoded` on iOS, `NullPointerException: decodeByteArray(...) must not be null` on Android. Throwaway code deleted; only the dependency stays |
| 2. Schema and migration | `card_image(card_id PK, bytes BLOB)`, migration `2.sqm`, snapshot `3.db` | RED: `testAndroidHostTest --tests MigrationTest` → 2 failed of 5 (`schemaVersionIsThree...`: version was 2; `...createsAnImageTable...`: `SQLiteException`, no table). GREEN: `generateCommonMainAppDatabaseSchema`, then `verifyCommonMainAppDatabaseMigration testAndroidHostTest` | PASS |
| 3. Repository and fakes | `Card.hasImage`, `create(..., image)`, `getImage`, `has_image` column, explicit image deletes in both repositories | RED: `testAndroidHostTest` failed to compile (`Too many arguments for 'create'`, `Unresolved reference 'getImage'`, `'hasImage'`). GREEN: `testAndroidHostTest --rerun` → 130 tests, 0 failed | PASS |
| 4. Use case and wiring | `GetCardImage`, `AppContainer.getCardImage` | RED: `Unresolved reference 'GetCardImage'`. GREEN: `testAndroidHostTest iosSimulatorArm64Test --rerun` → Android 133, iOS 103, 0 failed | PASS |
| 5. ViewModels | `CardListViewModel.loadImage(card)`, `CardEditorViewModel.hasImage` and `loadImage()`, `App.kt` wiring | RED: `No parameter with name 'getCardImage'`, `Unresolved reference 'loadImage'`/`'hasImage'`. GREEN: Android 142, iOS 112, 0 failed | PASS |
| 6. `CardImage` and screens | New composable, thumbnail in `CardRow`, read-only header in the editor, string, `DESIGN.md` | `assembleDebug linkDebugFrameworkIosSimulatorArm64`; `xcodebuild ... build` → `** BUILD SUCCEEDED **` | PASS (no unit test possible, see gaps) |
| 7. Manual verification | See the table below | emulator, simulator, `adb`, `sqlite3`, an added XCUITest | PASS with gaps listed |
| 8. Close out | This report, coverage, mutation check | `coverageReport`; two mutations | See below |

## Test specification

23 new test functions (one replaces the old schema-version test, so the Android host count rose by 22, from 120 to 142). The 12 that live in `commonTest` also run on the iOS simulator (100 to 112). Plus one new UI test.

| # | What is guaranteed | Test | Type | Result |
|---|---|---|---|---|
| 1 | Schema is version 3 | `MigrationTest.schemaVersionIsThreeAfterTheCardImageMigration` | integration | PASS |
| 2 | Upgrading from v2 keeps every card and its attributes (Vietnamese and Japanese text) | `MigrationTest.migratingFromVersionTwo_keepsCardsAndTheirAttributes` | integration | PASS |
| 3 | The migration creates a table that accepts an image for an old card | `MigrationTest.migratingFromVersionTwo_createsAnImageTableThatAcceptsAnImageForAnOldCard` | integration | PASS |
| 4 | A card created with image bytes reports `hasImage` and returns identical bytes through `getImage`, `get` and `observeByTopic` | `SqlCardRepositoryTest.create_withImage_reportsHasImageAndStoresTheBytes` | integration | PASS |
| 5 | A card created without an image has `hasImage = false`, `getImage` is null, and no row is stored | `SqlCardRepositoryTest.create_withoutImage_hasNoImageAndStoresNoRow` | integration | PASS |
| 6 | `getImage` of an unknown card is null | `SqlCardRepositoryTest.getImage_unknownCard_returnsNull` | integration | PASS |
| 7 | Two cards keep their own images | `SqlCardRepositoryTest.create_twoCards_eachKeepsItsOwnImage` | integration | PASS |
| 8 | A zero-byte image is stored as given (validation belongs to ingestion, M2/M3) | `SqlCardRepositoryTest.create_storesAZeroByteImageAsGiven` | integration | PASS |
| 9 | Updating a card's title and attributes keeps its image | `SqlCardRepositoryTest.update_keepsTheCardsImage` | integration | PASS |
| 10 | Deleting a card removes its image row (no orphan) and leaves other cards' images | `SqlCardRepositoryTest.delete_removesTheCardsImageRowButNotOtherCardsImages` | integration | PASS |
| 11 | Deleting a topic removes the images of its cards and leaves other topics' images | `SqlTopicRepositoryTest.delete_removesTheImagesOfItsCardsButNotOtherTopicsImages` | integration | PASS |
| 12-14 | `GetCardImage` returns the bytes, null without an image, null for an unknown card | `CardUseCasesTest.getCardImage_*` (3 tests) | unit | PASS |
| 15 | List state reports which cards have an image | `CardListViewModelTest.cardsReportWhetherTheyHaveAnImage` | unit | PASS |
| 16-17 | `loadImage` returns the bytes, and null for a card without one | `CardListViewModelTest.loadImage_returnsTheStoredBytes`, `..._returnsNullForACardWithoutAnImage` | unit | PASS |
| 18 | A repository failure while loading gives null and does **not** set `failure` | `CardListViewModelTest.loadImage_returnsNullWithoutRaisingFailureWhenTheRepositoryThrows` | unit | PASS |
| 19 | Editing a card with an image exposes it and does not make the draft dirty | `CardEditorViewModelTest.editingACardWithAnImage_exposesItWithoutMakingTheDraftDirty` | unit | PASS |
| 20-21 | A card without an image, and a new card, have none | `CardEditorViewModelTest.editingACardWithoutAnImage_hasNoImage`, `newCard_hasNoImage` | unit | PASS |
| 22 | Saving an edited card keeps its image | `CardEditorViewModelTest.savingAnEditedCard_keepsItsImage` | unit | PASS |
| 23 | Editor `loadImage` failure gives null without `failure` | `CardEditorViewModelTest.loadImage_returnsNullWithoutRaisingFailureWhenTheRepositoryThrows` | unit | PASS |
| 24 | Through the real iOS UI: a stored image shows in the editor (a "Card image" element exists); an undecodable image and a card without an image show none. Light and dark | `CardFilterUITests.testCardImagesShowInTheListAndTheEditor` (skips if the seeded topic is absent) | UI | PASS (light and dark) |

Totals after the milestone: **Android host 142 tests, iOS simulator 112 tests, 0 failed, 0 skipped** (before: 120 and 100). `verifyCommonMainAppDatabaseMigration`, `assembleDebug`, `linkDebugFrameworkIosSimulatorArm64` and `xcodebuild build` pass.

## Mutation check
Two mutations applied together, expecting exactly the two named tests to fail:
- `SqlCardRepository.delete` without `cardImages.deleteByCard` → `delete_removesTheCardsImageRowButNotOtherCardsImages` FAILED.
- `SqlTopicRepository.delete` without `cardImages.deleteByTopic` → `delete_removesTheImagesOfItsCardsButNotOtherTopicsImages` FAILED.

Result: `142 tests completed, 2 failed`, only those two. Both reverted; the full suites were rerun green afterwards.

## Manual verification (Task 7)

Test images were generated with the Python standard library (a recognisable 320x240 PNG, a 1,045,273-byte and a 504,928-byte incompressible PNG, and 93 bytes of garbage) and seeded with `sqlite3`'s `readfile()`. Emulator: `booklet-api36`. Simulator: iPhone 18 Pro.

| Step | Android emulator | iOS simulator |
|---|---|---|
| 1. Seeded card shows its image in the list and the editor; a card without one is unchanged | PASS (screenshots reviewed) | PASS (screenshots reviewed) |
| 2. Rotate in the editor | PASS, image reloads once, fields intact | n/a |
| 3. Garbage bytes: no image, no crash, no snackbar, no gap | PASS | PASS (UI test asserts it) |
| 4. ~1 MB image reads back | PASS (1,045,273 bytes displayed). Not tested above 1.0 MB | PASS |
| 5. Edit title and save keeps the image; deleting the card leaves no `card_image` row | PASS: image row still 146,537 bytes after save; after delete the card's rows in `card`, `card_attribute` and `card_image` were 0 and the orphan query returned 0 | Covered by unit tests only, not driven through the iOS UI |
| 6. Install over an existing v2 database | PASS: 3 topics, 159 cards, 314 attributes before and after; identical fingerprints (counts, summed lengths, summed ids); `user_version` 2 → 3 | PASS: 1 topic, 3 cards, 2 attributes; identical fingerprints; `user_version` 2 → 3 |
| 7. 100+ cards with many images, scrolling | No crash, no `OutOfMemoryError`, native heap ~25 MB after scrolling 150 cards with 50 images. **Frame timing inconclusive** (see gaps) | not measured |
| 8. Dark mode | PASS (list and editor screenshots) | PASS (list screenshot; the same UI test passed) |

## Coverage
`./gradlew :composeApp:coverageReport` (line coverage, Android host tests): `SqlCardRepository.kt` 100%, `SqlTopicRepository.kt` 100%, `CardUseCases.kt` 100%, `CardEditorViewModel.kt` 100%, `CardListViewModel.kt` 98%. `CardImage.kt` and all screens: 0% (no automated way to run Compose UI or decode bitmaps on the JVM here). A rough split of the whole report into logic and UI files gives 88.9% for the logic bucket, but that bucket includes wiring such as `AppContainer.kt` that has no test, so the per-file numbers above are the meaningful ones.

## Deviations from the plan
- **No separate index on `card_image`**: the primary key on `card_id` is already the lookup index.
- **Snapshot task must run alone**: `generateCommonMainAppDatabaseSchema` cannot share an invocation with `verifyCommonMainAppDatabaseMigration` (Gradle reports an implicit dependency). The plan's Validation block is updated to two commands.
- **`CardImage` takes an `imageKey`** and reads `load` through `rememberUpdatedState`; without it a fresh lambda on every recomposition would reload the image each time.
- **Failure behaviour**: the plan said a failed decode shows nothing. Implemented as: a neutral tile while loading, then the composable draws nothing and takes no space if the bytes are not an image.
- **Thumbnail is decorative** (`contentDescription = null`) because the title beside it already names the card; the editor image is described as "Card image". The plan said to describe it from the card title.
- **iOS UI test added** (`testCardImagesShowInTheListAndTheEditor`). It needs a hand-seeded "Images" topic and skips otherwise, so a clean-install run of the suite is unaffected.
- **`3.db` generated by the Gradle task**, as planned; `compose.components.resources` is a new dependency used only for `decodeToImageBitmap` (no resource files).
- **No Git**: no checkpoint commits could be made.

## Known gaps and honest limits
- **Scrolling smoothness is not established.** The first Bulk-topic run read 392 frames, 9.7% janky, p50 17 ms. The later "warm" comparison, run with the images and with them temporarily removed, gave about 120-130 ms median in both cases and 97-100% janky frames, so the emulator was slow regardless and the images showed no clear extra cost. That comparison cannot support a claim either way. Needs a physical device.
- Android: nothing above ~1 MB was read back, and the ~2 MB cursor-window limit behind the plan's 1 MB cap was not tested directly.
- iOS: card edit/delete with an image was not driven through the UI (unit tests only); rotation not applicable.
- No physical device on either platform.
- Compose UI (`CardImage`, the screens) has no automated tests; `CardImage`'s decode and failure path is covered by the manual steps and the iOS UI test, not by JVM tests.
- Test data on the dev devices was changed: on the emulator, the Pigeon card "Cháo thịt bầm" was edited and then deleted during step 5, and 53 seeded images remain; the simulator database has an extra "Images" topic with four cards.

## Merge evidence
Not applicable (no Git history to squash).
