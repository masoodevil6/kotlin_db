# ساختار ماژول `data_base:core`

هسته مشترک دیتابیس؛ شامل تنظیمات، annotationها، قراردادهای پایه مدل و ابزارهای خواندن query.

## نمودار درختی

```text
data_base:core
├── src
│   ├── main
│   │   ├── kotlin
│   │   │   ├── annotations
│   │   │   │   ├── ctes
│   │   │   │   │   ├── AnnotationCte.kt
│   │   │   │   │   └── AnnotationCteSelect.kt
│   │   │   │   └── models
│   │   │   │       ├── AnnotationModelColumn.kt
│   │   │   │       └── AnnotationModelTable.kt
│   │   │   ├── data_base
│   │   │   │   ├── DatabaseConfig.kt
│   │   │   │   ├── DatabaseConfigBuilder.kt
│   │   │   │   ├── DefaultDatabaseConfig.kt
│   │   │   │   └── DatabaseVersions.kt
│   │   │   ├── managers
│   │   │   │   └── models
│   │   │   │       └── IModelBase.kt
│   │   │   ├── query
│   │   │   │   ├── dialect
│   │   │   │   │   └── DialectQuery.kt
│   │   │   │   └── reader
│   │   │   │       ├── BuiltQuery.kt
│   │   │   │       ├── SqlParamData.kt
│   │   │   │       └── SqlTypeResolver.kt
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

## پیکربندی dialect و نسخهٔ هدف

`DatabaseConfig` منبع مشترک dialect و نسخهٔ هدف SQL است. dialect با `DialectQuery.MY_SQL` یا `DialectQuery.MARIA_DB` انتخاب می‌شود؛ `targetDatabaseVersion` اختیاری و product-aware است، مانند `MARIA_DB.Version(10, 4, 28)`. نسخهٔ واقعی اتصال داخل config ذخیره نمی‌شود. `DatabaseServerInfo` از metadata واقعی manager می‌آید. property قدیمی `DefaultDatabaseConfig.dialect` فقط facade deprecated روی `config.dialect` است.
