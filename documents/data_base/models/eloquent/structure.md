# ساختار ماژول `data_base:models:eloquent`

مدل‌سازی به سبک Eloquent، شامل BaseModel و مدل‌های نمونه مانند Users و UserPhones.

## نمودار درختی

```text
data_base:models:eloquent
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── interfaces
│   │   │   │   └── IModel.kt
│   │   │   ├── manager
│   │   │   │   └── BaseModel.kt
│   │   │   ├── modules
│   │   │   │   └── users
│   │   │   │       ├── UserPhones.kt
│   │   │   │       └── Users.kt
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
