package gog.my_project.data_base_manager.example.examples

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandle
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaOperation

const val ANALYTICS_EVENTS_TABLE = "manager_example_events"

fun createAnalyticsEventsOperation(): SchemaOperation = Schema.createTable {
    table { tableName(ANALYTICS_EVENTS_TABLE) }
    ifNotExists()
    addColumn {
        name("id")
        dataType(SchemaDataType.IntType)
        notNull()
        autoIncrement()
        primaryKey()
    }
    addColumn {
        name("event_name")
        dataType(SchemaDataType.VarcharType(160))
        notNull()
    }
}

/** Executes the operation against the handle's database, using the public Schema Facade. */
fun createAnalyticsEventsTable(database: DatabaseHandle): ExecuteResult<Boolean> =
    database.schema(createAnalyticsEventsOperation())
