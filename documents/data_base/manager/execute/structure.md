# ساختار ماژول `data_base:manager:execute`

اجرای کوئری‌ها و مدیریت نتیجه اجرا؛ از connection و core استفاده می‌کند.

## نمودار درختی

```text
data_base:manager:execute
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   └── IQueryExecute.kt
│   │   │   ├── manager
│   │   │   │   └── QueryExecute.kt
│   │   │   ├── tools
│   │   │   │   └── ExecuteResult.kt
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
