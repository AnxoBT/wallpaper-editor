import Foundation

/// Cómo se ajusta cada foto a la pantalla.
enum FitMode: String, Codable, CaseIterable {
    /// La foto entera, con su propia versión desenfocada detrás (sin bandas negras).
    case blur
    /// La foto llena la pantalla y se recorta lo que sobra.
    case crop
}

/// Orden en que salen las fotos.
enum Order: String, Codable, CaseIterable {
    case random
    case sequential
}

/// De dónde salen las fotos: todas, un álbum o fotos sueltas.
struct Source: Codable, Identifiable, Hashable {
    enum Kind: String, Codable {
        case library
        case album
        case photo
    }

    /// Identificador de Fotos (o "library" para todas).
    var id: String
    var kind: Kind
    var name: String

    static let library = Source(id: "library", kind: .library, name: "")
}

/// Por dónde va el pase: la actual, las anteriores (para volver) y las que faltan en esta vuelta.
struct RotationState: Codable, Equatable {
    var current: String?
    var history: [String] = []
    /// Las que se han dejado atrás al volver: "siguiente" rehace el camino.
    var forward: [String] = []
    /// Modo aleatorio: las que quedan por salir en esta vuelta.
    var pending: [String] = []
}

struct Settings: Codable {
    var sources: [Source] = []
    var order: Order = .random
    var fit: FitMode = .blur
    var rotation = RotationState()
}

/*
 Lectura tolerante: cada campo que falte (o no se entienda) toma su valor por defecto. Así los
 ajustes guardados por otra versión de la app se siguen leyendo en vez de perderse. Va en
 extensiones para que Swift siga generando los inicializadores normales.
 */
private extension KeyedDecodingContainer {
    func value<T: Decodable>(_ key: Key, or fallback: T) -> T {
        ((try? decodeIfPresent(T.self, forKey: key)) ?? nil) ?? fallback
    }
}

extension RotationState {
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            current: (try? values.decodeIfPresent(String.self, forKey: .current)) ?? nil,
            history: values.value(.history, or: []),
            forward: values.value(.forward, or: []),
            pending: values.value(.pending, or: [])
        )
    }
}

extension Settings {
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            sources: values.value(.sources, or: []),
            order: values.value(.order, or: .random),
            fit: values.value(.fit, or: .blur),
            rotation: values.value(.rotation, or: RotationState())
        )
    }
}

/// Guarda los ajustes en el propio móvil. La usan la app y las acciones de Atajos.
final class SettingsStore: @unchecked Sendable {
    static let shared = SettingsStore()

    private let defaults: UserDefaults
    private let lock = NSLock()
    private let key = "settings"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> Settings {
        lock.lock()
        defer { lock.unlock() }
        return read()
    }

    /// Cambia los ajustes de una vez (sin que otra acción se cuele en medio) y los devuelve.
    @discardableResult
    func update(_ change: (inout Settings) -> Void) -> Settings {
        lock.lock()
        defer { lock.unlock() }
        var settings = read()
        change(&settings)
        if let data = try? JSONEncoder().encode(settings) {
            defaults.set(data, forKey: key)
        }
        return settings
    }

    private func read() -> Settings {
        guard let data = defaults.data(forKey: key), let settings = try? JSONDecoder().decode(Settings.self, from: data) else {
            return Settings()
        }
        return settings
    }
}
