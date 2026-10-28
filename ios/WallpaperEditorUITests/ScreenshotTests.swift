import XCTest

/**
 Capturas de la app en el simulador, tocando la pantalla como una persona. Las guarda en la
 carpeta de `SHOT_DIR` (en el workflow, `TEST_RUNNER_SHOT_DIR`) con el idioma de `SHOT_LANG`.
 */
final class ScreenshotTests: XCTestCase {
    private var environment: [String: String] { ProcessInfo.processInfo.environment }

    func testScreenshots() throws {
        let language = environment["SHOT_LANG"] ?? "es"
        let app = XCUIApplication()
        app.launchArguments = ["-demo", "-step", "-AppleLanguages", "(\(language))", "-AppleLocale", language == "es" ? "es_ES" : "en_US"]
        app.launch()
        allowPhotoAccess()

        // La lista y la vista previa se cargan tras dar el permiso.
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 10))
        sleep(6)
        save("fondos-\(language)")

        app.tabBars.buttons.element(boundBy: 1).tap()
        sleep(2)
        save("atajos-\(language)")
    }

    /// Acepta el permiso de Fotos (en iOS 18 y posteriores, a veces se confirma dos veces).
    private func allowPhotoAccess() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let labels = ["Allow Full Access", "Permitir acceso completo", "Allow Access to All Photos", "Permitir acceso a todas las fotos"]
        for _ in 0..<3 {
            guard let button = labels.lazy.map({ springboard.buttons[$0] }).first(where: { $0.waitForExistence(timeout: 4) }) else { return }
            button.tap()
        }
    }

    private func save(_ name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let directory = environment["SHOT_DIR"] {
            let url = URL(fileURLWithPath: directory).appendingPathComponent("\(name).png")
            try? screenshot.pngRepresentation.write(to: url)
        }
    }
}
