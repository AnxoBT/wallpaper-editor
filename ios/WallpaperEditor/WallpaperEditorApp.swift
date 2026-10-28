import SwiftUI

@main
struct WallpaperEditorApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @StateObject private var model = AppModel()
    // Para las capturas automáticas: "-screen atajos" abre directamente la guía.
    @State private var tab = ProcessInfo.processInfo.arguments.contains("atajos") ? 1 : 0

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { WallpapersView() }
                .tabItem { Label("Fondos", systemImage: "photo.on.rectangle") }
                .tag(0)
            NavigationStack { ShortcutsGuideView() }
                .tabItem { Label("Atajos", systemImage: "square.2.layers.3d") }
                .tag(1)
        }
        .environmentObject(model)
        .tint(Color(red: 1, green: 0.36, blue: 0.54))
        .task { await model.start() }
        .alert(
            model.message ?? "",
            isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })
        ) {
            Button("Vale", role: .cancel) {}
        }
    }
}
