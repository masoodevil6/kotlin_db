# رویدادهای Operation

این راهنما نشان می‌دهد چطور چرخه‌ی Connection، Transaction و اجرای SQL را بدون دخالت در نتیجه‌ی عملیات مشاهده کنید. Eventها برای log، metrics، tracing و ابزارهای مانیتورینگ مناسب‌اند؛ جایگزین نتیجه‌ی `ExecuteResult` یا مسیر مدیریت Failure نیستند.

## روش معمول: ثبت Watch روی DatabaseHandle

برای برنامه‌ای که از Database Manager استفاده می‌کند، روی Handle یک Watch ثبت کنید. ثبت Watch خودش Connection باز نمی‌کند. همان listener رویدادهای Operationهای بعدی آن Handle را دریافت می‌کند:

```kotlin
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.watch
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType

val database: DatabaseHandleApi = manager[databaseKey]

val subscription = database.watch { event: DatabaseEvent ->
    val operationEvent = event as? OperationDatabaseEvent ?: return@watch

    when (operationEvent.type) {
        OperationDatabaseEventType.SQL_STARTED ->
            logger.debug("Operation SQL started: ${operationEvent.sql}")

        OperationDatabaseEventType.SQL_SUCCEEDED ->
            logger.debug("Operation SQL completed")

        OperationDatabaseEventType.SQL_FAILED -> {
            val failure = operationEvent.failure
            logger.warn(
                "Operation SQL failed: type=${failure?.typeName}, category=${failure?.category}",
            )
        }

        OperationDatabaseEventType.CONNECTION_OPENED -> logger.debug("Connection opened")
        OperationDatabaseEventType.CONNECTION_OPEN_FAILED -> logger.warn("Connection open failed")
        OperationDatabaseEventType.CONNECTION_CLOSED -> logger.debug("Connection closed")
        OperationDatabaseEventType.CONNECTION_CLOSE_FAILED -> logger.warn("Connection close failed")

        OperationDatabaseEventType.TRANSACTION_STARTED -> logger.debug("Transaction started")
        OperationDatabaseEventType.TRANSACTION_START_FAILED -> logger.warn("Transaction start failed")
        OperationDatabaseEventType.TRANSACTION_COMMITTED -> logger.debug("Transaction committed")
        OperationDatabaseEventType.TRANSACTION_COMMIT_FAILED -> logger.warn("Transaction commit failed")
        OperationDatabaseEventType.TRANSACTION_ROLLED_BACK -> logger.debug("Transaction rolled back")
        OperationDatabaseEventType.TRANSACTION_ROLLBACK_FAILED -> logger.warn("Transaction rollback failed")
    }
}

try {
    val result = database.connection { executor ->
        // QueryBuilder برنامه را با همین executor اجرا کنید.
        // هر اجرای SQL در این Scope، eventهای SQL_STARTED و SQL_SUCCEEDED/SQL_FAILED دارد.
        ExecuteResult.Success(Unit)
    }
    handleOperationResult(result)
} finally {
    subscription.close()
}
```

`watch` یک `DatabaseWatchSubscription` برمی‌گرداند. با `close()` فقط همان listener ثبت‌شده حذف می‌شود؛ listenerهای دیگر Handle باقی می‌مانند. بهتر است subscription را در `finally` ببندید تا در صورت خطا هم ثبت listener باقی نماند.

`DatabaseHandleApi.watch()` یک extension در پکیج `gog.my_project.data_base_manager.facade` است و باید import شود. این extension برای Handle مدیریت‌شده‌ی خود پروژه کار می‌کند؛ پیاده‌سازی سفارشی `DatabaseHandleApi` که قابلیت Watch را ارائه نکند، با `UnsupportedOperationException` رد می‌شود.

## اتصال مستقیم به OperationEventListener

در تست‌ها یا کدی که مستقیماً `OperationExecutionExecutor` می‌سازد، می‌توان `OperationEventListener` را به سازنده داد. این API نوع دقیق‌تری از event می‌دهد و در `SQL_FAILED`، خود `Throwable` را در `OperationEvent.error` قرار می‌دهد:

```kotlin
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_operation.executor.events.OperationEventType
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor

val operation = OperationExecutionExecutor(databaseContext, OperationEventListener { event ->
    when (event.type) {
        OperationEventType.SQL_STARTED -> logger.debug("SQL: ${event.sql}")
        OperationEventType.SQL_FAILED -> logger.warn(
            "SQL failed: ${event.error?.javaClass?.name}",
        )
        else -> Unit
    }
})

val result = operation.connection { scope ->
    scope.executeRead("SELECT 1 AS value") { rows ->
        if (rows.next()) rows.getInt("value") else null
    }
}
```

در برنامه‌های مبتنی بر Manager، معمولاً `DatabaseHandle.watch()` انتخاب مناسب‌تری است: Watch به‌جای Throwable خام، فقط نوع خطا و دسته‌ی آن را ارائه می‌کند. اتصال مستقیم به `OperationEventListener` بیشتر برای تست، instrumentation داخلی یا مصرف‌کننده‌ای مناسب است که دسترسی به Throwable را آگاهانه مدیریت می‌کند.

## انواع رویداد

| گروه | Eventها | چه زمانی منتشر می‌شوند |
|---|---|---|
| Connection | `CONNECTION_OPENED`, `CONNECTION_OPEN_FAILED`, `CONNECTION_CLOSED`, `CONNECTION_CLOSE_FAILED` | پس از بازشدن موفق Connection، هنگام شکست بازکردن، یا پس از موفق/ناموفق بودن بستن آن. |
| SQL | `SQL_STARTED`, `SQL_SUCCEEDED`, `SQL_FAILED` | پیرامون اجرای SELECT، INSERT، UPDATE، DELETE و DDL در Scope. شکست می‌تواند نتیجه‌ی `Failure`، Exception، Error یا خطای مصرف نتیجه و پاک‌سازی منابع باشد. |
| Transaction | `TRANSACTION_STARTED`, `TRANSACTION_START_FAILED`, `TRANSACTION_COMMITTED`, `TRANSACTION_COMMIT_FAILED`, `TRANSACTION_ROLLED_BACK`, `TRANSACTION_ROLLBACK_FAILED` | بعد از تغییر موفق Connection به حالت Transaction، یا هنگام شکست شروع، commit یا rollback. |

رویداد SQL فقط لحظه‌ی ارسال دستور به JDBC را نشان نمی‌دهد. برای خواندن‌هایی که callback مصرف row دارند، `SQL_SUCCEEDED` پس از پایان callback و پاک‌سازی منابع آن فراخوانی می‌شود. اگر callback یا پاک‌سازی شکست بخورد، نتیجه می‌تواند `SQL_FAILED` باشد.

در یک `connection {}` معمولی، رویدادهای باز و بسته‌شدن Connection منتشر می‌شوند. رویدادهای Transaction وقتی منتشر می‌شوند که مسیر Transaction اجرا شود؛ در Transaction فرزند، Connection والد استفاده می‌شود و برای فرزند Connection جدیدی باز یا بسته نمی‌شود.

ترتیب eventهای یک Operation ترتیب واقعی انتشار آن‌ها در همان اجراست. برای مثال ممکن است این توالی را ببینید:

```text
CONNECTION_OPENED
SQL_STARTED
SQL_SUCCEEDED
TRANSACTION_STARTED
SQL_STARTED
SQL_FAILED
TRANSACTION_ROLLED_BACK
CONNECTION_CLOSED
```

این فقط یک نمونه است؛ شکست در بازکردن Connection، شروع Transaction، commit، rollback یا cleanup توالی متفاوتی ایجاد می‌کند. برای مثال، اگر بازکردن Connection شکست بخورد، `CONNECTION_OPEN_FAILED` می‌آید و لزوماً eventی برای بستن Connection وجود ندارد.

## اطلاعات Event و حریم داده

`OperationDatabaseEvent` در مسیر Watch این فیلدها را دارد:

- `type`: نوع event.
- `sql`: متن SQL ارسالی به backend، در صورت وجود.
- `failure`: metadata امن خطا شامل `typeName` و `category`؛ متن خطا و خود Throwable را ندارد.

`OperationEvent` در listener مستقیم فیلد `error: Throwable?` دارد. این Throwable ممکن است متن یا جزئیات حساسِ driver را حمل کند؛ پیش از log کردن خود Throwable یا `message` آن، سیاست redaction برنامه را اعمال کنید.

مقادیر bind‌شده در SQL جایگذاری نمی‌شوند و از Event ارسال نمی‌شوند؛ بنابراین Queryهای پارامتری معمولاً `?` یا placeholderها را نشان می‌دهند. با این حال `sql` همیشه محرمانه‌زدایی‌شده نیست: اگر برنامه مقدار حساس را مستقیماً به صورت literal داخل رشته‌ی SQL بنویسد، همان متن ممکن است در event دیده شود. اطلاعات محرمانه را در SQL literal قرار ندهید.

## زمان اجرا و ایمنی Listener

- Eventها به‌صورت synchronous و در مسیر همان Operation منتشر می‌شوند؛ listener باید سریع باشد و کار طولانی را به صف یا executor خودش بسپارد.
- شکست listener، حتی `Error`، توسط مسیر انتشار Event گرفته می‌شود و نباید نتیجه‌ی Query، Failure اصلی یا cleanup را تغییر دهد. بنابراین از پرتاب Exception برای کنترل یا لغو Operation استفاده نکنید.
- Listener فقط برای مشاهده است. برای نتیجه‌ی واقعی Query یا Operation، `ExecuteResult` را بررسی کنید؛ رسیدن `SQL_SUCCEEDED` به معنی موفقیت کل کار سطح برنامه نیست.
- Handle پیش از شروع هر Operation فهرست listenerهای Watch را snapshot می‌گیرد. ثبت یا بستن subscription روی Operationهای بعدی اثر می‌گذارد؛ Operation در حال اجرا ممکن است listener snapshot قبلی خود را تا پایان نگه دارد.
- چند Operation روی یک Handle می‌توانند هم‌زمان event منتشر کنند. listener مشترک باید thread-safe باشد.
- Eventها شناسه‌ی Operation، timestamp یا شماره‌ی ترتیبی سراسری ندارند. هنگام هم‌زمانی، صرفاً با چسباندن یک stream مشترک نمی‌توان eventهای چند Operation را به‌طور قطعی به هم نسبت داد.

## Eventهای Domain

`DatabaseHandle.watch()` علاوه بر eventهای Operation، هر `DatabaseEvent` دامنه‌ای را هم که یک Integration از Scope فعال منتشر کند دریافت می‌کند. مصرف‌کننده باید نوع event را بررسی کند و eventهای ناشناخته را نادیده بگیرد یا جداگانه پردازش کند. `OperationEventListener` مستقیم فقط eventهای Operation را می‌گیرد؛ adapter عمومی `OperationEventListener.fromDatabaseEventListener(...)` مسیر سازگار برای انتقال DatabaseEventهای دامنه‌ای را فراهم می‌کند.

## انتخاب مسیر مناسب

| نیاز | مسیر پیشنهادی |
|---|---|
| مشاهده‌ی امن عملیات یک DatabaseHandle در برنامه | `databaseHandle.watch { ... }` |
| مشاهده‌ی نوع خطا بدون دریافت پیام یا Throwable خام | `OperationDatabaseEvent.failure` از Watch |
| مشاهده‌ی مستقیم Error/Throwable برای تست یا instrumentation کنترل‌شده | `OperationExecutionExecutor(..., OperationEventListener { ... })` |
| تصمیم‌گیری درباره موفقیت، شکست، commit یا توقف عملیات | نتیجه‌ی `ExecuteResult` و قرارداد Operation؛ نه Event listener |

## منبع قرارداد

این رفتار از `OperationEvent`, `OperationDatabaseEvent`, `OperationExecutionScope`, `OperationExecutionExecutor`, `OperationTransactionExecutor` و `DatabaseHandle.watch()` پیروی می‌کند. تست‌های `OperationEventsTest` ترتیب، پوشاندن مقادیر bind‌شده، انتقال خطا، ایزوله‌ماندن listener و lifecycle رویدادهای Transaction/Connection را بررسی می‌کنند.
