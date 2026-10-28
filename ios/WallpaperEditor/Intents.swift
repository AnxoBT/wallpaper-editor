import AppIntents
import UniformTypeIdentifiers

/*
 Acciones para la app Atajos. Ninguna app puede cambiar el fondo del iPhone por su cuenta, pero
 un atajo sí: «Siguiente fondo» (de aquí) → «Establecer foto como fondo» (de iOS). Ese atajo se
 puede lanzar con el Toque posterior, desde un widget o con una automatización por horario.
 */

struct NextWallpaperIntent: AppIntent {
    static var title: LocalizedStringResource = "Siguiente fondo"
    static var description = IntentDescription("Da la siguiente foto de tus fondos, ya ajustada a la pantalla. Úsala con «Establecer foto como fondo».")

    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        try await .result(value: WallpaperService.intentFile(.next))
    }
}

struct PreviousWallpaperIntent: AppIntent {
    static var title: LocalizedStringResource = "Fondo anterior"
    static var description = IntentDescription("Vuelve a la foto que tenías antes, ya ajustada a la pantalla. Úsala con «Establecer foto como fondo».")

    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        try await .result(value: WallpaperService.intentFile(.previous))
    }
}

struct RandomWallpaperIntent: AppIntent {
    static var title: LocalizedStringResource = "Fondo al azar"
    static var description = IntentDescription("Una foto cualquiera de tus fondos, ya ajustada a la pantalla. Úsala con «Establecer foto como fondo».")

    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        try await .result(value: WallpaperService.intentFile(.random))
    }
}

extension WallpaperService {
    /// La foto del paso pedido, como archivo para el atajo.
    static func intentFile(_ step: Step) async throws -> IntentFile {
        IntentFile(data: try await change(step), filename: "fondo.jpg", type: .jpeg)
    }
}

struct WallpaperShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: NextWallpaperIntent(),
            phrases: ["Siguiente fondo de \(.applicationName)"],
            shortTitle: "Siguiente fondo",
            systemImageName: "forward.fill"
        )
        AppShortcut(
            intent: PreviousWallpaperIntent(),
            phrases: ["Fondo anterior de \(.applicationName)"],
            shortTitle: "Fondo anterior",
            systemImageName: "backward.fill"
        )
        AppShortcut(
            intent: RandomWallpaperIntent(),
            phrases: ["Fondo al azar de \(.applicationName)"],
            shortTitle: "Fondo al azar",
            systemImageName: "shuffle"
        )
    }
}
