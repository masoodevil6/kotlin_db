# `retryDown()`

`retryDown()` یک Target تازه‌خوانده و اعتبارسنجی‌شده را در جهت DOWN ادامه می‌دهد. این API فقط Version مشخص‌شده را ادامه می‌دهد و به Version قبلی دیگری عبور نمی‌کند.

راهنماهای مرتبط: [وضعیت‌های Migration](../../states/README.md) و [`recovery()`](../recovery/README.md).

## امضا

در receiver مربوط به Migration:

```kotlin
retryDown(target: MigrationRecoveryTarget, toCompleteVersion: Boolean): ExecuteResult<Boolean>
```

`toCompleteVersion` باید صریحاً تعیین شود.

## ساخت Target از State

Target را از State API دریافت کنید و همان نمونه را به Retry بدهید. Target یک Snapshot/selector است، نه مجوز اجرا؛ ساخت دستی یا استفاده از `copy()` برای آن مناسب نیست، چون Revision Checkpoint با `copy()` حفظ نمی‌شود.

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
                    val isDownRetry = when (target.operation) {
                        MigrationRecoveryOperation.DOWN_EXECUTION ->
                            target.version.status == MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
                        MigrationRecoveryOperation.DOWN_RECORDING_EXECUTION ->
                            target.version.status == MigrationVersionState.STATUS_ROLLBACK_STARTED ||
                                target.version.status == MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
                        MigrationRecoveryOperation.START_EXECUTION ->
                            target.version.status == MigrationVersionState.STATUS_ROLLBACK_STARTED
                        else -> false
                    }
                    if (isDownRetry) retryDown(target, toCompleteVersion = false)
                    else ExecuteResult.Failure(IllegalArgumentException("Target is not a DOWN Retry state"))
                }
            }
        }
    }
}
```

ثابت‌های رسمی را به جای اعداد خام استفاده کنید:

```kotlin
MigrationVersionState.STATUS_ROLLBACK_STARTED
MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
```

Retry زیر `SystemLock.MIGRATION` دیتابیس را دوباره می‌خواند و Version ID، Tag ID، State، Checkpoint revision، Operation، Cursor و Registration را تطبیق می‌دهد. Target کهنه، ناسازگار یا متعلق به جهت UP پیش از Side Effect رد می‌شود.

## Status و کار مجاز

| Status | ادامهٔ مجاز در Retry DOWN |
|---|---|
| `200` — `STATUS_ROLLBACK_STARTED` | فقط با Checkpoint معتبر و Phase قابل اجرا. `EXECUTION_PENDING`/`PAUSED_NEXT` اجرای DOWN را آغاز می‌کنند؛ `RECORD_PENDING` فقط Recording را انجام می‌دهد؛ `FINALIZATION_PENDING` فقط حذف نهایی Version را انجام می‌دهد. `EXECUTION_IN_FLIGHT` با Status `200` خودکار ادامه نمی‌یابد. |
| `201` — `STATUS_ROLLBACK_FAILED_DOWN` | پس از اعتبارسنجی Diagnostic و Checkpoint، CAS نرمال‌سازی باید `APPLIED` شود؛ سپس همان Migration جاری دوباره در مسیر DOWN اجرا می‌شود. تلاش قبلی ممکن است Schema را تغییر داده باشد. |
| `202` — `STATUS_ROLLBACK_FAILED_RECORD` | فقط Phaseهای `RECORD_PENDING` یا `FINALIZATION_PENDING` پذیرفته می‌شوند. Recording یا Finalization ادامه می‌یابد، بدون اجرای دوبارهٔ `down()` برای همان Migration. |
| `210` — `STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED` | رد می‌شود؛ Retry خودکار یا Reset ندارد. |
| سایر Statusها | رد می‌شود؛ از جمله Statusهای UP و مقادیر ناشناخته. |

در `RECORD_PENDING`، History/Tag با شواهد موجود Reconcile می‌شوند. اگر عملیات DOWN از قبل کامل شده باشد، helper مجاز است آن را بدون DML تأیید و Checkpoint را جلو ببرد؛ اما تناقض، رکورد تکراری یا Association اشتباه Fail می‌شود. در `FINALIZATION_PENDING`، دیگر Migration مشخصی برای اجرا یا Record وجود ندارد و فقط حذف شرطی Version مجاز است.

برای `201`، Diagnostic باید Failure اجرای DOWN همان Version را مشخص کند و Migration آن با Cursor/Registration هم‌خوان باشد. برای `202`، Stage تشخیصی باید Recording DOWN باشد؛ در Finalization مقدار `migrationId` باید `null` باشد. Checkpoint/Diagnostic خراب یا ناسازگار، Cursor ثبت‌نشده یا Version حذف‌شده باعث توقف می‌شود.

پیش از ادامه، Failure Status به Status شروع نرمال می‌شود و Checkpoint همان Snapshot با CAS شرطی نگه داشته می‌شود:

| ورودی | CAS نرمال‌سازی | پس از `APPLIED` |
|---|---|---|
| `201` + `EXECUTION_IN_FLIGHT` | `201 → 200` با همان Checkpoint | اجرای مجدد `down()` برای Cursor معتبر، سپس Recording. |
| `202` + `RECORD_PENDING` یا `FINALIZATION_PENDING` | `202 → 200` با همان Checkpoint | فقط Recording یا Finalization؛ بدون اجرای دوبارهٔ `down()`. |

اگر CAS نتیجهٔ `NOT_APPLIED`، `INDETERMINATE` یا Failure بدهد، Retry متوقف می‌شود و عمل بعدی شروع نمی‌شود. اگر Retry دوباره Failure بخورد، State تازه را با `state(MigrationVersionSelection.LATEST_INCOMPLETE)` بخوانید؛ Target قبلی ممکن است به‌دلیل تغییر State/Revision دیگر معتبر نباشد. Failureهای واقعی Reconciliation ممکن است به `210` برسند و در آن حالت Retry خودکار پذیرفته نمی‌شود.

## ترتیب و محدوده

Migrationهای DOWN با ترتیب معکوس Declaration اجرا می‌شوند.

- `toCompleteVersion = true`: فعالیت ذخیره‌شده را ادامه می‌دهد، Migrationهای باقی‌ماندهٔ همان Version را به ترتیب معکوس اجرا می‌کند و در پایان Version را حذف می‌کند.
- `toCompleteVersion = false`: یک فعالیت Cursor را کامل می‌کند و اگر Migration دیگری باقی مانده باشد، Cursor بعدی در ترتیب DOWN با `PAUSED_NEXT` ذخیره می‌شود و نتیجه `Success(false)` است. اگر Migration دیگری نمانده باشد، Finalization انجام می‌شود و `Success(true)` برمی‌گردد.
- در `FINALIZATION_PENDING`، Finalization حتی با `false` انجام می‌شود؛ توقفی پس از Migration وجود ندارد.

برای DOWN، موفقیت نهایی با Status جدید نمایش داده نمی‌شود: Version row حذف می‌شود. `Success(false)` یعنی توقف موفق در مرز بعدی، نه Failure. Failure عادی به شکل `ExecuteResult.Failure` برمی‌گردد و Fatal `Error` طبق قرارداد Operation منتشر می‌شود.

## نکتهٔ ایمنی دربارهٔ Status `201`

`retryDown()` برای Status `201` تلاش مجدد اجرای Schema را مجاز می‌کند؛ این کار ثابت نمی‌کند که اجرای قبلی هیچ اثری نگذاشته است. Checkpoint و Diagnostic هویت و جهت Failure را می‌سنجند، اما Schema دیتابیس را introspect یا validate نمی‌کنند. پیش از Retry بررسی کنید و ریسک تکرار `down()` را بپذیرید. `recovery()` فعلی نیز Status `201` را به همین مسیر می‌فرستد.

Transitionهای Checkpoint با CAS هستند و ادامه فقط پس از `APPLIED` انجام می‌شود. `NOT_APPLIED`، `INDETERMINATE` یا خطای Query متوقف‌کننده است؛ پس از آن Schema، Recording یا Finalization بعدی آغاز نمی‌شود.

## نمونهٔ فراخوانی مستقیم

```kotlin
db.migration {
    retryDown(target, toCompleteVersion = true)
}
```

Result بیرونی `db.migration { ... }` نتیجهٔ اجرای Block است و Result خود `retryDown()` درون آن قرار می‌گیرد (`ExecuteResult<ExecuteResult<Boolean>>`).

**Source مرتبط:** `MigrationFacade.retryDown()`، `MigrationRunOrchestrationService.retryDown()`/`retryLocked()`، `MigrationVersionStateService.validateTargetSnapshotWhileMigrationLockHeld()`، `MigrationRetryTargetValidator`، `MigrationRetryDiagnosticValidator` و `MigrationVersionTransactionService.retryDown()`.

