# مثال‌های Session Advisory Lock

این راهنماها از API عمومی Session در Operation استفاده می‌کنند. مسیر معمول ساخت فرمان این است:

~~~kotlin
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock

fun requestLock(database: DatabaseHandleApi) =
    database.withOperationScope { ctx ->
        ctx.session(
            acquireSessionAdvisoryLock {
                resource("application", "cache-refresh")
                noWait()
            },
        ).execute()
    }
~~~

برای DatabaseHandle، ورودی فعلی Operation Scope یک extension عمومی است:

~~~kotlin
database.withOperationScope { ctx ->
    // ctx از نوع OperationExecutionScope است.
}
~~~

DatabaseHandleApi.connection { ... } در حال حاضر یک IQueryBuilderExecutor به callback می‌دهد، نه OperationExecutionContext؛ این API برای Query است. برای Session از withOperationScope استفاده کنید. این قابلیت در DatabaseHandle داخلی پشتیبانی می‌شود؛ پیاده‌سازی‌های سفارشی DatabaseHandleApi که Scope را ارائه نمی‌کنند، Failure برمی‌گردانند.

Scope و Connection را فقط برای مدت callback استفاده کنید. اگر لازم است پس از Acquire کاری انجام دهید یا Release صریح بفرستید، همان کارها باید پیش از خروج از callback و با همان ctx انجام شوند. نمونهٔ کامل در [استفاده از Session در Operation](operation-integration.md) است.

راهنماهای این پوشه:

1. [Acquire بدون انتظار](acquire-no-wait.md): NoWait، TimedOut و Release در همان Scope.
2. [Acquire با Timeout](acquire-with-timeout.md): Duration معتبر و تفاوت Timeout معنایی با خطای JDBC.
3. [Release](release-lock.md): Released و NotOwned و مالکیت Connection.
4. [مدیریت Result و Exception](handle-results.md): ExecuteResult، نتیجه‌های معنایی و خطاها.
5. [استفاده از Session در Operation](operation-integration.md): نمونهٔ end-to-end و محدودیت‌های Scope.

ماژول‌های Session core به Query و Schema وابسته نیستند. مصرف معمول از Builder و Operation API انجام می‌شود؛ SessionExecutor مستقیم فقط مسیر پیشرفته برای برنامه‌ای است که خودش JDBC Connection و Dialect را مدیریت می‌کند. جزئیات در [راهنمای Session Executor](../executor/README.md) آمده است.
