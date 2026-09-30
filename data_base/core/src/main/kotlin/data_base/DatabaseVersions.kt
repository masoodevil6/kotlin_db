package gog.my_project.data_base.core.data_base

enum class DatabaseProduct { MYSQL, MARIA_DB }

sealed interface ProductDatabaseVersion : Comparable<ProductDatabaseVersion> {
    val product: DatabaseProduct
    val major: Int
    val minor: Int
    val patch: Int

    override fun compareTo(other: ProductDatabaseVersion): Int {
        require(product == other.product) { "Cannot compare versions from different database products" }
        return compareValuesBy(this, other, ProductDatabaseVersion::major, ProductDatabaseVersion::minor, ProductDatabaseVersion::patch)
    }
}

object MYSQL {
    data class Version(
        override val major: Int,
        override val minor: Int,
        override val patch: Int,
    ) : ProductDatabaseVersion {
        override val product = DatabaseProduct.MYSQL
        init { require(major >= 0 && minor >= 0 && patch >= 0) { "Version components must be non-negative" } }
    }

    val V8_0 = Version(8, 0, 0)
}

object MARIA_DB {
    data class Version(
        override val major: Int,
        override val minor: Int,
        override val patch: Int,
    ) : ProductDatabaseVersion {
        override val product = DatabaseProduct.MARIA_DB
        init { require(major >= 0 && minor >= 0 && patch >= 0) { "Version components must be non-negative" } }
    }

    val V10_5_3 = Version(10, 5, 3)
}

data class DatabaseServerInfo(
    val product: DatabaseProduct,
    val version: ProductDatabaseVersion,
) {
    init { require(product == version.product) { "Server product must match version product" } }

    companion object {
        private val versionPattern = Regex("(\\d+)\\.(\\d+)\\.(\\d+)")

        fun parse(versionText: String): DatabaseServerInfo {
            val isMariaDb = versionText.contains("mariadb", ignoreCase = true)
            val versions = versionPattern.findAll(versionText.trim()).toList()
            val match = (if (isMariaDb) versions.lastOrNull() else versions.firstOrNull())
                ?: throw IllegalArgumentException("Unsupported database server version: $versionText")
            val (major, minor, patch) = match.groupValues.drop(1).take(3).map(String::toInt)
            return if (isMariaDb) {
                DatabaseServerInfo(DatabaseProduct.MARIA_DB, MARIA_DB.Version(major, minor, patch))
            } else {
                DatabaseServerInfo(DatabaseProduct.MYSQL, MYSQL.Version(major, minor, patch))
            }
        }
    }
}
