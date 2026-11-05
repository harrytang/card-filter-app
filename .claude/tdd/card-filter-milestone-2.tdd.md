# TDD Evidence — Milestone 2 (Dynamic attributes)

**Source plan**: [.claude/plans/card-filter-milestone-2.plan.md](../plans/card-filter-milestone-2.plan.md)
**Design**: [DESIGN.md](../../DESIGN.md)
**Not a git repository**, so no checkpoint commits exist. RED and GREEN evidence is recorded here instead.

Commands run with `JAVA_HOME` set to Android Studio's bundled JDK and the project wrapper (Gradle 9.8.1).

## User journeys (from the plan)

1. As a user, I add attributes (a name and one text value each) to a card; different cards in a topic can have different attribute sets.
2. As a user, I edit a card: change its title, change values, add and remove attributes.
3. As a user, a row I half-filled is refused with a message on the missing field; a row I left fully blank is ignored.
4. As a user, leaving the editor with unsaved changes asks me first.
5. As a user with an existing (Milestone 1) database, my cards survive the upgrade and open with no attributes.
6. As a user, deleting a card or a topic leaves no stray attribute data.
7. As a user, Vietnamese and Japanese text in titles, names and values is stored exactly as typed.

## Plan deviations

- `Attribute` lives in `Card.kt`, not its own file. No `CardDraft`/`AttributeDraft` types: use cases take `List<Attribute>` (raw as typed) and return a card-specific `CardSaveOutcome` with per-row errors (`AttributeError(index, field)`), because `SaveOutcome.Rejected(TitleError)` cannot carry row errors.
- The card list no longer has add/edit dialogs: `CardListViewModel` only observes and deletes (`cardPendingDelete`).
- The editor keeps each field's text in local Compose state (`DraftTextField`) and pushes changes to the ViewModel, to protect IME composition; ViewModel state is still the source for saving.
- Added an `isSaving` guard (not in the plan) after identifying that a double tap on Save would create two cards; and `loadFailed`, so a card that failed to load can never be saved over with a blank draft.
- Back handling uses `NavigationBackHandler` (`BackHandler` is deprecated in this Compose version). Resolved transitively; no new dependency.
- Also froze `2.db` (the v2 schema) next to `1.db`, so a future migration can be verified.

## Task report

| Cycle | RED (before production code) | GREEN |
|---|---|---|
| Domain: validation + use cases | `:composeApp:testAndroidHostTest` failed to compile: 151 errors, all missing `Attribute`, `CardSaveOutcome`, `AttributeField`, `AttributeError`, `GetCard`, `attributes` | passes (17 `CardUseCasesTest`) |
| Data + editor + list ViewModels (written together) | Compile failure: 365 errors, all missing production code (`CardEditorViewModel`, `GetCard`, `cardPendingDelete`, repository `get`/`update`, extra `attributes` parameters, 6 `overrides nothing`) | 82/82 Android host tests pass |
| Schema/migration | `verifyCommonMainAppDatabaseMigration` first run passed; a deliberately broken `1.sqm` (`value TEXT` instead of `NOT NULL`) made it **fail** with "fresh database looks different from migration database", proving the check is real. File restored | passes |

RED is compile-time in every cycle: the tests reference production types that did not exist and the errors are exactly those missing types, not setup problems. The data and presentation tests were written together before any of that production code, so they share one RED run.

Two build issues came up during GREEN and were fixed, neither a test failure: `BackHandler` lives in a separate artifact and is experimental/deprecated (switched to `NavigationBackHandler`); two of my own edits swallowed a newline in `libs.versions.toml` and `build.gradle.kts` and broke the build until repaired.

## Test specification

| # | What is guaranteed | Test | Type | Result |
|---|---|---|---|---|
| 1 | Title and every attribute name/value are trimmed at the ends only; diacritics, kana, full-width space and inner newlines are untouched | `CardUseCasesTest` (trim, keeps VN/JP) | unit | PASS |
| 2 | A row with both name and value blank is dropped; name-only or value-only is rejected with that row's submitted index and field; title and row errors are reported together; nothing is saved when rejected; duplicate names and zero attributes are accepted | `CardUseCasesTest` (8 tests) | unit | PASS |
| 3 | Edit replaces the title and the whole attribute list, can remove every attribute, and keeps the old card when rejected | `CardUseCasesTest` | unit | PASS |
| 4 | Attributes are stored and returned in the order given; cards with no attributes still appear; attributes never leak between cards or topics | `SqlCardRepositoryTest` | integration (in-memory SQLite) | PASS |
| 5 | `update` replaces attributes without leaving or duplicating rows; `delete` removes the card's attributes; `get` returns the card or null | `SqlCardRepositoryTest` | integration | PASS |
| 6 | **Deleting a topic leaves no orphan `card_attribute` rows** and keeps other topics' cards and attributes | `SqlTopicRepositoryTest.delete_removesTheTopicItsCardsAndTheirAttributesButNotOtherTopics` | integration | PASS |
| 7 | An attribute-only change makes `observeByTopic` emit again | `SqlCardRepositoryTest.observeByTopic_emitsAgainAfterAnAttributeOnlyChange` | integration | PASS |
| 8 | Vietnamese, Japanese and newlines in attributes round-trip exactly | `SqlCardRepositoryTest` | integration | PASS |
| 9 | A v1 database (M1 DDL copied verbatim, with non-ASCII titles) migrates with all cards intact and accepts attributes afterwards; schema version is 2 | `MigrationTest` (3 tests) | integration | PASS |
| 10 | Editor: new card starts clean; edit loads title and attributes; unknown card closes; load failure blocks saving; row keys stay unique; `isDirty` follows differences from the loaded draft (restoring text is clean); per-row errors appear and clear per field; save creates/updates and finishes; failure keeps the draft; **double tap saves once**; back asks before discarding a dirty draft | `CardEditorViewModelTest` (16 tests) | unit | PASS |
| 11 | List: shows only the topic's cards with their attributes; delete asks first and can be dismissed; failures set `failure` and keep the confirmation | `CardListViewModelTest` (9 tests) | unit | PASS |

### Mutation checks (tests can fail)

Each mutation was applied, tests run, and the file restored byte-for-byte (verified with `cmp`):

| Mutation | Failing tests (as intended) |
|---|---|
| Topic delete skips `cardAttributes.deleteByTopic` | `delete_removesTheTopicItsCardsAndTheirAttributes...` |
| `update` no longer clears the old attributes | `update_replacesTitleAndEveryAttribute...`, `update_toNoAttributesClearsThem`, `observeByTopic_emitsAgainAfterAnAttributeOnlyChange` |
| Editor save ignores the `isSaving` guard | `tappingSaveTwiceWhileSaving_createsOnlyOneCard` |

Not mutation-checked: the rest of the suite.

## Final verification

```
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test \
          :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 \
          :composeApp:verifyCommonMainAppDatabaseMigration :composeApp:coverageReport
BUILD SUCCESSFUL
Android host tests: 82 tests, 0 failures   (counted from the XML reports)
iOS simulator tests: 62 tests, 0 failures  (shared domain + ViewModel tests, run natively)
androidApp-debug.apk built; iOS framework links; no compiler warnings from project sources
```

The repository and migration tests run only on the Android host (JDBC SQLite); they are not run against the iOS native driver.

## Coverage and known gaps

Measured with `./gradlew :composeApp:coverageReport` (JaCoCo, Android host tests only, generated SQLDelight code excluded).

| Layer | Lines |
|---|---|
| data (repositories) | 100% (81/81) |
| domain: model, use cases, validation | 100% / 100% / 94.1% (12/12, 24/24, 32/34) |
| ViewModels (cards, topics) | 100% (126/126, 50/50) |
| `attempt` helper | 71.4% (5/7, inline-function attribution) |
| **Logic combined** | **98.8% (330/334)** |
| screens, design-system components, app wiring (`App.kt`, `AppContainer`), strings | 0% (0/591) |
| **Whole codebase** | **35.7% (330/925)** |

The 80% target is met for logic and **not met overall**, for the same reason as Milestone 1: the Compose UI has no automated tests.

## Manual verification (plan Task 8)

### Android (emulator `booklet-api36`, debug APK)

**The upgrade was simulated, not a real M1 install.** No M1 build or database existed any more (the emulator had been reset and the M1 sources were overwritten). Instead the frozen `1.db` snapshot (the exact M1 schema) was seeded with 2 topics and 3 cards (including `Cháo thịt bầm` and `ベビーフード たまご`), set to `user_version = 1`, and copied into the app's private data directory *before* the first launch of the M2 app. `AndroidSqliteDriver` therefore ran the real migration on first open.

| Step | Result |
|---|---|
| First launch on the seeded v1 database | No crash. Database afterwards: `user_version` 2, tables `card`, `card_attribute`, `topic`; all 3 cards intact with their original titles; 0 attribute rows |
| Topics and card list render from the migrated data | Pass |
| Open an old card in the editor | Title loaded, no attribute rows, **Save** and **Add attribute** shown |
| Add an attribute (`tuoi` / `7 thang`), Save | Editor closed; database has `1|tuoi|7 thang`; still 3 cards (no duplicate); list row shows `tuoi: 7 thang` under the title; the card without attributes shows only its title |
| New card with blank title and a name-only row, Save | Nothing saved. Title shows "Enter some text"; the Value field shows "Enter a value"; the filled Name field shows no error |
| Dirty draft, Back | First Back hides the keyboard only; second Back shows the discard prompt. "Keep editing" returns to the draft; Back again, then "Discard", returns to the card list. Afterwards still 3 cards, 1 attribute row |

My first attempt at the validation/discard steps was invalid and is not counted: the app had been relaunched onto the Topics list, no tap landed, and the Back presses left the app. It was redone from the correct screen with a tap helper that stops on any miss.

## Follow-up verification: iOS and real keyboards

Run after the first write-up, to close the gaps listed there.

### iOS (simulator `iPhone 18 Pro`, iOS 27, debug build)

**Migration on the iOS native driver (real).** A v1 database (the frozen `1.db` schema, seeded with 2 topics and 3 cards including `Cháo thịt bầm` and `ベビーフード たまご`, `user_version = 1`) was placed in the app's data container *before first launch*. After launching the M2 app: `user_version` 2, tables `card`, `card_attribute`, `topic`, all 3 cards intact, 0 attribute rows. As on Android, this is a seeded stand-in for an upgrade from an M1 build, not a real M1 install.

**Tap-through via XCUITest** (new target `iosAppUITests`, `iosApp/iosAppUITests/CardFilterUITests.swift`, generated by `xcodegen`; the first automation of any UI in this project). `testTopicAndCardAttributeLifecycle`, from a clean install, drives the real app through: empty state → create topic → open it → add a card with two attributes (the second through the keyboard's Next key) → list shows `age: 7 months` and `allergen: egg` → add a second card with a different attribute set → edit the first card (change a value, remove a row) → blank-title and name-without-value validation errors → discard prompt (Keep editing, then Back and Discard) → terminate and relaunch, data persisted → delete a card through the overflow menu with confirmation. **PASS.** This also closes Milestone 1's open item "iOS create/edit/delete was not driven by hand".

**Real Vietnamese Telex composition (iOS).** With the simulator's Vietnamese (Telex) keyboard enabled, `testVietnameseTelexComposition` taps the raw keys `t h i j t ␣ b a a f m` on the on-screen keyboard and the field contains `Thịt bầm` (first letter auto-capitalised); saved and shown in the list; stored bytes `5468E1BB8B742062E1BAA76D` = NFC exactly. **PASS.**

Both UI tests together, from a clean install: `Executed 2 tests, with 0 failures`.

### Android (emulator, Gboard with Vietnamese QWERTY/Telex and Japanese QWERTY/romaji added in its Languages settings)

Keys were tapped on Gboard itself (read through `uiautomator dump --windows`), so the app received genuine composition rather than injected text.

| Check | Result |
|---|---|
| Telex into the title: `t h i j t ␣ b a a f m` | Field `Thịt bầm`; saved; stored `5468E1BB8B742062E1BAA76D` (NFC, byte-identical to iOS) |
| Japanese romaji into the title: `tamago` | Composes to `たまご`; Gboard's candidate strip offers 卵, たまご, 玉子, タマゴ, 卵焼き; Enter commits the hiragana |
| Compose again and pick candidate `卵` | Field `たまご　卵`; saved exactly (`E3819FE381BEE38194E38080E58DB5`) |
| Japanese into an attribute **name** and **value** (multi-line field) | Name `あれるげｎ` (my input `arerugen` leaves a pending `n` in romaji; the IME and field behaved correctly), value `卵` picked from candidates; stored as typed in `card_attribute` |

### Findings from this round

1. **Gboard's Japanese space key types a full-width space (U+3000)**, so titles/values contain `　`. Search must split queries on U+3000 (the Milestone 3 plan already does) and trimming already treats it as whitespace (`CardUseCasesTest`).
2. **Both platforms hand the app NFC text from Telex**, byte-identical, so no normalisation is needed on input (search still folds, per the M3 plan).
3. **Layout (UX, not fixed):** with the keyboard open, the list's visible area ends about 35 pt above the keyboard, and a newly added row's Value field sits below it, so a person has to scroll or press the keyboard's Next key (which scrolls it into view). The lifecycle test uses Next for this reason. Candidate follow-up: focus the new row's name field when "Add attribute" is tapped, and review the bottom inset.
4. **Accessibility is better than expected:** a field showing a validation error exposes it in its label (`Card title, Enter some text`), so screen readers announce it.
5. iOS shows a one-time "Quickly Change Keyboards" overlay on first keyboard switch; the tests dismiss it.

### Still not done

- **Japanese on iOS: blocked.** The iOS 27 simulator runtime ships the Japanese keyboard files, but enabling `ja_JP-Romaji@sw=QWERTY-Japanese` (and Kana variants) through `AppleKeyboards` never adds it to the keyboard switcher, while Vietnamese enabled the same way works. Not tried: adding it through the Settings app UI. Japanese composition is therefore verified on **Android only**; on iOS only Japanese *storage and rendering* are verified (seeded rows, migration).
- Not verified on a physical device or with a different IME (Samsung keyboard, Apple's Japanese 12-key).
- Dark mode (either platform), a long (10+ line) value, and many rows with the keyboard open.
- Deleting a topic through the UI on either platform (deleting a *card* is covered on iOS; topic delete is covered by repository tests and a mutation check). Android deletion through the UI was not run.
- Milestone 2 stays `in-progress` in the PRD until a person has reviewed the items above.
