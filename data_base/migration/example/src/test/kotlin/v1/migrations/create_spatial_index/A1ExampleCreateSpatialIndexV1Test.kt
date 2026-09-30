package gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index

import gog.my_project.data_base.migration.ast.schema.create_spatial_index.MigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.builder.createSpatialIndex
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A1ExampleCreateSpatialIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersExactSingleColumnSpatialIndexSql() {
        assertEquals(
            "CREATE SPATIAL INDEX `spx_places_geom` ON `places` (`geom`)",
            dialect.render(A1ExampleCreateSpatialIndexV1().migration().ast),
        )
    }

    @Test
    fun trimsIdentifiersAndPreservesCasing() {
        val migration = createSpatialIndex {
            tableName(" Places ")
            name(" Spx_Places_Geom ")
            column(" GeoM ")
        }

        assertEquals("Places", migration.ast.tableName)
        assertEquals("Spx_Places_Geom", migration.ast.indexName)
        assertEquals("GeoM", migration.ast.columnName)
        assertEquals(
            "CREATE SPATIAL INDEX `Spx_Places_Geom` ON `Places` (`GeoM`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun quotesAndEscapesIdentifiersAndRendersOnlyOneColumn() {
        val migration = createSpatialIndex {
            tableName("order`data")
            name("spx`places")
            column("geo`metry")
        }

        assertEquals("geo`metry", migration.ast.columnName)
        assertEquals(
            "CREATE SPATIAL INDEX `spx``places` ON `order``data` (`geo``metry`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rejectsMissingOrBlankIdentifiers() {
        assertFailsWith<IllegalArgumentException> {
            createSpatialIndex { name("spx_places_geom"); column("geom") }
        }
        assertFailsWith<IllegalArgumentException> {
            createSpatialIndex { tableName(" "); name("spx_places_geom"); column("geom") }
        }
        assertFailsWith<IllegalArgumentException> {
            createSpatialIndex { tableName("places"); name(" "); column("geom") }
        }
        assertFailsWith<IllegalArgumentException> {
            createSpatialIndex { tableName("places"); name("spx_places_geom"); column(" ") }
        }
    }

    @Test
    fun rendererDefensivelyRejectsIncompleteAstBeforeQuoting() {
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateSpatialIndexAst())
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateSpatialIndexAst().apply {
                tableName = "places"
                indexName = "spx_places_geom"
            })
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateSpatialIndexAst().apply {
                tableName = "places"
                indexName = "spx_places_geom"
                columnName = " "
            })
        }
    }
}
