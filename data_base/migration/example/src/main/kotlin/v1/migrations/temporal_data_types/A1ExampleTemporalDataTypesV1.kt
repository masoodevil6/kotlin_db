package gog.my_project.data_base.migration.example.v1.migrations.temporal_data_types

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.params.data_types.DateTimeType
import gog.my_project.data_base.migration.params.data_types.DateType
import gog.my_project.data_base.migration.params.data_types.TimeType
import gog.my_project.data_base.migration.params.data_types.TimestampType

/** Render-only example of the temporal column types introduced in migration v1.16. */
class A1ExampleTemporalDataTypesV1 : IExampleV1 {
    override fun migration(): IMigrationRenderCreateTableApi =
        createTable {
            table { tableName("temporal_events") }
            addColumn {
                name("event_date")
                dataType(DateType())
                notNull()
            }
            addColumn {
                name("elapsed_time")
                dataType(TimeType(precision = 3))
            }
            addColumn {
                name("local_event_time")
                dataType(DateTimeType())
                notNull()
            }
            addColumn {
                name("recorded_at")
                dataType(TimestampType(precision = 6))
                notNull()
            }
        }
}
