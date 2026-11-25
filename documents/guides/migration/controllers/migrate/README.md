# اجرای Migration با `migrate()`

`migrate()` Migrationهای در انتظار را از روی `MigrationConfiguration` و Metadata ذخیره‌شده انتخاب می‌کند و آن‌ها را از مسیر Orchestration و Version pipeline موجود اجرا می‌کند.

## API

دو overload عمومی در `MigrationFacade` وجود دارد:

```kotlin
migrate()
migrate(versionTag)
```

- `migrate()` همهٔ Versionهای ثبت‌شده‌ای را که هنوز اعمال نشده‌اند، به ترتیب Configuration اجرا می‌کند.
- `migrate(versionTag)` فقط Versionهای در انتظار را تا خود Version مشخص‌شده، شامل آن، اجرا می‌کند.
- Tag هدف باید به Version ثبت‌شده در همان Configuration اشاره کند. Tag یا Version ناشناخته، یا هدفی که قبلاً اعمال شده است، قبل از اجرای Schema رد می‌شود.
- اگر Version در انتظار وجود نداشته باشد، اجرای عادی با `Success(true)` پایان می‌یابد.

نمونه:

```kotlin
val execution: ExecuteResult<ExecuteResult<Boolean>> = database.migration {
    migrate()
}
```

برای اجرای تا یک Version مشخص، همان `MigrationVersionTag`ای را که در Configuration برای Version استفاده کرده‌اید بدهید:

```kotlin
val execution: ExecuteResult<ExecuteResult<Boolean>> = database.migration {
    migrate(targetVersionTag)
}
```

نتیجه دو لایه است: `DatabaseHandleApi.migration { ... }` نتیجهٔ Block را در Operation result بیرونی نگه می‌دارد و نتیجهٔ `migrate()` در لایهٔ داخلی باقی می‌ماند. لایهٔ بیرونی می‌تواند شکست ورود یا اجرای Operation Scope را نشان دهد؛ لایهٔ داخلی نتیجهٔ خود Migration command است.

```kotlin
when (execution) {
    is ExecuteResult.Failure -> println("Operation failed: ${execution.exception}")
    is ExecuteResult.Success -> when (val command = execution.result) {
        null -> println("Migration command returned no result")
        is ExecuteResult.Failure -> println("Migration failed: ${command.exception}")
        is ExecuteResult.Success -> when (command.result) {
            true -> println("All selected Versions completed")
            false -> println("Unexpected partial result for normal migrate()")
            null -> println("Migration command returned no Boolean")
        }
    }
}
```

در اجرای عادی `migrate()`، `Success(true)` یعنی همهٔ Versionهای انتخاب‌شده کامل شدند. `Success(false)` برای Pause در مرز Migration متعلق به Retry path است و نتیجهٔ مورد انتظار اجرای عادی نیست. Failure عادی به‌صورت `ExecuteResult.Failure` منتقل می‌شود؛ Fatal `Error` طبق قرارداد Operation به‌صورت Fatal منتشر می‌شود.

## ترتیب و دامنهٔ اجرا

برای اجرای عادی UP، ترتیب کلی چنین است:

1. Orchestration زیر `SystemLock.MIGRATION` یک snapshot از Tagها، Versionها و History می‌خواند.
2. History موجود، ترتیب Versionها، روابط Tag/Version/History و Status/Checkpointها اعتبارسنجی می‌شوند.
3. Versionهای لازم از Configuration انتخاب می‌شوند؛ در حالت UP ترتیب Configuration حفظ می‌شود.
4. Tagهای Version و Migrationهای انتخاب‌شده پیش از شروع اجرای Versionها بررسی و آماده می‌شوند.
5. هر Version از همان `MigrationVersionTransactionService` عبور می‌کند: Schema هر Migration اجرا می‌شود، سپس Recording مربوط به Tag/History انجام می‌گیرد و در پایان Version نهایی می‌شود.
6. پس از تکمیل UP، Version با Status `103` ثبت می‌شود و Checkpoint پاک می‌شود.

کل مسیر از همان Operation Scope و Connection مدیریت‌شده استفاده می‌کند. Orchestration قفل Migration را برای انتخاب، اعتبارسنجی و اجرای Versionهای انتخاب‌شده نگه می‌دارد. Failure Capability محدود Operation نیز در همین مرز برای ثبت Failureهای مجاز استفاده می‌شود.

## History ناقص و Statusهای موجود

اجرای عادی برای عبور از Version ناقص یا ناسازگار طراحی نشده است. اگر History شامل Version ناتمام، Checkpoint خراب/ناسازگار، Status ناشناخته، Version خارج از Configuration، Tag تکراری یا رابطهٔ ناقص باشد، اعتبارسنجی پیش از اجرای Schema Failure می‌دهد.

Statusهای اصلی UP عبارت‌اند از:

| Status | معنی |
|---:|---|
| `100` | UP شروع شده است؛ Checkpoint مرحله و Cursor را مشخص می‌کند. |
| `101` | اجرای Schema یک Migration در UP شکست خورده است. |
| `102` | Schema موفق بوده، اما Recording یا Finalization شکست خورده است. |
| `103` | Version با موفقیت تکمیل شده است. |
| `110` | ناسازگاری یا وضعیت نیازمند بررسی دستی در مسیر UP؛ اجرای عادی مسدود است. |

`100` به‌تنهایی نمی‌گوید چه کاری باید انجام شود؛ Phase و Cursor Checkpoint لازم‌اند. `101` و `102` نیز از اجرای عادی عبور نمی‌کنند. ابتدا از [راهنمای State](../state/README.md) برای خواندن Target/وضعیت و از [راهنمای وضعیت‌های Migration](../../states/README.md) برای معنای کامل Status و Phase استفاده کنید. `recovery()` و Retry مسیرهای جداگانه‌ای هستند و نباید با `migrate()` جایگزین شوند.

## Recording و Atomicity

Schema execution و Recording دو مرحلهٔ مجزا در pipeline هستند. اگر Schema با موفقیت برگردد ولی درج یا تطبیق History/Tag شکست بخورد، وضعیت Recording جدا ثبت می‌شود تا مسیر مجاز بعدی بداند Schema همان Migration را دوباره اجرا نکند. اگر خود اجرای Schema شکست بخورد، اثر نهایی DDL ممکن است به رفتار دیتابیس و عملیات انجام‌شده وابسته باشد.

نام داخلی `MigrationVersionTransactionService` به‌تنهایی تضمین نمی‌کند که همهٔ DDLها و تمام Recording یک Version در یک تراکنش دیتابیسی اتمیک و قابل Rollback باشند. این API قرارداد عمومی برای Rollback کردن اثر DDL یا اتمیک بودن Schema و Metadata ارائه نمی‌کند. به محدودیت‌های دیتابیس و semantics هر DDL توجه کنید و برای Version ناقص از مسیر State/Recovery پشتیبانی‌شده استفاده کنید؛ Status را برای عبور از اعتبارسنجی دستی تغییر ندهید.

## نکات عملی

- ترتیب Migrationها در هر Version مطابق Declaration است.
- اجرای `migrate()` چند Version، هر Version را از همان pipeline اجرا می‌کند؛ شکست در یک Version اجرای Versionهای بعدی را متوقف می‌کند.
- رویدادهای شروع، پایان و Failure Migration/Version از مسیر event موجود منتشر می‌شوند. جزئیات مصرف آن‌ها در [راهنمای Operation events](../../../operation/events/README.md) آمده است.
- `status()` گزارش تحلیلی Configuration و Metadata است و برای مشاهدهٔ ساختاری قبل/بعد از اجرا کاربرد دارد؛ [راهنمای status](../status/README.md) را ببینید.

## Source مرتبط

- Facade و امضاها: `data_base/migration/src/main/kotlin/facade/MigrationFacade.kt`
- Controller: `data_base/migration/executor/src/main/kotlin/controller/MigrationController.kt`
- Lock، انتخاب Version، اعتبارسنجی و traversal: `data_base/migration/executor/src/main/kotlin/service/run/MigrationRunOrchestrationService.kt`
- انتخاب UP تا Target: `data_base/migration/executor/src/main/kotlin/service/version_selection/MigrationVersionSelectionService.kt`
- Gate ساختار History: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionHistoryValidator.kt`
- اجرای Schema، Recording و Finalization: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionTransactionService.kt`
- نتیجهٔ بیرونی DSL: `data_base/migration/src/main/kotlin/facade/DatabaseHandleMigration.kt`
