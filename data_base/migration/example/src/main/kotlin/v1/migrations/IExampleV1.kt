package gog.my_project.data_base.migration.example.v1.migrations

import gog.my_project.data_base_schema.*


interface IExampleV1 {
    fun migration(): SchemaOperation

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)

        println("\n=============================================")
        println("Migration V1")
        println("---------------------------")
        println(sql)
    }

    }
