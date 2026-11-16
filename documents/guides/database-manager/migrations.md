# Migration و DatabaseManager

Database Manager برای هر `DatabaseRegistration`، Database Handle متناظر را ارائه می‌کند. Migration Configuration نیز به همان Registration متصل می‌شود و از مسیر رسمی `DatabaseHandleApi.migration { ... }` اجرا می‌شود:

```kotlin
val manager: DatabaseManagerApi = DatabaseManager {
    migrationTags = appMigrationTags
    database(MainDatabase) {
        config = mainDatabaseConfig
        migrationConfiguration = mainMigrationConfiguration
    }
}

val database = manager[MainDatabase]
val result = database.migration { migrate() }.toValue().toValue()
```

برای Migration dependency مصرف‌کننده به Aggregate ماژول `:data_base:migration` است. Configuration هر registration مستقل است؛ نمونه‌ای از ثبت دو دیتابیس در [راهنمای Registration](../migration/registration.md) آمده است.

دستورات `init`، `current`، `status`، `migrate` و `rollback`، Global Failure Gate، Statusها و محدودیت Atomicity در [راهنمای اجرای Migration](../migration/execution.md) مستند شده‌اند. برای این مسیر مصرف‌کننده لازم نیست مستقیم `MigrationController` یا Executor را بسازد.

نمونهٔ API عمومی دو دیتابیس در [example ریشهٔ پروژه](../../../example/README.md) است. آن برنامه به‌صورت پیش‌فرض به دیتابیس متصل نمی‌شود؛ اجرای Migration و Query با property صریح opt-in می‌شود. ماژول `data_base:migration:example` فقط Schema را رندر می‌کند و دیتابیس را تغییر نمی‌دهد.
