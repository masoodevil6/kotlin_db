# ساختار ماژول `data_base:manager`

ماژول والد مدیریت دیتابیس که بخش‌های اتصال و اجرای عملیات را گروه‌بندی می‌کند.

## نمودار درختی

```text
data_base:manager
├── connection
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── interfaces
│   │   │   │   │   └── IDatabaseConnection.kt
│   │   │   │   ├── manager
│   │   │   │   │   └── DatabaseConnection.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── execute
│   ├── src
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   ├── interfaces
│   │   │   │   │   └── IQueryExecute.kt
│   │   │   │   ├── manager
│   │   │   │   │   └── QueryExecute.kt
│   │   │   │   ├── tools
│   │   │   │   │   └── ExecuteResult.kt
│   │   │   │   └── Main.kt
│   │   │   └── resources
│   │   └── test
│   │       ├── kotlin
│   │       └── resources
│   └── build.gradle.kts
├── src
│   ├── main
│   │   ├── kotlin
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
