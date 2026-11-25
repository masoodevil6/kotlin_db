# اجرای Migration از طریق Database Handle

این راهنما نحوهٔ استفاده از فرمان‌های Migration روی یک Database Handle را نشان می‌دهد. Handle باید از Registrationای گرفته شده باشد که `MigrationConfiguration` به آن متصل است. جزئیات تعریف و ثبت Migration در [تعریف Migration](../migration_define/README.md) و روش ترتیب‌دهی Versionها و Groupها در [Version و گروه‌بندی](../version_define/README.md) آمده است.

## راهنماها

- [اجرای init، migrate و rollback](commands.md)
- [خواندن current، status و state](inspection.md)
- [Recovery و Retry](recovery.md)
- [وضعیت‌های ذخیره‌شده](../states/README.md)

## شکل فراخوانی

فرمان‌ها داخل receiver مربوط به `db.migration { ... }` اجرا می‌شوند:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult

val result: ExecuteResult<Boolean> = db.migration {
    migrate()
}.toValue()
```

Extension بیرونی یک `ExecuteResult` برای Operation/اجرای block می‌دهد و مقدار block را داخل Success قرار می‌دهد. چون خود فرمان هم `ExecuteResult` برمی‌گرداند، `toValue()` اول نتیجهٔ بیرونی را unwrap می‌کند. برای فرمان‌هایی که مقدار غیر nullable دارند، `toValue()` دوم نتیجهٔ فرمان را unwrap می‌کند؛ Failure را با همان Throwable پرتاب می‌کند. برای `state()` و `recovery()` که نتیجهٔ موفقشان می‌تواند null باشد، به‌جای `toValue()` نتیجه را با `when` بررسی کنید.

نمونهٔ بررسی Failure داخلی بدون پرتاب آن:

```kotlin
val outer = db.migration { migrate() }
val commandResult = outer.toValue()

when (commandResult) {
    is ExecuteResult.Success -> println("Migration result: ${commandResult.result}")
    is ExecuteResult.Failure -> println("Migration failed: ${commandResult.exception}")
}
```

## مالکیت اجرا

فرمان از Scope و Connection مدیریت‌شدهٔ همان Handle استفاده می‌کند. Migration Executor مسئولیت انتخاب و اجرای Migration را بر عهده دارد؛ اجرای Schema، Recording، Lock و Cleanup را به‌صورت دستی داخل block جایگزین نکنید. `current()`، `status()` و `state(...)` خواندنی‌اند؛ `migrate()`، `rollback()`، `retryUp()`، `retryDown()` و `recovery()` می‌توانند داده یا Schema را تغییر دهند.

راهنمای [تعریف Migration](../migration_define/README.md) را برای اتصال Configuration و [راهنمای اصلی](../README.md) را برای فهرست کامل بخش‌ها ببینید.
