# ساختار ماژول `data_base:query:example`

نمونه‌های کاربرد query builder برای select، insert، update و delete.

## نمودار درختی

```text
data_base:query:example
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── managers
│   │   │   │   ├── IManagerExample.kt
│   │   │   │   └── ManagerExampleV1.kt
│   │   │   ├── v1
│   │   │   │   └── queries
│   │   │   │       ├── delete
│   │   │   │       │   └── A1ExampleDeleteV1.kt
│   │   │   │       ├── insert
│   │   │   │       │   └── A1ExampleInsertV1.kt
│   │   │   │       ├── select
│   │   │   │       │   ├── A1ExampleSelectV1.kt
│   │   │   │       │   ├── A2ExampleSelectV1.kt
│   │   │   │       │   ├── A3ExampleSelectV1.kt
│   │   │   │       │   ├── A4ExampleSelectV1.kt
│   │   │   │       │   ├── A5ExampleSelectV1.kt
│   │   │   │       │   ├── A6ExampleSelectV1.kt
│   │   │   │       │   └── A7ExampleSelectV1.kt
│   │   │   │       ├── update
│   │   │   │       │   └── A1ExampleUpdateV1.kt
│   │   │   │       └── IExampleV1.kt
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
