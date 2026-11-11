package cash.p.terminal.core.managers

import io.horizontalsystems.databasenaming.DatabaseNameResolver

/** What a kit's default file, directory and namespace names become on disk. */
interface KitStorageNaming {
    fun resolverFor(accountId: String): DatabaseNameResolver

    /** For files every account shares. */
    val sharedResolver: DatabaseNameResolver
}

/** The kits' own names. Hands over the [DatabaseNameResolver.Identity] object: the kits compare by reference. */
object IdentityKitStorageNaming : KitStorageNaming {
    override fun resolverFor(accountId: String): DatabaseNameResolver = DatabaseNameResolver.Identity

    override val sharedResolver: DatabaseNameResolver = DatabaseNameResolver.Identity
}
