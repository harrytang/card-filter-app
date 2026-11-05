# TDD Evidence — Milestone 1 (Topics and basic cards)

**Source plan**: [.claude/plans/card-filter-milestone-1.plan.md](../plans/card-filter-milestone-1.plan.md)
**Design**: [DESIGN.md](../../DESIGN.md)
**Not a git repository**, so no checkpoint commits exist. RED and GREEN evidence is recorded here instead.

Commands run with `JAVA_HOME` set to Android Studio's bundled JDK 25 and the project wrapper (Gradle 9.8.1).

## User journeys (from the plan)

1. As a user, I create a topic and see it in my list.
2. As a user, I rename or delete a topic; deleting also removes its cards.
3. As a user, I open a topic and add, edit and delete cards in it; cards never leak into other topics.
4. As a user, I type Vietnamese or Japanese text and it is stored exactly as typed.
5. As a user, a blank name or title is refused with a message instead of being saved.

## Task report

| Cycle | RED (before production code) | GREEN |
|---|---|---|
| Domain: use cases + validation | `:composeApp:testAndroidHostTest` failed to compile: 154 `Unresolved reference` errors for `CreateTopic`, `AddCard`, `SaveOutcome`, `Topic`, etc. | 12/12 pass |
| Data: SQLDelight repositories | Compile failure: `AppDatabase`, `SqlTopicRepository`, `SqlCardRepository` unresolved | First attempt hit 3 compile errors (`QueryResult<Long>` returned where `Unit` was expected); fixed; 23/23 pass |
| Presentation: ViewModels | Compile failure: `TopicListViewModel`, `CardListViewModel`, `CardDialog` unresolved (16 errors) | 39/39 pass |

RED is compile-time in every cycle: the new tests reference production types that did not exist, and the errors were exactly those missing types, not setup or dependency problems.

## Test specification

| # | What is guaranteed | Test | Type | Result |
|---|---|---|---|---|
| 1 | Topic name is trimmed before saving; blank is rejected and nothing is saved | `TopicUseCasesTest` (create, blank) | unit | PASS |
| 2 | Vietnamese `thịt bầm` and Japanese `ベビーフード` survive unchanged (only surrounding whitespace incl. full-width `U+3000` is trimmed) | `TopicUseCasesTest.createTopic_keepsVietnameseAndJapaneseTextUnchanged` | unit | PASS |
| 3 | Rename trims, rejects blank, keeps the old name on rejection | `TopicUseCasesTest` | unit | PASS |
| 4 | Delete topic removes only that topic | `TopicUseCasesTest` | unit | PASS |
| 5 | Card add/edit trim and reject blank; cards are scoped per topic; delete removes only that card | `CardUseCasesTest` (6 tests) | unit | PASS |
| 6 | Ids are generated, topics list in creation order, ties broken by insertion order | `SqlTopicRepositoryTest` | integration (in-memory SQLite) | PASS |
| 7 | Deleting a topic removes its cards in the same transaction and leaves other topics' cards (no orphans, not reliant on `ON DELETE CASCADE`) | `SqlTopicRepositoryTest.delete_removesTheTopicAndItsCardsButNotOtherTopics` | integration | PASS |
| 8 | Vietnamese and Japanese text round-trips through the database exactly | `SqlTopicRepositoryTest.roundTrip_...` | integration | PASS |
| 9 | Card create, update, delete and per-topic ordering | `SqlCardRepositoryTest` (4 tests) | integration | PASS |
| 10 | ViewModel dialogs: add, rename, edit, delete-with-confirmation, dismiss, blank-title error, error cleared on typing | `TopicListViewModelTest`, `CardListViewModelTest` (16 tests) | unit | PASS |
| 11 | Repository failure sets `failure` and keeps the dialog open; cancellation is not swallowed (`attempt` rethrows it) | the two `aFailingRepository_...` tests | unit | PASS (cancellation rethrow is by construction, not separately tested) |

## Final verification (after the UI layer and the contrast fix)

```
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test \
          :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
BUILD SUCCESSFUL
Android host tests: 39 tests, 0 failures
iOS simulator tests: 28 tests, 0 failures   (shared domain + ViewModel tests, run natively)
androidApp/build/outputs/apk/debug/androidApp-debug.apk built
```

The 11 repository tests run only on the Android host (JDBC SQLite); they are not run against the iOS native driver.

## Coverage and known gaps

- **Coverage (measured): logic layers 97.9% of lines, whole codebase 31.2%.** The 80% target is met for the logic and **not met overall**, because the Compose UI has no automated tests (0%). Run `./gradlew :composeApp:coverageReport`; the HTML report is in `composeApp/build/reports/jacoco/coverageReport/html`. Kover was tried first and reported about 0%: it does not instrument the Android host-test task with the Android KMP library plugin, so it was removed. The report is built with JaCoCo from the data AGP writes when `enableCoverage = true`. Generated SQLDelight code is excluded. Android host tests only; the iOS runs are not part of it.

  | Layer | Lines | Branches |
  |---|---|---|
  | domain | 97.0% (32/33) | n/a |
  | data (repositories) | 100% (41/41) | n/a |
  | ViewModels + `attempt` | 97.4% (111/114) | 94.7% |
  | **Logic combined** | **97.9% (184/188)** | **94.7%** |
  | screens, design-system components, app wiring, UI strings | 0% (0/402) | 0% |
  | **Whole codebase** | **31.2% (184/590)** | 17.0% |

  The 4 uncovered logic lines are `onInputChanged` in the card ViewModel plus compiler attributions on inline functions (`saveTrimmed`, `attempt`) that JaCoCo reports against the definition although the tests exercise them.
- **Some tests were added after the code.** After the first coverage run, 10 tests were added for failure paths that already worked (failing list read, failing delete, guard clauses, cancellation in `attempt`). These are coverage-gap tests, not red-first, and were not mutation-checked.
- **Test counts now:** 49 Android host tests and 38 iOS simulator tests, 0 failures (the earlier 39/28 figures above predate these).
- **Compose UI is untested by automation** (screens, dialogs, navigation wiring in `App.kt`). They compile on both targets and are unverified at runtime.
- **Typing Vietnamese/Japanese through a keyboard (IME) is untested** on both platforms. `adb input` and `simctl` cannot type non-ASCII text, and I did not install a third-party keyboard. Rendering and storage of that text were checked (see below), composition through a real keyboard was not.
- **iOS interaction is untested.** The simulator cannot be tapped from the command line, so create/edit/delete on iOS was not driven by hand.

## Manual verification (plan Task 6)

### Android (emulator `booklet-api36`, API 36, debug APK) — driven through the real UI

| Step | Result |
|---|---|
| Launch | Starts, no crash in logcat |
| Empty state | "No topics yet" message and add button shown |
| Blank topic name, Save | Dialog stays open, "Enter some text" shown; error clears on typing |
| Create topics `Pigeon`, `Wakodo` | Both listed |
| Open topic, add 2 cards | Cards listed; topic name in the app bar |
| Open `Wakodo` | Empty, so cards are scoped per topic |
| Edit a card | Dialog pre-filled with the current title; new title saved |
| Delete a card: Cancel, then Delete | Cancel keeps it; Delete removes it |
| Rename a topic | Updated in the list |
| System back button from the card screen | Returns to the topic list |
| Force-stop and relaunch | Topics and cards persisted |
| `sqlite3` on the device database | Rows match what the UI showed |
| Delete a topic that has a card | Confirmation shown; afterwards `topic` has 1 row and `card` has 0 (no orphans) |
| Vietnamese `Đồ ăn dặm — thịt bầm`, `Cháo thịt bầm cà rốt` and Japanese `ベビーフード たまご（卵）鶏肉 とり` written into the database, app relaunched | All render correctly with the system font (light and dark) |
| Dark mode | Full dark palette applied, readable |

Defect found and fixed: in light mode the status bar clock and icons were invisible (white on white) on Android 15+. Fixed with `enableEdgeToEdge()` in `MainActivity`, re-verified by screenshot.

### iOS (simulator `iPhone 18 Pro`, Xcode project generated from `iosApp/project.yml`)

| Step | Result |
|---|---|
| Build `iosApp` for the simulator | First build failed to link (undefined `sqlite3_*`); fixed by adding `-lsqlite3`; second build succeeded |
| First launch | **Crashed**: Compose Multiplatform requires `CADisableMinimumFrameDurationOnPhone` in `Info.plist`; added; launches and stays running |
| Empty state | Renders; a white strip below the content was fixed by making the SwiftUI host ignore safe areas |
| Database created | `Library/Application Support/databases/cardfilter.db` exists |
| Vietnamese topic inserted into that database, app relaunched | Listed with diacritics intact |

Not done on iOS: any tap-driven flow, restart persistence from the UI, dark mode.
- **Versions were upgraded after the manual run.** Android SDK platform 37.0 was installed and the project moved to `compileSdk` 37, Compose Multiplatform 1.12.1, lifecycle 2.10.0 and navigation 2.10.0. After that: 39 Android host tests and 28 iOS simulator tests passed, the Android build and the Xcode build succeeded, and a smoke test passed on both platforms (Android: create topic, open it, add a card, back, restart, reopen; iOS: launches and shows stored data). The detailed Android and iOS tables below were recorded on the earlier versions (1.11.1 / 2.9.6 / 2.9.2) and were not repeated in full.
- iOS simulator device is a machine-specific default (`iPhone 18 Pro`), overridable with `-PiosSimulatorDevice=...`.
