# آغاز Migration از طریق Database Handle

برای اجرای Migration، ابتدا `MigrationConfiguration` را به همان Registration وصل کنید که تنظیمات اتصالش را دارد؛ سپس از extension عمومی `DatabaseHandleApi.migration { ... }` روی Handle همان کلید استفاده کنید. Migration را از داخل `connection { ... }` یا `transaction { ... }` دیگری آغاز نکنید؛ extension خودش از Scope مدیریت‌شدهٔ Operation استفاده می‌کند.

## وابستگی و ثبت Configuration

برای API عمومی Migration، وابستگی aggregate زیر را به ماژول مصرف‌کننده اضافه کنید:

```kotlin
dependencies {
    implementation(project(":data_base:migration"))
}
```

`AppMigrationTags` و `CreateCustomers` در این مثال انواعی هستند که برنامهٔ شما تعریف می‌کند:

```kotlin
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.facade.migration
import gog.my_project.data_base_migration.facade.migrationConfiguration

object AppDatabase : DatabaseKey

val appMigrations: MigrationConfiguration = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
    }
}

val manager: DatabaseManagerApi = DatabaseManager {
    migrationTags = AppMigrationTags
    database(AppDatabase) {
        config = appDatabaseConfig
        migrationConfiguration = appMigrations
    }
}
```

`appDatabaseConfig` را مطابق [راهنمای تنظیمات اتصال](../database_define/database-configuration.md) بسازید. `migrationConfiguration` به Registration داخل همان `database(key) { ... }` تعلق دارد. برای چند پایگاه داده، هر Registration می‌تواند Configuration جدا داشته باشد؛ Tagهای استفاده‌شده باید با Tagهای معرفی‌شده به Manager سازگار باشند.

## آماده‌سازی و اجرای UP

Handle را با همان Key ثبت‌شده بگیرید. `init()` جداول داخلی Migration را آماده می‌کند؛ `migrate()` Migrationهای pending را اجرا می‌کند:

```kotlin
val database = manager[AppDatabase]

try {
    val initialized = database.migration { init() }
        .toValue()  // نتیجهٔ Operation و اجرای block
        .toValue()  // نتیجهٔ خود init()

    check(initialized) { "Migration system initialization did not complete" }

    val migrated = database.migration { migrate() }
        .toValue()  // نتیجهٔ Operation و اجرای block
        .toValue()  // نتیجهٔ خود migrate()

    check(migrated) { "Migration did not complete" }
} finally {
    manager.shutdown()
}
```

در این API دو لایهٔ نتیجه وجود دارد: `DatabaseHandleApi.migration` نتیجهٔ Operation/block را برمی‌گرداند و خود `init()` یا `migrate()` نیز `ExecuteResult<Boolean>` دارد. به همین دلیل در نمونه هر لایه جداگانه با `toValue()` unwrap می‌شود. `toValue()` در صورت Failure همان Throwable را پرتاب می‌کند؛ اگر لازم است خطا را بدون پرتاب بررسی کنید، لایهٔ بیرونی و نتیجهٔ داخلی را جدا با `when` بررسی کنید.

`init()` Migrationهای برنامه را اجرا نمی‌کند؛ اجرای آن‌ها کار `migrate()` است. برای اجرای Migration تا یک Version مشخص یا برای DOWN، فرمان‌های `migrate(target)` و `rollback(...)` را ببینید: [راهنمای init، migrate و rollback](../../migration/executor/commands.md). تعریف Version و ترتیب Migrationها در [راهنمای Migration](../../migration/migration_define/README.md) آمده است.

Migration Executor انتخاب، اجرای Schema، ثبت Metadata، Lock و cleanup را مدیریت می‌کند. آن مراحل را با Query دستی یا با بازکردن Connection/Transaction جداگانه جایگزین نکنید. جزئیات قرارداد نتیجه و مالکیت اجرا در [راهنمای اجرای Migration](../../migration/executor/README.md) است.

extension `migration { ... }` به Registration و Operation Scope متعلق به Handle مدیریت‌شدهٔ `DatabaseManager` نیاز دارد. محدودیت این extension و سایر قابلیت‌های Handle سفارشی در [راهنمای Executor](README.md) آمده است.
