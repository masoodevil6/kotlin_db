# رویدادهای Operation و Migration

این راهنما قرارداد فعلی رویدادهای قابل مشاهده از یک `DatabaseHandle` را توضیح می‌دهد. `DatabaseEvent` قرارداد مشترک Operation و Migration است و Watch همان رویدادها را به‌صورت همگام به listener می‌رساند. Watch یک Event Bus مستقل، صف، تاریخچه یا ذخیره‌سازی پایدار نیست؛ callbackهای آن در مسیر انتشار اجرا می‌شوند.

## ثبت Watch روی Handle

`DatabaseHandleApi.watch` یک extension است و برای Handle مدیریت‌شده کار می‌کند. ثبت آن Connection باز نمی‌کند. رویدادها از Operationهای بعدی همان Handle می‌آیند؛ در نتیجه رویدادهای Operation Lock و Migration نیز در همان Watch قابل دریافت‌اند. چون `DatabaseEvent` یک قرارداد باز است، نوع هر رویداد را بررسی کنید و فرض نکنید همه رویدادها `OperationDatabaseEvent` هستند.

`watch` یک `DatabaseWatchSubscription` می‌دهد؛ `close()` فقط همان listener را حذف می‌کند. پیاده‌سازی سفارشی `DatabaseHandleApi` که capability داخلی Handle مدیریت‌شده را ندارد، با `UnsupportedOperationException` رد می‌شود. Handle پیش از شروع Operation از listenerهای ثبت‌شده snapshot می‌گیرد، بنابراین بستن Subscription روی Operationهای بعدی اثر می‌گذارد.

نمونهٔ زیر فرض می‌کند `manager` از قبل ساخته شده و `key` به یک Registration با Migration configuration اشاره می‌کند. این تابع مالک lifecycle همان Manager است و Subscription را قبل از Shutdown می‌بندد:

```kotlin
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.watch
import gog.my_project.data_base_migration.core.events.MigrationEvent
import gog.my_project.data_base_migration.core.events.MigrationFailurePersistenceEvent
import gog.my_project.data_base_migration.facade.migration
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationLockEvent

fun runWatchedMigration(manager: DatabaseManagerApi, key: DatabaseKey) {
    try {
        val database: DatabaseHandleApi = manager[key]
        val subscription = database.watch { event: DatabaseEvent ->
            when (event) {
                is OperationDatabaseEvent -> {
                    val failure = event.failure?.let { "${it.typeName}/${it.category}" }
                    println("operation type=${event.type}, id=${event.operationId}, failure=$failure")
                }

                is OperationLockEvent -> {
                    val failure = event.failure?.let { "${it.typeName}/${it.category}" }
                    println(
                        "lock type=${event.type}, operation=${event.operationId}, " +
                            "attempt=${event.lockAttemptId}, resource=${event.namespace}/${event.name}, " +
                            "cleanupAfterUnknownAcquisition=${event.cleanupAfterUnknownAcquisition}, " +
                            "failure=$failure",
                    )
                }

                is MigrationFailurePersistenceEvent -> {
                    val failure = event.failure?.let { "${it.typeName}/${it.category}" }
                    println(
                        "migration persistence operation=${event.operationId}, version=${event.versionId}, " +
                            "mode=${event.writeMode}, status=${event.statusOutcome}, " +
                            "checkpoint=${event.checkpointOutcome}, failure=$failure",
                    )
                }

                is MigrationEvent -> {
                    val failure = event.failure?.let { "${it.typeName}/${it.category}" }
                    println(
                        "migration type=${event.type}, command=${event.command}, " +
                            "operation=${event.operationId}, version=${event.versionName}, " +
                            "target=${event.targetVersionName}, failure=$failure",
                    )
                }

                else -> println("unhandled DatabaseEvent: ${event::class.qualifiedName}")
            }
        }

        try {
            when (val operationResult = database.migration { migrate() }) {
                is ExecuteResult.Failure ->
                    println("Operation failed: ${operationResult.exception::class.simpleName}")

                is ExecuteResult.Success -> when (val migrationResult = operationResult.result) {
                    null -> println("Migration command returned no result")
                    is ExecuteResult.Failure ->
                        println("Migration failed: ${migrationResult.exception::class.simpleName}")

                    is ExecuteResult.Success ->
                        println("Migration command result: ${migrationResult.result}")
                }
            }
        } finally {
            subscription.close()
        }
    } finally {
        manager.shutdown()
    }
}
```

در این API نتیجهٔ `database.migration { migrate() }` تو در تو است: نتیجهٔ بیرونی مربوط به اجرای Operation است و در صورت موفقیت، مقدار داخلی نتیجهٔ دستور Migration است. رویدادها برای مشاهده‌اند و جایگزین بررسی هر دو نتیجه نیستند. نمونه فقط نوع خطا و دستهٔ امن آن را چاپ می‌کند؛ `failure` پیام خطا یا خود `Throwable` را نگه نمی‌دارد.

Watch از listenerهایی که هنگام شروع Operation ثبت بوده‌اند snapshot می‌گیرد. انتشار همگام است؛ بنابراین callback باید کوتاه باشد. خطای listener، از جمله `Error`، از رفتار Operation و cleanup جدا نگه داشته می‌شود. چند Operation هم‌زمان روی یک Handle ممکن است رویدادها را در stream مشترک interleave کنند؛ listener مشترک باید thread-safe باشد.

## Eventهای Operation و هویت اجرا

`OperationDatabaseEvent` نمایش امن رویدادهای Operation برای Watch است. `type` یکی از رویدادهای Connection، SQL یا Transaction است؛ `sql` در صورت وجود متن statement پارامتری را دارد و مقادیر bind‌شده را شامل نمی‌شود؛ `failure` فقط `typeName` و `category` را دارد. اگر برنامه مقدار حساس را مستقیماً به شکل literal در SQL قرار دهد، متن آن ممکن است در `sql` دیده شود.

هر Root Operation پذیرفته‌شده و مدیریت‌شده یک `operationId` می‌گیرد؛ ID پس از پذیرش توسط Coordinator و پیش از آماده‌سازی Pipeline یا بازکردن Connection ساخته می‌شود. همان شناسه از رویدادهای بازشدن تا بسته‌شدن Connection، رویدادهای SQL و Transaction، Stageهای Pipeline و Child Transaction حفظ می‌شود. Root Operationهای مستقل حتی روی یک Handle، IDهای جدا دارند. Scopeهایی که خارج از این lifecycle ساخته شده‌اند ممکن است `operationId == null` داشته باشند.

`operationId` شناسهٔ یک اجرای Operation است؛ با `DatabaseIdentity` که در تعیین هویت دیتابیس برای lock به‌کار می‌رود، و با `lockAttemptId` که یک تلاش مشخص برای گرفتن Lock را مشخص می‌کند، تفاوت دارد. `OperationEvent` برای listener مستقیم Operation نیز `operationId` دارد، اما `OperationEvent` خودش `DatabaseEvent` نیست و از Watch به شکل `OperationDatabaseEvent` تبدیل‌شده دریافت می‌شود.

رویدادهای SQL مرز اجرای SQL در Scope را گزارش می‌کنند. برای queryهایی که callback مصرف row دارند، `SQL_SUCCEEDED` پس از پایان callback و پاک‌سازی منابع صادر می‌شود. فرمان‌های داخلی Lock نیز ممکن است `SQL_STARTED`/`SQL_SUCCEEDED` بدون متن SQL ایجاد کنند. موفقیت اجرای SQL به‌تنهایی معنای موفقیت Lock یا کل Operation را ندارد.

## رویدادهای معنایی Operation Lock

`OperationLockEvent` نتیجهٔ معنایی bracket مربوط به Lock را نشان می‌دهد. نوع‌های فعلی آن عبارت‌اند از:

| Event | معنا |
|---|---|
| `LOCK_ACQUISITION_STARTED` | تلاش برای گرفتن Lock آغاز شد. |
| `LOCK_ACQUIRED` | Backend تأیید کرد Lock گرفته شده و callback می‌تواند اجرا شود. |
| `LOCK_TIMED_OUT` | انتظار به پایان رسید و callback اجرا نشد. ممکن است فرمان SQL از نظر اجرا موفق بوده باشد، در حالی که نتیجهٔ معنایی گرفتن Lock، Timeout بوده است. |
| `LOCK_ACQUISITION_FAILED` | Backend نتیجهٔ قطعی شکست Acquisition را گزارش کرد. |
| `LOCK_ACQUISITION_UNKNOWN` | پس از خطای اجرا/ارتباط، معلوم نیست Backend Lock را گرفته است یا نه. |
| `LOCK_RELEASE_STARTED` | تلاش برای Release آغاز شد. |
| `LOCK_RELEASED` | Backend Release را تأیید کرد. |
| `LOCK_NOT_OWNED` | Backend اعلام کرد این Connection مالک Lock نیست. |
| `LOCK_RELEASE_FAILED` | Release با شکست قطعی گزارش شد. |
| `LOCK_RELEASE_UNKNOWN` | پس از خطا، نتیجهٔ Release نامشخص است. |

فیلدهای `namespace` و `name` منبع منطقی Lock را گزارش می‌کنند، نه نام نهایی backend که Operation از `DatabaseIdentity` می‌سازد. `lockAttemptId` رویدادهای یک bracket را به هم مرتبط می‌کند. `failure` اطلاعات امن شکست است. `cleanupAfterUnknownAcquisition` نشان می‌دهد Release بخشی از پاک‌سازی best-effort پس از Acquisition نامشخص است.

در `LOCK_ACQUISITION_UNKNOWN`، Operation ابتدا Failure را ثبت می‌کند و سپس Release best-effort را با همان `lockAttemptId` انجام می‌دهد. رویدادهای Release در این پاک‌سازی مقدار `cleanupAfterUnknownAcquisition = true` دارند. حتی `LOCK_RELEASED` در این مسیر ثابت نمی‌کند Acquisition اولیه موفق بوده؛ Connection پس از آن برای discard علامت‌گذاری می‌شود. Failure اصلی حفظ می‌شود و شکست cleanup به آن افزوده می‌شود. در Release معمولی پس از callback نیز Failure/Unknown باعث درخواست discard می‌شود.

سه نتیجه را از هم جدا نگه دارید:

- `SQL_SUCCEEDED` یعنی فراخوانی SQL و پاک‌سازی مربوط به آن در مرز SQL موفق بوده است.
- `LOCK_ACQUIRED`, `LOCK_TIMED_OUT`, `LOCK_RELEASED` و سایر Lock Eventها نتیجهٔ معنایی backend را گزارش می‌کنند.
- `ExecuteResult` و `OperationLockResult` نتیجهٔ قابل‌کنترل فراخواننده‌اند؛ Eventها آن را جایگزین نمی‌کنند.

`withOperationLock` نتیجهٔ `ExecuteResult<OperationLockResult<T>>` می‌دهد: Acquisition موفق مقدار `Acquired` دارد، Timeout مقدار `TimedOut` و شکست اجرای bracket مقدار `Failure`. Callback فقط پس از تأیید Acquisition اجرا می‌شود. wrapper مربوط به Migration، `TimedOut` را به `TimeoutException` در `ExecuteResult.Failure` تبدیل می‌کند.

## Migration و Failure Persistence

`MigrationEvent` گزارش lifecycle فرمان Migration است: `MIGRATION_STARTED`, `MIGRATION_VERSION_STARTED`, `MIGRATION_VERSION_APPLIED`, `MIGRATION_VERSION_ROLLED_BACK`, `MIGRATION_COMPLETED` و `MIGRATION_FAILED`. این رویدادها از Scope فعال منتشر می‌شوند و `operationId` همان Root Operation را دارند؛ برای همین از همان `database.watch` دریافت می‌شوند.

`MigrationFailurePersistenceEvent` نتیجهٔ تلاش برای ثبت وضعیت شکست یک Version را گزارش می‌کند:

- `STATUS_ONLY` یعنی فقط Status درخواست شده؛ در این حالت `checkpointOutcome` برابر `NOT_ATTEMPTED` است.
- `STATUS_CHECKPOINT_CAS` یعنی Status و Checkpoint با یک CAS درخواست شده‌اند.
- `APPLIED` یعنی capability دقیقاً یک row متاثر گزارش کرده است؛ `NOT_APPLIED` یعنی هیچ rowی متاثر نشده؛ `INDETERMINATE` یعنی نتیجهٔ اجرا یا تعداد rowها قطعی نیست؛ `NOT_ATTEMPTED` یعنی آن بخش اصلاً درخواست نشده است. `APPLIED` تضمین Commit نهایی یا Durability نیست.

در مسیر CAS، نتیجهٔ نوشتن ترکیبی به هر دو فیلد outcome نسبت داده می‌شود؛ در مسیر status-only، Checkpoint تلاش نمی‌شود. اگر ساخت diagnostic یا Checkpoint ممکن نباشد، مسیر می‌تواند به status-only برگردد. اگر خود نوشتن خطا دهد، event نتیجه را `INDETERMINATE` گزارش می‌کند. Event هرگز به‌تنهایی تضمین دوام داده نمی‌دهد.

برای شکست یک Version که مسیر ثبت Failure Status را طی می‌کند و Release آن با موفقیت تأیید می‌شود، ترتیب مشاهده در یک Migration مدیریت‌شده چنین است:

```text
MIGRATION_STARTED
... Migration Version events ...
MigrationFailurePersistenceEvent
LOCK_RELEASE_STARTED
LOCK_RELEASED
MIGRATION_FAILED
CONNECTION_CLOSED
```

`MIGRATION_STARTED` پیش از گرفتن Lock منتشر می‌شود. Event ثبت Failure در زمان اجرای عملیات persistence و پیش از Release منتشر می‌شود؛ Release در bracket مربوط به Operation انجام می‌شود؛ `MIGRATION_FAILED` پس از بازگشت bracket و بسته‌شدن Connection پس از خروج از Root Operation منتشر می‌شود. وجود `MigrationFailurePersistenceEvent` به مسیری بستگی دارد که ثبت Status/Checkpoint را انجام می‌دهد؛ برای مثال Timeout در Acquisition یا خطای کشنده الزاماً رویداد persistence تولید نمی‌کند. ترتیب بالا ترتیب اجرای این مسیر است، نه تضمین ذخیره‌سازی پایدار Eventها.

اگر Release نتیجهٔ دیگری داشته باشد، Event پایانی Lock به‌جای `LOCK_RELEASED` می‌تواند `LOCK_NOT_OWNED`، `LOCK_RELEASE_FAILED` یا `LOCK_RELEASE_UNKNOWN` باشد؛ Migration failure همچنان پس از پایان bracket گزارش می‌شود.

## Listener مستقیم Operation

کدی که خودش `OperationExecutionExecutor` می‌سازد می‌تواند `OperationEventListener` دریافت کند. این قرارداد فقط رویدادهای Operation را می‌دهد و در `SQL_FAILED` ممکن است `Throwable` خام داشته باشد؛ متن یا خود آن را بدون redaction ثبت نکنید. برای دریافت Eventهای دامنه‌ای از Migration و Lock در Manager-managed flow، از `DatabaseHandleApi.watch` استفاده کنید.

## قراردادهای Source

- [قرارداد مشترک Eventها و Watch](../../../../data_base/core/src/main/kotlin/events/DatabaseEvent.kt)
- [ثبت Watch روی Handle و snapshot کردن listenerها](../../../../data_base/manager/src/main/kotlin/facade/DatabaseHandle.kt) و [extension عمومی `watch`](../../../../data_base/manager/src/main/kotlin/facade/DatabaseWatch.kt)
- [OperationEvent و adapter به DatabaseEvent](../../../../data_base/operation/executor/src/main/kotlin/events/OperationEvent.kt)، [OperationDatabaseEvent](../../../../data_base/operation/executor/src/main/kotlin/events/OperationDatabaseEvent.kt)، [OperationLockEvent](../../../../data_base/operation/executor/src/main/kotlin/events/OperationLockEvent.kt)، [OperationId](../../../../data_base/operation/executor/src/main/kotlin/transaction/OperationId.kt)، [OperationExecutionScope](../../../../data_base/operation/executor/src/main/kotlin/transaction/OperationExecutionScope.kt)، [Connection Executor](../../../../data_base/operation/executor/src/main/kotlin/transaction/OperationExecutionExecutor.kt) و [Transaction Executor](../../../../data_base/operation/executor/src/main/kotlin/transaction/OperationTransactionExecutor.kt)
- [MigrationEvent](../../../../data_base/migration/core/src/main/kotlin/events/MigrationEvent.kt)، [MigrationFailurePersistenceEvent](../../../../data_base/migration/core/src/main/kotlin/events/MigrationFailurePersistenceEvent.kt)، [انتشار رویدادهای Migration و Lock](../../../../data_base/migration/executor/src/main/kotlin/service/run/MigrationRunOrchestrationService.kt)، [ثبت Failure Status و Checkpoint](../../../../data_base/migration/executor/src/main/kotlin/service/version/MigrationVersionTransactionService.kt) و [API عمومی Migration روی Handle](../../../../data_base/migration/src/main/kotlin/facade/DatabaseHandleMigration.kt)
