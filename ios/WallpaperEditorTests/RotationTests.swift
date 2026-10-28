import XCTest
@testable import WallpaperEditor

/// Generador fijo para que el orden aleatorio sea siempre el mismo en las pruebas.
private struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64

    mutating func next() -> UInt64 {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return state
    }
}

final class RotationTests: XCTestCase {
    private let images = (1...8).map { "img\($0)" }

    func testRandomRoundShowsEveryPhotoOnce() {
        var generator = SeededGenerator(state: 42)
        var state = RotationState()
        var shown: [String] = []
        for _ in images {
            state = Rotation.next(state, images: images, order: .random, using: &generator)
            shown.append(state.current!)
        }
        XCTAssertEqual(Set(shown), Set(images))
    }

    func testPreviousGoesBackAndNextRedoesThePath() {
        var generator = SeededGenerator(state: 7)
        var state = RotationState()
        var shown: [String] = []
        for _ in 0..<4 {
            state = Rotation.next(state, images: images, order: .random, using: &generator)
            shown.append(state.current!)
        }
        state = Rotation.previous(state, images: images)!
        XCTAssertEqual(state.current, shown[2])
        state = Rotation.previous(state, images: images)!
        XCTAssertEqual(state.current, shown[1])
        state = Rotation.next(state, images: images, order: .random, using: &generator)
        XCTAssertEqual(state.current, shown[2])
        state = Rotation.next(state, images: images, order: .random, using: &generator)
        XCTAssertEqual(state.current, shown[3])
    }

    func testSequentialWrapsAround() {
        var state = RotationState(current: "img8")
        state = Rotation.next(state, images: images, order: .sequential)
        XCTAssertEqual(state.current, "img1")
    }

    func testPreviousSkipsDeletedPhotos() {
        let state = RotationState(current: "img3", history: ["img1", "img2"])
        XCTAssertEqual(Rotation.previous(state, images: images.filter { $0 != "img2" })?.current, "img1")
        XCTAssertNil(Rotation.previous(RotationState(current: "img1"), images: images))
    }

    func testAspectFillAndFit() {
        let box = CGSize(width: 1080, height: 2400)
        let fill = WallpaperRenderer.aspect(CGSize(width: 1000, height: 1000), in: box, fill: true)
        XCTAssertEqual(fill.height, 2400, accuracy: 0.5)
        XCTAssertEqual(fill.midX, 540, accuracy: 0.5)
        let fit = WallpaperRenderer.aspect(CGSize(width: 1000, height: 1000), in: box, fill: false)
        XCTAssertEqual(fit.width, 1080, accuracy: 0.5)
        XCTAssertEqual(fit.midY, 1200, accuracy: 0.5)
    }

    func testRenderHasScreenSize() {
        let photo = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 300)).image { context in
            UIColor.orange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 400, height: 300))
        }
        for fit in FitMode.allCases {
            let output = WallpaperRenderer.render(photo, size: CGSize(width: 1080, height: 2400), fit: fit)
            XCTAssertEqual(output.size.width * output.scale, 1080)
            XCTAssertEqual(output.size.height * output.scale, 2400)
        }
    }

    /// Con "foto entera", arriba y abajo no quedan bandas negras: va la foto desenfocada.
    func testBlurFillsTheBands() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let photo = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 300), format: format).image { context in
            UIColor(red: 1, green: 0.5, blue: 0, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 400, height: 300))
        }
        let output = WallpaperRenderer.render(photo, size: CGSize(width: 1080, height: 2400), fit: .blur)
        let top = pixel(output, x: 540, y: 40)
        XCTAssertGreaterThan(top.red, 120, "La banda de arriba es \(top)")
        XCTAssertGreaterThan(top.green, 40)
    }

    /// El fondo desenfocado es suave: sin cuadros (píxeles vecinos casi iguales).
    func testBlurIsSmooth() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        // Rayas blanco/negro: sin un desenfoque de verdad se verían bloques.
        let photo = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 300), format: format).image { context in
            for stripe in 0..<20 {
                (stripe % 2 == 0 ? UIColor.white : UIColor.black).setFill()
                context.fill(CGRect(x: stripe * 20, y: 0, width: 20, height: 300))
            }
        }
        let output = WallpaperRenderer.render(photo, size: CGSize(width: 1080, height: 2400), fit: .blur)
        var biggest = 0
        for x in stride(from: 100, to: 980, by: 1) {
            biggest = max(biggest, abs(pixel(output, x: x, y: 200).red - pixel(output, x: x + 1, y: 200).red))
        }
        XCTAssertLessThan(biggest, 6, "Salto entre píxeles vecinos: \(biggest)")
    }

    /// Ajustes guardados por otra versión (con campos de menos o desconocidos) se siguen leyendo.
    func testOldSettingsStillDecode() throws {
        let json = #"{"order":"sequential","rotation":{"current":"img2"},"futuro":1}"#
        let settings = try JSONDecoder().decode(Settings.self, from: Data(json.utf8))
        XCTAssertEqual(settings.order, .sequential)
        XCTAssertEqual(settings.fit, .blur)
        XCTAssertEqual(settings.sources, [])
        XCTAssertEqual(settings.rotation.current, "img2")
        XCTAssertEqual(settings.rotation.history, [])
    }

    private func pixel(_ image: UIImage, x: Int, y: Int) -> (red: Int, green: Int, blue: Int) {
        var data = [UInt8](repeating: 0, count: 4)
        let space = CGColorSpaceCreateDeviceRGB()
        let context = CGContext(data: &data, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: space,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(image.cgImage!, in: CGRect(x: -x, y: -(Int(image.size.height) - 1 - y), width: Int(image.size.width), height: Int(image.size.height)))
        return (Int(data[0]), Int(data[1]), Int(data[2]))
    }
}
