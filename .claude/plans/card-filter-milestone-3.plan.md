# Plan: Card Filter — Search Cards (Milestone 3)

**Source PRD**: .claude/prds/card-filter.prd.md
**Selected Milestone**: 3 — Lọc card ("Users narrow the card list by what the cards say")
**Complexity**: Medium (little logic, but text folding needs per-platform code and the real risk is IME typing on two platforms)
**Depends on**: M1 and M2 code (both built). Both are still `in-progress` in the PRD only for manual close-out items. Their open **IME typing** item blocks M3 acceptance too, because search is only as good as the text typed into it (Task 7 covers it).

## Summary
Add one search box to the card list of a topic. Typing narrows the list to cards where **any** typed word appears in the card's title or in any attribute **value** (never an attribute name). Matching ignores case and Vietnamese diacritics (`thit bam` finds `thịt bầm`) and treats hiragana and katakana as the same (`たまご` finds `タマゴ`), but does not link kanji to kana. It searches only the open topic. Filtering runs in Kotlin over the cards M2 already loads, so there is **no schema change and no migration**. The product must also say plainly that this finds cards containing words; it does not prove a card lacks something (PRD risk 1).

## Decisions Made in This Plan (confirm or override)
| Decision | Choice | Why |
|---|---|---|
| Where filtering runs | In Kotlin, in `commonMain`, over `Card` objects already in memory | SQL `LIKE` cannot fold Vietnamese diacritics or kana. M2 loads attributes onto `Card` for exactly this. Fine for hundreds of cards (the PDFs hold ~100). Revisit above ~1,000 |
| Fields searched | Title **and** attribute values; never attribute names | PRD says values only, but the PRD has no `title`. M2 kept `title` and recorded "M3 searches both". A card named `Cháo cá` must be findable by its name |
| Match type | Substring of the folded text, per query word | Japanese has no word boundaries, so whole-word matching would break `たまご` inside a longer string. Cost: `an` finds `banana`; acceptable for this PRD |
| Multiple words | A card shows if it contains **any** word (OR), per PRD | Words split on any Unicode whitespace including U+3000 (the space a Japanese IME types) and U+00A0 |
| Blank query | Shows every card, in the normal order | Whitespace-only counts as blank. A word that folds to nothing (a lone combining accent) is dropped, so it cannot match everything by accident |
| Folding steps | lowercase → Unicode NFD → drop combining marks **U+0300–U+036F only** → map `đ`/`Đ` to `d` → hiragana to katakana (U+3041–3096 → +0x60, U+309D/E → U+30FD/E) | NFD turns `ế` into `e` plus marks and also normalises composed vs decomposed typing. Stripping only the Latin range is deliberate: the kana voicing marks (U+3099/309A) must **survive**, or `かき` would match `がぎ` and `たまご` would match `たまこ` |
| NFD source | `expect`/`actual`: `java.text.Normalizer` on Android, `NSString.decomposedStringWithCanonicalMapping` on iOS | Kotlin common has no normaliser. **Alternative**: a hand-written Vietnamese table in common code (no platform code, but only covers listed letters). Chosen option covers every language the user might type. `commonTest` runs on both platforms, so it proves the two actuals agree |
| Not folded | Half-width katakana (`ｶﾞ`), full-width Latin (`ａｂｃ`), kanji↔kana | PRD excludes kanji; the others are not asked for. Needs NFKC. Recorded as an open question rather than silently guessed |
| ViewModel state | Keep `cards` as the **full** topic list; add `query` and `visibleCards` | Existing tests and the "is this topic empty?" screen branch keep their meaning. The screen picks `cards.isEmpty()` (topic empty) vs `visibleCards.isEmpty()` (no matches) |
| Search field IME safety | New `SearchField` that owns its text locally, like `DraftTextField` | M2 learned a field backed by async state breaks Vietnamese and Japanese composition. `initialText` read once, so rotation and return from the editor restore the query from the ViewModel |
| Visibility of the field | Shown whenever the topic has at least one card (unfiltered), kept while the query matches nothing | Hiding it on zero matches would trap the user with no way to clear |
| Clearing | Clear (×) button inside the field; query is also cleared when the user taps **Add card** | A new card that does not match the active query would silently vanish after Save. Small and cheap |
| Persistence | Query lives in the ViewModel only: survives rotation and the trip to the editor, resets when leaving the topic | No requirement to remember it longer |
| Safety wording | While a query is active, show a one-line note under the field; the no-results state repeats it | PRD risk 1 mitigation ("ghi rõ... tìm kiếm khớp từ khoá, không phải bộ lọc an toàn"). **Wording is yours to confirm** (UI strings are English for now, see `Strings.kt`) |
| Not doing | Highlighting matches, result counts, search history, per-attribute filters, exclusion, AND mode, cross-topic search, debounce, a background dispatcher | Not in the PRD MVP, or not needed at this size |

## Patterns to Mirror
| Category | Source | Pattern |
|---|---|---|
| Naming | `composeApp/src/commonMain/kotlin/app/cardfilter/domain/usecase/CardUseCases.kt:12-14` | Small class, one responsibility, `operator fun invoke`. Search logic is a pure domain class, not a use case, because it needs no repository |
| Errors | `.../presentation/cards/CardListViewModel.kt:39, 44` | Upstream `.catch` turns a failing flow into `failure` state. Keep it **before** combining with the query so the existing failure behaviour is unchanged. Search itself cannot fail |
| Data access | `.../data/SqlCardRepository.kt` (`observeByTopic`) | Untouched. Search consumes the existing `Flow<List<Card>>`. Topic scoping already happens there |
| ViewModel | `.../presentation/cards/CardListViewModel.kt:18-24, 36-47` | One `UiState` data class, events as functions, collectors launched in `init` |
| UI | `.../designsystem/Components.kt:129-156` (`DraftTextField`) | Local text state + `onTextChange` callback; Material `OutlinedTextField`; no business logic in the composable |
| UI states | `.../designsystem/Components.kt:101-121` (`EmptyState`), `.../presentation/cards/CardListScreen.kt:86-111` | `when` over loading / empty / list. Add a "no matches" branch using `EmptyState` |
| Strings | `.../presentation/Strings.kt:20-27` | All user-visible text as `const val` / small functions in `Strings` |
| Tests | `.../commonTest/.../CardListViewModelTest.kt:30-51, 66-72`, `.../testutil/FakeRepositories.kt:39-60`, `.../CardUseCasesTest.kt:52-62` | `UnconfinedTestDispatcher` as Main, `FakeCardRepository`, a dedicated "Vietnamese and Japanese unchanged" style case |
| expect/actual | none in repo | M1 dropped its planned `DatabaseDriverFactory` for plain constructor wiring (see M1 deviations). No pattern exists; this plan uses the standard KMP `expect fun` + one `actual` per source set. Do not invent more than that one function |

## Files to Change
Paths relative to `composeApp/src` unless noted.

| File | Action | Why |
|---|---|---|
| `commonMain/kotlin/app/cardfilter/domain/search/TextFolding.kt` | CREATE | `foldForSearch(text)`: the steps in the decision table; declares `internal expect fun decompose(text: String): String` |
| `androidMain/kotlin/app/cardfilter/domain/search/TextFolding.android.kt` | CREATE | `actual` via `java.text.Normalizer` (NFD) |
| `iosMain/kotlin/app/cardfilter/domain/search/TextFolding.ios.kt` | CREATE | `actual` via `NSString.decomposedStringWithCanonicalMapping` |
| `commonMain/kotlin/app/cardfilter/domain/search/CardSearch.kt` | CREATE | `CardSearch(cards)` folds each card once; `filter(query)` returns matching cards in original order; `parseQuery` splits and folds words |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardListViewModel.kt` | UPDATE | `query`, `visibleCards` in state; `onQueryChange`; combine the card flow with the query |
| `commonMain/kotlin/app/cardfilter/presentation/cards/CardListScreen.kt` | UPDATE | Search field above the list, no-results branch, safety note, clear query on Add |
| `commonMain/kotlin/app/cardfilter/designsystem/Components.kt` | UPDATE | `SearchField` (local text, clear button, search IME action that dismisses the keyboard) |
| `commonMain/kotlin/app/cardfilter/presentation/Strings.kt` | UPDATE | Search hint, clear label, no-results title/message, safety note |
| `DESIGN.md` (repo root) | UPDATE | Document `SearchField` and the safety-note rule |
| `commonTest/kotlin/app/cardfilter/domain/search/TextFoldingTest.kt` | CREATE | Folding rules, including NFC vs NFD input and the dakuten guard |
| `commonTest/kotlin/app/cardfilter/domain/search/CardSearchTest.kt` | CREATE | Matching rules from the PRD |
| `commonTest/kotlin/app/cardfilter/presentation/cards/CardListViewModelTest.kt` | UPDATE | Query behaviour in the ViewModel |
| `.claude/tdd/card-filter-milestone-3.tdd.md` | CREATE | RED/GREEN evidence and manual results, as for M1 and M2 |
| `.claude/prds/card-filter.prd.md` | UPDATE | M3 row → `in-progress`, Plan cell → this file (done at plan creation) |

No change to `AppContainer.kt`, the `.sq` files, migrations, or the schema snapshots. `App.kt` is unchanged because the screen already receives its ViewModel.

## Tasks
Set `JAVA_HOME` to Android Studio's bundled JDK as in M1/M2 before running Gradle.

### Task 1: Folding spike on both platforms (tests first)
- **Action**: Write `TextFoldingTest` first and see it fail to compile. Cases: `ThỊT` → `thit`; `bầm` equals `bam` after folding; composed (`ế`) and decomposed (`e` + U+0302 + U+0301) input fold the same; `đ`/`Đ` → `d`; `たまご` and `タマゴ` fold equal; `か` ≠ `が` and `か` ≠ `ガ` (voicing kept) while `が` (composed) equals `か`+U+3099 (decomposed) and equals `ガ`; `ぱ` ≠ `ば`; `ー` unchanged; `鶏` ≠ `とり` ≠ `トリ`; Latin-only text is unchanged apart from case; empty and combining-mark-only strings fold to empty. Then implement `foldForSearch` and both `actual`s.
- **Mirror**: `CardUseCasesTest.kt:52-62`.
- **Validate**: `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test` — the same `commonTest` class passes on both. If the iOS interop call does not compile or `decomposedStringWithCanonicalMapping` is unavailable, stop here and fall back to the common-code table alternative before going on.

### Task 2: CardSearch (tests first)
- **Action**: Write `CardSearchTest` first. Cases: matches a title; matches an attribute value; **does not** match an attribute name (`thành phần` as a name, query `thanh phan` finds nothing unless a value contains it); case-insensitive; `thit bam` finds `thịt bầm`; two words find a card containing either (OR) and one containing both appears once; substring (`trứng` finds `trứng gà`); blank and whitespace-only query (including U+3000) return all cards in the original order; query of separators only returns all; a lone combining mark is ignored; no match returns empty; card with no attributes is found by title only; result keeps input order; hiragana query finds katakana value and the reverse; kanji `鶏` is not found by `とり`.
- **Mirror**: `CardListViewModelTest.kt:66-72` for fixture style.
- **Validate**: both Gradle test tasks above.

### Task 3: ViewModel (tests first)
- **Action**: Extend `CardListViewModelTest` first. Cases: initial `query` is empty and `visibleCards == cards`; `onQueryChange` narrows `visibleCards` while `cards` keeps the whole topic; clearing the query restores all; a card created while a query is active appears iff it matches; a card edited so it stops matching drops out; cards of another topic never appear even if they match; deleting a card chosen from the filtered list still works and removes it; a failing card flow still ends loading and sets `failure` (existing test must pass unchanged); `onQueryChange` before the first emission does not crash. Then implement: put `query` in a `MutableStateFlow`, build `observeCards(topicId).catch{...}.map(::CardSearch)`, `combine` with the query, and write `cards`, `visibleCards`, `query`, `isLoading` in one `update`.
- **Mirror**: `CardListViewModel.kt:36-47`, `CardListViewModelTest.kt:30-51`.
- **Validate**: both Gradle test tasks above.

### Task 4: SearchField and strings
- **Action**: Add `SearchField(initialText, label, clearLabel, onTextChange, onSearch)` to `Components.kt`: single line, `ImeAction.Search`, no auto-capitalisation, trailing clear button that empties its own text and reports `""`, leading search icon. The clear button needs a 48 dp target and a `contentDescription` from `Strings`, like the other icon buttons. Add the strings. No logic inside the composable.
- **Mirror**: `DraftTextField` (`Components.kt:129-156`), DESIGN.md rules 1-4.
- **Validate**: `./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64`.

### Task 5: Wire the screen
- **Action**: In `CardListScreen`, put `SearchField` above the list inside a `Column` (so it does not scroll away), shown when `state.cards` is not empty. Branches: loading → nothing; topic empty → existing `EmptyState`; query active and `visibleCards` empty → `EmptyState` with the no-matches text and the safety note; otherwise the list of `visibleCards`. Show the safety note as supporting text only while the query is non-blank. Keep the FAB; call `onQueryChange("")` before `onAddCard()`. The `LazyColumn` still keys rows by card id. Dismiss the keyboard on the Search IME action.
- **Mirror**: `CardListScreen.kt:86-111`, `EmptyState`.
- **Validate**: `./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`.

### Task 6: DESIGN.md
- **Action**: Document `SearchField` (local-text rule and why), where the field sits, and the rule that search results are never presented as an "allowed/safe" list.
- **Validate**: `DESIGN.md` matches what shipped.

### Task 7: Manual verification on both platforms
- **Action**: Enter about 10 real cards by hand from the Pigeon and Wakodo PDFs (this also delivers M2's "try real data before M3" risk item). Then, on the Android emulator **and** the iOS simulator:
  1. `thit bam` finds a card whose value is `thịt bầm`; `THỊT` finds it too.
  2. `trứng` and `trung` find the same cards; a query of two words shows cards with either.
  3. `たまご` finds a `タマゴ` card; `タマゴ` finds a `たまご` card; `とり` does **not** find `鶏`; `かき` does not find `がぎ`.
  4. A word that only appears in an attribute **name** finds nothing.
  5. Clear (×) restores the full list; a blank query shows everything.
  6. Open a result, edit it so it no longer matches, go back: it drops out. Tap Add card with a query active: the query clears.
  7. Rotate (Android) and open/close a card: the query and the field text agree.
  8. **Real keyboard**: type Vietnamese Telex (`thịt bầm`) and Japanese IME (`たまご`, converting to `卵`) **into the search field**. The list must follow while composing, the composing underline must not jump or duplicate characters, and the final text must match what was typed. This also closes M1/M2's open IME item.
  9. No-results state shows the safety note; with 100+ cards (paste a few duplicates) typing does not stutter; dark mode.
- **Mirror**: manual tables in `.claude/tdd/card-filter-milestone-2.tdd.md`.
- **Validate**: every step passes on both platforms. Record any platform-specific failure before closing.

### Task 8: Close out
- **Action**: Write `.claude/tdd/card-filter-milestone-3.tdd.md` (RED/GREEN per cycle, test counts, coverage). Re-run the coverage report. Set the PRD M3 row to `complete` only after Task 7 passes.
- **Validate**: PRD row matches reality.

## Validation
```bash
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:coverageReport
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```
The migration verification task is not needed: the schema does not change. (The `-destination ... ARCHS=arm64` flags are required; without them Xcode tries `x86_64` and fails.)

## Implementation Status (updated during build)

Evidence: [.claude/tdd/card-filter-milestone-3.tdd.md](../tdd/card-filter-milestone-3.tdd.md). Design: [DESIGN.md](../../DESIGN.md).

**Done (Tasks 1-6, built test-first):** NFD `expect`/`actual` (Android `java.text.Normalizer`, iOS `NSString.decomposedStringWithCanonicalMapping`; no fallback table was needed), `foldForSearch`, `CardSearch`, ViewModel `query`/`visibleCards`/`onQueryChange`, `SearchField`, screen wiring, strings, `DESIGN.md`. 120 Android host tests and 100 iOS simulator tests pass; APK builds; iOS framework links; `xcodebuild` succeeds. Three mutation checks were caught by the intended tests. Logic coverage 99.1%.

**Task 7 (manual), both platforms:** on iOS a new XCUITest drives the search through the real UI, including Telex composition in the search field, in light and dark. It found one defect (no-results message hidden by the keyboard), fixed with `imePadding()`. On the Android emulator the search was driven by hand over `adb`, with real Gboard key taps for Telex (`thịt bầm`) and Japanese romaji (`みるく` found a katakana-only card), the Search key, clear-on-add, rotation and a 150-card topic; `imePadding()` was confirmed there too. Android data was seeded straight into SQLite (invented cards), not typed.

**Still open (close-out):** Japanese typing into the search field on iOS is blocked by the simulator; kanji-vs-reading and voiced-vs-unvoiced kana are unit-tested but were not typed through an IME; no real Pigeon/Wakodo data entered; Android dark mode and a physical device not checked; the safety-note wording needs your confirmation. Milestone 3 stays `in-progress` in the PRD until a person checks these.

**Deviations:** see the evidence report (single query source in `CardListUiState`, `imePadding()`, `\u` escapes, the `xcodebuild` flags, the added UI test).

## Risks
| Risk | Likelihood | Mitigation |
|---|---|---|
| Users read search results as "safe" (a card missing the word `trứng` is not shown as egg-free, and a card with a missing or mistyped value will not be found) | High | Safety note while a query is active and in the no-results state; DESIGN.md rule. PRD still asks you whether exclusion belongs in a later release |
| Stripping all combining marks would erase Japanese voicing (`かき` finds `がぎ`) | Medium | Strip only U+0300–U+036F; dedicated tests in Task 1 and Task 2 |
| iOS and Android normalise differently, so the same query behaves differently | Medium | One `commonTest` suite runs on both; Task 1 stops and falls back to a common-code table if the iOS call is not usable |
| IME composition breaks in the search field (Telex, Japanese), the exact thing M1 and M2 never verified by hand | Medium | Field owns its text locally; Task 7 step 8 on both platforms. This is the main reason the milestone cannot be called done from tests alone |
| Query text and ViewModel query disagree after rotation or returning from the editor | Medium | `initialText` read once from the ViewModel; Task 7 step 7 |
| A new or edited card disappears because it no longer matches the active query | Medium | Query cleared on Add; the edited-card case is expected behaviour and is in the manual test |
| Half-width katakana and full-width Latin not matched, so PDF text in those forms is missed | Low | Documented as not done. Raise before release if the Pigeon/Wakodo data uses them |
| Typing stutters on large topics (filtering on the main thread, folded on every card change) | Low | Cards are folded once per list change, not per keystroke. Measure in Task 7; add a background dispatcher only if it shows |
| Substring match is noisy (`an` finds `banana`) | Low | Accepted for Japanese support; revisit after real use |
| Title/`name` overlap: users type the card name twice, so both are searched | Low | Same as M2's recorded decision |
| PRD requirements still unvalidated (users, metrics TBD) | High (inherited) | After Task 7, put the app in front of one real user with real data before building more |
| Compose UI has no automated tests, so overall coverage stays below 80% | High (known) | Keep search logic in `domain/search` and the ViewModel (target ≥80% there, as in M1/M2); UI covered by Task 7 |

## Acceptance
- [ ] All tasks complete
- [ ] `testAndroidHostTest`, `iosSimulatorArm64Test`, `assembleDebug`, iOS framework link and `xcodebuild` pass
- [ ] `thit bam` finds `thịt bầm`; hiragana and katakana find each other; `とり` does not find `鶏`; `かき` does not find `がぎ`
- [ ] Multiple words show a card if it contains any of them; attribute names are never matched; only the open topic is searched
- [ ] The same folding tests pass on Android and iOS
- [ ] Vietnamese and Japanese typed through a real keyboard work in the search field on both platforms
- [ ] The safety note appears whenever a query is active
- [ ] Logic-layer coverage ≥ 80%
- [ ] No schema change, no exclusion/AND filters, no cross-topic search (out of PRD scope)
