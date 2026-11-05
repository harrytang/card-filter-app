# TDD Evidence — Milestone 3 (Search cards)

**Source plan**: [.claude/plans/card-filter-milestone-3.plan.md](../plans/card-filter-milestone-3.plan.md)
**Design**: [DESIGN.md](../../DESIGN.md)
**Not a git repository**, so no checkpoint commits exist. RED and GREEN evidence is recorded here instead.

Commands run with `JAVA_HOME` set to Android Studio's bundled JDK and the project wrapper (Gradle 9.8.1).

## User journeys (from the plan)

1. As a user, I type a word and see only the cards of this topic whose title or attribute **value** contains it; attribute names never match.
2. As a user, `thit bam` finds `thịt bầm`, capitals do not matter, and several words match a card that has any of them.
3. As a user, `たまご` finds `タマゴ` and the reverse, but `かき` does not find `がぎ` and `とり` does not find `鶏`.
4. As a user, I can clear the search, and a query with no results tells me so and says it is not an allergy check.
5. As a user, adding a card clears the search so the new card cannot disappear after saving.

## Plan deviations

- **ViewModel**: no separate `query` flow. The query lives only in `CardListUiState.query`; the collector combines the card flow with `_state.map { it.query }.distinctUntilChanged()`. One source of truth, same behaviour as planned.
- `CardSearch` also exposes `cards` (the unfiltered list) so the ViewModel needs no second copy.
- Added **`imePadding()`** to the list content column (not in the plan) after the iOS screenshot showed the no-results message, including the "not an allergy check" sentence, hidden behind the keyboard.
- Unicode constants are written as `\uXXXX` escapes in source and tests, after the first draft contained invisible raw combining characters.
- The plan's `xcodebuild` command needs `-destination 'platform=iOS Simulator,name=iPhone 18 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES`; without it Xcode tries `x86_64` and fails with "Unknown iOS simulator arch".
- Added an XCUITest (`testVietnameseTelexSearchNarrowsCards`) and a `snapshot()` helper to `iosApp/iosAppUITests/CardFilterUITests.swift`. Not in the plan; it covers Task 7 on iOS.

## Task report

| Cycle | RED (before production code) | GREEN |
|---|---|---|
| Folding + search + ViewModel (tests for all three written first) | `:composeApp:testAndroidHostTest` failed to compile with **87 errors, all missing production code**: 37 `foldForSearch`, 2 `CardSearch`, 11 `onQueryChange`, 12 `visibleCards`, 5 `query`; the other 20 (type inference, `it`) cascade from those | Android host: **120 tests, 0 failures**. iOS simulator: **100 tests, 0 failures** (both +38 over M2's 82 and 62) |

RED is compile-time: the errors are exactly the types and members that did not exist, not setup problems.

## Test specification

| # | What is guaranteed | Test | Type | Result |
|---|---|---|---|---|
| 1 | Folding ignores case; removes Vietnamese diacritics (`thịt bầm` → `thit bam`); `đ`/`Đ` → `d`; composed and decomposed Vietnamese fold the same | `TextFoldingTest` (4 tests) | unit, **both platforms** | PASS |
| 2 | Hiragana and katakana fold the same (incl. `ゝ`/`ヽ`); **voicing marks survive** (`か`≠`が`≠`ガ`, `ぱ`≠`ば`); composed and decomposed `が` fold the same; `ー` unchanged; kanji never equals its reading; empty and accent-only text fold to nothing | `TextFoldingTest` (6 tests) | unit, **both platforms** | PASS |
| 3 | Matches the title and any attribute value; never an attribute name; case and diacritics ignored both ways; substring (`trung` finds `trứng gà`) | `CardSearchTest` | unit | PASS |
| 4 | Several words match when **any** is present, a card with all of them is listed once; words split on U+3000 and U+00A0 | `CardSearchTest` | unit | PASS |
| 5 | Blank, whitespace-only and separator-only queries return every card in order; a lone accent is ignored (cannot match everything); no match returns empty; a card with no attributes is found by title only; result order is input order | `CardSearchTest` | unit | PASS |
| 6 | Kana: either script finds the other; voiced and unvoiced stay different; kanji not found by reading | `CardSearchTest` | unit | PASS |
| 7 | ViewModel: `cards` keeps the whole topic while `visibleCards` narrows; clearing restores all; the query follows case/diacritics/kana; a card created while searching appears only if it matches; an edited card that stops matching drops out; other topics never appear; a card picked from the filtered list can be deleted; typing before the first cards arrive neither crashes nor loses the query; the query survives list changes | `CardListViewModelTest` (+10 tests) | unit | PASS |

### Mutation checks (tests can fail)

Each mutation was applied, the Android host tests run, and the file restored byte for byte (`cmp`):

| Mutation | Failing tests (as intended) |
|---|---|
| Also strip the kana voicing marks (U+3099/309A) | `TextFoldingTest.voicingMarksAreKept_soVoicedAndUnvoicedKanaStayDifferent`, `CardSearchTest.voicedAndUnvoicedKanaAreDifferentWords` |
| Also search attribute names | `CardSearchTest.neverMatchesAnAttributeName` |
| Require **all** words instead of any | `CardSearchTest.severalWordsMatchWhenAnyOneIsPresent`, `CardSearchTest.wordsAreSplitOnIdeographicAndNoBreakSpaces` |

Not mutation-checked: the rest of the suite.

## Final verification

```
./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test \
          :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 \
          :composeApp:coverageReport
BUILD SUCCESSFUL
Android host tests: 120 tests, 0 failures   (counted from the XML reports)
iOS simulator tests: 100 tests, 0 failures  (shared domain + ViewModel tests, run natively)
androidApp-debug.apk built; iOS framework links; no compiler warnings from project sources
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
** BUILD SUCCEEDED **
```

The migration verification task was not run: the schema did not change.

## Coverage and known gaps

Measured with `./gradlew :composeApp:coverageReport` (JaCoCo, Android host tests only).

| Layer | Lines |
|---|---|
| `domain/search` (new) | **100% (28/28)** |
| `CardListViewModel` (all inner classes) | 100% of lines except one partial branch in a collector lambda (`$2`, 6/7) |
| **Logic combined** (data, domain, use cases, validation, ViewModels) | **99.1% (339/342)** |
| screens, design-system components, app wiring, strings | 0% (no Compose UI tests) |
| **Whole codebase** | **36.2% (370/1021)** |

The 80% target is met for logic and **not met overall**, for the same reason as M1 and M2. The coverage figures were taken before the one-line `imePadding()` change (UI only).

## Manual verification (plan Task 7)

### iOS (simulator `iPhone 18 Pro`, iOS 27) — driven through XCUITest, clean install each run

`testVietnameseTelexSearchNarrowsCards`, run after the two existing tests (all three: `Executed 3 tests, with 0 failures`), and on its own in light and dark appearance (passed, passed, and passed again after the `imePadding()` fix). It creates a topic, adds three cards (two on the English keyboard, `Thịt bầm` composed through real Telex key presses), then:

| Step | Result |
|---|---|
| Query `egg` | Only `Rice porridge` remains; the "Shows cards that contain any of these words…" note is shown |
| Clear (×) | All three cards return |
| `EGG MILK` | Rice porridge and Carrot puree (either word, case ignored); `Thịt bầm` hidden |
| `zzz` | "No matching cards" with "not an allergy check" |
| `thit bam` (plain letters) | Finds the card titled `Thịt bầm`; others hidden |
| **Telex composition inside the search field** (`t h i j t ␣ b a a f m`) | Field value `thịt bầm`; list follows and shows only `Thịt bầm` |
| English keyboard's Search key | Keyboard dismissed, query kept, list unchanged |
| Add card, then Back | Query cleared; all three cards listed |

Screenshots were reviewed in light and dark: the field is pinned above the list, the note wraps to two lines, contrast and the clear and Search keys look right in both. **One defect found and fixed**: the no-results message was centred behind the keyboard and cut off. After adding `imePadding()` the whole message, including the safety sentence, sits above the keyboard (re-run in dark mode).

### Android (emulator `booklet-api36`, API 36, 1080x2400, light mode, debug APK built after the `imePadding()` fix)

**Data was seeded, not typed.** The emulator had no data, so 3 topics (Pigeon 6 cards, Wakodo 2, Bulk 150) with Vietnamese and Japanese text were inserted straight into the app's SQLite database with `sqlite3`, then the app was relaunched. A 7th Pigeon card, `ミルク粥` with note `ミルク` (katakana only), was added the same way for the kana check. These are made-up cards modelled on the PDFs, not real Pigeon/Wakodo rows. Typing used `adb shell input text` for ASCII (key events, no composition) and real taps on Gboard's keys for Vietnamese and Japanese, which is what makes those two rows real composition.

| Step | Result |
|---|---|
| `thit bam` (ASCII) in Pigeon | Only `Cháo thịt bầm`. Wakodo's `Set 1 thịt bầm` does **not** appear (scoped to the open topic). Note shown |
| `egg` / `EGG sua` | Rice porridge / Súp bí đỏ + Rice porridge (any word, case ignored, `sữa` folds to `sua`) |
| `zzz` with the **docked keyboard open** | "No matching cards" and the whole message, including "not an allergy check", is above the keyboard. **`imePadding()` verified on Android** (with `enableEdgeToEdge()`) |
| `allergen` (only ever an attribute **name**) | No match |
| `do` | `Súp bí đỏ` (`đ` folds to `d`) |
| **Real Telex** on Gboard Vietnamese: `t h i j t ␣ b a a f m` | Field `thịt bầm`; Gboard's strip still shows the word being composed (bầm/băm/bằm); list follows and shows only `Cháo thịt bầm` |
| Gboard **Search key** | Keyboard hidden (`mInputShown=false`), focus cleared, query and results kept |
| Add card, then Back | Query cleared, all 6 cards listed, field back to its hint |
| Re-entering the topic from the topic list | Query reset (field empty, all cards) |
| Rotate to landscape and back with query `egg` | Field text and results agree in both orientations |
| **Real Japanese romaji** on Gboard: `m i r u k u` (composes `みるく`, underline still active) | List narrows to `ミルク粥` (katakana only): **hiragana found katakana through a real IME**. Committing the candidate `ミルク` keeps the same result |
| Bulk topic (150 cards): `thit bam` | 15 matching cards listed (010, 020, 030, …). `dumpsys gfxinfo` while typing: 15 frames, median 17 ms, p99 48 ms, versus 13 frames, median 16 ms, p99 32 ms for the same typing in the 7-card topic. The emulator renders in software and both runs exceed the 16 ms frame budget, so this shows no extra stutter from 150 cards but is **not a performance proof** |

Two setup traps, neither an app defect: Gboard's first-run Japanese **stylus-handwriting tutorial** covered the app and made the keyboard float (fixed by cancelling it and setting `secure stylus_handwriting_enabled` to 0, restored afterwards); and one early run typed ASCII while Gboard was still on Japanese, so the query became `ティtばm` and correctly matched nothing. A stray empty `databases` file I created with a mistyped command briefly stopped the app creating its database; it was removed.

### Still not done

- **Japanese typed into the search field on iOS**: still blocked (the Japanese keyboard cannot be enabled in this simulator runtime, per the M2 report). Kana matching is covered by unit tests that run natively on iOS, and by the Android IME check above.
- **Kanji vs reading through typing**: `とり` not finding `鶏` and `かき` not finding `がぎ` are covered by unit tests only. The Android check proved hiragana finds katakana; it did not type kanji.
- Real Pigeon/Wakodo card data was not entered; the seeded cards are invented. No 100+ card check on iOS.
- Long note wrapping on a narrow phone, and a physical device or other IME (Samsung keyboard, Apple's Japanese 12-key): not checked.
- Android dark mode: not checked (iOS dark mode was).
- The safety-note wording is mine and still needs the owner's confirmation.
- Milestone 3 stays `in-progress` in the PRD until a person has reviewed the items above.
