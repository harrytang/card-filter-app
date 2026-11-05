# Card Images (Offline) and Bulk JSON Import

## Problem
People building a topic from a long product list (for example the Wakodo and Pigeon baby-food ingredient lists) must create every card by hand, and cards cannot show a product image. Typing 30+ cards is slow and error-prone, and an image that depends on a network link would not display when the device is offline. *(Who is affected and how often is not confirmed. See Evidence.)*

## Evidence
- Observation: the provided JSON sample contains 6 Wakodo products, each with `name`, `image` (URL), `ingredients` and `month`. This shows the shape of the data to import, not that users need the feature.
- Assumption — needs validation via user research: entering product lists by hand is a real pain, and users want product images visible without a connection.

## Users
- **Primary**: TBD — needs validation via user research. Working assumption: a person (likely a parent or caregiver) who builds a topic from a published product ingredient list and consults it on a phone, possibly without signal.
- **Not for**: TBD — needs validation.

## Hypothesis
We believe **bulk JSON import and offline-stored card images** will **remove the manual effort of entering product lists and keep cards complete without a connection** for **people building topics from product ingredient lists (primary user TBD)**.
We'll know we're right when **TBD — needs validation via usability test**. Candidate measure: a topic of about 30 cards is ready in under 2 minutes, and every card whose image downloaded successfully displays its image with the device offline.

## Success Metrics
| Metric | Target | How measured |
|---|---|---|
| Time to create a topic of ~30 cards via import | TBD (candidate: < 2 min) | TBD — needs validation via usability test |
| Cards with a downloaded image that display it while offline | 100% | Manual/automated check with network disabled |
| Invalid files that end in an error with no cards created | 100% of invalid files | Test with malformed and invalid-entry files |

## Scope
**MVP**
- A card has an optional, built-in image. When it has one, the card displays the image.
- When a card with an image is added, the image is downloaded once and stored on the device, so it displays with no network.
- The user can import a JSON array of cards (fields `name`, `image`, `ingredients`, `month` as in the sample).
- The whole file is validated before anything is created. If the file is malformed or any entry is invalid, the user sees an error and nothing is imported.
- If an image fails to download (bad URL, offline, error), the card is saved as if it had no image. Import does not fail.
- An imported card that matches an existing card overwrites it.

**Out of scope**
- JSON export or backup — separate concern, already an open question in the main PRD.
- PDF import — import is JSON only.
- Retrying failed image downloads later — failed images stay missing in this version.
- Choosing an image from the device camera or gallery — images come only from a URL.
- Editing or replacing a card's image after it is added — not requested.
- Partial import with a per-entry error report — chosen behavior is stop on first problem.

## Delivery Milestones
<!-- Business outcomes, not engineering tasks. /plan turns each into a plan. -->
<!-- Status: pending | in-progress | complete -->

| # | Milestone | Outcome | Status | Plan |
|---|---|---|---|---|
| 1 | Card image | A card can have an image, and cards with an image display it | complete | .claude/plans/card-image-offline-and-bulk-import-milestone-1.plan.md |
| 2 | Offline image | An image is downloaded when the card is added and shows with no network; a failed download leaves a normal card without an image | complete | .claude/plans/card-image-offline-and-bulk-import-milestone-2.plan.md |
| 3 | Bulk import | The user imports a valid JSON file and gets the cards (with images); an invalid file shows an error and imports nothing; matching cards are overwritten | complete | .claude/plans/card-image-offline-and-bulk-import-milestone-3.plan.md |

## Open Questions
- [ ] Who is the primary user, and how often do they add many cards at once? (also open in `card-filter.prd.md`)
- [ ] Duplicates: what makes two cards "the same"? Working assumption: same name within the same topic.
- [ ] Does import go into the currently open topic, or can it create a new topic from the file?
- [ ] If the app is killed mid-import after a valid file is accepted, is a partially imported topic acceptable?
- [ ] Field mapping: is `month` stored as a text attribute (e.g. "7")? Are `name` and `ingredients` ordinary attributes, as in the main PRD?
- [ ] Should unknown extra keys in the JSON become attributes, or be ignored?
- [ ] Is there a time limit or progress indicator for importing ~30 images?
- [ ] If the device is offline during import, should cards import without images, or should the import be blocked? Current behavior implies the former.
- [ ] Is there a cap on image size or total image storage?
- [ ] What happens to the stored image when a card is deleted or overwritten by an import?

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Overwrite-on-duplicate silently destroys user edits to existing cards | Medium | High | Define "same card" precisely; consider a confirmation before overwrite (TBD) |
| A failed image download is invisible, so the user does not know why a card has no image | Medium | Medium | Decide whether to tell the user how many images failed (TBD) |
| Importing many images is slow or uses a lot of storage on the device | Medium | Medium | Set image size and storage limits; show progress (TBD) |
| Image URLs from third-party sites may be removed, blocked or restricted by the site owner | Medium | Medium | Images are stored once on download; confirm acceptable use of the source images (TBD) |
| Requirements are unvalidated (users, need and metrics are TBD), so this may be built for a need that does not exist | High | Medium | Validate with 1-2 real users or a prototype using the Wakodo sample |

---
*Status: DRAFT — requirements only. Implementation planning pending via /plan.*
