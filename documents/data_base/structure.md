# ساختار ماژول `data_base`

ماژول والد کتابخانه دیتابیس و محل سازمان‌دهی بخش‌های core، manager، models، query و migration.

## نمودار درختی

```text
data_base
├── core
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── annotations
│   │   │   │   │   ├── ctes
│   │   │   │   │   │   ├── AnnotationCte.kt
│   │   │   │   │   │   └── AnnotationCteSelect.kt
│   │   │   │   │   └── models
│   │   │   │   │       ├── AnnotationModelColumn.kt
│   │   │   │   │       └── AnnotationModelTable.kt
│   │   │   │   ├── data_base
│   │   │   │   │   ├── DatabaseConfig.kt
│   │   │   │   │   ├── DatabaseConfigBuilder.kt
│   │   │   │   │   └── DefaultDatabaseConfig.kt
│   │   │   │   ├── managers
│   │   │   │   │   └── models
│   │   │   │   │       └── IModelBase.kt
│   │   │   │   ├── query
│   │   │   │   │   ├── dialect
│   │   │   │   │   │   └── DialectQuery.kt
│   │   │   │   │   └── reader
│   │   │   │   │       ├── BuiltQuery.kt
│   │   │   │   │       ├── SqlParamData.kt
│   │   │   │   │       └── SqlTypeResolver.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── manager
│   ├── connection
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   └── IDatabaseConnection.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── DatabaseConnection.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── execute
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   └── IQueryExecute.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── QueryExecute.kt
│   │   │   │   │   ├── tools
│   │   │   │   │   │   └── ExecuteResult.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── migration
│   ├── api
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   ├── create_table
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── IMigrationColumnApi.kt
│   │   │   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   │   │   └── IMigrationRenderCreateTableApi.kt
│   │   │   │   │   │   │   └── table
│   │   │   │   │   │   │       └── IMigrationTableApi.kt
│   │   │   │   │   │   └── IMigrationApi.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── ast
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   ├── create_table
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── IMigrationColumnAst.kt
│   │   │   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   │   │   └── IMigrationRenderCreateTableAst.kt
│   │   │   │   │   │   │   └── table
│   │   │   │   │   │   │       └── IMigrationTableAst.kt
│   │   │   │   │   │   └── IMigrationAst.kt
│   │   │   │   │   ├── schema
│   │   │   │   │   │   └── create_table
│   │   │   │   │   │       ├── column
│   │   │   │   │   │       │   └── MigrationColumnAst.kt
│   │   │   │   │   │       ├── render_query_create_table
│   │   │   │   │   │       │   └── MigrationRenderCreateTableAst.kt
│   │   │   │   │   │       └── table
│   │   │   │   │   │           └── MigrationTableAst.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── builder
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── ast
│   │   │   │   │   │   └── create_table
│   │   │   │   │   │       ├── column
│   │   │   │   │   │       │   └── MigrationColumnBuilder.kt
│   │   │   │   │   │       ├── render_migration_create_table
│   │   │   │   │   │       │   └── MigrationRenderCreateTableBuilder.kt
│   │   │   │   │   │       └── table
│   │   │   │   │   │           └── MigrationTableBuilder.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── dialect
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── data_class
│   │   │   │   │   │   ├── create_table
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── MigrationColumnData.kt
│   │   │   │   │   │   │   ├── render_migration_create_table
│   │   │   │   │   │   │   │   └── MigrationRenderCreateTableData.kt
│   │   │   │   │   │   │   └── table
│   │   │   │   │   │   │       └── MigrationTableData.kt
│   │   │   │   │   │   └── MigrationDataClass.kt
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   ├── IAstRenderer.kt
│   │   │   │   │   │   ├── IRenderContext.kt
│   │   │   │   │   │   ├── IRendererRegistry.kt
│   │   │   │   │   │   └── ISqlDialect.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   ├── BaseSqlDialect.kt
│   │   │   │   │   │   ├── RenderContext.kt
│   │   │   │   │   │   └── RendererRegistry.kt
│   │   │   │   │   ├── nodes
│   │   │   │   │   │   └── create_table
│   │   │   │   │   │       ├── column
│   │   │   │   │   │       │   └── IMigrationColumnCapability.kt
│   │   │   │   │   │       ├── render_migration_create_table
│   │   │   │   │   │       │   └── IMigrationRenderCreateTableCapability.kt
│   │   │   │   │   │       └── table
│   │   │   │   │   │           └── IMigrationTableCapability.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── example
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── executor
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   └── IMigrationExecutor.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── MigrationExecutor.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── params
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── data_types
│   │   │   │   │   │   ├── BooleanType.kt
│   │   │   │   │   │   ├── DecimalType.kt
│   │   │   │   │   │   ├── IntType.kt
│   │   │   │   │   │   ├── JsonType.kt
│   │   │   │   │   │   ├── MigrationColumnDataType.kt
│   │   │   │   │   │   ├── TextType.kt
│   │   │   │   │   │   └── VarcharType.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── renderer
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── dialects
│   │   │   │   │   │   └── MySqlDialect.kt
│   │   │   │   │   ├── interfaces
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── DialectSelector.kt
│   │   │   │   │   ├── nodes
│   │   │   │   │   │   └── create_table
│   │   │   │   │   │       ├── column
│   │   │   │   │   │       │   └── MySqlMigrationColumnCapability.kt
│   │   │   │   │   │       ├── render_migration_create_table
│   │   │   │   │   │       │   └── MySqlMigrationRenderCreateTableCapability.kt
│   │   │   │   │   │       └── table
│   │   │   │   │   │           └── MySqlMigrationTableCapability.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── models
│   ├── eloquent
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   └── IModel.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── BaseModel.kt
│   │   │   │   │   ├── modules
│   │   │   │   │   │   └── users
│   │   │   │   │   │       ├── UserPhones.kt
│   │   │   │   │   │       └── Users.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── query
│   ├── api
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   ├── api
│   │   │   │   │   │   │   ├── delete_api
│   │   │   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │   │   │       └── IQueryRenderDeleteApi.kt
│   │   │   │   │   │   │   ├── insert_api
│   │   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   │   └── IQueryColumnInsertApi.kt
│   │   │   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │   │   │       └── IQueryRenderInsertApi.kt
│   │   │   │   │   │   │   ├── select_api
│   │   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   │   └── IQueryColumnsApi.kt
│   │   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   │   └── IQueryColumnsBaseApi.kt
│   │   │   │   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   │   │   │   └── IQueryConditionsGroupsApi.kt
│   │   │   │   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   │   │   │   └── IQueryConditionsApi.kt
│   │   │   │   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   │   │   │   └── IQueryConditionsCollectionApi.kt
│   │   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   │   └── IQueryJoinsApi.kt
│   │   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   │   └── IQueryJoinsItemApi.kt
│   │   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   │   └── IQueryOptionGroupApi.kt
│   │   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   │   └── IQueryOptionLimitApi.kt
│   │   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   │   └── IQueryOptionOffsetApi.kt
│   │   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   │   └── IQueryOptionOrderApi.kt
│   │   │   │   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   │   │   │   └── IQueryRenderSelectApi.kt
│   │   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   │   └── IQuerySelectApi.kt
│   │   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   │   └── IQueryTableApi.kt
│   │   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   │   └── IQueryWhereApi.kt
│   │   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   │   └── IQueryWithsApi.kt
│   │   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │   │       └── IQueryWithsItemApi.kt
│   │   │   │   │   │   │   ├── update_api
│   │   │   │   │   │   │   │   ├── column_update
│   │   │   │   │   │   │   │   │   └── IQueryColumnUpdateApi.kt
│   │   │   │   │   │   │   │   └── query_render_update
│   │   │   │   │   │   │   │       └── IQueryRenderUpdateApi.kt
│   │   │   │   │   │   │   └── IQueryApi.kt
│   │   │   │   │   │   └── cte
│   │   │   │   │   │       └── ICte.kt
│   │   │   │   │   ├── tools
│   │   │   │   │   │   └── enums
│   │   │   │   │   │       ├── SqlConditionOperation.kt
│   │   │   │   │   │       ├── SqlLogicals.kt
│   │   │   │   │   │       ├── SqlMethodColumn.kt
│   │   │   │   │   │       ├── SqlOrderType.kt
│   │   │   │   │   │       └── SqlTypeJoin.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── ast
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   ├── delete_interface
│   │   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │   │       └── IQueryRenderDeleteAst.kt
│   │   │   │   │   │   ├── insert_interface
│   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   └── IQueryColumnInsertAst.kt
│   │   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │   │       └── IQueryRenderInsertAst.kt
│   │   │   │   │   │   ├── select_interface
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── IQueryColumnsAst.kt
│   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   └── IQueryColumnsBaseAst.kt
│   │   │   │   │   │   │   ├── conditions
│   │   │   │   │   │   │   │   └── IQueryIsConditionAst.kt
│   │   │   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   │   │   └── IQueryConditionsGroupsAst.kt
│   │   │   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   │   │   └── IQueryConditionsAst.kt
│   │   │   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   │   │   └── IQueryConditionsCollectionAst.kt
│   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   └── IQueryJoinsAst.kt
│   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   └── IQueryJoinsItemAst.kt
│   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   └── IQueryOptionGroupAst.kt
│   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   └── IQueryOptionLimitAst.kt
│   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   └── IQueryOptionOffsetAst.kt
│   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   └── IQueryOptionOrderAst.kt
│   │   │   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   │   │   └── IQueryRenderSelectAst.kt
│   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   └── IQuerySelectAst.kt
│   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   └── IQueryTableAst.kt
│   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   └── IQueryWhereAst.kt
│   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   └── IQueryWithsAst.kt
│   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │       └── IQueryWithsItemAst.kt
│   │   │   │   │   │   ├── update_interface
│   │   │   │   │   │   │   ├── column_update
│   │   │   │   │   │   │   │   └── IQueryColumnUpdateAst.kt
│   │   │   │   │   │   │   └── query_render_update
│   │   │   │   │   │   │       └── IQueryRenderUpdateAst.kt
│   │   │   │   │   │   └── IQueryAst.kt
│   │   │   │   │   ├── schema
│   │   │   │   │   │   ├── delete_schema
│   │   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │   │       └── QueryRenderDeleteAst.kt
│   │   │   │   │   │   ├── insert_schema
│   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   └── QueryColumnInsertAst.kt
│   │   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │   │       └── QueryRenderInsertAst.kt
│   │   │   │   │   │   ├── select_schema
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── QueryColumnsAst.kt
│   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   └── QueryColumnsBaseAst.kt
│   │   │   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   │   │   └── QueryConditionsGroupsAst.kt
│   │   │   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   │   │   └── QueryConditionsAst.kt
│   │   │   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   │   │   └── QueryConditionsCollectionAst.kt
│   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   └── QueryJoinsAst.kt
│   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   └── QueryJoinsItemAst.kt
│   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   └── QueryOptionGroupAst.kt
│   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   └── QueryOptionLimitAst.kt
│   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   └── QueryOptionOffsetAst.kt
│   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   └── QueryOptionOrderAst.kt
│   │   │   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   │   │   └── QueryRenderSelectAst.kt
│   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   └── QuerySelectAst.kt
│   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   └── QueryTableAst.kt
│   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   └── QueryWhereAst.kt
│   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   └── QueryWithsAst.kt
│   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │       └── QueryWithsItemAst.kt
│   │   │   │   │   │   └── update_schema
│   │   │   │   │   │       ├── column_update
│   │   │   │   │   │       │   └── QueryColumnUpdateAst.kt
│   │   │   │   │   │       └── query_render_update
│   │   │   │   │   │           └── QueryRenderUpdateAst.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── builder
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── ast
│   │   │   │   │   │   ├── delete_builder
│   │   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │   │       └── QueryRenderDeleteBuilder.kt
│   │   │   │   │   │   ├── insert_builder
│   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   └── QueryColumnInsertBuilder.kt
│   │   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │   │       └── QueryRenderInsertBuilder.kt
│   │   │   │   │   │   ├── select_builder
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── QueryColumnsBuilder.kt
│   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   └── QueryColumnsBaseBuilder.kt
│   │   │   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   │   │   └── QueryConditionsGroupsBuilder.kt
│   │   │   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   │   │   └── QueryConditionsBuilder.kt
│   │   │   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   │   │   └── QueryConditionsCollectionBuilder.kt
│   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   └── QueryJoinsBuilder.kt
│   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   └── QueryJoinsItemBuilder.kt
│   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   └── QueryOptionGroupBuilder.kt
│   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   └── QueryOptionLimitBuilder.kt
│   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   └── QueryOptionOffsetBuilder.kt
│   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   └── QueryOptionOrderBuilder.kt
│   │   │   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   │   │   └── QueryRenderSelectBuilder.kt
│   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   └── QuerySelectBuilder.kt
│   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   └── QueryTableBuilder.kt
│   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   └── QueryWhereBuilder.kt
│   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   └── QueryWithsBuilder.kt
│   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │       └── QueryWithsItemBuilder.kt
│   │   │   │   │   │   └── update_builder
│   │   │   │   │   │       ├── column_update
│   │   │   │   │   │       │   └── QueryColumnUpdateBuilder.kt
│   │   │   │   │   │       └── query_render_update
│   │   │   │   │   │           └── QueryRenderUpdateBuilder.kt
│   │   │   │   │   ├── cte
│   │   │   │   │   │   └── modules
│   │   │   │   │   │       └── users
│   │   │   │   │   │           └── CteInfoUser.kt
│   │   │   │   │   ├── interfaces
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── dialect
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── data_class
│   │   │   │   │   │   ├── delete_data
│   │   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │   │       └── QueryRenderDeleteData.kt
│   │   │   │   │   │   ├── insert_data
│   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   └── QueryColumnInsertData.kt
│   │   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │   │       └── QueryRenderInsertData.kt
│   │   │   │   │   │   ├── select_data
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── QueryColumnData.kt
│   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   └── QueryColumnBaseData.kt
│   │   │   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   │   │   └── QueryConditionsGroupsData.kt
│   │   │   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   │   │   └── QueryConditionsData.kt
│   │   │   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   │   │   └── QueryConditionCollectionData.kt
│   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   └── QueryJoinsData.kt
│   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   └── QueryJoinsItemData.kt
│   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   └── QueryOptionGroupData.kt
│   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   └── QueryOptionLimitData.kt
│   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   └── QueryOptionOffsetData.kt
│   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   └── QueryOptionOrderData.kt
│   │   │   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   │   │   └── IQueryRenderSelectData.kt
│   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   └── QuerySelectData.kt
│   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   └── QueryTableData.kt
│   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   └── QueryWhereData.kt
│   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   └── QueryWithsData.kt
│   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │       └── QueryWithsItemData.kt
│   │   │   │   │   │   ├── update_data
│   │   │   │   │   │   │   ├── column_update
│   │   │   │   │   │   │   │   └── QueryColumnUpdateData.kt
│   │   │   │   │   │   │   └── query_render_update
│   │   │   │   │   │   │       └── QueryRenderUpdateData.kt
│   │   │   │   │   │   └── QueryDataClass.kt
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   ├── IAstRenderer.kt
│   │   │   │   │   │   ├── IRenderContext.kt
│   │   │   │   │   │   ├── IRendererRegistry.kt
│   │   │   │   │   │   └── ISqlDialect.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   ├── BaseSqlDialect.kt
│   │   │   │   │   │   ├── RenderContext.kt
│   │   │   │   │   │   └── RendererRegistry.kt
│   │   │   │   │   ├── nodes
│   │   │   │   │   │   ├── insert_modes
│   │   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │   │       └── IQueryRenderDeleteCapability.kt
│   │   │   │   │   │   ├── insert_nodes
│   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   └── IQueryColumnInsertCapability.kt
│   │   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │   │       └── IQueryRenderInsertCapability.kt
│   │   │   │   │   │   ├── select_nodes
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── IQueryColumnCapability.kt
│   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   └── IQueryColumnBaseCapability.kt
│   │   │   │   │   │   │   ├── condition_group
│   │   │   │   │   │   │   │   └── IQueryConditionGroupCapability.kt
│   │   │   │   │   │   │   ├── condition_item
│   │   │   │   │   │   │   │   └── IQueryConditionCapability.kt
│   │   │   │   │   │   │   ├── condition_item_collection
│   │   │   │   │   │   │   │   └── IQueryConditionCollectionCapability.kt
│   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   └── IQueryJoinsCapability.kt
│   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   └── IQueryJoinsItemCapability.kt
│   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   └── IQueryOptionGroupCapability.kt
│   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   └── IQueryOptionLimitCapability.kt
│   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   └── IQueryOptionOffsetCapability.kt
│   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   └── IQueryOptionOrderCapability.kt
│   │   │   │   │   │   │   ├── query
│   │   │   │   │   │   │   │   └── IQueryRenderSelectCapability.kt
│   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   └── IQuerySelectCapability.kt
│   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   └── IQueryTableCapability.kt
│   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   └── IQueryWhereCapability.kt
│   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   └── IQueryWithsCapability.kt
│   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │       └── IQueryWithsItemCapability.kt
│   │   │   │   │   │   └── update_nodes
│   │   │   │   │   │       ├── column_update
│   │   │   │   │   │       │   └── IQueryColumnUpdateCapability.kt
│   │   │   │   │   │       └── query_render_update
│   │   │   │   │   │           └── IQueryRenderUpdateCapability.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── example
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── managers
│   │   │   │   │   │   ├── IManagerExample.kt
│   │   │   │   │   │   └── ManagerExampleV1.kt
│   │   │   │   │   ├── v1
│   │   │   │   │   │   └── queries
│   │   │   │   │   │       ├── delete
│   │   │   │   │   │       │   └── A1ExampleDeleteV1.kt
│   │   │   │   │   │       ├── insert
│   │   │   │   │   │       │   └── A1ExampleInsertV1.kt
│   │   │   │   │   │       ├── select
│   │   │   │   │   │       │   ├── A1ExampleSelectV1.kt
│   │   │   │   │   │       │   ├── A2ExampleSelectV1.kt
│   │   │   │   │   │       │   ├── A3ExampleSelectV1.kt
│   │   │   │   │   │       │   ├── A4ExampleSelectV1.kt
│   │   │   │   │   │       │   ├── A5ExampleSelectV1.kt
│   │   │   │   │   │       │   ├── A6ExampleSelectV1.kt
│   │   │   │   │   │       │   └── A7ExampleSelectV1.kt
│   │   │   │   │   │       ├── update
│   │   │   │   │   │       │   └── A1ExampleUpdateV1.kt
│   │   │   │   │   │       └── IExampleV1.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── executor
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── interfaces
│   │   │   │   │   │   └── IQueryBuilderExecutor.kt
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── QueryBuilderExecutor.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── renderer
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── kotlin
│   │   │   │   │   ├── dialects
│   │   │   │   │   │   └── MySqlDialect.kt
│   │   │   │   │   ├── interfaces
│   │   │   │   │   ├── manager
│   │   │   │   │   │   └── DialectSelector.kt
│   │   │   │   │   ├── nodes
│   │   │   │   │   │   ├── delete_nodes
│   │   │   │   │   │   │   └── query
│   │   │   │   │   │   │       └── MySqlQueryRenderDeleteCapability.kt
│   │   │   │   │   │   ├── insert_nodes
│   │   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   │   └── MySqlQueryColumnInsertCapability.kt
│   │   │   │   │   │   │   └── query
│   │   │   │   │   │   │       └── MySqlQueryRenderInsertCapability.kt
│   │   │   │   │   │   ├── select_nodes
│   │   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   │   └── MySqlQueryColumnCapability.kt
│   │   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   │   └── MySqlQueryColumnBaseCapability.kt
│   │   │   │   │   │   │   ├── condition_group
│   │   │   │   │   │   │   │   └── MySqlQueryConditionGroupCapability.kt
│   │   │   │   │   │   │   ├── condition_item
│   │   │   │   │   │   │   │   └── MySqlQueryConditionCapability.kt
│   │   │   │   │   │   │   ├── condition_item_collection
│   │   │   │   │   │   │   │   └── MySqlQueryConditionCollectionCapability.kt
│   │   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   │   └── MySqlQueryJoinsCapability.kt
│   │   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   │   └── MySqlQueryJoinsItemCapability.kt
│   │   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   │   └── MySqlQueryOptionGroupCapability.kt
│   │   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   │   └── MySqlQueryOptionLimitCapability.kt
│   │   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   │   └── MySqlOptionOffsetCapability.kt
│   │   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   │   └── MySqlOptionOrderCapability.kt
│   │   │   │   │   │   │   ├── query
│   │   │   │   │   │   │   │   └── MySqlQueryCapability.kt
│   │   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   │   └── MySqlQuerySelectCapability.kt
│   │   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   │   └── MySqlQueryTableCapability.kt
│   │   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   │   └── MySqlQueryWhereCapability.kt
│   │   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   │   └── MySqlQueryWithsCapability.kt
│   │   │   │   │   │   │   └── withs_item
│   │   │   │   │   │   │       └── MySqlQueryWithsItemCapability.kt
│   │   │   │   │   │   └── update_nodes
│   │   │   │   │   │       ├── column_update
│   │   │   │   │   │       │   └── MySqlQueryColumnUpdateCapability.kt
│   │   │   │   │   │       └── query
│   │   │   │   │   │           └── MySqlQueryRenderUpdateCapability.kt
│   │   │   │   │   └── Main.kt
│   │   │   │   └── resources
│   │   │   └── test
│   │   │       ├── kotlin
│   │   │       └── resources
│   │   └── build.gradle.kts
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── src
│   ├── main
│   │   ├── java
│   │   │   └── gog
│   │   │       └── my_project
│   │   │           └── Main.kt
│   │   └── resources
│   └── test
│       ├── java
│       └── resources
└── build.gradle.kts
```

## توضیح کوتاه اجزای ماژول

- `build.gradle.kts`: تنظیمات Gradle، dependencyها و taskهای این ماژول.
- `src/main/kotlin`: کد اصلی Kotlin ماژول.
- `src/test` در صورت وجود: تست‌های واحد ماژول.
