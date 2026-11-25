# خواندن وضعیت Migration با `state()`

`state()` برای خواندن رکورد Version ذخیره‌شده و آماده‌کردن یک snapshot از آن است. این فرمان `up()` یا `down()` را اجرا نمی‌کند و Status، Checkpoint، History یا Tag را تغییر نمی‌دهد.

## انتخاب Version

برای API typed یکی از این سه فراخوانی را به کار ببرید:

```kotlin
state(MigrationVersionSelection.LATEST)
state(MigrationVersionSelection.LATEST_INCOMPLETE)
state(versionId)
```

| انتخاب | رفتار |
|---|---|
| `LATEST` | Version با بزرگ‌ترین ID ذخیره‌شده، بدون توجه به Status. |
| `LATEST_INCOMPLETE` | Version با بزرگ‌ترین ID که Status خام آن با Status تکمیل‌شده (`103`) برابر نیست. Statusهای ناشناخته نیز انتخاب می‌شوند. |
| `state(versionId)` | همان Version با ID مشخص؛ اگر وجود نداشته باشد، نتیجهٔ داخلی `Success(null)` است. |

اگر هیچ Version مطابق انتخاب پیدا نشود، State API نتیجهٔ داخلی `Success(null)` می‌دهد. خطای Query، قفل یا تبدیل نتیجه به `ExecuteResult.Failure` همان State command می‌رسد.

## مدل برگردانده‌شده

دو گروه overload وجود دارد:

```kotlin
fun state(selection: MigrationVersionSelection): ExecuteResult<MigrationRecoveryTarget?>
fun state(versionId: Int): ExecuteResult<MigrationRecoveryTarget?>

@Deprecated("Use state(MigrationVersionSelection) or state(versionId) for a typed recovery target")
fun state(lastIncompleteVersion: Boolean = false): ExecuteResult<MigrationVersionState?>
```

در `MigrationRecoveryTarget` این فیلدها در دسترس‌اند:

- `version.versionId` و `version.tagId`: شناسه‌های رکوردهای persisted.
- `version.status`: عدد خام Status دیتابیس؛ مقادیر ناشناخته بدون نگاشت حفظ می‌شوند.
- `version.message`: مقدار ذخیره‌شده در ستون `message`، بدون Parse یا تبدیل؛ ممکن است `null` یا قالب تشخیصی قدیمی باشد.
- `migration`: Migration ثبت‌شده در Configuration، فقط اگر از رابطهٔ persisted Version → Version Tag → Configuration به‌طور بی‌ابهام Resolve شود؛ در غیر این صورت `null` است.
- `operation`: طبقه‌بندی State/Checkpoint برای نشان‌دادن نوع وضعیت. این مقدار به‌تنهایی مجوز اجرا یا Retry نیست.
- `checkpointRevision`: Revision مربوط به Checkpoint معتبر در زمان خواندن، اگر وجود داشته باشد. متن کامل Checkpoint در مدل عمومی Target افشا نمی‌شود.

State Service Migration را حدس نمی‌زند. اگر Tag مفقود، تکراری، از نوع نادرست، متصل به Configuration ناموجود، یا دارای شناسهٔ مبهم باشد، `migration` ممکن است `null` بماند. برای Failureهای نهایی‌سازی نیز ممکن است Cursor نداشته باشد و Migration مشخصی قابل ارائه نباشد.

نمونهٔ بررسی Target آخرین Version ناقص:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection

val execution = database.migration {
    state(MigrationVersionSelection.LATEST_INCOMPLETE)
}

when (execution) {
    is ExecuteResult.Failure -> println("Operation failed: ${execution.exception}")
    is ExecuteResult.Success -> when (val command = execution.result) {
        null -> println("State command returned no result")
        is ExecuteResult.Failure -> println("State read failed: ${command.exception}")
        is ExecuteResult.Success -> {
            val target = command.result
            if (target == null) {
                println("No incomplete Version")
            } else {
                println("versionId=${target.version.versionId}")
                println("tagId=${target.version.tagId}")
                println("raw status=${target.version.status}")
                println("message=${target.version.message}")
                println("migrationId=${target.migration?.id?.value}")
                println("operation=${target.operation}")
                println("checkpointRevision=${target.checkpointRevision}")

                when (target.operation) {
                    MigrationRecoveryOperation.MIGRATE_DONE -> println("Version is completed")
                    MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED,
                    MigrationRecoveryOperation.DOWN_MANUAL_RECOVERY_REQUIRED,
                    MigrationRecoveryOperation.UNKNOWN -> println("Automatic action is not available")
                    else -> println("Inspect the raw status and use the matching supported command")
                }
            }
        }
    }
}
```

همانند سایر فرمان‌های Migration، `database.migration { ... }` نتیجهٔ Block را flatten نمی‌کند؛ در نمونه، نتیجهٔ اجرای Operation بیرونی و نتیجهٔ خود `state()` داخلی‌اند. برای گزارش Failure، هر دو را بررسی کنید.

## انتخاب Boolean قدیمی

Overload زیر برای سازگاری باقی مانده و Deprecated است:

```kotlin
state(lastIncompleteVersion = false) // آخرین Version با بیشترین ID
state(lastIncompleteVersion = true)  // آخرین Version با status != 103
```

این overload فقط `MigrationVersionState?` برمی‌گرداند و `operation` یا Migration Resolve‌شده در Target را ندارد. برای Retry یا تصمیم‌گیری مبتنی بر نوع عملیات، از `MigrationVersionSelection` استفاده کنید.

## State، Retry و تازگی Target

`MigrationRecoveryOperation` یک توصیف از State است، نه تضمین retryable بودن آن. برای نمونه، Status دستی، Status ناشناخته، Checkpoint خراب یا ترکیب ناسازگار Status/Checkpoint باید fail closed بماند. برای Retry فقط همان Target typed را که State API ساخته است به `retryUp()` یا `retryDown()` بدهید.

Target یک snapshot/selector است، نه مجوز اجرا. Retry زیر همان `SystemLock.MIGRATION`، Version، Tag، Status، Checkpoint، Revision، Migration ID و Configuration فعلی را دوباره بررسی می‌کند؛ Target قدیمی یا تغییریافته رد می‌شود. Target را دستی نسازید و با `copy()` ویرایش نکنید: `checkpointRevision` عمداً خارج از سازندهٔ اصلی Data Class است تا سازگاری قبلی حفظ شود، و `copy()` آن Revision را منتقل نمی‌کند.

برای اجرای ترکیبی بر اساس آخرین Version ناقص، API `recovery()` را ببینید؛ برای دیدن جزئیات یک رکورد یا انتخاب خودتان، `state()` مناسب است. راهنماهای مرتبط: [Status تحلیلی](../status/README.md)، [`retryUp()`](../retry_up/README.md)، [`retryDown()`](../retry_down/README.md) و [`recovery()`](../recovery/README.md).

## Snapshot و Lock

هر overload خواندن State قفل `SystemLock.MIGRATION` را با سازوکار موجود Operation می‌گیرد. Scope باید Managed root Connection Scope فعال، دارای `DatabaseIdentity` canonical و dialect پشتیبانی‌شدهٔ MySQL یا MariaDB باشد؛ روی همان Scope نیز نباید قفل session advisory دیگری فعال باشد. Scope ناسازگار از قرارداد Operation lock شکست می‌خورد و خواندن بدون قفل جایگزین آن نمی‌شود.

نتیجه یک snapshot از Version انتخاب‌شده و Tag/Configuration لازم برای ساخت Target در محدودهٔ Lock است. پس از برگشت، وضعیت دیتابیس می‌تواند با اجرای Migration دیگری تغییر کند؛ به همین دلیل Retry State را دوباره زیر قفل اعتبارسنجی می‌کند. خود `state()` هیچ Migration یا DML/DDL اجرا نمی‌کند.

## Source مرتبط

- APIهای Facade: `data_base/migration/src/main/kotlin/facade/MigrationFacade.kt`
- API و قرارداد Controller: `data_base/migration/executor/src/main/kotlin/controller/MigrationController.kt`
- انتخاب Version و خواندن Target: `data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionStateService.kt` و `MigrationVersionHistoryReader.kt`
- انتخاب‌ها و Status خام: `data_base/migration/api/src/main/kotlin/model/migration_state/MigrationVersionSelection.kt` و `MigrationVersionState.kt`
- مدل Target و طبقه‌بندی Operation: `data_base/migration/api/src/main/kotlin/model/migration_state/MigrationRecoveryTarget.kt` و `MigrationRecoveryOperation.kt`
- اعتبارسنجی مشترک Status/Checkpoint: `data_base/migration/executor/src/main/kotlin/service/version/MigrationCheckpointStatusValidator.kt`
- اعتبارسنجی Target تازه برای Retry: `data_base/migration/executor/src/main/kotlin/service/version/MigrationRetryTargetValidator.kt`
- نتیجهٔ بیرونی DSL: `data_base/migration/src/main/kotlin/facade/DatabaseHandleMigration.kt`
