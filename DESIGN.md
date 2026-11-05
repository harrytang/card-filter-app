# Card Filter — Design System

Generated for a greenfield app (no existing styles to extract). Tokens live in
[design-tokens.json](design-tokens.json) and are implemented in
[Theme.kt](composeApp/src/commonMain/kotlin/app/cardfilter/designsystem/Theme.kt).

## Direction

A calm, plain utility for reading and searching lists of food and ingredient data. The PRD's
likely audience is a caregiver checking ingredients, so the UI favours legibility and trust
over decoration: warm paper background, one deep sage-green accent, white cards with a hairline
border, no gradients, no glass effects, no animation beyond Material defaults.

## Decisions and rationale

| Decision | Choice | Why |
|---|---|---|
| Accent | Deep sage green `#2F6B4F` (light), pale sage `#8FD0AB` (dark) | Food-adjacent and calm; avoids the purple/blue default look |
| Background | Warm off-white `#FBF8F3`, dark `#121815` | Softer than pure white for long reading; both themes are complete |
| Font | System font, no custom family | Vietnamese diacritics and Japanese glyphs must render everywhere; a Latin-only font would silently fall back |
| Type scale | 22 / 16 / 16 / 14 / 14 sp | Few steps, 16 sp body for readability |
| Spacing | 4-pt scale: 4, 8, 12, 16, 24, 32 | One rhythm everywhere; `minTouchTarget` 48 dp |
| Radius | 8 / 12 / 16 dp | Rounded only on surfaces that are tappable or floating |
| Cards | `surface` fill + 1 dp `outlineVariant` border, no shadow | Separation without visual noise |

## Contrast (WCAG 2.2, measured)

| Pair | Light | Dark | Requirement |
|---|---|---|---|
| onBackground / background | 14.0 | 14.7 | 4.5 (text) |
| onSurface / surface | 14.8 | 13.2 | 4.5 |
| onSurfaceVariant / background | 5.7 | 8.4 | 4.5 |
| onPrimary / primary | 6.3 | 8.6 | 4.5 |
| error / surface | 6.5 | 9.4 | 4.5 |
| outline / surface (input borders) | 4.0 | 3.6 | 3.0 (non-text) |

`outlineVariant` is intentionally subtle (decorative card borders only) and does not meet 3:1;
never use it for something a user must see to operate (inputs, focus, selected state).
The `outline` values were darkened after the first measurement failed the 3:1 minimum.

## Components (`designsystem/Components.kt`)

- `TextInputDialog` — single-field dialog, auto-focus, Done key submits, inline error text.
- `ConfirmDeleteDialog` — destructive confirmation, confirm label in the error colour.
- `EmptyState` — centred title and hint for an empty list.
- `ItemActionsMenu` — overflow button (48 dp target) with edit and delete.
- `DraftTextField` — outlined field that owns its text locally and reports each change upward;
  `initialText` is read once. Single-line or multi-line (`minLines`), inline error text. Used by the
  card editor for the title, attribute names and attribute values.
- `SearchField` — single-line outlined field with a leading search icon, `ImeAction.Search`, no
  auto-capitalisation and a clear (×) button (48 dp target, `contentDescription` from `Strings`) that
  appears once there is text. Same local-text rule as `DraftTextField`; optional supporting text.

- `CardImage` (`designsystem/CardImage.kt`) — a card's stored image. Takes an `imageKey` and a
  `load` lambda; loads the bytes and decodes them off the main thread, and reloads only when the key
  changes (callers pass a fresh lambda on every recomposition). While loading it shows a
  `surfaceVariant` tile the size the caller asked for, so a list row does not jump. If there are no
  bytes, or they do not decode as an image, it draws nothing at all.

Card images: the card list row shows a 64 dp rounded thumbnail before the title, only for cards that
have an image; a card without one looks exactly as before and reserves no space. The thumbnail is
decorative (`contentDescription = null`) because the title beside it already names the card. The card
editor shows the image read-only above the title (200 dp high, `ContentScale.Fit`, described as "Card
image"). The editor never changes or replaces it.

Adding an image: the **new-card** editor has an optional "Image URL" `DraftTextField` after the title
(single line, `KeyboardType.Uri`, no auto-capitalisation) with the hint "Optional. The image is saved on
this device." underneath. The field is absent when editing a stored card. While Save downloads the
image (`isSaving` with a non-blank URL) the field is disabled, the hint reads "Downloading image…" and
a `LinearProgressIndicator` sits above the form; the Save button is already disabled while saving. A
download that fails for any reason (bad address, offline, error status, too large, not an image) is
not reported: the card is saved without an image, as rule 8 says.

Importing cards (`presentation/cards/CardListScreen.kt`, `CardImportDialogs.kt`): the card list's top bar has
an "Import" text button (disabled while an import is running). It opens a dropdown with "From file" and "Paste JSON". "Paste JSON" opens a dialog with a multi-line field (Import disabled while blank; an error closes the dialog, as for a file) that feeds the same checks and flow. "From file" opens the system file picker with no file
type filter, because some Android file managers label `.json` files as plain text and would grey them out;
a file that is not JSON is reported as such. Each step is an `AlertDialog` and nothing else changes on the
list: "Reading…" and "Importing 12 of 30…" (a `LinearProgressIndicator` plus Cancel; back or tapping
outside also cancels, which is safe because nothing is written until the end); a `ConfirmDeleteDialog`
("Replace existing cards?", up to five titles, "Overwrite and import" in the error colour) shown only when
the file would overwrite cards (rule 2); a result dialog with the summary ("Imported 30 cards (4 replaced).",
then "3 images could not be downloaded." when some images failed); and an error dialog that names the card
number or the file problem and ends with "Nothing was imported." Unlike a single add (rule 8), an import
reports failed images, because after 30 cards the user cannot otherwise tell which lost theirs.

Card list search (`presentation/cards/CardListScreen.kt`): the `SearchField` is pinned above the list
(it does not scroll away) and is shown whenever the topic has at least one card, including while the
query matches nothing, so it can always be cleared. While the query is not blank, the field's supporting
text carries the note that this finds cards containing words and does not show what a card lacks. A
query with no results shows an `EmptyState` that repeats it ("This is a text search, not an allergy
check"). The list passes only `visibleCards` to the rows; an empty topic still shows the "No cards yet"
state. Tapping the add button clears the query first.

Card editor (`presentation/cards/CardEditorScreen.kt`): one outlined `surface` block per attribute,
holding a name field, a remove button (48 dp target) and a multi-line value field. "Add attribute" is
an outlined button (48 dp high) below the last block. Card rows in the list show the title plus up to
three `name: value` lines in `onSurfaceVariant`, then "+N more".

## Rules

1. Use `MaterialTheme.colorScheme` and `Spacing`; no raw hex values or literal dp spacing in screens.
2. Destructive actions always go through `ConfirmDeleteDialog`.
3. Every icon-only control has a `contentDescription` from `Strings`.
4. Do not add a custom font without verifying Vietnamese and Japanese coverage.
5. A text field that users type Vietnamese or Japanese into keeps its text in local Compose state
   (`DraftTextField`, `SearchField`, or the dialog's own state), never bound straight to an async `StateFlow`: a value
   that lags the keyboard by a frame breaks IME composition.
6. Discarding unsaved edits counts as destructive: confirm with `ConfirmDeleteDialog` using the
   "Discard" / "Keep editing" labels.
7. Search results are matches, never a verdict. Do not label, colour or order results as "safe",
   "allowed" or "free of" anything, and keep the note under an active query (PRD risk: a card missing
   a word is not proof that it lacks the ingredient).

8. A missing or undecodable image is a normal state, not an error: show nothing (no placeholder
   icon, no snackbar, no failure flag). Only the "still loading" tile is drawn. This covers a failed
   download when a card is added: the card is saved without an image and nothing is shown about it.
   (Whether to tell the user is an open product decision in the PRD, not a design rule.)

## Not done yet

- No interactive HTML token preview (the generator step was skipped; the app itself is the preview).
- No competitor review; not needed for an MVP utility.
- Milestone 3 (search) was driven on both platforms: the pinned field, the note, the no-results state with
  the keyboard open (needs `imePadding()`, which is in place) and real IME composition in the search
  field (Telex on both, Japanese romaji on Android). Checked in light mode on Android and in light and
  dark on iOS. Not checked: Android dark mode, Japanese typing on iOS (simulator limitation), the note's
  wrapping on a narrow phone, and a physical device.
- Milestone 2 screens were checked in light mode only, on an Android emulator and the iOS simulator
  (editor, validation errors, discard prompt, list with attribute preview). Dark mode and long values
  are not yet checked. With the keyboard open, a newly added attribute row's Value field falls below
  the visible area (see the Milestone 2 evidence report).
