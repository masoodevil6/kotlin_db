# ساختار ماژول `data_base:manager:connection`

مدیریت اتصال به دیتابیس و پیاده‌سازی اتصال؛ به core و درایور MySQL وابسته است.

## نمودار درختی

```text
data_base:manager:connection
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   └── IDatabaseConnection.kt
│   │   │   ├── manager
│   │   │   │   └── DatabaseConnection.kt
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
