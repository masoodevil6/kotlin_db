# ساختار ماژول `data_base:migration:params`

تعریف انواع داده ستون‌ها مانند Int، Boolean، Decimal، Json، Text و Varchar.

## نمودار درختی

```text
data_base:migration:params
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── data_types
│   │   │   │   ├── BooleanType.kt
│   │   │   │   ├── DecimalType.kt
│   │   │   │   ├── IntType.kt
│   │   │   │   ├── JsonType.kt
│   │   │   │   ├── MigrationColumnDataType.kt
│   │   │   │   ├── TextType.kt
│   │   │   │   └── VarcharType.kt
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
