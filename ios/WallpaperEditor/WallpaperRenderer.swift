import UIKit

/// Ajusta una foto al tamaño exacto de la pantalla, para que iOS no la vuelva a recortar.
enum WallpaperRenderer {
    /// Tamaño de la pantalla en píxeles, en vertical.
    @MainActor
    static var screenPixels: CGSize {
        let bounds = UIScreen.main.nativeBounds.size
        return CGSize(width: min(bounds.width, bounds.height), height: max(bounds.width, bounds.height))
    }

    static func render(_ image: UIImage, size: CGSize, fit: FitMode) -> UIImage {
        // UIImage.draw y size ya respetan la orientación de la foto: no hace falta girarla antes.
        let photo = image
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let canvas = CGRect(origin: .zero, size: size)
            UIColor.black.setFill()
            context.fill(canvas)
            let filled = aspect(photo.size, in: size, fill: true)
            switch fit {
            case .crop:
                photo.draw(in: filled)
            case .blur:
                // Detrás, la misma foto desenfocada y algo más oscura; delante, la foto entera.
                if let background = blurred(photo) {
                    drawSmooth(background, in: filled, context: context.cgContext, height: size.height)
                } else {
                    photo.draw(in: filled)
                }
                // Mezclando (fill(_:) a secas copia el color y lo dejaría negro).
                UIColor(white: 0, alpha: 0.18).setFill()
                context.fill(canvas, blendMode: .normal)
                photo.draw(in: aspect(photo.size, in: size, fill: false))
            }
        }
    }

    /// Rectángulo de una imagen de tamaño `size` centrada en `box`: llenándola o cabiendo entera.
    static func aspect(_ size: CGSize, in box: CGSize, fill: Bool) -> CGRect {
        guard size.width > 0, size.height > 0 else { return CGRect(origin: .zero, size: box) }
        let scaleX = box.width / size.width
        let scaleY = box.height / size.height
        let scale = fill ? max(scaleX, scaleY) : min(scaleX, scaleY)
        let width = size.width * scale
        let height = size.height * scale
        return CGRect(x: (box.width - width) / 2, y: (box.height - height) / 2, width: width, height: height)
    }

    /// Dibuja ampliando con suavizado (UIImage.draw no siempre lo hace y se ven cuadros).
    private static func drawSmooth(_ image: CGImage, in rect: CGRect, context: CGContext, height: CGFloat) {
        context.saveGState()
        context.interpolationQuality = .high
        // CoreGraphics tiene el origen abajo: se da la vuelta para dibujar como UIKit.
        context.translateBy(x: 0, y: height)
        context.scaleBy(x: 1, y: -1)
        context.draw(image, in: CGRect(x: rect.minX, y: height - rect.maxY, width: rect.width, height: rect.height))
        context.restoreGState()
    }

    /**
     La foto muy desenfocada, pequeña (se amplía al dibujarla): se reduce a 40 píxeles de lado y
     se le pasa tres veces un desenfoque de caja, que se parece mucho a uno gaussiano.
     */
    static func blurred(_ image: UIImage) -> CGImage? {
        guard image.size.width > 0, image.size.height > 0 else { return nil }
        let side: CGFloat = 40
        let scale = side / max(image.size.width, image.size.height)
        let width = max(1, Int((image.size.width * scale).rounded()))
        let height = max(1, Int((image.size.height * scale).rounded()))
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let space = CGColorSpaceCreateDeviceRGB()
        let info = CGImageAlphaInfo.noneSkipLast.rawValue
        let drawn: Bool = pixels.withUnsafeMutableBytes { buffer in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                          bytesPerRow: width * 4, space: space, bitmapInfo: info) else { return false }
            context.interpolationQuality = .high
            UIGraphicsPushContext(context)
            context.translateBy(x: 0, y: CGFloat(height))
            context.scaleBy(x: 1, y: -1)
            image.draw(in: CGRect(x: 0, y: 0, width: width, height: height))
            UIGraphicsPopContext()
            return true
        }
        guard drawn else { return nil }
        for _ in 0..<3 {
            boxBlur(&pixels, width: width, height: height, radius: 2, horizontal: true)
            boxBlur(&pixels, width: width, height: height, radius: 2, horizontal: false)
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData) else { return nil }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4, space: space,
                       bitmapInfo: CGBitmapInfo(rawValue: info), provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }

    /// Media de cada píxel con sus vecinos en una dirección (los bordes se repiten).
    private static func boxBlur(_ pixels: inout [UInt8], width: Int, height: Int, radius: Int, horizontal: Bool) {
        let source = pixels
        let length = horizontal ? width : height
        let lines = horizontal ? height : width
        let count = radius * 2 + 1
        for line in 0..<lines {
            for position in 0..<length {
                var red = 0
                var green = 0
                var blue = 0
                for offset in -radius...radius {
                    let p = min(max(position + offset, 0), length - 1)
                    let index = horizontal ? (line * width + p) * 4 : (p * width + line) * 4
                    red += Int(source[index])
                    green += Int(source[index + 1])
                    blue += Int(source[index + 2])
                }
                let index = horizontal ? (line * width + position) * 4 : (position * width + line) * 4
                pixels[index] = UInt8(red / count)
                pixels[index + 1] = UInt8(green / count)
                pixels[index + 2] = UInt8(blue / count)
                pixels[index + 3] = 255
            }
        }
    }
}
