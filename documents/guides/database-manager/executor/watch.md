# پایش عملیات با Watch

`DatabaseHandle.watch()` رویدادهای Operationهایی را که از یک Handle مدیریت‌شده اجرا می‌شوند در اختیار برنامه می‌گذارد. از آن برای ثبت Log، شمارش رخدادها، Metrics یا مشاهدهٔ چرخهٔ Connection و Transaction استفاده کنید. Watch نتیجهٔ عملیات را تغییر نمی‌دهد و جایگزین بررسی `ExecuteResult` نیست.

## ثبت Watch

`watch` یک extension روی `DatabaseHandleApi` است؛ بنابراین باید آن را import کنید. ثبت Listener به‌تنهایی Connection باز نمی‌کند. Listener فقط رویدادهای عملیات بعدی همان Handle را می‌بیند:

شاخهٔ `MigrationEvent` در نمونه برای برنامه‌ای است که ماژول Migration را هم دارد؛ اگر فقط از Manager و Operation استفاده می‌کنید، importهای Migration و آن شاخه را حذف کنید.

```kotlin
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.watch
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType
import gog.my_project.data_base_migration.core.events.MigrationEvent
import gog.my_project.data_base_migration.core.events.MigrationEventType

val database: DatabaseHandleApi = manager[MainDatabase]

val subscription = database.watch { event: DatabaseEvent ->
    when (event) {
        is OperationDatabaseEvent -> when (event.type) {
            OperationDatabaseEventType.CONNECTION_OPENED -> logger.debug("Connection opened")
            OperationDatabaseEventType.CONNECTION_CLOSED -> logger.debug("Connection closed")
            OperationDatabaseEventType.SQL_STARTED -> logger.debug("SQL started: ${event.sql}")
            OperationDatabaseEventType.SQL_SUCCEEDED -> logger.debug("SQL completed")
            OperationDatabaseEventType.SQL_FAILED -> logger.warn(
                "SQL failed: ${event.failure?.typeName}, ${event.failure?.category}",
            )
            else -> Unit
        }

        is MigrationEvent -> when (event.type) {
            MigrationEventType.MIGRATION_STARTED -> logger.info("${event.command} started")
            MigrationEventType.MIGRATION_VERSION_STARTED ->
                logger.info("Version started: ${event.versionName}")
            MigrationEventType.MIGRATION_VERSION_APPLIED ->
                logger.info("Version applied: ${event.versionName}")
            MigrationEventType.MIGRATION_VERSION_ROLLED_BACK ->
                logger.info("Version rolled back: ${event.versionName}")
            MigrationEventType.MIGRATION_COMPLETED -> logger.info("${event.command} completed")
            MigrationEventType.MIGRATION_FAILED -> logger.warn(
                "${event.command} failed: ${event.failure?.typeName}",
            )
        }

        else -> Unit // برای Eventهای جدید یا متعلق به دامنه‌های دیگر
    }
}

try {
    val result = database.connection { executor ->
        runApplicationQueries(executor) // Queryهای برنامه با همین executor اجرا شوند.
    }
    handleOperationResult(result) // موفقیت یا Failure را جداگانه بررسی کنید.
} finally {
    subscription.close()
}
```

`runApplicationQueries` و `handleOperationResult` در این مثال تابع‌های برنامه هستند. هر Query که از executor همین callback اجرا شود، رویدادهای SQL مربوط به خود را منتشر می‌کند. اگر فقط رویدادهای Connection را می‌خواهید، بدنهٔ callback می‌تواند کار دیگری انجام دهد؛ باز و بسته‌شدن Connection همچنان قابل مشاهده است.

## چرخهٔ Subscription

هر فراخوانی `watch` یک `DatabaseWatchSubscription` می‌دهد. آن را در محدوده‌ای نگه دارید که واقعاً به پایش نیاز دارید و در پایان با `close()` ببندید؛ بستن Subscription فقط همان Listener را حذف می‌کند. Subscriptionهای دیگر روی Handle فعال می‌مانند. `close()` تکراری بی‌خطر است.

```kotlin
val subscription = database.watch { event -> record(event) }
try {
    runWorkForThisPeriod()
} finally {
    subscription.close()
}
```

Handle، Listenerهای ثبت‌شده را پیش از آغاز هر Operation snapshot می‌گیرد. بنابراین بستن Watch مانع دریافت رویدادهای Operationهایی می‌شود که بعداً شروع شوند؛ یک Operation که قبلاً snapshot گرفته ممکن است تا پایان همچنان همان Listener را صدا بزند.

## چه اطلاعاتی در دسترس است؟

`OperationDatabaseEvent` چرخهٔ Connection، اجرای SQL و Transaction را با `OperationDatabaseEventType` نشان می‌دهد. در رخدادهای SQL، فیلد `sql` متن statement ارسالی به backend را دارد و در صورت وجود از placeholderهای JDBC استفاده می‌کند؛ مقادیر bindشده در Event قرار نمی‌گیرند. فیلد `failure` فقط نام نوع خطا و دستهٔ آن (`EXCEPTION`، `ERROR` یا `THROWABLE`) را دارد؛ خود Throwable و متن پیام خطا ارائه نمی‌شود.

رویدادهای Migration مانند شروع فرمان، شروع یا پایان Version و شکست فرمان نیز از همان Watch قابل دریافت‌اند. فیلد `failure` در این رویدادها نیز فقط metadata امن خطا را نگه می‌دارد. برای فهرست کامل نوع‌ها و ترتیب معمول رخدادها، [راهنمای رویدادهای Operation](../../operation/events/README.md) را ببینید.

## محدودیت‌ها و مدیریت خطا

- Watch فقط رویدادهای همان Handle را مشاهده می‌کند؛ Listener یک Handle دیگر را دریافت نمی‌کند.
- Listener هم‌زمان و در مسیر انتشار Event فراخوانی می‌شود. کار سنگین یا مسدودکننده داخل آن می‌تواند اجرای همان Operation را کند کند؛ برای پردازش طولانی، Event را سریع به صف برنامه بسپارید.
- خطای Listener، حتی `Error`، از مسیر مشاهده‌گر مهار می‌شود تا Observer نتیجهٔ Operation، cleanup یا Observerهای دیگر را تغییر ندهد. به همین دلیل خطای ثبت Log را از نتیجهٔ `ExecuteResult` تشخیص دهید و Failure عملیات را با Watch مدیریت نکنید.
- نتیجهٔ اصلی را همیشه از `ExecuteResult` بگیرید. رویداد `SQL_FAILED` نشانهٔ پایش است و به‌تنهایی قرارداد نتیجهٔ Operation را جایگزین نمی‌کند.
- extension عمومی فقط روی `DatabaseHandle` مدیریت‌شدهٔ این Manager کار می‌کند. اگر یک پیاده‌سازی سفارشی `DatabaseHandleApi` قابلیت Watch را عرضه نکند، فراخوانی `watch` با `UnsupportedOperationException` رد می‌شود.
- محدودیت سایر extensionها برای Handleهای سفارشی در بخش [سازگاری Handleهای سفارشی](README.md) آمده است.
- Watch Connection، Scope یا JDBC خام را به Listener نمی‌دهد و برای اجرای Query یا تغییر نتیجهٔ Operation نیست.

## وابستگی و مسیرهای مرتبط

برای گرفتن Handle و اجرای Connection یا Transaction، [راهنمای Executor](README.md) را ببینید. اگر Listener سطح پایین‌تر Operation لازم دارید، [راهنمای رویدادهای Operation](../../operation/events/README.md) تفاوت آن API را توضیح می‌دهد.
