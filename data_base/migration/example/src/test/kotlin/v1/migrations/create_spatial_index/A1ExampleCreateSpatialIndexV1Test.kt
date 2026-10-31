package gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index

import gog.my_project.data_base.migration.ast.schema.create_spatial_index.MigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.builder.ast.create_spatial_index.MigrationCreateSpatialIndexBuilder
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




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
        val migration = MigrationCreateSpatialIndexBuilder().apply {
            tableName(" Places ")
            name(" Spx_Places_Geom ")
            column(" GeoM ")
        }

        assertEquals("Places", migration.ast.metadata.tableName)
        assertEquals("Spx_Places_Geom", migration.ast.metadata.indexName)
        assertEquals("GeoM", migration.ast.metadata.columnName)
        assertEquals(
            "CREATE SPATIAL INDEX `Spx_Places_Geom` ON `Places` (`GeoM`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun quotesAndEscapesIdentifiersAndRendersOnlyOneColumn() {
        val migration = MigrationCreateSpatialIndexBuilder().apply {
            tableName("order`data")
            name("spx`places")
            column("geo`metry")
        }

        assertEquals("geo`metry", migration.ast.metadata.columnName)
        assertEquals(
            "CREATE SPATIAL INDEX `spx``places` ON `order``data` (`geo``metry`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rendersBlankIdentifiersWithoutDatabaseSemanticValidation() {
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateSpatialIndexBuilder().apply { name("spx_places_geom"); column("geom") }.ast)
        }
        assertEquals(
            "CREATE SPATIAL INDEX `spx_places_geom` ON `` (`geom`)",
            dialect.render(MigrationCreateSpatialIndexBuilder().apply { tableName(" "); name("spx_places_geom"); column("geom") }.ast),
        )
        assertEquals(
            "CREATE SPATIAL INDEX `` ON `places` (`geom`)",
            dialect.render(MigrationCreateSpatialIndexBuilder().apply { tableName("places"); name(" "); column("geom") }.ast),
        )
        assertEquals(
            "CREATE SPATIAL INDEX `spx_places_geom` ON `places` (``)",
            dialect.render(MigrationCreateSpatialIndexBuilder().apply { tableName("places"); name("spx_places_geom"); column(" ") }.ast),
        )
    }

    @Test
    fun rendererQuotesDirectAstIdentifiersWithoutSemanticChecks() {
        assertFailsWith<IllegalArgumentException> { dialect.render(MigrationCreateSpatialIndexAst()) }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateSpatialIndexAst().apply {
                metadata.tableName = "places"
                metadata.indexName = "spx_places_geom"
            })
        }
        assertEquals(
            "CREATE SPATIAL INDEX `spx_places_geom` ON `places` (` `)",
            dialect.render(MigrationCreateSpatialIndexAst().apply {
                metadata.tableName = "places"
                metadata.indexName = "spx_places_geom"
                metadata.columnName = " "
            }),
        )
    }
}
