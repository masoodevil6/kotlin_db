package gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index

import gog.my_project.data_base_schema.*





class A1ExampleCreateSpatialIndexV1(
    private val table: String = "places",
    private val index: String = "spx_places_geom",
    private val column: String = "geom",
) {
    fun migration(): SchemaOperation =
        Schema.createSpatialIndex {
            tableName(table)
            name(index)
            column(column)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("CREATE SPATIAL INDEX example")
        println("---------------------------")
        println(sql)
        println("Prerequisite: places.geom must be a spatial column eligible for indexing.")
    }

    }
