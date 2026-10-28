import PhotosUI
import SwiftUI

/// Pantalla de fondos: de dónde salen las fotos, en qué orden y cómo se ajustan.
struct WallpapersView: View {
    @EnvironmentObject private var model: AppModel
    @State private var pickedPhotos: [PhotosPickerItem] = []
    @State private var choosingAlbum = false

    var body: some View {
        Form {
            if !model.hasAccess {
                Section {
                    Text("Wallpaper Editor necesita ver tus fotos para usarlas como fondos.")
                    Button("Abrir Ajustes") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }
            }

            Section {
                HStack(alignment: .top, spacing: 16) {
                    PreviewFrame(image: model.preview)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(model.images.count) fotos")
                            .font(.headline)
                        Text("Así queda en tu pantalla. Para ponerla de fondo, usa el atajo (pestaña Atajos).")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        HStack {
                            Button { Task { await model.step(.previous) } } label: { Image(systemName: "backward.fill") }
                                .accessibilityLabel(Text("Anterior"))
                            Button { Task { await model.step(.next) } } label: { Image(systemName: "forward.fill") }
                                .accessibilityLabel(Text("Siguiente"))
                        }
                        .buttonStyle(.bordered)
                        .disabled(model.images.isEmpty)
                    }
                }
                .padding(.vertical, 4)
            }

            Section {
                ForEach(model.settings.sources) { source in
                    SourceRow(source: source)
                }
                .onDelete(perform: model.remove)
                if !model.settings.sources.contains(where: { $0.kind == .library }) {
                    Button {
                        model.add(.library)
                    } label: {
                        Label("Añadir todas las fotos", systemImage: "photo.stack")
                    }
                }
                Button {
                    choosingAlbum = true
                } label: {
                    Label("Añadir un álbum", systemImage: "rectangle.stack.badge.plus")
                }
                PhotosPicker(selection: $pickedPhotos, matching: .images, photoLibrary: .shared()) {
                    Label("Añadir fotos sueltas", systemImage: "plus")
                }
            } header: {
                Text("De dónde salen")
            } footer: {
                Text("Los álbumes se leen cada vez: las fotos nuevas entran solas.")
            }

            Section {
                Picker("Orden", selection: Binding(get: { model.settings.order }, set: model.setOrder)) {
                    Text("Al azar").tag(Order.random)
                    Text("En orden").tag(Order.sequential)
                }
                Picker("Ajuste", selection: Binding(get: { model.settings.fit }, set: model.setFit)) {
                    Text("Foto entera").tag(FitMode.blur)
                    Text("Llenar la pantalla").tag(FitMode.crop)
                }
            } header: {
                Text("Cómo cambian")
            } footer: {
                Text("Foto entera: con la misma foto desenfocada detrás. Llenar la pantalla: recorta lo que sobra.")
            }

            Section {
                Button {
                    Task { await model.syncAlbum() }
                } label: {
                    if model.working {
                        ProgressView()
                    } else {
                        Text("Crear o actualizar el álbum «\(PhotoLibrary.albumName)»")
                    }
                }
                .disabled(model.images.isEmpty || model.working)
            } header: {
                Text("Álbum para Atajos (opcional)")
            } footer: {
                Text("Solo hace falta para el atajo sencillo de la guía. Las fotos no se copian: el álbum solo las agrupa.")
            }
        }
        .navigationTitle("Fondos")
        .sheet(isPresented: $choosingAlbum) {
            AlbumPicker { album in
                model.add(Source(id: album.id, kind: .album, name: album.name))
                choosingAlbum = false
            }
        }
        .onChange(of: pickedPhotos) { _, items in
            let ids = items.compactMap(\.itemIdentifier)
            if !ids.isEmpty { model.addPhotos(ids) }
            pickedPhotos = []
        }
        .refreshable { model.reload() }
    }
}

private struct SourceRow: View {
    let source: Source

    var body: some View {
        switch source.kind {
        case .library:
            Label("Todas las fotos", systemImage: "photo.stack")
        case .album:
            Label(source.name, systemImage: "rectangle.stack")
        case .photo:
            HStack {
                Thumbnail(id: source.id)
                    .frame(width: 36, height: 36)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                Text("Foto suelta")
            }
        }
    }
}

/// La pantalla del móvil en pequeño, con la foto como quedaría.
private struct PreviewFrame: View {
    let image: UIImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14).fill(Color.black)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "photo")
                    .font(.title)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: 110, height: 238)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(.secondary.opacity(0.4)))
    }
}

struct Thumbnail: View {
    let id: String
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color.secondary.opacity(0.2)
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .task(id: id) { image = await PhotoLibrary.image(id, maxSide: 120) }
    }
}

/// Elegir uno de los álbumes de Fotos.
private struct AlbumPicker: View {
    let onPick: (PhotoLibrary.Album) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var albums: [PhotoLibrary.Album] = []

    var body: some View {
        NavigationStack {
            List(albums) { album in
                Button {
                    onPick(album)
                } label: {
                    HStack {
                        Text(album.name)
                        Spacer()
                        Text("\(album.count)").foregroundStyle(.secondary)
                    }
                }
            }
            .overlay {
                if albums.isEmpty { Text("No hay álbumes con fotos.").foregroundStyle(.secondary) }
            }
            .navigationTitle("Álbumes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
            }
            .task { albums = PhotoLibrary.albums() }
        }
    }
}
