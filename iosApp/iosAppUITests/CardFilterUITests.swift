import XCTest

/// Drives the real app through the Compose UI. Run against a clean install (the tests create their
/// own data): `simctl uninstall` first, then `xcodebuild test`.
final class CardFilterUITests: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        // On failure, leave the accessibility tree in the log so the failing screen can be inspected.
        if let run = testRun, run.failureCount > 0 {
            print("HIERARCHY_BEGIN\n\(app.debugDescription)\nHIERARCHY_END")
            try? XCUIScreen.main.screenshot().pngRepresentation
                .write(to: URL(fileURLWithPath: "/tmp/cardfilter-ui-failure.png"))
        }
    }

    // MARK: - Helpers

    private func element(labelled label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func containing(_ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    /// A text input labelled [label]; Compose exposes both single-line and multi-line fields as text views/fields.
    private func field(_ label: String, at index: Int = 0) -> XCUIElement {
        // A field showing a validation error exposes it in its label ("Card title, Enter some text").
        let predicate = NSPredicate(
            format: "(label == %@ OR label BEGINSWITH %@) AND (elementType == %d OR elementType == %d)",
            label,
            label + ",",
            XCUIElement.ElementType.textField.rawValue,
            XCUIElement.ElementType.textView.rawValue
        )
        return app.descendants(matching: .any).matching(predicate).element(boundBy: index)
    }

    private func tap(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let button = app.buttons[label].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 8), "no button '\(label)'", file: file, line: line)
        button.tap()
    }

    private func type(_ text: String, into field: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(field.waitForExistence(timeout: 8), "field missing for '\(text)'", file: file, line: line)
        // Tap the upper part of the field: the keyboard's predictive bar can overlap its lower edge.
        // Retry until the field really has keyboard focus.
        for _ in 0..<3 {
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.25)).tap()
            if (field.value(forKey: "hasKeyboardFocus") as? Bool) == true { break }
            usleep(400_000)
        }
        field.typeText(text)
    }

    /// Writes a screenshot for visual review (the simulator shares the host's /tmp).
    private func snapshot(_ name: String) {
        try? XCUIScreen.main.screenshot().pngRepresentation
            .write(to: URL(fileURLWithPath: "/tmp/cardfilter-\(name).png"))
    }

    private func assertShown(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(containing(text).waitForExistence(timeout: 8), "'\(text)' not on screen", file: file, line: line)
    }

    private func assertGone(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let gone = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: containing(text)
        )
        XCTAssertEqual(XCTWaiter().wait(for: [gone], timeout: 5), .completed, "'\(text)' still on screen", file: file, line: line)
    }

    // MARK: - Tests

    /// Taps a key on the on-screen keyboard (letters are upper-case while shift is active).
    private func tapKey(_ character: Character) {
        let text = String(character)
        let key = app.keys[text].exists ? app.keys[text] : app.keys[text.uppercased()]
        XCTAssertTrue(key.waitForExistence(timeout: 5), "no key '\(text)'")
        key.tap()
    }

    /// The simulator remembers the last keyboard, so cycle until the one whose return key reads [returnKey].
    private func selectKeyboard(returnKey: String, file: StaticString = #filePath, line: UInt = #line) {
        for _ in 0..<5 {
            XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5), file: file, line: line)
            if app.buttons["Continue"].exists { app.buttons["Continue"].tap(); sleep(1) }
            if app.keyboards.firstMatch.buttons[returnKey].exists { return }
            app.buttons["Next keyboard"].tap()
            sleep(1)
            // iOS shows a one-time "Quickly Change Keyboards" tutorial over the keyboard.
            if app.buttons["Continue"].waitForExistence(timeout: 1) { app.buttons["Continue"].tap() }
        }
        XCTFail("keyboard with return key '\(returnKey)' never appeared", file: file, line: line)
    }

    private func typeKeys(_ keys: String) {
        for character in keys {
            if character == " " { app.keys["space"].tap() } else { tapKey(character) }
        }
    }

    /// Real IME composition: Telex key presses must compose into Vietnamese diacritics inside the field.
    ///
    /// Preconditions: the simulator has the Vietnamese (Telex) keyboard
    /// (`xcrun simctl spawn <udid> defaults write -g AppleKeyboards -array "en_US@sw=QWERTY;hw=Automatic"
    /// "vi_VN@sw=Telex;hw=Automatic"`, then reboot it) and the "Pigeon" topic from
    /// `testTopicAndCardAttributeLifecycle` exists (it runs first, alphabetically). The test returns the
    /// keyboard to English before saving so later runs type plain ASCII.
    func testVietnameseTelexComposition() {
        app.launch()
        tap("Pigeon")
        tap("Add card")
        field("Card title").coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.25)).tap()
        selectKeyboard(returnKey: "Tiếp")

        typeKeys("thijt baafm") // Telex: thị t, bầm (auto-capitalised first letter)
        XCTAssertEqual(field("Card title").value as? String, "Thịt bầm")

        selectKeyboard(returnKey: "next")
        tap("Save")
        assertShown("Thịt bầm")
    }

    /// Milestone 3: the card search box. Creates its own topic, so it must run after
    /// `testTopicAndCardAttributeLifecycle` (that test needs an empty install); the name sorts it last.
    /// Needs the Vietnamese (Telex) keyboard right after English in the keyboard list, as
    /// `testVietnameseTelexComposition` does.
    func testVietnameseTelexSearchNarrowsCards() {
        app.launch()

        tap("Add topic")
        type("Search", into: field("Topic name"))
        tap("Save")
        tap("Search")

        // Two cards typed on the English keyboard, one composed through Telex.
        tap("Add card")
        type("Rice porridge", into: field("Card title"))
        tap("Add attribute")
        type("allergen", into: field("Attribute name", at: 0))
        type("egg", into: field("Value", at: 0))
        tap("Save")

        tap("Add card")
        type("Carrot puree", into: field("Card title"))
        tap("Add attribute")
        type("allergen", into: field("Attribute name", at: 0))
        type("milk", into: field("Value", at: 0))
        tap("Save")

        tap("Add card")
        field("Card title").coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.25)).tap()
        selectKeyboard(returnKey: "Tiếp")
        typeKeys("thijt baafm")
        XCTAssertEqual(field("Card title").value as? String, "Thịt bầm")
        selectKeyboard(returnKey: "next")
        tap("Save")
        assertShown("Rice porridge")
        assertShown("Carrot puree")
        assertShown("Thịt bầm")

        // One word narrows the list; the note appears while a query is active.
        let search = field("Search this topic")
        type("egg", into: search)
        assertShown("Rice porridge")
        assertGone("Carrot puree")
        assertGone("Thịt bầm")
        assertShown("Shows cards that contain any of these words")
        snapshot("search-active")

        // Clear restores everything.
        tap("Clear search")
        assertShown("Carrot puree")
        assertShown("Thịt bầm")

        // Case-insensitive, and several words match when any one is present.
        type("EGG MILK", into: search)
        assertShown("Rice porridge")
        assertShown("Carrot puree")
        assertGone("Thịt bầm")
        tap("Clear search")

        // Nothing matches: the no-results state says this is not an allergy check.
        type("zzz", into: search)
        assertShown("No matching cards")
        assertShown("not an allergy check")
        snapshot("search-no-results")
        tap("Clear search")

        // Diacritics are ignored: plain "thit bam" finds the card titled "Thịt bầm".
        type("thit bam", into: search)
        assertShown("Thịt bầm")
        assertGone("Rice porridge")
        assertGone("Carrot puree")
        tap("Clear search")

        // Real Telex composition inside the search field itself. The field starts on English
        // (its return key reads "search"); one press of Next keyboard reaches Telex, and the composed
        // text proves which keyboard is active.
        search.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.25)).tap()
        selectKeyboard(returnKey: "search")
        app.buttons["Next keyboard"].tap()
        sleep(1)
        if app.buttons["Continue"].waitForExistence(timeout: 1) { app.buttons["Continue"].tap() }
        typeKeys("thijt baafm")
        XCTAssertEqual(field("Search this topic").value as? String, "thịt bầm")
        assertShown("Thịt bầm")
        assertGone("Rice porridge")
        assertGone("Carrot puree")

        // Back to English; the Search key dismisses the keyboard and keeps the query.
        selectKeyboard(returnKey: "search")
        app.keyboards.firstMatch.buttons["search"].tap()
        assertShown("Thịt bầm")
        assertGone("Rice porridge")

        // Adding a card clears the query, so a new card cannot vanish after saving.
        tap("Add card")
        tap("Back")
        assertShown("Rice porridge")
        assertShown("Carrot puree")
        assertShown("Thịt bầm")
    }

    func testTopicAndCardAttributeLifecycle() {
        app.launch()

        // Topic
        assertShown("No topics yet")
        tap("Add topic")
        type("Pigeon", into: field("Topic name"))
        tap("Save")
        tap("Pigeon")
        assertShown("No cards yet")

        // Card with two attributes
        tap("Add card")
        type("Rice porridge", into: field("Card title"))
        tap("Add attribute")
        type("age", into: field("Attribute name", at: 0))
        type("7 months", into: field("Value", at: 0))
        tap("Add attribute")
        // With the keyboard open the new row's Value field is below the visible area, so use the
        // keyboard's Next key (as a person would): it moves focus to Value and scrolls it into view.
        type("allergen", into: field("Attribute name", at: 1))
        app.typeText("\n")
        app.typeText("egg")
        tap("Save")
        assertShown("Rice porridge")
        assertShown("age: 7 months")
        assertShown("allergen: egg")

        // A second card with a different attribute set
        tap("Add card")
        type("Carrot puree", into: field("Card title"))
        tap("Add attribute")
        type("texture", into: field("Attribute name", at: 0))
        type("smooth", into: field("Value", at: 0))
        tap("Save")
        assertShown("Carrot puree")
        assertShown("texture: smooth")

        // Edit the first card: change a value and remove the second attribute
        app.buttons.matching(NSPredicate(format: "label CONTAINS 'Rice porridge'")).firstMatch.tap()
        assertShown("Edit card")
        let value = field("Value", at: 0)
        value.tap()
        value.press(forDuration: 1.0)
        app.menuItems["Select All"].tap()
        value.typeText("8 months")
        app.buttons.matching(identifier: "Remove attribute").element(boundBy: 1).tap()
        tap("Save")
        assertShown("age: 8 months")
        assertGone("allergen: egg")

        // Validation: blank title, name without a value
        tap("Add card")
        tap("Add attribute")
        type("age", into: field("Attribute name", at: 0))
        tap("Save")
        assertShown("Enter some text")
        assertShown("Enter a value")

        // Discard prompt on a dirty draft
        type("draft", into: field("Card title"))
        tap("Back")
        assertShown("Discard changes?")
        tap("Keep editing")
        assertShown("Add card")
        tap("Back")
        tap("Discard")
        assertGone("Discard changes?")
        assertShown("Carrot puree")

        // Persistence across a relaunch
        app.terminate()
        app.launch()
        tap("Pigeon")
        assertShown("age: 8 months")
        assertShown("texture: smooth")

        // Delete a card
        app.buttons.matching(NSPredicate(format: "label == 'More actions'")).element(boundBy: 1).tap()
        tap("Delete card")
        tap("Delete")
        assertGone("Carrot puree")
        assertShown("Rice porridge")
    }

    /// Needs a database seeded by hand with a topic "Images" holding the cards "With image" (a valid
    /// PNG), "Broken image" (a stored image that is not decodable) and "No image". The app cannot attach
    /// an image yet (Milestone 2), so the test cannot create one itself; it skips when the topic is absent.
    func testCardImagesShowInTheListAndTheEditor() throws {
        app.launch()
        try XCTSkipUnless(
            app.buttons["Images"].firstMatch.waitForExistence(timeout: 5),
            "no seeded 'Images' topic"
        )
        tap("Images")
        assertShown("With image")
        assertShown("Broken image")
        assertShown("No image")
        sleep(2) // let the thumbnails load
        snapshot("images-list")

        containing("With image").tap()
        XCTAssertTrue(element(labelled: "Card image").waitForExistence(timeout: 8), "stored image not shown")
        snapshot("images-editor")
        tap("Back")

        // A stored image that cannot be decoded draws nothing, and a card without one has none.
        containing("Broken image").tap()
        assertShown("Card title")
        XCTAssertFalse(element(labelled: "Card image").waitForExistence(timeout: 3), "undecodable image was drawn")
        tap("Back")

        containing("No image").tap()
        assertShown("Card title")
        XCTAssertFalse(element(labelled: "Card image").waitForExistence(timeout: 3), "card without image shows one")
    }

    /// Milestone 2. Needs network access and a clean install; run it on its own (it leaves a topic behind).
    /// Adds cards with an image URL: a reachable one must end up stored (the editor shows "Card image"), an
    /// unreachable one must still save the card, with no image and no error, and a rejected form must keep
    /// the typed URL.
    func testCardImageIsDownloadedWhenTheCardIsAdded() {
        app.launch()
        tap("Add topic")
        type("Downloads", into: field("Topic name"))
        tap("Save")
        tap("Downloads")

        // A reachable image is downloaded and stored with the card.
        tap("Add card")
        type("Online image", into: field("Card title"))
        type("https://www.gstatic.com/webp/gallery/1.png", into: field("Image URL"))
        snapshot("new-card-editor")
        tap("Save")
        XCTAssertTrue(containing("Online image").waitForExistence(timeout: 45), "card not saved")
        containing("Online image").tap()
        let stored = element(labelled: "Card image").waitForExistence(timeout: 8)
        snapshot("download-editor")
        XCTAssertTrue(stored, "downloaded image was not stored with the card")
        tap("Back")

        // An unreachable image still saves the card, without an image and without an error.
        tap("Add card")
        type("Broken link", into: field("Card title"))
        type("https://does-not-exist.invalid/a.png", into: field("Image URL"))
        tap("Save")
        XCTAssertTrue(containing("Broken link").waitForExistence(timeout: 45), "card not saved")
        assertGone("Something went wrong")
        containing("Broken link").tap()
        assertShown("Card title")
        XCTAssertFalse(element(labelled: "Card image").waitForExistence(timeout: 3), "unreachable image was stored")
        tap("Back")

        // A rejected form keeps the typed URL and creates nothing.
        tap("Add card")
        type("https://www.gstatic.com/webp/gallery/1.png", into: field("Image URL"))
        tap("Save")
        assertShown("Enter some text")
        XCTAssertEqual(field("Image URL").value as? String, "https://www.gstatic.com/webp/gallery/1.png")
        tap("Back")
        tap("Discard")
        assertGone("Discard changes?")
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label == 'More actions'")).count, 2)
    }

    /// Milestone 2. Needs network access. Each URL below cannot give a usable image (missing page, a web
    /// page, a scheme that is not http(s), plain http, an image over 1 MB, an SVG, text that is not a link):
    /// the card must still be saved, with no image and no error.
    func testImageUrlsThatCannotBeUsedSaveACardWithoutAnImage() {
        app.launch()
        tap("Add topic")
        type("Bad links", into: field("Topic name"))
        tap("Save")
        tap("Bad links")

        let cases: [(title: String, url: String)] = [
            ("Not found", "https://www.gstatic.com/nope-404.png"),
            ("Web page", "https://example.com/"),
            ("Wrong scheme", "ftp://example.com/a.png"),
            ("Plain http", "http://example.com/a.png"),
            ("Too big", "https://upload.wikimedia.org/wikipedia/commons/9/97/The_Earth_seen_from_Apollo_17.jpg"),
            ("Vector", "https://upload.wikimedia.org/wikipedia/commons/4/4f/SVG_Logo.svg"),
            ("Not a link", "not a url"),
        ]
        for (title, url) in cases {
            tap("Add card")
            type(title, into: field("Card title"))
            type(url, into: field("Image URL"))
            tap("Save")
            XCTAssertTrue(containing(title).waitForExistence(timeout: 45), "'\(title)' was not saved")
            assertGone("Something went wrong")
            containing(title).tap()
            assertShown("Card title")
            XCTAssertFalse(
                element(labelled: "Card image").waitForExistence(timeout: 2),
                "'\(title)' stored an image it should not have"
            )
            tap("Back")
        }
    }

    /// Opens Import > From file and picks [name] in the system document picker. Needs the file to be in
    /// the simulator's "On My iPhone" storage (the test setup notes say how it was copied there).
    private func importFile(named name: String) {
        tap("Import")
        let fromFile = element(labelled: "From file")
        XCTAssertTrue(fromFile.waitForExistence(timeout: 8), "no 'From file' menu item")
        fromFile.tap()
        let file = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", name)).firstMatch
        if !file.waitForExistence(timeout: 10) {
            // The picker may open on Recents; go to the local storage.
            let browse = app.buttons["Browse"].firstMatch
            if browse.waitForExistence(timeout: 3) { browse.tap() }
            let local = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == 'On My iPhone'")).firstMatch
            if local.waitForExistence(timeout: 5) { local.tap() }
        }
        XCTAssertTrue(file.waitForExistence(timeout: 10), "'\(name)' not offered by the document picker")
        snapshot("ios-picker")
        file.tap()
    }

    /// Milestone 3. Needs network access and `wakodo.json` (the PRD's six products) in the simulator's
    /// "On My iPhone" storage. Imports it through the real document picker and checks the result.
    func testImportsTheWakodoSampleFromTheFilePicker() {
        app.launch()
        tap("Add topic")
        type("Sample products", into: field("Topic name"))
        tap("Save")
        tap("Sample products")
        importFile(named: "wakodo")

        XCTAssertTrue(containing("Imported 6 cards").waitForExistence(timeout: 90), "no import result")
        snapshot("ios-import-result")
        XCTAssertFalse(containing("could not be downloaded").exists, "some images failed")
        tap("OK")
        assertShown("month: 7")
        assertShown("month: 9")
        sleep(3) // let the thumbnails load
        snapshot("ios-import-list")
        containing("Classic Vegetables").tap()
        XCTAssertTrue(element(labelled: "Card image").waitForExistence(timeout: 8), "imported image not shown")
        tap("Back")

        // Importing it again asks first; Cancel changes nothing.
        importFile(named: "wakodo")
        assertShown("Replace existing cards?")
        snapshot("ios-import-confirm")
        tap("Cancel")
        assertGone("Replace existing cards?")
        // The list is lazy: only the first cards are on screen, so check one of those is still there.
        assertShown("month: 7")
    }

    /// Milestone 3. An invalid file shows the error and imports nothing. Needs `invalid2.json` in storage.
    func testAnInvalidFileFromThePickerImportsNothing() {
        app.launch()
        tap("Add topic")
        type("Invalid import", into: field("Topic name"))
        tap("Save")
        tap("Invalid import")
        importFile(named: "invalid2")

        assertShown("Nothing was imported")
        snapshot("ios-import-invalid")
        tap("OK")
        assertShown("No cards yet")
    }

    /// Milestone 2. Needs network access. A downloaded image is still there after the app is relaunched,
    /// the editor of a stored card has no URL field, and saving an edit keeps the image.
    func testDownloadedImageSurvivesRelaunchAndEditing() {
        app.launch()
        tap("Add topic")
        type("Keeps", into: field("Topic name"))
        tap("Save")
        tap("Keeps")
        tap("Add card")
        type("Keeper", into: field("Card title"))
        type("https://www.gstatic.com/webp/gallery/1.png", into: field("Image URL"))
        tap("Save")
        XCTAssertTrue(containing("Keeper").waitForExistence(timeout: 45), "card not saved")

        app.terminate()
        app.launch()
        tap("Keeps")
        containing("Keeper").tap()
        XCTAssertTrue(element(labelled: "Card image").waitForExistence(timeout: 8), "image gone after relaunch")
        XCTAssertFalse(field("Image URL").exists, "a stored card must not offer an image URL")

        type(" edited", into: field("Card title"))
        tap("Save")
        assertShown("Keeper edited")
        containing("Keeper edited").tap()
        XCTAssertTrue(element(labelled: "Card image").waitForExistence(timeout: 8), "edit lost the image")
    }
}
