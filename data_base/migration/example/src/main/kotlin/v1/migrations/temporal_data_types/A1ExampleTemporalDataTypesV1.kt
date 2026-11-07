package gog.my_project.data_base.migration.example.v1.migrations.temporal_data_types

import gog.my_project.data_base_schema.*

import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1




/** Render-only example of the temporal column types introduced in migration v1.16. */
class A1ExampleTemporalDataTypesV1 : IExampleV1 {
    override fun migration(): SchemaOperation =
        Schema.createTable {
            table { tableName("temporal_events") }
            addColumn {
                name("event_date")
                dataType(SchemaDataType.DateType)
                notNull()
            }
            addColumn {
                name("elapsed_time")
                dataType(SchemaDataType.TimeType(precision = 3))
            }
            addColumn {
                name("local_event_time")
                dataType(SchemaDataType.DateTimeType())
                notNull()
            }
            addColumn {
                name("recorded_at")
                dataType(SchemaDataType.TimestampType(precision = 6))
                notNull()
            }
        }
}
