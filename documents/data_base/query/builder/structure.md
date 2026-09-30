# ساختار ماژول `data_base:query:builder`

ساخت کوئری به‌صورت fluent و تبدیل ورودی کاربر به AST.

## نمودار درختی

```text
data_base:query:builder
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── ast
│   │   │   │   ├── delete_builder
│   │   │   │   │   └── query_render_delete
│   │   │   │   │       └── QueryRenderDeleteBuilder.kt
│   │   │   │   ├── insert_builder
│   │   │   │   │   ├── column_insert
│   │   │   │   │   │   └── QueryColumnInsertBuilder.kt
│   │   │   │   │   └── query_render_insert
│   │   │   │   │       └── QueryRenderInsertBuilder.kt
│   │   │   │   ├── select_builder
│   │   │   │   │   ├── column
│   │   │   │   │   │   └── QueryColumnsBuilder.kt
│   │   │   │   │   ├── column_base
│   │   │   │   │   │   └── QueryColumnsBaseBuilder.kt
│   │   │   │   │   ├── conditions_group
│   │   │   │   │   │   └── QueryConditionsGroupsBuilder.kt
│   │   │   │   │   ├── conditions_item
│   │   │   │   │   │   └── QueryConditionsBuilder.kt
│   │   │   │   │   ├── conditions_item_collection
│   │   │   │   │   │   └── QueryConditionsCollectionBuilder.kt
│   │   │   │   │   ├── joins
│   │   │   │   │   │   └── QueryJoinsBuilder.kt
│   │   │   │   │   ├── joins_item
│   │   │   │   │   │   └── QueryJoinsItemBuilder.kt
│   │   │   │   │   ├── option_group
│   │   │   │   │   │   └── QueryOptionGroupBuilder.kt
│   │   │   │   │   ├── option_limit
│   │   │   │   │   │   └── QueryOptionLimitBuilder.kt
│   │   │   │   │   ├── option_offset
│   │   │   │   │   │   └── QueryOptionOffsetBuilder.kt
│   │   │   │   │   ├── option_order
│   │   │   │   │   │   └── QueryOptionOrderBuilder.kt
│   │   │   │   │   ├── query_render_select
│   │   │   │   │   │   └── QueryRenderSelectBuilder.kt
│   │   │   │   │   ├── select
│   │   │   │   │   │   └── QuerySelectBuilder.kt
│   │   │   │   │   ├── table
│   │   │   │   │   │   └── QueryTableBuilder.kt
│   │   │   │   │   ├── where
│   │   │   │   │   │   └── QueryWhereBuilder.kt
│   │   │   │   │   ├── withs
│   │   │   │   │   │   └── QueryWithsBuilder.kt
│   │   │   │   │   └── withs_item
│   │   │   │   │       └── QueryWithsItemBuilder.kt
│   │   │   │   └── update_builder
│   │   │   │       ├── column_update
│   │   │   │       │   └── QueryColumnUpdateBuilder.kt
│   │   │   │       └── query_render_update
│   │   │   │           └── QueryRenderUpdateBuilder.kt
│   │   │   ├── cte
│   │   │   │   └── modules
│   │   │   │       └── users
│   │   │   │           └── CteInfoUser.kt
│   │   │   ├── interfaces
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
