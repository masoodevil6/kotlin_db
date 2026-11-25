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

برای Migration dependency مصرف‌کننده به Aggregate ماژول `:data_base:migration` است. Configuration هر Registration مستقل است؛ روش ثبت آن در [راهنمای ثبت Migrationها](../../migration/migration_define/register-migrations.md) آمده است.

روش اجرای `init`، `migrate` و `rollback` در [راهنمای فرمان‌ها](../../migration/executor/commands.md)، خواندن `current`، `status` و `state` در [راهنمای بازرسی](../../migration/executor/inspection.md)، و Statusها و Gate اجرای عادی در [راهنمای وضعیت‌های Migration](../../migration/states/README.md) آمده است. محدودیت‌های اجرای Schema و Atomicity در [راهنمای migrate](../../migration/controllers/migrate/README.md) و [راهنمای اجرای Migration](../../migration/executor/README.md) توضیح داده شده‌اند. برای این مسیر مصرف‌کننده لازم نیست مستقیم `MigrationController` یا Executor را بسازید.

نمونهٔ API عمومی دو دیتابیس در [example ریشهٔ پروژه](../../../../example/README.md) است. آن برنامه به‌صورت پیش‌فرض به دیتابیس متصل نمی‌شود؛ اجرای Migration و Query با property صریح opt-in می‌شود. ماژول `data_base:migration:example` فقط Schema را رندر می‌کند و دیتابیس را تغییر نمی‌دهد.
