import Foundation

/**
 Siguiente / anterior del pase de fondos (la misma lógica que en Android).

 - Siguiente: si antes se volvió atrás, rehace el camino; si no, la siguiente en orden o una al
   azar que no haya salido en esta vuelta.
 - Anterior: vuelve exactamente a la que había antes, hasta `historySize` pasos.
 */
enum Rotation {
    static let historySize = 20

    static func next(_ state: RotationState, images: [String], order: Order) -> RotationState {
        var generator = SystemRandomNumberGenerator()
        return next(state, images: images, order: order, using: &generator)
    }

    static func next<G: RandomNumberGenerator>(_ state: RotationState, images: [String], order: Order, using generator: inout G) -> RotationState {
        guard !images.isEmpty else { return state }
        let available = Set(images)
        var result = state
        result.history = pushed(state.history, state.current)

        // Rehacer el camino si se había vuelto atrás.
        let forward = state.forward.filter(available.contains)
        if let last = forward.last {
            result.current = last
            result.forward = Array(forward.dropLast())
            return result
        }

        switch order {
        case .sequential:
            let index = state.current.flatMap { images.firstIndex(of: $0) } ?? -1
            result.current = images[(index + 1) % images.count]
        case .random:
            var pending = state.pending.filter { available.contains($0) && $0 != state.current }
            if pending.isEmpty {
                // Nueva vuelta: todas en orden aleatorio, sin repetir la actual justo después.
                pending = images.filter { $0 != state.current }.shuffled(using: &generator)
                if pending.isEmpty { pending = images }
            }
            result.current = pending.removeFirst()
            result.pending = pending
        }
        result.forward = []
        return result
    }

    /// Vuelve a la anterior que siga existiendo; nil si no hay ninguna.
    static func previous(_ state: RotationState, images: [String]) -> RotationState? {
        let available = Set(images)
        var history = state.history.filter(available.contains)
        guard let last = history.popLast() else { return nil }
        var result = state
        result.current = last
        result.history = history
        result.forward = pushed(state.forward, state.current)
        return result
    }

    /// Una cualquiera (distinta de la actual), sin seguir el orden.
    static func random(_ state: RotationState, images: [String]) -> RotationState {
        guard let pick = images.filter({ $0 != state.current }).randomElement() ?? images.first else { return state }
        var result = state
        result.history = pushed(state.history, state.current)
        result.current = pick
        result.forward = []
        return result
    }

    private static func pushed(_ list: [String], _ item: String?) -> [String] {
        guard let item else { return list }
        return Array((list + [item]).suffix(historySize))
    }
}
