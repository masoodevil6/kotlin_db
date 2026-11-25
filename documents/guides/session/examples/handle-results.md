# مدیریت Result و Exception

نتیجهٔ معنایی Session با Failure اجرایی تفاوت دارد. TimedOut یک پاسخ معتبر Acquire است و NotOwned یک پاسخ معتبر Release است؛ هیچ‌کدام به‌تنهایی Exception نیستند.

~~~kotlin
package sample.session

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import kotlin.time.Duration.Companion.seconds

fun acquireAndReport(database: DatabaseHandleApi): ExecuteResult<Unit> {
    val result: ExecuteResult<AcquireSessionLockResult> =
        database.withOperationScope { ctx ->
            ctx.session(
                acquireSessionAdvisoryLock {
                    resource("application", "cache-refresh")
                    waitTimeout(30.seconds)
                },
            ).execute()
        }

    return result
        .success { value ->
            when (value) {
                AcquireSessionLockResult.Acquired -> println("Lock acquired.")
                AcquireSessionLockResult.TimedOut -> println("The Lock was busy.")
            }
            Unit
        }
        .error { failure ->
            println("Session operation failed: " + failure.message)
        }
}
~~~

نوع value از نوع API ورودی استخراج می‌شود؛ Acquire نتیجهٔ Acquire می‌دهد و Release نتیجهٔ Release. callback مربوط به error یک Throwable می‌گیرد. متد success مقدار موفق را تبدیل می‌کند؛ متد error فقط Failure را مشاهده می‌کند و همان ExecuteResult را برمی‌گرداند.

خطاهای Renderer، پروتکل JDBC و اجرای SQL در مسیر Operation به ExecuteResult.Failure تبدیل می‌شوند. در سطح Session، انواع مشخص آن‌ها SessionRenderException، SessionProtocolException و SessionExecutionException هستند؛ SQLException علت SessionExecutionException را حفظ می‌کند. SQLTimeoutException خطای اجراست و TimedOut نیست.

اگر Scope به‌دلیل شکست قبلی Failure را latch کرده باشد، عملیات بعدی Session پیش از JDBC رد می‌شود. در مسیر generic API، Error به Failure تبدیل یا بلعیده نمی‌شود: Operation آن را fatal ثبت می‌کند و همان Error را دوباره پرتاب می‌کند.

callbackهای بالا نتیجه را پس از برگشت Scope گزارش می‌کنند. Connection در این زمان متعلق به Operation بوده و Scope آن را بسته است. اگر می‌خواهید تا زمانی که Lock گرفته شده کاری انجام دهید، آن کار و Release را داخل همان callback مدیریت‌شده انجام دهید؛ نمونهٔ [Operation integration](operation-integration.md) این ترتیب را نشان می‌دهد.
