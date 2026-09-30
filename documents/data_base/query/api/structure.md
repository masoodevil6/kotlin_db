# ساختار ماژول `data_base:query:api`

قراردادها و interfaceهای عمومی برای select، insert، update، delete، شرط‌ها، join و optionها.

## نمودار درختی

```text
data_base:query:api
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   ├── api
│   │   │   │   │   ├── delete_api
│   │   │   │   │   │   └── query_render_delete
│   │   │   │   │   │       └── IQueryRenderDeleteApi.kt
│   │   │   │   │   ├── insert_api
│   │   │   │   │   │   ├── column_insert
│   │   │   │   │   │   │   └── IQueryColumnInsertApi.kt
│   │   │   │   │   │   └── query_render_insert
│   │   │   │   │   │       └── IQueryRenderInsertApi.kt
│   │   │   │   │   ├── select_api
│   │   │   │   │   │   ├── column
│   │   │   │   │   │   │   └── IQueryColumnsApi.kt
│   │   │   │   │   │   ├── column_base
│   │   │   │   │   │   │   └── IQueryColumnsBaseApi.kt
│   │   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   │   └── IQueryConditionsGroupsApi.kt
│   │   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   │   └── IQueryConditionsApi.kt
│   │   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   │   └── IQueryConditionsCollectionApi.kt
│   │   │   │   │   │   ├── joins
│   │   │   │   │   │   │   └── IQueryJoinsApi.kt
│   │   │   │   │   │   ├── joins_item
│   │   │   │   │   │   │   └── IQueryJoinsItemApi.kt
│   │   │   │   │   │   ├── option_group
│   │   │   │   │   │   │   └── IQueryOptionGroupApi.kt
│   │   │   │   │   │   ├── option_limit
│   │   │   │   │   │   │   └── IQueryOptionLimitApi.kt
│   │   │   │   │   │   ├── option_offset
│   │   │   │   │   │   │   └── IQueryOptionOffsetApi.kt
│   │   │   │   │   │   ├── option_order
│   │   │   │   │   │   │   └── IQueryOptionOrderApi.kt
│   │   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   │   └── IQueryRenderSelectApi.kt
│   │   │   │   │   │   ├── select
│   │   │   │   │   │   │   └── IQuerySelectApi.kt
│   │   │   │   │   │   ├── table
│   │   │   │   │   │   │   └── IQueryTableApi.kt
│   │   │   │   │   │   ├── where
│   │   │   │   │   │   │   └── IQueryWhereApi.kt
│   │   │   │   │   │   ├── withs
│   │   │   │   │   │   │   └── IQueryWithsApi.kt
│   │   │   │   │   │   └── withs_item
│   │   │   │   │   │       └── IQueryWithsItemApi.kt
│   │   │   │   │   ├── update_api
│   │   │   │   │   │   ├── column_update
│   │   │   │   │   │   │   └── IQueryColumnUpdateApi.kt
│   │   │   │   │   │   └── query_render_update
│   │   │   │   │   │       └── IQueryRenderUpdateApi.kt
│   │   │   │   │   └── IQueryApi.kt
│   │   │   │   └── cte
│   │   │   │       └── ICte.kt
│   │   │   ├── tools
│   │   │   │   └── enums
│   │   │   │       ├── SqlConditionOperation.kt
│   │   │   │       ├── SqlLogicals.kt
│   │   │   │       ├── SqlMethodColumn.kt
│   │   │   │       ├── SqlOrderType.kt
│   │   │   │       └── SqlTypeJoin.kt
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
