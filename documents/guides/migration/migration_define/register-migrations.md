# ثبت Migrationها در Configuration

`migrationConfig { ... }`، Versionها و Migrationهای هر Version را به ترتیب ثبت دریافت می‌کند. `migrationGroup(...)` فقط ساختار ثبت‌ها را گروه‌بندی می‌کند؛ ترتیب اجرای Migrationها همان ترتیب ثبت است.

```kotlin
import gog.my_project.data_base_migration.api.builder.migrationConfig

val customerMigrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
    }

    migrationVersion(AppMigrationTags.Versions.V2) {
        migrationGroup(AppMigrationTags.Groups.CUSTOMERS) {
            migration<AddCustomerEmail>()
        }
    }
}
```

در UP، Versionها و Migrationهای ثبت‌شده به ترتیب Configuration اجرا می‌شوند؛ در DOWN، Migrationهای Version به ترتیب معکوس اجرا می‌شوند. Group ترتیب مستقلی ایجاد نمی‌کند و Tag پایدار دیتابیس نیست.

## اتصال به Database Registration

Tagهای قابل‌شناسایی Manager را یک بار معرفی کنید و Configuration هر دیتابیس را به Registration همان دیتابیس متصل کنید:

```kotlin
val manager = DatabaseManager {
    migrationTags = AppMigrationTags

    database(MainDatabase) {
        config = mainDatabaseConfig
        migrationConfiguration = customerMigrations
    }
}
```

سپس Migration را از Handle همان Registration اجرا کنید:

```kotlin
val result = manager[MainDatabase].migration {
    migrate()
}
```

برای چند دیتابیس، هر Registration می‌تواند Configuration مستقل داشته باشد. Tagها و Configuration باید با هم سازگار و در همان Manager ثبت شده باشند.
