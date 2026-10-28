import Foundation
import UIKit

/// Estado de la app para las pantallas.
@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var settings = SettingsStore.shared.load()
    @Published private(set) var images: [String] = []
    @Published private(set) var hasAccess = PhotoLibrary.hasAccess
    /// Cómo queda en la pantalla la foto actual (o la primera).
    @Published private(set) var preview: UIImage?
    @Published var message: String?
    @Published private(set) var working = false

    private let arguments = ProcessInfo.processInfo.arguments

    func start() async {
        hasAccess = await PhotoLibrary.requestAccess()
        // Para las capturas automáticas: todas las fotos del simulador como fuente.
        if arguments.contains("-demo"), settings.sources.isEmpty {
            update { $0.sources = [.library] }
        }
        reload()
        if arguments.contains("-step") {
            await step(.next)
        }
    }

    func update(_ change: (inout Settings) -> Void) {
        settings = SettingsStore.shared.update(change)
    }

    func reload() {
        guard hasAccess else { return }
        let sources = settings.sources
        Task {
            // Con "Todas las fotos" recorre toda la fototeca: fuera del hilo de la pantalla.
            images = await Task.detached { PhotoLibrary.images(for: sources) }.value
            await refreshPreview()
        }
    }

    func refreshPreview() async {
        let current = settings.rotation.current.flatMap { images.contains($0) ? $0 : nil } ?? images.first
        guard let id = current, let jpeg = await WallpaperService.rendered(id, fit: settings.fit) else {
            preview = nil
            return
        }
        preview = UIImage(data: jpeg)
    }

    func add(_ source: Source) {
        update { settings in
            if !settings.sources.contains(where: { $0.id == source.id }) { settings.sources.append(source) }
        }
        reload()
    }

    func addPhotos(_ ids: [String]) {
        update { settings in
            for id in ids where !settings.sources.contains(where: { $0.id == id }) {
                settings.sources.append(Source(id: id, kind: .photo, name: ""))
            }
        }
        reload()
    }

    func remove(at offsets: IndexSet) {
        update { $0.sources.remove(atOffsets: offsets) }
        reload()
    }

    func setOrder(_ order: Order) {
        update { $0.order = order }
    }

    func setFit(_ fit: FitMode) {
        update { $0.fit = fit }
        Task { await refreshPreview() }
    }

    /// Lo mismo que hará el atajo, pero solo para verlo aquí (la app no puede poner el fondo).
    func step(_ step: WallpaperService.Step) async {
        do {
            let jpeg = try await WallpaperService.change(step)
            settings = SettingsStore.shared.load()
            preview = UIImage(data: jpeg)
        } catch {
            message = error.localizedDescription
        }
    }

    func syncAlbum() async {
        working = true
        defer { working = false }
        do {
            let count = try await PhotoLibrary.syncAlbum(with: images)
            message = String(localized: "Álbum «\(PhotoLibrary.albumName)» listo con \(count) fotos.")
        } catch {
            message = error.localizedDescription
        }
    }
}
