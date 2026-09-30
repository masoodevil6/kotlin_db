package gog.my_project.data_base.core.data_base

import gog.my_project.data_base.core.query.dialect.DialectQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseConfigDialectTest {
    @Test
    fun builderKeepsDialectAndTargetVersionAsSeparateSettings() {
        val config = DatabaseConfigBuilder()
            .dialect(DialectQuery.MARIA_DB)
            .targetDatabaseVersion(MARIA_DB.Version(10, 4, 28))
            .build()

        assertEquals(DialectQuery.MARIA_DB, config.dialect)
        assertEquals(MARIA_DB.Version(10, 4, 28), config.targetDatabaseVersion)
    }

    @Test
    fun parsesProductAndNumericVersionFromManagerVersionString() {
        assertEquals(
            DatabaseServerInfo(DatabaseProduct.MARIA_DB, MARIA_DB.Version(10, 4, 28)),
            DatabaseServerInfo.parse("10.4.28-MariaDB"),
        )
        assertEquals(
            DatabaseServerInfo(DatabaseProduct.MYSQL, MYSQL.Version(8, 0, 36)),
            DatabaseServerInfo.parse("8.0.36"),
        )
    }

    @Test
    fun rejectsProductMismatchAndCrossProductVersionComparison() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseServerInfo(DatabaseProduct.MYSQL, MARIA_DB.Version(10, 4, 28))
        }
        assertFailsWith<IllegalArgumentException> {
            MYSQL.Version(8, 0, 0).compareTo(MARIA_DB.Version(10, 4, 28))
        }
        assertFailsWith<IllegalArgumentException> { MYSQL.Version(-1, 0, 0) }
    }
}
