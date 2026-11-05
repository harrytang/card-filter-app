# Plan: Card Image (Milestone 1 of Card Images and Bulk Import)

**Source PRD**: .claude/prds/card-image-offline-and-bulk-import.prd.md
**Selected Milestone**: 1 — Card image ("A card can have an image, and cards with an image display it")
**Complexity**: Medium (one new table + migration, one image-decoding spike on two platforms, small UI change)
**Depends on**: the card-filter M1-M3 code (built). Those milestones are still `in-progress` in the main PRD only for manual close-out items; none of them blocks this plan.

## Summary
A card can own one optional image, stored on the device, and the card list row and the card editor show it. This milestone builds the storage and the display only. There is no way to *attach* an image from the UI yet: that is Milestone 2 (download from a URL when a card is added). Milestone 1 is therefore verified by repository tests plus an image seeded straight into the database on a device (the same approach M3 used for Android data). Choosing storage now is the real work: it also decides how Milestone 2 downloads and Milestone 3 imports and overwrites.

## Decisions Made in This Plan (confirm or override)
| Decision | Choice | Why |
|---|---|---|
| Where image bytes live | A new table `card_image(card_id PK, bytes BLOB)`, one row per card | One transaction covers card + image, so import (M3) is all-or-nothing for free. Deleting or overwriting a card removes the image in the same transaction, which answers PRD open question 10. No platform file-path code (the repo has no `expect`/`actual` for storage). **Alternative**: files in app storage with the path in `card`. Better for very large images, but needs per-platform directories, orphan cleanup, and is not covered by JVM host tests |
| Separate table, not a column on `card` | Yes | `selectWithAttributesByTopic` loads every card of a topic on every change; a BLOB column would drag all images through that query. The list query only asks "does this card have an image" |
| Domain shape | `Card.hasImage: Boolean = false`; bytes fetched on demand via `CardRepository.getImage(id): ByteArray?` | Keeps `Card` cheap and `assertEquals` on `Card` valid (a `ByteArray` field in a data class compares by reference). Default value keeps every existing test compiling |
| Editing a card keeps its image | `update` never touches `card_image` | PRD: editing or replacing an image is out of scope. Without this, saving a title change would silently erase the image |
| Image size cap | **1 MB per image**, enforced when an image is stored (applied in M2/M3 ingestion; the repository only stores what it is given) | Android's SQLite cursor window is about 2 MB per row, so a larger BLOB can fail to read back. Needs a check in Task 7. Number is **yours to confirm** (PRD open question 9) |
| Decoding bytes to a bitmap | Spike first (Task 1): Compose Multiplatform resources `ByteArray.decodeToImageBitmap()` in common code. Fallback: one `expect fun decodeImage(bytes): ImageBitmap?` with an `actual` per platform | Keeps UI in `commonMain`. The repo has no image code, so this is unproven here |
| Decode failure | Treated as "no image": the composable shows nothing, no crash | Stored bytes might not be a valid image (truncated, or HTML error page saved by a bad URL in M2). M2 also validates, but the display must not trust that |
| Where the image shows | Card list row (thumbnail) and the card editor (header, read-only) | PRD says "the card displays the image". The editor is the only card detail view |
| Loading images in the list | Per row, lazily, from a `loadImage` lambda, off the main thread, via `produceState` | Rows scroll in and out of a `LazyColumn`; loading only visible rows keeps memory flat. Decoded bitmaps are not cached beyond `remember` in this milestone (measure in Task 7) |
| Schema version | v2 → v3 with a real migration `2.sqm`, new snapshot `3.db` | Same path M2 took for `card_attribute`. Dev databases are already at v2 |
| Not doing in M1 | Any UI to add an image, downloading, the Ktor dependency, `INTERNET` permission, image URL field | Milestone 2. Nothing in this plan needs network |

## Carried to Milestones 2 and 3 (not built here, but decided here so M1 does not box them in)
- **M2** adds an "Image URL" field to the new-card editor only (PRD: no editing or replacing). Save downloads once, checks the 1 MB cap and that the bytes decode, then stores them. On any failure the card is saved without an image. Needs: an HTTP client (Ktor with `HttpTimeout`: an offline Save must not hang), `android.permission.INTERNET` (the manifest has none today), and a check of iOS App Transport Security if any sample URL is `http://`. The downloader sits behind an interface in `domain` so tests use a fake, as with the repositories.
- **M3** needs "same card" = same title within the same topic (PRD working assumption) and an overwrite that replaces attributes and the image in one transaction. `CardRepository` will need a find-by-title lookup then; it is not added now.
- The PRD's sample JSON is **not in the repo**. Put it under `composeApp/src/commonTest/resources/` before M3; it is also the only source of real image URLs for M2.

## Patterns to Mirror
| Category | Source | Pattern |
|---|---|---|
| Naming | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt:12-18` | One small class per use case with `operator fun invoke` (`GetCard`); `GetCardImage` follows it |
| Errors | `.../presentation/Attempt.kt` and `CardListViewModel.onDeleteConfirmed` | Repository exceptions go through `attempt`. Image loading is the one exception: a failed load shows no image instead of raising the shared failure snackbar, because a missing image is a normal state in this PRD |
| Data access | `.../data/SqlCardRepository.kt:24-28, 34-42` | Reads via `asFlow().mapToList(ioDispatcher)`; writes via `withContext(ioDispatcher)` + `database.transactionWithResult` |
| Cascade | `.../data/SqlCardRepository.kt:54-63`, `.../data/SqlTopicRepository.kt:39-48` | Children deleted **explicitly**, deepest first, in one transaction; no `ON DELETE CASCADE`. `card_image` joins that list in both `delete` methods |
| Schema | `.../sqldelight/app/cardfilter/data/db/CardAttribute.sq`, `.../sqldelight/migrations/1.sqm` | Table in its own `.sq`, index on the FK column, migration file copies the `CREATE TABLE` verbatim |
| Join row | `.../data/SqlCardRepository.kt:70-92` | `toRow(...)` maps one SQL row; gets one more column (`has_image`) |
| UI | `.../designsystem/Components.kt` (`EmptyState`, `DraftTextField`), `.../presentation/cards/CardListScreen.kt` (`CardRow`) | Stateless composables, `Spacing` tokens, strings from `Strings`, no logic in the composable |
| Tests | `.../androidHostTest/.../SqlRepositoriesTest.kt` (`TestDatabase`, `ticking()`), `.../androidHostTest/.../MigrationTest.kt`, `.../commonTest/.../testutil/FakeRepositories.kt` | In-memory JDBC SQLite for the data layer; a verbatim copy of the previous schema for migration tests; fakes in `commonTest` |
| expect/actual | `.../domain/search/TextFolding*.kt` (M3) | Only if the decode spike fails: one `expect fun` + one `actual` per source set, nothing more |

## Files to Change
Paths relative to `composeApp/src` unless noted.

| File | Action | Why |
|---|---|---|
| `commonMain/sqldelight/app/cardfilter/data/db/CardImage.sq` | CREATE | `card_image` table; `insert`, `selectByCard`, `deleteByCard`, `deleteByTopic` |
| `commonMain/sqldelight/migrations/2.sqm` | CREATE | `CREATE TABLE card_image` for databases at v2 |
| `commonMain/sqldelight/databases/3.db` | CREATE (generated) | Schema snapshot used by migration verification; produced by the Gradle task, not hand-written |
| `commonMain/sqldelight/app/cardfilter/data/db/Card.sq` | UPDATE | Both `selectWithAttributes*` queries gain `EXISTS(SELECT 1 FROM card_image WHERE card_id = card.id) AS has_image` |
| `commonMain/kotlin/app/cardfilter/domain/model/Card.kt` | UPDATE | `hasImage: Boolean = false` |
| `commonMain/kotlin/app/cardfilter/domain/repository/CardRepository.kt` | UPDATE | `create(..., image: ByteArray? = null)`; `getImage(id)` |
| `commonMain/kotlin/app/cardfilter/data/SqlCardRepository.kt` | UPDATE | Store image on create, read it, map `has_image`, delete the image row with the card |
| `commonMain/kotlin/app/cardfilter/data/SqlTopicRepository.kt` | UPDATE | `delete` removes the topic's image rows before its cards |
| `commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt` | UPDATE | Add `GetCardImage` |
| `commonMain/kotlin/app/cardfilter/AppContainer.kt` | UPDATE | Expose `getCardImage` |
| `commonMain/kotlin/app/cardfilter/designsystem/CardImage.kt` | CREATE | `CardImage(load, contentDescription, modifier)`: loads bytes, decodes, draws; nothing on failure |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardListViewModel.kt` | UPDATE | `suspend fun loadImage(card): ByteArray?` delegating to `GetCardImage`, returning null on failure |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardListScreen.kt` | UPDATE | Thumbnail in `CardRow` when `card.hasImage` |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorViewModel.kt` | UPDATE | Expose whether the loaded card has an image and a loader (read-only; draft/dirty logic untouched) |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardEditorScreen.kt` | UPDATE | Image header above the title when present |
| `commonMain/kotlin/app/cardfilter/App.kt` | UPDATE | Pass `getCardImage` into both ViewModels |
| `commonMain/kotlin/app/cardfilter/presentation/Strings.kt` | UPDATE | Image content description |
| `composeApp/build.gradle.kts` | UPDATE | Add the Compose resources dependency if the spike keeps it (Task 1) |
| `gradle/libs.versions.toml` | UPDATE | Only if a catalog entry is needed for that dependency |
| `commonTest/.../testutil/FakeRepositories.kt` | UPDATE | `FakeCardRepository` stores images, implements `getImage`, keeps image on update |
| `commonTest/.../domain/usecase/CardUseCasesTest.kt` | UPDATE | `GetCardImage` cases |
| `commonTest/.../presentation/cards/CardListViewModelTest.kt` | UPDATE | `loadImage` cases |
| `androidHostTest/.../data/SqlRepositoriesTest.kt` | UPDATE | Image persistence, cascade, `hasImage`, update keeps image |
| `androidHostTest/.../data/MigrationTest.kt` | UPDATE | v3 expectations; v2 → v3 keeps cards and attributes |
| `DESIGN.md` (repo root) | UPDATE | Document `CardImage` and the "missing image is normal, shows nothing" rule |
| `.claude/tdd/card-image-offline-and-bulk-import-milestone-1.tdd.md` | CREATE | RED/GREEN evidence and manual results, as for earlier milestones |
| `.claude/prds/card-image-offline-and-bulk-import.prd.md` | UPDATE | M1 row → `in-progress`, Plan cell → this file (done at plan creation) |

No change to `AndroidManifest.xml`, the iOS project, or `Card`'s validation (`CardValidation.kt`, `AddCard`, `EditCard`): nothing here touches the network, and image input has no UI until M2.

## Tasks
Set `JAVA_HOME` to Android Studio's bundled JDK as in earlier milestones before running Gradle.

### Task 1: Image-decoding spike on both platforms
- **Action**: Before any other code, prove the decode path. Add the Compose resources dependency, write a throwaway composable that calls `decodeToImageBitmap()` on a small PNG held as a `ByteArray` and on garbage bytes, and run it on the Android emulator and the iOS simulator. Record: does it compile on both targets, does it render the PNG, does the garbage input throw (expected) so `CardImage` must catch it. If the call or dependency is unavailable in Compose Multiplatform 1.12.1, switch to the `expect fun decodeImage(bytes: ByteArray): ImageBitmap?` fallback (Android `BitmapFactory`, iOS Skia `Image.makeFromEncoded`) before going on.
- **Mirror**: M3 Task 1 (spike with a stop-and-fall-back rule).
- **Validate**: `./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64` passes and the PNG renders on both platforms. Delete the throwaway composable.

### Task 2: Schema and migration (tests first)
- **Action**: Extend `MigrationTest` first: `schemaVersionIsThree...` (the existing `schemaVersionIsTwoAfterTheAttributesMigration` test is renamed and now asserts 3); add `installMilestoneTwoDatabase()` as a verbatim copy of the v2 schema (topic, card, card_attribute, indexes, a few rows, written as raw SQL and not derived from the `.sq` files); migrate 2 → 3 and assert cards and attributes survive and a card can then get an image. See it fail. Then add `CardImage.sq` and `2.sqm`, run the schema-snapshot task to produce `3.db`, and run migration verification.
- **Mirror**: `MigrationTest.kt` (`installMilestoneOneDatabase`), `1.sqm`, `CardAttribute.sq`.
- **Validate**: `./gradlew :composeApp:generateCommonMainAppDatabaseSchema :composeApp:verifyCommonMainAppDatabaseMigration :composeApp:testAndroidHostTest`. If a task name differs, list them with `./gradlew :composeApp:tasks --all | grep -i sqldelight`.

### Task 3: Repository and fakes (tests first)
- **Action**: Write the `SqlRepositoriesTest` cases first: a card created with image bytes reports `hasImage = true` and `getImage` returns identical bytes; a card created without one reports `hasImage = false` and `getImage` returns null; `getImage` for an unknown id returns null; **updating a card's title and attributes keeps its image**; deleting the card removes its image row (count rows with a `TestDatabase` helper like `attributeRowCount`, so an orphan fails the test); deleting a topic removes the images of all its cards and leaves other topics' images alone; two cards keep their own images; a zero-byte image is stored as given (the 1 MB cap and "is it an image" checks belong to ingestion in M2/M3, not the repository). Mirror the same behaviour in `FakeCardRepository`. Then implement: `CardImage.sq`, the `has_image` column in both `Card.sq` queries, `toRow` and `Card.hasImage`, `create`/`getImage`, and the deletes in both repositories.
- **Mirror**: `SqlRepositoriesTest.kt` orphan-count tests for attributes; `SqlCardRepository.kt:54-63`; `SqlTopicRepository.kt:39-48`.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest`. Existing tests must pass unchanged (the new `Card.hasImage` default and `create` image default are the reason).

### Task 4: Use case and wiring (tests first)
- **Action**: In `CardUseCasesTest` add: `GetCardImage` returns the stored bytes, returns null for a card with none, returns null for an unknown card. Implement `GetCardImage`, expose it in `AppContainer`, and pass it to both ViewModels in `App.kt`.
- **Mirror**: `CardUseCases.kt:12-18`, `CardUseCasesTest.kt` fixture style.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`.

### Task 5: ViewModels (tests first)
- **Action**: Extend `CardListViewModelTest`: `loadImage` returns the stored bytes for a card with an image, null for one without, and null (no exception, no `failure` flag) when the repository throws. Same for the editor: a loaded card with an image exposes it; a new card exposes none; **dirty tracking is unchanged** (loading the image does not make the draft dirty, and an unmodified save still leaves the image alone). Then implement the two small changes.
- **Mirror**: `CardListViewModelTest.kt` (`UnconfinedTestDispatcher` as Main, `FakeCardRepository`), `CardEditorViewModelTest.kt`.
- **Validate**: both Gradle test tasks above.

### Task 6: CardImage composable and screens
- **Action**: Create `CardImage` (loads bytes with `produceState`, decodes on a background dispatcher, catches decode errors, draws with `ContentScale.Crop` in a fixed-size rounded box, `contentDescription` from the card title). Show it as a thumbnail in `CardRow` and as a header in `CardEditorScreen` when the card has an image. Layout must not shift when a row has no image (no reserved empty space). Add the string and update `DESIGN.md`.
- **Mirror**: `CardListScreen.kt` (`CardRow`), `Components.kt` conventions, DESIGN.md rules.
- **Validate**: `./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`.

### Task 7: Manual verification on both platforms
- **Action**: Seed an image directly into each device database (invented card, a real small PNG; on Android via `adb` + `run-as`, on the iOS simulator via `sqlite3` on the app container, as M3 did for Android data). Then on the Android emulator **and** the iOS simulator:
  1. A seeded card shows its image in the list row and in the editor; a card without one looks exactly as before.
  2. Rotate (Android), open and close the editor: image still shows, no flicker loop.
  3. Seed a row with garbage bytes: the card shows no image and nothing crashes or shows the failure snackbar.
  4. Seed a ~1 MB image on Android: it reads back (this is the cursor-window check behind the 1 MB cap). If it fails, lower the cap and record it.
  5. Edit the title of a card with an image and save: the image remains. Delete the card, then check the database: no `card_image` row is left.
  6. Install the build over an existing v2 database: the app opens, existing cards and attributes are intact.
  7. A topic of ~100 cards, a third with ~500 KB images: scrolling does not stutter or run out of memory. If it does, add a decoded-bitmap cache or downscale before M2.
  8. Dark mode.
- **Mirror**: manual tables in `.claude/tdd/card-filter-milestone-2.tdd.md`.
- **Validate**: every step passes on both platforms. Record any platform-specific failure before closing.

### Task 8: Close out
- **Action**: Write `.claude/tdd/card-image-offline-and-bulk-import-milestone-1.tdd.md` (RED/GREEN per cycle, test counts, coverage, spike result). Re-run the coverage report. Set the PRD M1 row to `complete` only after Task 7 passes.
- **Validate**: PRD row matches reality.

## Validation
```bash
./gradlew :composeApp:generateCommonMainAppDatabaseSchema   # run alone: Gradle rejects it in the same call as the verify task
./gradlew :composeApp:verifyCommonMainAppDatabaseMigration
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:coverageReport
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```
The `-destination ... ARCHS=arm64` flags are required; without them Xcode tries `x86_64` and fails.

## Implementation Status (updated during build)

Evidence: [.claude/tdd/card-image-offline-and-bulk-import-milestone-1.tdd.md](../tdd/card-image-offline-and-bulk-import-milestone-1.tdd.md). Design: [DESIGN.md](../../DESIGN.md).

**Done (Tasks 1-6, built test-first):** decode spike (Compose resources `decodeToImageBitmap` works on both platforms; undecodable bytes throw a different exception on each, so `CardImage` catches any `Exception`), `card_image` table with migration `2.sqm` and snapshot `3.db`, `Card.hasImage`, `create(..., image)` and `getImage`, explicit image deletes in both repositories, `GetCardImage`, ViewModel `loadImage`, `CardImage`, list thumbnail and read-only editor header. Android host 142 tests and iOS simulator 112 tests pass; APK builds; iOS framework links; `xcodebuild` succeeds. Two mutation checks were caught by the intended tests. The touched logic files are at 98-100% line coverage.

**Task 7 (manual):** checked on the Android emulator and the iOS simulator with seeded images, in light and dark: images shown in the list and editor, garbage bytes shown as a normal card, a ~1 MB image, edit-keeps-image and delete-leaves-no-row (Android), rotation (Android), and an in-place upgrade of existing v2 databases with identical card and attribute fingerprints on both. An iOS UI test was added for the display and failure cases.

**Still open (close-out):** scrolling smoothness on a long list with many images is **not established** (the emulator timings were inconclusive); no physical device; nothing above ~1 MB read back on Android, so the 1 MB cap is unconfirmed against the ~2 MB cursor window; iOS edit/delete with an image covered by unit tests only. Milestone 1 stays `in-progress` in the PRD until a person decides these are acceptable.

**Deviations:** see the evidence report (no separate index, snapshot task run alone, `imageKey` on `CardImage`, decorative thumbnail, the added iOS UI test, no Git checkpoints).

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| Common-code image decoding is not available or behaves differently on iOS (Skia) and Android (`BitmapFactory`) | Medium | Task 1 spike first, with a defined fallback (`expect`/`actual`). Host tests cannot decode on the JVM, so decoding is covered by manual steps, not unit tests |
| A BLOB near the size cap fails to read on Android (cursor window ~2 MB per row) | Medium | Separate table so the list query never reads bytes; Task 7 step 4 tests ~1 MB; the cap is lowered if it fails |
| Database grows with images (30 cards × up to 1 MB = up to ~30 MB), and the installed app has no way to reclaim it except deleting cards | Medium | Cap per image; PRD open question 9 on a total cap stays open; deleting a card removes its image |
| Per-row decoding makes scrolling janky or uses much memory on long lists | Medium | Decode off the main thread, only visible rows; Task 7 step 7 measures; add a cache or downscale only if it shows |
| `update` accidentally drops the image (replace-all style like attributes) | Medium | Explicit test "updating a card keeps its image" in Task 3 and Task 5 |
| Orphan `card_image` rows after card or topic delete (foreign keys cannot be relied on) | Medium | Explicit deletes in both repositories; orphan-count tests as for attributes |
| Milestone 1 is not reachable from the UI, so nobody can see the feature work until M2 | High (by design) | Seeded-database verification now; consider doing M1 and M2 back to back and demoing after M2 |
| iOS and Android differ on the DB file location for seeding | Low | Seed through `sqlite3`/`adb` as in M3; if awkward, add a debug-only seed behind a build flag and remove it before M2 |
| PRD requirements are unvalidated (users, need and metrics are TBD) | High (inherited) | After M2, put the app in front of one real user with the Wakodo sample before building M3 |
| Compose UI has no automated tests, so overall coverage stays below 80% | High (known) | Keep logic in the repository, use case and ViewModels (target ≥ 80% there); UI covered by Task 7 |

## Acceptance
- [ ] All tasks complete
- [ ] Migration verification, `testAndroidHostTest`, `iosSimulatorArm64Test`, `assembleDebug`, iOS framework link and `xcodebuild` pass
- [ ] A card with a stored image shows it in the list and the editor on Android and iOS; a card without one is unchanged
- [ ] Editing a card keeps its image; deleting a card or topic leaves no `card_image` row
- [ ] An undecodable image shows nothing and does not crash or raise the failure snackbar
- [ ] An app upgraded from schema v2 keeps every card and attribute
- [ ] Logic-layer coverage ≥ 80%
- [ ] No download, no image input UI, no `INTERNET` permission, no import (M2 and M3)
