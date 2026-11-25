# Recovery و Retry

`retryUp()` و `retryDown()` برای ادامهٔ یک Target مشخص هستند. `recovery()` آخرین Version ناقص را پیدا می‌کند و Dispatch را به Retry متناظر می‌سپارد. این فرمان‌ها داخل همان API مصرف‌کننده اجرا می‌شوند:

```kotlin
val result = db.migration {
    recovery(toCompleteVersion = true)
}
```

راهنمای کامل قرارداد Status/Checkpoint در [وضعیت‌های Migration](../states/README.md) و راهنمای هر فرمان در [retryUp](../controllers/retry_up/README.md)، [retryDown](../controllers/retry_down/README.md) و [recovery](../controllers/recovery/README.md) است.

## Retry روی Target خوانده‌شده

Target را از `state(MigrationVersionSelection.LATEST_INCOMPLETE)` بگیرید و همان نمونه را به Retry بدهید:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState

val targetResult = db.migration {
    state(MigrationVersionSelection.LATEST_INCOMPLETE)
}.toValue()

when (targetResult) {
    is ExecuteResult.Failure -> println("State read failed: ${targetResult.exception}")
    is ExecuteResult.Success -> {
        val target = targetResult.result
        if (target == null) {
            println("No incomplete Version")
        } else {
            val retryResult = db.migration {
                when (target.operation) {
                    MigrationRecoveryOperation.UP_EXECUTION,
                    MigrationRecoveryOperation.UP_RECORDING_EXECUTION ->
                        retryUp(target, toCompleteVersion = true)

                    MigrationRecoveryOperation.DOWN_EXECUTION,
                    MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION ->
                        retryDown(target, toCompleteVersion = true)

                    MigrationRecoveryOperation.START_EXECUTION -> when (target.version.status) {
                        MigrationVersionState.STATUS_MIGRATE_STARTED -> retryUp(target, toCompleteVersion = true)
                        MigrationVersionState.STATUS_ROLLBACK_STARTED -> retryDown(target, toCompleteVersion = true)
                        else -> ExecuteResult.Failure(IllegalStateException("Incompatible start status"))
                    }

                    else -> ExecuteResult.Failure(IllegalStateException("Target is not automatically retryable"))
                }
            }.toValue()

            when (retryResult) {
                is ExecuteResult.Success -> println("Retry result: ${retryResult.result}")
                is ExecuteResult.Failure -> println("Retry failed: ${retryResult.exception}")
            }
        }
    }
}
```

این کد فقط نمونهٔ dispatch بر اساس Target است؛ برای انتخاب خودکار Version ناقص از `recovery()` استفاده کنید. خود Retry زیر Lock، Target و Checkpoint را دوباره اعتبارسنجی می‌کند و Target قدیمی یا ناسازگار را پیش از Side Effect رد می‌کند.

## معنای `toCompleteVersion`

- `true`: فعالیت ذخیره‌شده را ادامه می‌دهد و باقی Migrationهای همان Version را نیز تا تکمیل Version اجرا می‌کند.
- `false`: فعالیت جاری را کامل می‌کند و در مرز Migration بعدی Pause می‌کند؛ اگر Migration دیگری باقی مانده باشد، نتیجهٔ موفق `Success(false)` است. اگر فقط Finalization باقی باشد، Finalization انجام می‌شود.
- Retry از Version هدف به Version دیگری عبور نمی‌کند.

برای `101`/`201` ممکن است Schema همان Migration دوباره اجرا شود؛ قبل از Retry اثر تلاش قبلی را بررسی و ریسک را بپذیرید. برای `102`/`202`، Phase معتبر `RECORD_PENDING` یا `FINALIZATION_PENDING` باعث ادامهٔ Recording/Finalization بدون اجرای مجدد Schema می‌شود. `110` و `210` وضعیت‌های مداخلهٔ دستی‌اند؛ Retry خودکار آن‌ها را نمی‌پذیرد. CAS ناموفق یا نامشخص نیز اجازهٔ ادامهٔ Side Effect بعدی را نمی‌دهد.

اگر `recovery()` Version ناقصی پیدا نکند، نتیجهٔ موفق آن مقدار null است. در این حالت، `Success(false)` را به‌عنوان no-op تفسیر نکنید؛ `false` در Retry نشان‌دهندهٔ توقف موفق در مرز Version است.
