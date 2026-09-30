package gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index

import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.builder.createSpatialIndex
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleCreateSpatialIndexV1(
    private val table: String = "places",
    private val index: String = "spx_places_geom",
    private val column: String = "geom",
) {
    fun migration(): IMigrationCreateSpatialIndexApi =
        createSpatialIndex {
            tableName(table)
            name(index)
            column(column)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("CREATE SPATIAL INDEX example")
        println("---------------------------")
        println(sql)
        println("Prerequisite: places.geom must be a spatial column eligible for indexing.")
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("create spatial index query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("create spatial index execute: $result") },
        )
    }
}
