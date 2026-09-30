# ساختار ماژول `data_base:query:executor`

اتصال builder، renderer و اتصال دیتابیس برای آماده‌سازی و اجرای کوئری.

## نمودار درختی

```text
data_base:query:executor
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   └── IQueryBuilderExecutor.kt
│   │   │   ├── manager
│   │   │   │   └── QueryBuilderExecutor.kt
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
