package gog.my_project.data_base.migration.example.v1.migrations.create_table_timestamps

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.params.data_types.IntType

class A1ExampleCreateTableTimestampsV1 : IExampleV1 {
    override fun migration(): IMigrationRenderCreateTableApi = createTable {
        table { tableName("posts") }
        addColumn {
            name("id")
            dataType(IntType())
            notNull()
            autoIncrement()
            primaryKey()
        }
        timestamps()
        softDeletes()
    }
}
