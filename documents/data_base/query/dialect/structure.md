# ساختار ماژول `data_base:query:dialect`

مدل‌های داده، context و قراردادهای رندر مستقل از موتور SQL.

## نمودار درختی

```text
data_base:query:dialect
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── data_class
│   │   │   │   ├── delete_data
│   │   │   │   │   └── query_render_delete
│   │   │   │   │       └── QueryRenderDeleteData.kt
│   │   │   │   ├── insert_data
│   │   │   │   │   ├── column_insert
│   │   │   │   │   │   └── QueryColumnInsertData.kt
│   │   │   │   │   └── query_render_insert
│   │   │   │   │       └── QueryRenderInsertData.kt
│   │   │   │   ├── select_data
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── QueryColumnData.kt
│   │   │   │   │   ├── column_base
│   │   │   │   │   │   └── QueryColumnBaseData.kt
│   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   └── QueryConditionsGroupsData.kt
│   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   └── QueryConditionsData.kt
│   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   └── QueryConditionCollectionData.kt
│   │   │   │   │   ├── joins
│   │   │   │   │   │   └── QueryJoinsData.kt
│   │   │   │   │   ├── joins_item
│   │   │   │   │   │   └── QueryJoinsItemData.kt
│   │   │   │   │   ├── option_group
│   │   │   │   │   │   └── QueryOptionGroupData.kt
│   │   │   │   │   ├── option_limit
│   │   │   │   │   │   └── QueryOptionLimitData.kt
│   │   │   │   │   ├── option_offset
│   │   │   │   │   │   └── QueryOptionOffsetData.kt
│   │   │   │   │   ├── option_order
│   │   │   │   │   │   └── QueryOptionOrderData.kt
│   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   └── IQueryRenderSelectData.kt
│   │   │   │   │   ├── select
│   │   │   │   │   │   └── QuerySelectData.kt
│   │   │   │   │   ├── table
│   │   │   │   │   │   └── QueryTableData.kt
│   │   │   │   │   ├── where
│   │   │   │   │   │   └── QueryWhereData.kt
│   │   │   │   │   ├── withs
│   │   │   │   │   │   └── QueryWithsData.kt
│   │   │   │   │   └── withs_item
│   │   │   │   │       └── QueryWithsItemData.kt
│   │   │   │   ├── update_data
│   │   │   │   │   ├── column_update
│   │   │   │   │   │   └── QueryColumnUpdateData.kt
│   │   │   │   │   └── query_render_update
│   │   │   │   │       └── QueryRenderUpdateData.kt
│   │   │   │   └── QueryDataClass.kt
│   │   │   ├── interfaces
│   │   │   │   ├── IAstRenderer.kt
│   │   │   │   ├── IRenderContext.kt
│   │   │   │   ├── IRendererRegistry.kt
│   │   │   │   └── ISqlDialect.kt
│   │   │   ├── manager
│   │   │   │   ├── BaseSqlDialect.kt
│   │   │   │   ├── RenderContext.kt
│   │   │   │   └── RendererRegistry.kt
│   │   │   ├── nodes
│   │   │   │   ├── insert_modes
│   │   │   │   │   └── query_render_delete
│   │   │   │   │       └── IQueryRenderDeleteCapability.kt
│   │   │   │   ├── insert_nodes
│   │   │   │   │   ├── column_insert
│   │   │   │   │   │   └── IQueryColumnInsertCapability.kt
│   │   │   │   │   └── query_render_insert
│   │   │   │   │       └── IQueryRenderInsertCapability.kt
│   │   │   │   ├── select_nodes
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── IQueryColumnCapability.kt
│   │   │   │   │   ├── column_base
│   │   │   │   │   │   └── IQueryColumnBaseCapability.kt
│   │   │   │   │   ├── condition_group
│   │   │   │   │   │   └── IQueryConditionGroupCapability.kt
│   │   │   │   │   ├── condition_item
│   │   │   │   │   │   └── IQueryConditionCapability.kt
│   │   │   │   │   ├── condition_item_collection
│   │   │   │   │   │   └── IQueryConditionCollectionCapability.kt
│   │   │   │   │   ├── joins
│   │   │   │   │   │   └── IQueryJoinsCapability.kt
│   │   │   │   │   ├── joins_item
│   │   │   │   │   │   └── IQueryJoinsItemCapability.kt
│   │   │   │   │   ├── option_group
│   │   │   │   │   │   └── IQueryOptionGroupCapability.kt
│   │   │   │   │   ├── option_limit
│   │   │   │   │   │   └── IQueryOptionLimitCapability.kt
│   │   │   │   │   ├── option_offset
│   │   │   │   │   │   └── IQueryOptionOffsetCapability.kt
│   │   │   │   │   ├── option_order
│   │   │   │   │   │   └── IQueryOptionOrderCapability.kt
│   │   │   │   │   ├── query
│   │   │   │   │   │   └── IQueryRenderSelectCapability.kt
│   │   │   │   │   ├── select
│   │   │   │   │   │   └── IQuerySelectCapability.kt
│   │   │   │   │   ├── table
│   │   │   │   │   │   └── IQueryTableCapability.kt
│   │   │   │   │   ├── where
│   │   │   │   │   │   └── IQueryWhereCapability.kt
│   │   │   │   │   ├── withs
│   │   │   │   │   │   └── IQueryWithsCapability.kt
│   │   │   │   │   └── withs_item
│   │   │   │   │       └── IQueryWithsItemCapability.kt
│   │   │   │   └── update_nodes
│   │   │   │       ├── column_update
│   │   │   │       │   └── IQueryColumnUpdateCapability.kt
│   │   │   │       └── query_render_update
│   │   │   │           └── IQueryRenderUpdateCapability.kt
│   │   │   └── Main.kt
│   │   └── resources
│   └── test
│       ├── kotlin
│       └── resources
└── build.gradle.kts
```

## توضیح کوتاه اجزای ماژول

- `build.gradle.kts`: تنظیمات Gradle، dependencyها و taskهای این ماژول.
- `src/main/kotlin`: کد اصلی Kotlin ماژول.
- `src/test` در صورت وجود: تست‌های واحد ماژول.
