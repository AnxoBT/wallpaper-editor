import Foundation
import UIKit

enum WallpaperError: LocalizedError {
    case noAccess
    case noPhotos
    case noPrevious
    case cannotLoad
    case cannotCreateAlbum

    var errorDescription: String? {
        switch self {
        case .noAccess: return String(localized: "Wallpaper Editor no tiene permiso para ver tus fotos. Ábrela y permite el acceso.")
        case .noPhotos: return String(localized: "No hay fotos para los fondos. Abre Wallpaper Editor y elige un álbum o unas fotos.")
        case .noPrevious: return String(localized: "No hay ningún fondo anterior.")
        case .cannotLoad: return String(localized: "No se pudo abrir la foto.")
        case .cannotCreateAlbum: return String(localized: "No se pudo crear el álbum.")
        }
    }
}

/// Cambiar de foto en el pase y darla ya ajustada a la pantalla. Lo usan las acciones de Atajos y la app.
enum WallpaperService {
    enum Step {
        case next
        case previous
        case random
    }

    /// Avanza el pase y devuelve la foto nueva en JPEG, al tamaño de la pantalla.
    static func change(_ step: Step) async throws -> Data {
        guard PhotoLibrary.hasAccess else { throw WallpaperError.noAccess }
        let images = PhotoLibrary.images(for: SettingsStore.shared.load().sources)
        guard !images.isEmpty else { throw WallpaperError.noPhotos }
        // Leer, avanzar y guardar el pase de una vez: si dos atajos llegan a la vez (dos toques
        // seguidos), el segundo parte de donde dejó el primero y no repite la misma foto.
        var failure: WallpaperError?
        let settings = SettingsStore.shared.update { settings in
            switch step {
            case .next:
                settings.rotation = Rotation.next(settings.rotation, images: images, order: settings.order)
            case .previous:
                if let previous = Rotation.previous(settings.rotation, images: images) {
                    settings.rotation = previous
                } else {
                    failure = .noPrevious
                }
            case .random:
                settings.rotation = Rotation.random(settings.rotation, images: images)
            }
        }
        if let failure { throw failure }
        guard let id = settings.rotation.current, let jpeg = await rendered(id, fit: settings.fit) else { throw WallpaperError.cannotLoad }
        return jpeg
    }

    /// La foto ajustada a la pantalla, en JPEG.
    static func rendered(_ id: String, fit: FitMode) async -> Data? {
        let size = await MainActor.run { WallpaperRenderer.screenPixels }
        guard let image = await PhotoLibrary.image(id, screen: size, fill: fit == .crop) else { return nil }
        return WallpaperRenderer.render(image, size: size, fit: fit).jpegData(compressionQuality: 0.92)
    }
}
