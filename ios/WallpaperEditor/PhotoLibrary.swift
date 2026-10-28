import Photos
import UIKit

/// Acceso a Fotos: álbumes, las fotos de las fuentes y el álbum que usan los Atajos.
enum PhotoLibrary {
    /// Nombre del álbum que se crea para los Atajos.
    static let albumName = "Wallpaper Editor"

    static var hasAccess: Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .authorized || status == .limited
    }

    static func requestAccess() async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        return status == .authorized || status == .limited
    }

    struct Album: Identifiable, Hashable {
        let id: String
        let name: String
        let count: Int
    }

    private static var imagesOnly: PHFetchOptions {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        return options
    }

    /// Favoritas y los álbumes del usuario que tienen fotos.
    static func albums() -> [Album] {
        var result: [Album] = []
        func add(_ collection: PHAssetCollection) {
            let count = PHAsset.fetchAssets(in: collection, options: imagesOnly).count
            if count > 0 {
                result.append(Album(id: collection.localIdentifier, name: collection.localizedTitle ?? "—", count: count))
            }
        }
        PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: .smartAlbumFavorites, options: nil)
            .enumerateObjects { collection, _, _ in add(collection) }
        PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: nil)
            .enumerateObjects { collection, _, _ in add(collection) }
        return result
    }

    /// Fotos de las fuentes, sin repetir, de la más antigua a la más reciente dentro de cada fuente.
    static func images(for sources: [Source]) -> [String] {
        var seen = Set<String>()
        var result: [String] = []
        func collect(_ assets: PHFetchResult<PHAsset>) {
            assets.enumerateObjects { asset, _, _ in
                if seen.insert(asset.localIdentifier).inserted { result.append(asset.localIdentifier) }
            }
        }
        for source in sources {
            switch source.kind {
            case .library:
                collect(PHAsset.fetchAssets(with: imagesOnly))
            case .album:
                if let collection = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [source.id], options: nil).firstObject {
                    collect(PHAsset.fetchAssets(in: collection, options: imagesOnly))
                }
            case .photo:
                collect(PHAsset.fetchAssets(withLocalIdentifiers: [source.id], options: imagesOnly))
            }
        }
        return result
    }

    /// La foto con su lado mayor como mucho de `maxSide` píxeles (de iCloud si hace falta).
    static func image(_ id: String, maxSide: CGFloat) async -> UIImage? {
        await image(id) { width, height in maxSide / max(width, height) }
    }

    /**
     La foto al tamaño justo para la pantalla `screen`: llenándola (`fill`, sin ampliar después y
     perder nitidez) o cabiendo entera. Nunca más grande que la original.
     */
    static func image(_ id: String, screen: CGSize, fill: Bool) async -> UIImage? {
        await image(id) { width, height in
            let scaleX = screen.width / width
            let scaleY = screen.height / height
            return fill ? max(scaleX, scaleY) : min(scaleX, scaleY)
        }
    }

    private static func image(_ id: String, scaleFor: (CGFloat, CGFloat) -> CGFloat) async -> UIImage? {
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return nil }
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .exact
        options.isNetworkAccessAllowed = true
        let width = CGFloat(asset.pixelWidth)
        let height = CGFloat(asset.pixelHeight)
        let scale = width > 0 && height > 0 ? min(1, scaleFor(width, height)) : 1
        let target = CGSize(width: (width * scale).rounded(), height: (height * scale).rounded())
        return await withCheckedContinuation { continuation in
            PHImageManager.default().requestImage(for: asset, targetSize: target, contentMode: .aspectFit, options: options) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }

    /**
     Deja el álbum «Wallpaper Editor» con exactamente estas fotos. Solo se añaden referencias:
     las fotos no se copian ni se mueven, y quitar una del álbum no la borra.
     */
    static func syncAlbum(with ids: [String]) async throws -> Int {
        let album = try await albumCollection()
        let wanted = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
        var wantedIds = Set<String>()
        wanted.enumerateObjects { asset, _, _ in wantedIds.insert(asset.localIdentifier) }
        var present = Set<String>()
        var toRemove: [PHAsset] = []
        PHAsset.fetchAssets(in: album, options: nil).enumerateObjects { asset, _, _ in
            present.insert(asset.localIdentifier)
            if !wantedIds.contains(asset.localIdentifier) { toRemove.append(asset) }
        }
        var toAdd: [PHAsset] = []
        wanted.enumerateObjects { asset, _, _ in
            if !present.contains(asset.localIdentifier) { toAdd.append(asset) }
        }
        let remove = toRemove as NSArray
        let add = toAdd as NSArray
        try await PHPhotoLibrary.shared().performChanges {
            guard let request = PHAssetCollectionChangeRequest(for: album) else { return }
            if remove.count > 0 { request.removeAssets(remove) }
            if add.count > 0 { request.addAssets(add) }
        }
        return wantedIds.count
    }

    private final class Box: @unchecked Sendable {
        var value: String?
    }

    private static func albumCollection() async throws -> PHAssetCollection {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title == %@", albumName)
        if let existing = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options).firstObject {
            return existing
        }
        let created = Box()
        try await PHPhotoLibrary.shared().performChanges {
            created.value = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName)
                .placeholderForCreatedAssetCollection.localIdentifier
        }
        guard let id = created.value,
              let album = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [id], options: nil).firstObject
        else {
            throw WallpaperError.cannotCreateAlbum
        }
        return album
    }
}
