# ساختار ماژول `data_base:query:ast`

مدل‌سازی درخت نحوی کوئری و schemaهای select، insert، update و delete.

## نمودار درختی

```text
data_base:query:ast
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   ├── delete_interface
│   │   │   │   │   └── query_render_delete
│   │   │   │   │       └── IQueryRenderDeleteAst.kt
│   │   │   │   ├── insert_interface
│   │   │   │   │   ├── column_insert
│   │   │   │   │   │   └── IQueryColumnInsertAst.kt
│   │   │   │   │   └── query_render_insert
│   │   │   │   │       └── IQueryRenderInsertAst.kt
│   │   │   │   ├── select_interface
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── IQueryColumnsAst.kt
│   │   │   │   │   ├── column_base
│   │   │   │   │   │   └── IQueryColumnsBaseAst.kt
│   │   │   │   │   ├── conditions
│   │   │   │   │   │   └── IQueryIsConditionAst.kt
│   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   └── IQueryConditionsGroupsAst.kt
│   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   └── IQueryConditionsAst.kt
│   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   └── IQueryConditionsCollectionAst.kt
│   │   │   │   │   ├── joins
│   │   │   │   │   │   └── IQueryJoinsAst.kt
│   │   │   │   │   ├── joins_item
│   │   │   │   │   │   └── IQueryJoinsItemAst.kt
│   │   │   │   │   ├── option_group
│   │   │   │   │   │   └── IQueryOptionGroupAst.kt
│   │   │   │   │   ├── option_limit
│   │   │   │   │   │   └── IQueryOptionLimitAst.kt
│   │   │   │   │   ├── option_offset
│   │   │   │   │   │   └── IQueryOptionOffsetAst.kt
│   │   │   │   │   ├── option_order
│   │   │   │   │   │   └── IQueryOptionOrderAst.kt
│   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   └── IQueryRenderSelectAst.kt
│   │   │   │   │   ├── select
│   │   │   │   │   │   └── IQuerySelectAst.kt
│   │   │   │   │   ├── table
│   │   │   │   │   │   └── IQueryTableAst.kt
│   │   │   │   │   ├── where
│   │   │   │   │   │   └── IQueryWhereAst.kt
│   │   │   │   │   ├── withs
│   │   │   │   │   │   └── IQueryWithsAst.kt
│   │   │   │   │   └── withs_item
│   │   │   │   │       └── IQueryWithsItemAst.kt
│   │   │   │   ├── update_interface
│   │   │   │   │   ├── column_update
│   │   │   │   │   │   └── IQueryColumnUpdateAst.kt
│   │   │   │   │   └── query_render_update
│   │   │   │   │       └── IQueryRenderUpdateAst.kt
│   │   │   │   └── IQueryAst.kt
│   │   │   ├── schema
│   │   │   │   ├── delete_schema
│   │   │   │   │   └── query_render_delete
│   │   │   │   │       └── QueryRenderDeleteAst.kt
│   │   │   │   ├── insert_schema
│   │   │   │   │   ├── column_insert
│   │   │   │   │   │   └── QueryColumnInsertAst.kt
│   │   │   │   │   └── query_render_insert
│   │   │   │   │       └── QueryRenderInsertAst.kt
│   │   │   │   ├── select_schema
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── QueryColumnsAst.kt
│   │   │   │   │   ├── column_base
│   │   │   │   │   │   └── QueryColumnsBaseAst.kt
│   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   └── QueryConditionsGroupsAst.kt
│   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   └── QueryConditionsAst.kt
│   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   └── QueryConditionsCollectionAst.kt
│   │   │   │   │   ├── joins
│   │   │   │   │   │   └── QueryJoinsAst.kt
│   │   │   │   │   ├── joins_item
│   │   │   │   │   │   └── QueryJoinsItemAst.kt
│   │   │   │   │   ├── option_group
│   │   │   │   │   │   └── QueryOptionGroupAst.kt
│   │   │   │   │   ├── option_limit
│   │   │   │   │   │   └── QueryOptionLimitAst.kt
│   │   │   │   │   ├── option_offset
│   │   │   │   │   │   └── QueryOptionOffsetAst.kt
│   │   │   │   │   ├── option_order
│   │   │   │   │   │   └── QueryOptionOrderAst.kt
│   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   └── QueryRenderSelectAst.kt
│   │   │   │   │   ├── select
│   │   │   │   │   │   └── QuerySelectAst.kt
│   │   │   │   │   ├── table
│   │   │   │   │   │   └── QueryTableAst.kt
│   │   │   │   │   ├── where
│   │   │   │   │   │   └── QueryWhereAst.kt
│   │   │   │   │   ├── withs
│   │   │   │   │   │   └── QueryWithsAst.kt
│   │   │   │   │   └── withs_item
│   │   │   │   │       └── QueryWithsItemAst.kt
│   │   │   │   └── update_schema
│   │   │   │       ├── column_update
│   │   │   │       │   └── QueryColumnUpdateAst.kt
│   │   │   │       └── query_render_update
│   │   │   │           └── QueryRenderUpdateAst.kt
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
