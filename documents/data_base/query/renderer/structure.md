# ساختار ماژول `data_base:query:renderer`

رندر کردن AST کوئری به SQL اختصاصی dialect؛ پیاده‌سازی فعلی MySQL است.

## نمودار درختی

```text
data_base:query:renderer
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── dialects
│   │   │   │   └── MySqlDialect.kt
│   │   │   ├── interfaces
│   │   │   ├── manager
│   │   │   │   └── DialectSelector.kt
│   │   │   ├── nodes
│   │   │   │   ├── delete_nodes
│   │   │   │   │   └── query
│   │   │   │   │       └── MySqlQueryRenderDeleteCapability.kt
│   │   │   │   ├── insert_nodes
│   │   │   │   │   ├── column_insert
│   │   │   │   │   │   └── MySqlQueryColumnInsertCapability.kt
│   │   │   │   │   └── query
│   │   │   │   │       └── MySqlQueryRenderInsertCapability.kt
│   │   │   │   ├── select_nodes
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── MySqlQueryColumnCapability.kt
│   │   │   │   │   ├── column_base
│   │   │   │   │   │   └── MySqlQueryColumnBaseCapability.kt
│   │   │   │   │   ├── condition_group
│   │   │   │   │   │   └── MySqlQueryConditionGroupCapability.kt
│   │   │   │   │   ├── condition_item
│   │   │   │   │   │   └── MySqlQueryConditionCapability.kt
│   │   │   │   │   ├── condition_item_collection
│   │   │   │   │   │   └── MySqlQueryConditionCollectionCapability.kt
│   │   │   │   │   ├── joins
│   │   │   │   │   │   └── MySqlQueryJoinsCapability.kt
│   │   │   │   │   ├── joins_item
│   │   │   │   │   │   └── MySqlQueryJoinsItemCapability.kt
│   │   │   │   │   ├── option_group
│   │   │   │   │   │   └── MySqlQueryOptionGroupCapability.kt
│   │   │   │   │   ├── option_limit
│   │   │   │   │   │   └── MySqlQueryOptionLimitCapability.kt
│   │   │   │   │   ├── option_offset
│   │   │   │   │   │   └── MySqlOptionOffsetCapability.kt
│   │   │   │   │   ├── option_order
│   │   │   │   │   │   └── MySqlOptionOrderCapability.kt
│   │   │   │   │   ├── query
│   │   │   │   │   │   └── MySqlQueryCapability.kt
│   │   │   │   │   ├── select
│   │   │   │   │   │   └── MySqlQuerySelectCapability.kt
│   │   │   │   │   ├── table
│   │   │   │   │   │   └── MySqlQueryTableCapability.kt
│   │   │   │   │   ├── where
│   │   │   │   │   │   └── MySqlQueryWhereCapability.kt
│   │   │   │   │   ├── withs
│   │   │   │   │   │   └── MySqlQueryWithsCapability.kt
│   │   │   │   │   └── withs_item
│   │   │   │   │       └── MySqlQueryWithsItemCapability.kt
│   │   │   │   └── update_nodes
│   │   │   │       ├── column_update
│   │   │   │       │   └── MySqlQueryColumnUpdateCapability.kt
│   │   │   │       └── query
│   │   │   │           └── MySqlQueryRenderUpdateCapability.kt
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
