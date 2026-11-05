# Plan: Card Filter — Dynamic Attributes (Milestone 2)

**Source PRD**: .claude/prds/card-filter.prd.md
**Selected Milestone**: 2 — Thuộc tính linh động ("Each card carries attributes the user defines")
**Complexity**: Medium (one new table + migration, one new screen; the rest is reshaping M1 code)
**Depends on**: Milestone 1 code (done). M1 is still `in-progress` in the PRD only because of manual close-out items (iOS tap-through, IME typing). They do not block this plan, but Task 8 re-covers the IME gap.

## Summary
A card gets any number of user-defined attributes, each one a name and one text value (PRD scope item 3). Cards in the same topic may have different attribute sets. The card's single-field add/edit dialog is replaced by a card editor screen: title, then a list of name/value rows the user can add and remove. Existing M1 cards and databases keep working through a SQLDelight migration. Search (M3) is out of scope, but attributes are loaded onto the `Card` domain object so M3 can filter in Kotlin without another schema change.

## Decisions Made in This Plan (confirm or override)
| Decision | Choice | Why |
|---|---|---|
| Card `title` | **Keep it** as the required heading; attributes are extra | M1 cards already have titles and the list needs a heading per row. Overriding means dropping `title` and treating `name` as just another attribute: a larger migration and a card with no label. M3 must then search the title **and** the attribute values |
| Attribute storage | New table `card_attribute(id, card_id, name, value)`, one row per attribute | Per-card varying sets (PRD) fit rows; a JSON blob would block SQL queries and make migrations opaque |
| Saving attributes | Replace-all inside one transaction (delete the card's rows, insert the new list) | The editor edits a whole draft. Attribute ids never leave the data layer, so there is nothing to diff |
| Attribute order | Insertion order (`ORDER BY id`) | User controls order by the order they add rows. Reordering is not in the PRD |
| Blank rows | A row with **both** name and value blank is dropped on save. Name-only or value-only is a per-row error | Lets the user leave a stray empty row without an error, but never stores half an attribute |
| Duplicate names on one card | **Allowed** | PRD does not forbid it and search ignores names. Revisit if M3 testing shows confusion |
| Value field | Multi-line `TextField` | Ingredient lists in the PDFs are long. The PRD says a single text value, not single-line |
| Whitespace | Trim ends of name and value; inner text and newlines unchanged | Same rule as M1 (`saveTrimmed`): diacritics, kana and kanji kept as typed |
| Card editor | Full screen route, not a dialog | Variable-length form with several fields does not fit `AlertDialog` |
| Leaving the editor | Confirm before discarding a dirty draft | Losing a typed ingredient list to an accidental back is a real cost |
| Card row | Shows title plus up to 3 `name: value` lines; tap opens the editor; overflow menu keeps Edit and Delete | PRD outcome is that cards carry their attributes, so the user must see them |
| Schema versioning | Real SQLDelight migration `1.sqm`; capture the v1 schema before touching anything | M1's plan promised this path. Dev emulator/simulator DBs are already at v1 and nothing is lost by doing it now |
| Not doing | Attribute-name autocomplete, reordering, typed values, import from PDF | Not in PRD MVP. Autocomplete would address the "same meaning, different name" risk and is a candidate follow-up |

## Patterns to Mirror
| Category | Source | Pattern |
|---|---|---|
| Naming | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt:15-23` | One class per use case with `operator fun invoke`; one `Observe*` use case per read |
| Errors | `.../domain/validation/SaveOutcome.kt:5-25`, `.../presentation/Attempt.kt:6-13` | Validation returns a sealed `SaveOutcome`, never throws; `attempt` converts repository exceptions to `failure` state and rethrows cancellation |
| Data access | `.../data/SqlCardRepository.kt:21-29` | `asFlow().mapToList(ioDispatcher)` for reads; `withContext(ioDispatcher)` + `transactionWithResult` for writes |
| Cascade | `.../data/SqlTopicRepository.kt:39-48` | Children deleted **explicitly** in one transaction, no `ON DELETE CASCADE` |
| Schema | `.../sqldelight/app/cardfilter/data/db/Card.sq:1-17` | `INTEGER PK AUTOINCREMENT`, index on the FK column, `lastInsertId` query |
| ViewModel | `.../presentation/cards/CardListViewModel.kt:30-37, 96-108` | One `UiState` data class per screen, events as functions, `save` wraps use cases in `attempt` |
| UI | `.../presentation/cards/CardListScreen.kt:142-176`, `.../designsystem/Components.kt:36-77` | Stateless composables, state hoisted to the ViewModel, `Spacing` tokens, strings from `Strings` |
| Navigation | `.../App.kt:18-20, 43-62` | Route string constants, `navArgument` with `NavType.LongType` |
| Tests | `.../commonTest/.../testutil/FakeRepositories.kt:38-58`, `.../androidHostTest/.../SqlRepositoriesTest.kt:15-26`, `.../CardListViewModelTest.kt:30-50` | In-memory fakes in `commonTest`; `TestDatabase` on in-memory JDBC SQLite; `UnconfinedTestDispatcher` as Main |

## Files to Change
Paths relative to `composeApp/src` unless noted.

| File | Action | Why |
|---|---|---|
| `build.gradle.kts` (composeApp) | UPDATE | `schemaOutputDirectory` + migration verification for `AppDatabase` |
| `commonMain/sqldelight/.../data/db/CardAttribute.sq` | CREATE | `card_attribute` table, index on `card_id`, insert / delete-by-card / delete-by-topic queries |
| `commonMain/sqldelight/.../data/db/Card.sq` | UPDATE | Add a card+attributes `LEFT JOIN` query (keeps cards with zero attributes); add `selectById` |
| SQLDelight migrations dir `1.sqm` (location confirmed in Task 2) | CREATE | `CREATE TABLE card_attribute` for databases already at v1 |
| `commonMain/.../domain/model/Attribute.kt` | CREATE | `Attribute(name, value)` |
| `commonMain/.../domain/model/Card.kt` | UPDATE | Add `attributes: List<Attribute>` |
| `commonMain/.../domain/validation/CardDraft.kt` | CREATE | `CardDraft`, `AttributeDraft`, validation to a card-specific outcome with per-row errors |
| `commonMain/.../domain/repository/CardRepository.kt` | UPDATE | `create(topicId, title, attributes)`, `update(id, title, attributes)` replacing `updateTitle`, `get(id)` |
| `commonMain/.../data/SqlCardRepository.kt` | UPDATE | Group join rows into cards; transactional create/update/delete of attributes |
| `commonMain/.../data/SqlTopicRepository.kt` | UPDATE | `delete` also removes the topic's attribute rows (before cards, same transaction) |
| `commonMain/.../domain/usecase/CardUseCases.kt` | UPDATE | `AddCard` / `EditCard` take a draft; add `GetCard` |
| `commonMain/.../AppContainer.kt` | UPDATE | Expose `getCard` |
| `commonMain/.../presentation/cards/CardEditorViewModel.kt` | CREATE | Draft state: load existing card, edit title, add/remove/edit rows, validate, save, dirty tracking |
| `commonMain/.../presentation/cards/CardEditorScreen.kt` | CREATE | Editor UI |
| `commonMain/.../presentation/cards/CardListViewModel.kt` | UPDATE | Remove Add/Edit dialogs, `titleError`, `onInputChanged`; keep delete confirmation |
| `commonMain/.../presentation/cards/CardListScreen.kt` | UPDATE | FAB and row tap navigate to the editor; row shows attribute preview |
| `commonMain/.../presentation/Strings.kt` | UPDATE | Editor, attribute and discard strings; per-row error text |
| `commonMain/.../App.kt` | UPDATE | Editor route `topics/{topicId}/cards/new` and `.../cards/{cardId}` |
| `commonMain/.../designsystem/Components.kt` | UPDATE | Generalise the delete-confirm dialog (or add a discard variant) |
| `DESIGN.md` (repo root) | UPDATE | Document the new components/rules (multi-line field, attribute row) |
| `commonTest/.../testutil/FakeRepositories.kt` | UPDATE | Fake card repo stores attributes |
| `commonTest/.../usecase/CardUseCasesTest.kt` | UPDATE | New validation rules |
| `commonTest/.../presentation/cards/CardListViewModelTest.kt` | UPDATE | Dialogs removed |
| `commonTest/.../presentation/cards/CardEditorViewModelTest.kt` | CREATE | Editor behaviour |
| `androidHostTest/.../data/SqlRepositoriesTest.kt` | UPDATE | Attribute persistence, ordering, cascade-by-hand, Flow updates |
| `androidHostTest/.../data/MigrationTest.kt` | CREATE | v1 database with cards migrates to v2 without losing data |
| `.claude/prds/card-filter.prd.md` | UPDATE | Milestone 2 row → `in-progress`, Plan cell → this file (done at plan creation) |

## Tasks

### Task 1: Freeze the v1 schema
- **Action**: **Before editing any `.sq` file**, configure `schemaOutputDirectory` in the `sqldelight { databases { create("AppDatabase") } }` block and generate the schema snapshot (`1.db`) from the current M1 schema. Keep it in the tree (the repo is not under git, so there is no commit to make it durable).
- **Mirror**: `composeApp/build.gradle.kts` sqldelight block.
- **Validate**: A `1.db` file exists under the schema directory and contains `topic` and `card` tables only.

### Task 2: Schema, migration, queries
- **Action**: Create `CardAttribute.sq` (`id INTEGER PK AUTOINCREMENT`, `card_id INTEGER NOT NULL REFERENCES card(id)`, `name TEXT NOT NULL`, `value TEXT NOT NULL`, index on `card_id`). Add `1.sqm` with the same `CREATE TABLE` + index. The `.sq` holds the latest schema and `.sqm` holds the step from v1, so both must define it. Add queries: card+attributes join ordered `card.created_at, card.id, card_attribute.id`; `selectById`; insert attribute; `deleteByCard`; `deleteByTopic` as `DELETE ... WHERE card_id IN (SELECT id FROM card WHERE topic_id = ?)`. Confirm the migrations directory SQLDelight expects by running the verify task, rather than guessing.
- **Mirror**: `Card.sq:1-17`.
- **Validate**: Schema generation succeeds; the migration verification task passes against `1.db`.

### Task 3: Domain and validation (tests first)
- **Action**: Write failing tests, then implement `Attribute`, `Card.attributes`, `CardDraft`/`AttributeDraft` and validation. Cases: title blank → rejected; fully blank row dropped; name-only and value-only rows each rejected with that row's index and kind; trims ends of name/value; keeps `thịt bầm`, `ベビーフード`, inner newlines unchanged; duplicate names accepted; zero attributes accepted. Result type is card-specific (the existing `SaveOutcome.Rejected(TitleError)` cannot carry per-row errors); topics keep using `SaveOutcome`.
- **Mirror**: `SaveOutcome.kt`, `CardUseCasesTest.kt`.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test` — new tests fail first for missing types, then pass.

### Task 4: Repositories (tests first)
- **Action**: Failing tests first in `SqlRepositoriesTest`: create stores attributes in order; a card with no attributes still comes back; `update` replaces the whole attribute list (removed rows gone, none duplicated); `get(id)` returns the card or null; `delete(card)` removes its attributes; **`deleteTopic` leaves no orphan `card_attribute` rows** and keeps other topics' cards and attributes; `observeByTopic` emits again after an attribute-only change; Vietnamese and Japanese attribute text round-trips exactly. Then implement. `create` and `update` run in one `transactionWithResult`/`transaction`.
- **Mirror**: `SqlCardRepository.kt:21-29`, `SqlTopicRepository.kt:39-48`.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest`.

### Task 5: Migration test
- **Action**: In `MigrationTest`, build a v1 database with raw M1 DDL on an in-memory `JdbcSqliteDriver`, insert a topic and cards, run `AppDatabase.Schema.migrate(driver, 1, 2)`, then assert the cards are intact (including non-ASCII titles), `card_attribute` exists and is empty, and a new card with attributes saves and reads back. The Gradle verification task from Task 2 covers structure; this covers data.
- **Mirror**: `TestDatabase` in `SqlRepositoriesTest.kt:15-26`.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest`.

### Task 6: Use cases and editor ViewModel (tests first)
- **Action**: Update `AddCard`/`EditCard`, add `GetCard`. Write `CardEditorViewModelTest` first, then `CardEditorViewModel`. Cases: new-card mode starts empty with no rows; edit mode loads title and attributes; add row, remove row, edit name/value; rows have stable keys; save with invalid rows exposes per-row errors and does not call the repository; typing clears that row's error; successful save emits a "saved" signal; repository failure sets `failure`; `isDirty` is false when unchanged and true after any edit; unknown card id in edit mode is handled (treated as finished or error, decided in the test). Update `CardListViewModelTest` for removed dialogs.
- **Mirror**: `CardListViewModel.kt:30-37, 96-108`, `CardListViewModelTest.kt`.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test`.

### Task 7: UI and navigation
- **Action**: `CardEditorScreen`: top bar with back and Save; title field; one block per attribute (name field, multi-line value field, remove button with a 48 dp target and a `contentDescription` from `Strings`); "Add attribute" button; per-row error text; discard confirmation when back is pressed on a dirty draft. Update `CardListScreen` (FAB and row tap open the editor, row shows up to 3 attribute lines, overflow menu keeps Edit/Delete). Add the two editor routes to `App.kt`, scoping the editor ViewModel key by `topicId`/`cardId`. Use `Spacing` and `MaterialTheme.colorScheme` only; keep the editor scrollable with the keyboard open. Update `DESIGN.md`.
- **Mirror**: `CardListScreen.kt`, `Components.kt`, DESIGN.md rules 1-4.
- **Validate**: `./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`.

### Task 8: Manual verification on both platforms
- **Action**:
  1. **Upgrade path first**: with the **M1 build** already installed and holding topics and cards (Android emulator and iOS simulator), install the M2 build over it without clearing data. Confirm the old cards are still listed and open in the editor with zero attributes.
  2. Create a card with 3 attributes, save, relaunch, confirm it persisted.
  3. In one topic create two cards with different attribute names and counts.
  4. Edit a card: change a value, remove a row, add a row; confirm the list reflects it. Leave one row fully blank and confirm it is dropped. Leave a name with no value and confirm the row error.
  5. Back on a dirty draft → discard prompt; Cancel keeps editing, Discard leaves.
  6. Delete a card and a topic (check no orphan `card_attribute` rows via `sqlite3` on the Android emulator).
  7. **Type with a real keyboard**: Vietnamese Telex (`thịt bầm`) and Japanese IME (`ベビーフード`, `たまご`) into the title, an attribute name and a multi-line value, on both platforms. This also closes M1's open IME item.
  8. Long value (a 10+ line ingredient list) scrolls and does not overflow the editor or the card row; dark mode check.
- **Mirror**: Manual table in `.claude/tdd/card-filter-milestone-1.tdd.md`.
- **Validate**: Every step passes on both platforms. Record any platform-specific failure before closing.

### Task 9: Close out
- **Action**: Record evidence (RED/GREEN per cycle, test counts, coverage) in `.claude/tdd/card-filter-milestone-2.tdd.md`. Set the PRD M2 row to `complete` only after Task 8 passes. Re-run the coverage report.
- **Validate**: PRD row matches reality.

## Validation
Run with `JAVA_HOME` set to Android Studio's bundled JDK 25, as in M1.
```bash
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:coverageReport
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug build
```
Also run the SQLDelight migration verification task from Task 2.

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| Forgetting to snapshot v1 before editing `.sq` files, so the migration cannot be verified | Medium | Task 1 is first and blocks Task 2 |
| `.sq` and `.sqm` drift (table defined in one, not the other) so fresh installs and upgraded installs get different schemas | Medium | Migration verification task plus `MigrationTest` assert both paths end in the same schema |
| Orphan `card_attribute` rows when a card or topic is deleted (FK cascade not relied on) | Medium | Explicit deletes in the same transaction; dedicated orphan test in Task 4 |
| `LEFT JOIN` grouping drops cards with no attributes or mis-orders attributes | Medium | Tests for zero-attribute cards and for ordering; explicit `ORDER BY` including `card_attribute.id` |
| Observers miss attribute-only changes | Low | Replace-all runs in the same transaction as the card write; test asserts a new emission |
| SQLDelight migration runs untested on the iOS native driver (repo tests use JDBC) | Medium | Task 8 step 1 upgrades a real M1 install on the iOS simulator |
| IME composition bugs in multiple text fields (Vietnamese Telex, Japanese) on Compose iOS; M1 never tested real typing | Medium | Task 8 step 7 on both platforms; all M3 search depends on text entered here |
| Draft lost on process death (ViewModel-held state, not saved state) | Low | Rotation and nav are covered by the ViewModel; a discard prompt covers the accidental-back case; saved-state restore is a follow-up if it proves painful |
| Large keyboard-covered forms (editor with many rows) hide the focused field | Medium | Scrollable column with IME insets handled; checked in Task 8 step 8 |
| Title/`name` overlap: users type the card name twice (title and a `name` attribute) | Medium | Decision recorded above; label the title field clearly; M3 searches both |
| PRD requirements still unvalidated (users, metrics TBD) | High (inherited) | M2 is the point where real PDF data can be entered; try a handful of Pigeon/Wakodo cards by hand before M3 |
| Compose UI has no automated tests, so overall coverage stays below 80% | High (known) | Keep logic in ViewModels/use cases (target ≥80% there, as in M1); UI covered by Task 8 |

## Implementation Status (updated during build)

Evidence: [.claude/tdd/card-filter-milestone-2.tdd.md](../tdd/card-filter-milestone-2.tdd.md). Design: [DESIGN.md](../../DESIGN.md).

**Done (Tasks 1-7, built test-first):** v1 and v2 schema snapshots, `card_attribute` table and `1.sqm` migration (verification proven to detect a bad migration), domain validation with per-row errors, repositories, migration test, use cases, editor and list ViewModels, editor screen, navigation, `DraftTextField`, `DESIGN.md`. 82 Android host tests and 62 iOS simulator tests pass; APK builds; iOS framework links. Three mutation checks were caught by the intended tests.

**Task 8 (manual), done on both platforms except where noted:**
- Android emulator: upgrade from a seeded v1 database (a stand-in: no real M1 build was available), add attribute, validation errors, discard prompt; real Gboard typing: Vietnamese Telex and Japanese romaji (title, attribute name, attribute value, kanji candidate).
- iOS simulator: migration of a seeded v1 database on the native driver; an automated XCUITest lifecycle (create topic and cards, attributes, edit, validation, discard, relaunch persistence, delete); real Vietnamese Telex typing. New `iosAppUITests` target in `iosApp/project.yml`.

**Still open (close-out):**
- **Japanese IME on iOS is blocked**: the simulator will not enable the Japanese keyboard through preferences. Verified on Android only.
- Dark mode, long values, many rows with the keyboard open, deleting a topic through the UI.
- Not tried on a physical device or other IMEs.
- UX observation (not fixed): with the keyboard open a newly added row's Value field is below the visible area (Next key scrolls it into view). See the evidence report.
- Whole-codebase coverage is 35.7% (logic 98.8%); the Compose UI is covered only by the iOS XCUITest above and manual runs.
- Milestone 2 stays `in-progress` in the PRD until a person checks the items above.

**Deviations:** see the evidence report (`Attribute` in `Card.kt`, no draft types, `isSaving`/`loadFailed` guards, `NavigationBackHandler`, `2.db` snapshot).

## Acceptance
- [x] Tasks 1-7 complete (Task 8 partly, Task 9 evidence written; PRD row intentionally not set to `complete`)
- [x] `testAndroidHostTest`, `iosSimulatorArm64Test`, `assembleDebug`, iOS framework link and the migration verification all pass
- [x] A card can be created and edited with any number of name/value attributes; two cards in a topic can have different attribute sets (ViewModel and repository tests; one attribute added through the real UI on Android)
- [x] An M1 database upgrades in place on Android and iOS: old cards keep their titles and open with zero attributes (**seeded v1 database on both drivers**, not a real M1 install, which no longer existed)
- [x] Deleting a card or topic leaves no orphan attribute rows (repository tests + mutation check; card delete also driven through the iOS UI; topic delete not driven through either UI)
- [ ] Vietnamese and Japanese text round-trips unchanged, including when typed through a real keyboard (Vietnamese Telex: **verified on Android and iOS**; Japanese romaji: **verified on Android only**, iOS blocked because the simulator will not enable the Japanese keyboard)
- [x] Logic-layer coverage ≥ 80% (98.8%)
- [x] No search or filtering code written (that is Milestone 3)
