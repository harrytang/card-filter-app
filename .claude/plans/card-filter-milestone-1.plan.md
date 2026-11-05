# Plan: Card Filter — Topics and Basic Cards (Milestone 1)

**Source PRD**: .claude/prds/card-filter.prd.md
**Selected Milestone**: 1 — Chủ đề và card cơ bản ("Users can create a topic and add cards to it")
**Complexity**: Medium (most of the effort is greenfield KMP scaffolding, not feature logic)

## Summary
Stand up a Kotlin Multiplatform project (Compose Multiplatform UI, SQLDelight local storage) targeting Android and iOS, then deliver topic CRUD and card CRUD inside a topic. Cards carry only a title in this milestone. Dynamic attributes (M2) and search (M3) are out of scope, but the schema and module layout are chosen so they slot in via a SQLDelight migration without rework.

## Decisions Made in This Plan (confirm or override)
| Decision | Choice | Why |
|---|---|---|
| UI | Compose Multiplatform (shared UI) | PRD wants one app for iOS + Android; shared UI avoids writing every screen twice |
| Storage | SQLDelight (SQLite) | Local-only, no account (PRD scope 5). M3 search is easier over SQL/rows than over a key-value blob |
| State holders | `androidx.lifecycle.ViewModel` (KMP-supported) + `StateFlow` | Standard in current KMP templates |
| DI | Manual constructor injection via an `AppContainer` | Two repositories only; Koin is not worth it yet |
| Navigation | Navigation Compose (multiplatform) | Two screens now, more later |
| Card in M1 | `title` column only | PRD M2 defines attributes. `name` may become an attribute then; M2 decides whether `title` migrates into it |
| Package / app id | `app.cardfilter` (placeholder) | PRD gives none. Change before first store build |
| Library versions | Latest stable at implementation time, pinned in `gradle/libs.versions.toml` | Not pinned in this plan so they are not guessed |

## Patterns to Mirror
Greenfield repo (only the PRD and 2 PDFs exist). There is no existing code to mirror and no pattern is invented here. Conventions below are the external, standard KMP ones the implementation follows.

| Category | Source | Pattern |
|---|---|---|
| Naming | none in repo — Kotlin official style | `PascalCase` types, `camelCase` functions, one top-level class per file; SQLDelight files `Topic.sq`, `Card.sq` |
| Errors | none in repo | Validation returns a sealed result (`Valid` / `Invalid(reason)`), not exceptions. DB failures propagate and are shown as a generic UI error state |
| Logging | none in repo | None in M1. Add only if a real need appears |
| Data access | none in repo | Repository interface in `commonMain`, SQLDelight-backed impl, queries exposed as `Flow` via `asFlow().mapToList` |
| Tests | none in repo | `kotlin.test` in `commonTest`; repository tests in `androidUnitTest` using SQLDelight `JdbcSqliteDriver.IN_MEMORY` |

## Files to Change
Paths are relative to the repo root. `composeApp` and `iosApp` are the standard KMP wizard layout.

| File | Action | Why |
|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` | CREATE | KMP + Compose + SQLDelight build setup, version catalog |
| `composeApp/build.gradle.kts` | CREATE | Android + iOS targets, dependencies, SQLDelight config |
| `composeApp/src/commonMain/sqldelight/app/cardfilter/db/Topic.sq` | CREATE | `topic` table + queries |
| `composeApp/src/commonMain/sqldelight/app/cardfilter/db/Card.sq` | CREATE | `card` table + queries |
| `composeApp/src/commonMain/kotlin/app/cardfilter/domain/Topic.kt`, `Card.kt` | CREATE | Domain models (decoupled from generated SQLDelight types) |
| `composeApp/src/commonMain/kotlin/app/cardfilter/domain/TitleValidation.kt` | CREATE | Trim + non-blank rule shared by topic name and card title |
| `composeApp/src/commonMain/kotlin/app/cardfilter/data/TopicRepository.kt`, `CardRepository.kt` | CREATE | Interfaces |
| `composeApp/src/commonMain/kotlin/app/cardfilter/data/SqlTopicRepository.kt`, `SqlCardRepository.kt` | CREATE | SQLDelight implementations |
| `composeApp/src/commonMain/kotlin/app/cardfilter/data/DatabaseDriverFactory.kt` | CREATE | `expect class` for the platform driver |
| `composeApp/src/androidMain/kotlin/app/cardfilter/data/DatabaseDriverFactory.android.kt` | CREATE | `AndroidSqliteDriver` actual |
| `composeApp/src/iosMain/kotlin/app/cardfilter/data/DatabaseDriverFactory.ios.kt` | CREATE | `NativeSqliteDriver` actual |
| `composeApp/src/commonMain/kotlin/app/cardfilter/AppContainer.kt` | CREATE | Wires driver → database → repositories |
| `composeApp/src/commonMain/kotlin/app/cardfilter/ui/topics/TopicListScreen.kt`, `TopicListViewModel.kt` | CREATE | List, create, rename, delete topics |
| `composeApp/src/commonMain/kotlin/app/cardfilter/ui/cards/CardListScreen.kt`, `CardListViewModel.kt` | CREATE | List, create, edit, delete cards in a topic |
| `composeApp/src/commonMain/kotlin/app/cardfilter/ui/components/` | CREATE | Shared text-input dialog and delete-confirm dialog |
| `composeApp/src/commonMain/kotlin/app/cardfilter/App.kt` | CREATE | Theme + nav graph |
| `composeApp/src/androidMain/.../MainActivity.kt`, `iosApp/` | CREATE | Platform entry points |
| `composeApp/src/commonTest/...` | CREATE | Validation and ViewModel tests with fake repositories |
| `composeApp/src/androidUnitTest/...` | CREATE | Repository tests against in-memory SQLite |
| `.claude/prds/card-filter.prd.md` | UPDATE | Milestone 1 row → `in-progress`, Plan cell → this file |

## Tasks

### Task 1: Scaffold the KMP project
- **Action**: Generate a Compose Multiplatform project (Android + iOS) with the KMP wizard layout, pin versions in the version catalog, set the app id. Add dependencies: SQLDelight (runtime, coroutines-extensions, android-driver, native-driver, `sqlite-driver` for `androidUnitTest`), lifecycle-viewmodel-compose, navigation-compose, kotlinx-coroutines.
- **Mirror**: Standard wizard output (no repo pattern exists).
- **Validate**: `./gradlew :composeApp:assembleDebug` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` both succeed. The empty app launches on an Android emulator and an iOS simulator.

### Task 2: Schema and driver
- **Action**: Define `topic(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL)` and `card(id INTEGER PK AUTOINCREMENT, topic_id INTEGER NOT NULL REFERENCES topic(id), title TEXT NOT NULL, created_at INTEGER NOT NULL)` with an index on `card(topic_id)`. Queries: select-all (topics ordered by `created_at`, cards by topic), insert, update, delete. Add `expect/actual DatabaseDriverFactory`. Set SQLDelight `version` so M2 can add `1.sqm` migrations.
- **Mirror**: SQLDelight `.sq` conventions.
- **Validate**: `./gradlew :composeApp:generateCommonMainAppDatabaseInterface` generates without errors.

### Task 3: Domain, validation, repositories (tests first)
- **Action**: Write failing tests first, then implement.
  - `TitleValidation`: trims, rejects blank, accepts Vietnamese and Japanese text unchanged.
  - Repositories: create/list/rename/delete topic; create/list/edit/delete card; cards are scoped to their topic; `deleteTopic` removes the topic's cards **explicitly inside one transaction** (do not rely on `ON DELETE CASCADE`, see Risks).
  - Repositories expose `Flow` so the UI updates without manual refresh.
- **Mirror**: Interface in `commonMain`, SQL impl behind it.
- **Validate**: `./gradlew :composeApp:testDebugUnitTest` (runs `commonTest` and `androidUnitTest`).

### Task 4: ViewModels (tests first)
- **Action**: `TopicListViewModel` and `CardListViewModel` (takes `topicId` from nav args) expose a `StateFlow<UiState>` and handler functions: add, rename/edit, delete, with validation errors surfaced in state. Test with fake repositories in `commonTest`, using `kotlinx-coroutines-test`.
- **Mirror**: Single `UiState` data class per screen, events as functions.
- **Validate**: `./gradlew :composeApp:testDebugUnitTest`.

### Task 5: UI and navigation
- **Action**: `TopicListScreen` (list, FAB to add, row actions for rename and delete, empty state), `CardListScreen` (same for cards, back to topics, topic name in the app bar), shared input dialog and delete-confirm dialog. Wire `AppContainer` in `App.kt`; `MainActivity` and the iOS `ComposeUIViewController` both call `App()`.
- **Mirror**: Material 3 components, stateless composables with state hoisted to the ViewModel.
- **Validate**: Manual run of the acceptance flow below on Android emulator and iOS simulator.

### Task 6: Cross-platform manual verification
- **Action**: On both platforms: create two topics, add cards to each, confirm cards are not shared between topics, rename and delete a card, delete a topic (its cards disappear), kill and relaunch the app, confirm data persisted. Enter Vietnamese (`thịt bầm`) and Japanese (`ベビーフード`, `たまご`) text and confirm it round-trips unchanged.
- **Mirror**: n/a
- **Validate**: All steps pass on both platforms. Record any platform-specific failure before closing the milestone.

### Task 7: Close out
- **Action**: Update the PRD milestone table (done at plan creation for `in-progress`; set to `complete` only after Task 6 passes).
- **Validate**: PRD row reflects reality.

## Validation
```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug build
```
Requires macOS with Xcode and the Android SDK (both needed for the iOS and Android checks).

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| `ON DELETE CASCADE` silently does nothing because SQLite foreign keys are off by default on some drivers, leaving orphan cards | Medium | Delete cards explicitly in a transaction in `deleteTopic`; add a test asserting no orphans |
| KMP/Compose/SQLDelight version incompatibility at scaffold time | Medium | Start from the official wizard output; pin versions in the catalog; get Task 1 green on both targets before writing feature code |
| iOS builds cannot be validated from CI or a non-Mac machine | Low | Plan assumes the local Mac; note it if CI is added later |
| Compose Multiplatform iOS text input quirks with IME composition (Japanese, Vietnamese Telex) in `TextField` | Medium | Cover in Task 6 manual test with real Vietnamese and Japanese input; this matters for M2/M3 since all data is typed text |
| `title`-only card design conflicts with M2's "name is an attribute" | Low | Isolated to one column and one migration; decide at M2 planning |
| PRD requirements are still unvalidated (users and metrics TBD) | High (inherited) | M1 is small and exercises the data model; do not extend beyond M1 before validating with a real user |

## Implementation Status (updated during build)

Evidence: [.claude/tdd/card-filter-milestone-1.tdd.md](../tdd/card-filter-milestone-1.tdd.md). Design: [DESIGN.md](../../DESIGN.md).

**Done (Tasks 1-5, built test-first where testable):** KMP scaffold, schema, domain, repositories, ViewModels, design system, screens, navigation, Android and iOS entry points. 39 Android host tests and 28 iOS simulator tests pass; Android APK builds; iOS framework links.

**Deviations from the plan above:**
- Layered packages inside `composeApp` with a use-case layer (`domain/usecase`), following the android-clean-architecture skill. Not separate Gradle modules.
- Android app is a separate `androidApp` module. AGP 9 no longer allows the app and Kotlin Multiplatform plugins in one module.
- SQL files are under `data/db` (package `app.cardfilter.data.db`), not `db`.
- No `expect/actual DatabaseDriverFactory`: each platform entry point builds its own `SqlDriver` and passes it to `AppContainer`.
- Repository tests run in `androidHostTest` (task `:composeApp:testAndroidHostTest`), not `androidUnitTest`.
- Versions: Compose Multiplatform 1.12.1, lifecycle 2.10.0, navigation 2.10.0 with `compileSdk` 37 (Android SDK platform 37.0 installed). Initially built on older versions because platform 37 was missing; upgraded afterwards and re-verified.
- Added: a `DESIGN.md` + `design-tokens.json` design system.

**Task 6 (manual verification):** done on Android through the real UI, and on iOS up to launch, rendering and reading the database. Details and two defects found and fixed (invisible light-mode status bar on Android; iOS launch crash from a missing `Info.plist` key) are in the evidence report.
- iOS host project: `iosApp/project.yml` (generate with `xcodegen generate` inside `iosApp/`; needs `brew install xcodegen`).

**Still open (close-out):**
- iOS create/edit/delete flow was not driven by hand (the simulator cannot be tapped from the command line).
- Typing Vietnamese/Japanese through a real keyboard (IME composition) is untested on both platforms.
- Coverage is measured (`./gradlew :composeApp:coverageReport`): logic layers 97.9% of lines, whole codebase 31.2%. The 80% target is met for the logic but not overall, because the Compose UI has no automated tests. Closing that gap needs Compose UI tests, which are not in this milestone's plan.
- Milestone 1 stays `in-progress` in the PRD until the items above are checked by a person.

## Acceptance
- [ ] All tasks complete
- [ ] `testDebugUnitTest`, `assembleDebug`, and the iOS framework link all pass
- [ ] Topic create/view/rename/delete and card create/view/edit/delete work on Android and iOS
- [ ] Data survives app restart on both platforms; deleting a topic leaves no orphan cards
- [ ] Vietnamese and Japanese text round-trips unchanged
- [ ] No code written outside this milestone's scope (no attributes, no search)
