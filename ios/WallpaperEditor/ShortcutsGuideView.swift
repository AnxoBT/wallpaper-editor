import SwiftUI

/**
 Guía para cambiar el fondo con Atajos. iOS no deja que una app cambie el fondo ni que instale
 atajos, así que el usuario los crea una vez siguiendo estos pasos.
 */
struct ShortcutsGuideView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("En iPhone ninguna app puede cambiar el fondo por su cuenta. Un atajo sí: lo creas una vez y luego cambias de fondo con un toque en la parte de atrás del móvil, desde el escritorio o solo, por horario.")
                    .foregroundStyle(.secondary)

                GuideCard(number: 1, title: "Crea el atajo «Siguiente fondo»", systemImage: "square.2.layers.3d") {
                    Step("Abre Atajos y toca +.")
                    Step("Añade la acción «Siguiente fondo» (búscala por «Wallpaper Editor»).")
                    Step("Añade debajo «Establecer foto como fondo». Toca la flecha y desactiva «Mostrar vista previa» y «Recortar al sujeto».")
                    Step("Ponle de nombre «Siguiente fondo».")
                    Note("Haz otro igual con «Fondo anterior» para poder volver atrás.")
                    Button {
                        if let url = URL(string: "shortcuts://") { UIApplication.shared.open(url) }
                    } label: {
                        Label("Abrir Atajos", systemImage: "arrow.up.forward.app")
                    }
                    .buttonStyle(.borderedProminent)
                }

                GuideCard(number: 2, title: "Doble y triple toque", systemImage: "hand.tap") {
                    Step("Ajustes › Accesibilidad › Tocar › Toque posterior.")
                    Step("Doble toque: «Siguiente fondo».")
                    Step("Triple toque: «Fondo anterior».")
                    Note("Son toques en la parte de atrás del iPhone y funcionan en cualquier pantalla.")
                }

                GuideCard(number: 3, title: "Cambio automático", systemImage: "clock") {
                    Step("Atajos › Automatización › Nueva automatización › Hora del día.")
                    Step("Elige la hora y «Diariamente», marca «Ejecutar inmediatamente» y elige «Siguiente fondo».")
                    Note("Repítelo con varias horas para que cambie más veces al día.")
                }

                GuideCard(number: 4, title: "Botón en el escritorio", systemImage: "apps.iphone") {
                    Step("Mantén pulsado el escritorio › Editar › Añadir widget › Atajos.")
                    Step("Elige el widget y en él «Siguiente fondo».")
                }

                GuideCard(number: 5, title: "Si la acción de la app no aparece", systemImage: "questionmark.circle") {
                    Step("En Fondos, toca «Crear o actualizar el álbum».")
                    Step("Haz el atajo con «Buscar fotos» (Álbum es «Wallpaper Editor»), «Obtener elemento de la lista» (Elemento aleatorio) y «Establecer foto como fondo».")
                    Note("Así salen al azar y sin el ajuste de la app.")
                }
            }
            .padding()
        }
        .navigationTitle("Atajos")
    }
}

private struct GuideCard<Content: View>: View {
    let number: Int
    let title: LocalizedStringKey
    let systemImage: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Text("\(number)")
                    .font(.headline)
                    .frame(width: 28, height: 28)
                    .background(Circle().fill(Color.accentColor.opacity(0.2)))
                Label(title, systemImage: systemImage)
                    .font(.headline)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
    }
}

private struct Note: View {
    let text: LocalizedStringKey

    init(_ text: LocalizedStringKey) {
        self.text = text
    }

    var body: some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(.secondary)
    }
}

private struct Step: View {
    let text: LocalizedStringKey

    init(_ text: LocalizedStringKey) {
        self.text = text
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("•")
            Text(text)
        }
    }
}
