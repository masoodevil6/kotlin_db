# Acquire با Timeout

waitTimeout(Duration) فقط مدت‌زمان‌های متناهی با ثانیهٔ کامل و در بازهٔ ۱ تا ۳۰۰ را می‌پذیرد. از 30.seconds استفاده کنید؛ مقدار اعشاری مانند 1.5.seconds، مقدار منفی، صفر، بی‌نهایت و مقدار بیش از ۳۰۰ ثانیه رد می‌شوند. برای صفر ثانیه از noWait() استفاده کنید.

~~~kotlin
package sample.session

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import kotlin.time.Duration.Companion.seconds

fun acquireWithTimeout(database: DatabaseHandleApi): ExecuteResult<AcquireSessionLockResult> =
    database.withOperationScope { ctx ->
        ctx.session(
            acquireSessionAdvisoryLock {
                resource("application", "scheduled-job")
                waitTimeout(30.seconds)
            },
        ).execute()
    }
~~~

اگر دیتابیس نتیجهٔ صفر برگرداند، ExecuteResult موفق است و مقدار معنایی TimedOut خواهد بود. اگر اجرای JDBC با SQLTimeoutException یا خطای دیگری مواجه شود، Operation آن را به ExecuteResult.Failure تبدیل می‌کند؛ این دو نتیجه یکی نیستند.

این نمونه فقط نتیجهٔ Acquire را برمی‌گرداند. پس از بازگشت callback، Operation Scope تمام و Connection آن بسته می‌شود؛ بنابراین Lock حاصل از Acquired برای کار بیرونی نگه داشته نمی‌شود. در کد واقعی، کار محافظت‌شده و در صورت نیاز Release را داخل همان callback و از همان ctx اجرا کنید. برای نمونه‌ای که Release را در همان Scope می‌فرستد، [Acquire بدون انتظار](acquire-no-wait.md) را ببینید.
