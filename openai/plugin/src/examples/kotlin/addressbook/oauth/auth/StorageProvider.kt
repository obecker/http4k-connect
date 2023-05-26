package addressbook.oauth.auth

import org.http4k.connect.storage.Storage

/**
 * Provides instances of storage for the given type of object.
 */
interface StorageProvider {
    operator fun <T : Any> invoke(): Storage<T>
}