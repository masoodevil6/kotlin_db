# `retryUp()`

`retryUp()` یک Target تازه‌خوانده و اعتبارسنجی‌شده را در جهت UP ادامه می‌دهد. این API برای یک Version است؛ Migration دیگری یا Version بعدی را هدف نمی‌گیرد.

راهنماهای مرتبط: [وضعیت‌های Migration](../../states/README.md) و [`recovery()`](../recovery/README.md).

## امضا

در receiver مربوط به Migration:

```kotlin
retryUp(target: MigrationRecoveryTarget, toCompleteVersion: Boolean): ExecuteResult<Boolean>
```

`toCompleteVersion` مقدار پیش‌فرض ندارد و باید در فراخوانی مشخص شود.

## ساخت Target از State

Target را از State API بگیرید؛ نمونه را دستی نسازید یا با `copy()` تغییر ندهید. Revision Checkpoint بخشی از Snapshot است و `copy()` تولیدشده برای Data Class آن را حفظ نمی‌کند.

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState

val command = db.migration {
    when (val selected = state(MigrationVersionSelection.LATEST_INCOMPLETE)) {
        is ExecuteResult.Failure -> selected
        is ExecuteResult.Success -> {
            val target = selected.result
            when {
                target == null -> ExecuteResult.Success<Boolean>(null)
                else -> {
                    val isUpRetry = when (target.operation) {
                        MigrationRecoveryOperation.UP_EXECUTION ->
                            target.version.status == MigrationVersionState.STATUS_MIGRATE_FAILED_UP
                        MigrationRecoveryOperation.UP_RECORDING_EXECUTION ->
                            target.version.status == MigrationVersionState.STATUS_MIGRATE_STARTED ||
                                target.version.status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
                        MigrationRecoveryOperation.START_EXECUTION ->
                            target.version.status == MigrationVersionState.STATUS_MIGRATE_STARTED
                        else -> false
                    }
                    if (isUpRetry) retryUp(target, toCompleteVersion = false)
                    else ExecuteResult.Failure(IllegalArgumentException("Target is not an UP Retry state"))
                }
            }
        }
    }
}
```

ثابت‌های رسمی را به جای اعداد خام استفاده کنید:

```kotlin
MigrationVersionState.STATUS_MIGRATE_STARTED
MigrationVersionState.STATUS_MIGRATE_FAILED_UP
MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
```

اگر Target را در یک فراخوانی `db.migration { ... }` می‌خوانید و بعداً در فراخوانی دیگری Retry می‌کنید، همان شیء Target را نگه دارید. Retry وضعیت دیتابیس را زیر قفل دوباره می‌خواند؛ تغییر Status، Checkpoint revision، Tag، Cursor، Configuration یا ثبت Migration باعث رد Target می‌شود.

## Status و کار مجاز

| Status | ادامهٔ مجاز در Retry UP |
|---|---|
| `100` — `STATUS_MIGRATE_STARTED` | فقط با Checkpoint معتبر و Phase قابل اجرا. `EXECUTION_PENDING`/`PAUSED_NEXT` اجرای Schema را آغاز می‌کنند؛ `RECORD_PENDING` فقط Recording را انجام می‌دهد؛ `FINALIZATION_PENDING` فقط Version را نهایی می‌کند. `EXECUTION_IN_FLIGHT` در Status `100` به‌طور خودکار رد می‌شود. |
| `101` — `STATUS_MIGRATE_FAILED_UP` | بعد از تطبیق Diagnostic و Checkpoint، CAS نرمال‌سازی باید `APPLIED` شود؛ سپس همان Migration جاری دوباره وارد اجرای UP می‌شود. Schema ممکن است اجرای قبلی اثر گذاشته باشد. |
| `102` — `STATUS_MIGRATE_FAILED_RECORD` | فقط Checkpointهای `RECORD_PENDING` یا `FINALIZATION_PENDING` پذیرفته می‌شوند. برای Record pending، فقط ثبت/تطبیق Tag و History انجام می‌شود؛ برای Finalization فقط Status نهایی نوشته می‌شود. Schema همان Migration تکرار نمی‌شود. |
| `110` — `STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED` | رد می‌شود؛ Retry خودکار یا Reset ندارد. |
| سایر Statusها | رد می‌شود؛ از جمله Statusهای DOWN و Status ناشناخته. |

برای Status `101`، Diagnostic باید Execution UP همان Version را توصیف کند و `migrationId` آن با Cursor و Migration ثبت‌شده در همان Version منطبق باشد. برای `102` Diagnostic و Checkpoint باید مرحلهٔ Recording/Finalization را مشخص کنند. اگر Checkpoint خراب یا قدیمی باشد، یا Migration از Configuration فعلی قابل Resolve نباشد، Side Effect اجرا نمی‌شود.

پیش از ادامه، Failure Status به Status شروع نرمال می‌شود و Checkpoint همان Snapshot با CAS شرطی نگه داشته می‌شود:

| ورودی | CAS نرمال‌سازی | پس از `APPLIED` |
|---|---|---|
| `101` + `EXECUTION_IN_FLIGHT` | `101 → 100` با همان Checkpoint | اجرای مجدد `up()` برای Cursor معتبر، سپس Recording. |
| `102` + `RECORD_PENDING` یا `FINALIZATION_PENDING` | `102 → 100` با همان Checkpoint | فقط Recording یا Finalization؛ بدون اجرای دوبارهٔ `up()`. |

اگر CAS نتیجهٔ `NOT_APPLIED`، `INDETERMINATE` یا Failure بدهد، Retry متوقف می‌شود و عمل بعدی شروع نمی‌شود. اگر Retry دوباره Failure بخورد، State تازه را با `state(MigrationVersionSelection.LATEST_INCOMPLETE)` بخوانید؛ Target قبلی ممکن است به‌دلیل تغییر State/Revision دیگر معتبر نباشد. Failureهای ثبت‌شدهٔ Reconciliation ممکن است به `110` برسند و در آن حالت Retry خودکار پذیرفته نمی‌شود.

## ترتیب و محدوده

UP Migrationها به ترتیب Declaration در Version اجرا می‌شوند. Retry از Cursor ذخیره‌شده ادامه می‌یابد و از همان Version بیرون نمی‌رود.

- `toCompleteVersion = true`: پس از فعالیت Cursor، بقیهٔ Migrationهای همان Version نیز به ترتیب اجرا و Version نهایی می‌شود.
- `toCompleteVersion = false`: فعالیت Cursor انجام می‌شود و اگر Migration دیگری بماند، Cursor بعدی با Phase `PAUSED_NEXT` ذخیره و فراخوانی با `Success(false)` تمام می‌شود. Status شروع `100` باقی می‌ماند. اگر Cursor فعلی آخرین Migration باشد، Finalization انجام می‌شود و نتیجه `Success(true)` است.
- Target از Phase `FINALIZATION_PENDING` مستقیماً Finalization می‌شود؛ `false` مانع این کار نیست.

`Success(true)` یعنی Version تکمیل شده؛ `Success(false)` یعنی کار با موفقیت در مرز بعدی Pause شده است. این دو مقدار معادل موفقیت/شکست Schema نیستند. خطای عادی به شکل `Failure` برمی‌گردد؛ Fatal `Error` به‌صورت Fatal منتشر می‌شود.

## نکتهٔ ایمنی دربارهٔ Status `101`

اجرای مجدد `up()` می‌تواند با اثر باقی‌مانده از تلاش قبلی برخورد کند. Status `101` و Checkpoint/Diagnostic، هویت و محل Failure را اعتبارسنجی می‌کنند اما Schema دیتابیس را بررسی نمی‌کنند. با فراخوانی Retry برای `101` مسئولیت بررسی و پذیرش ریسک تلاش مجدد با مصرف‌کننده است. `recovery()` نیز در Source فعلی Status `101` را به همین مسیر می‌فرستد.

هر transition Checkpoint با CAS انجام می‌شود. فقط نتیجهٔ `APPLIED` اجازهٔ فعالیت بعدی را می‌دهد؛ `NOT_APPLIED`، `INDETERMINATE` یا Failure در Query باعث توقف می‌شود و Schema/Recording بعدی آغاز نمی‌شود.

## نمونهٔ فراخوانی مستقیم

اگر Target را از قبل گرفته‌اید:

```kotlin
db.migration {
    retryUp(target, toCompleteVersion = true)
}
```

`db.migration { ... }` یک Result بیرونی برای اجرای Block دارد و مقدار بازگشتی Block نیز Result خود Retry است. در نتیجه نوع بیرونی در این مثال `ExecuteResult<ExecuteResult<Boolean>>` است.

**Source مرتبط:** `MigrationFacade.retryUp()`، `MigrationRunOrchestrationService.retryUp()`/`retryLocked()`، `MigrationVersionStateService.validateTargetSnapshotWhileMigrationLockHeld()`، `MigrationRetryTargetValidator`، `MigrationRetryDiagnosticValidator` و `MigrationVersionTransactionService.retryUp()`.

