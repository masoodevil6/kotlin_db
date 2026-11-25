# Acquire بدون انتظار

متد noWait() مقدار انتظار را صفر ثانیه می‌گذارد. اگر Lock در دسترس نباشد، نتیجهٔ معنایی Acquire برابر TimedOut است؛ این نتیجه خطای JDBC نیست.

~~~kotlin
package sample.session

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock

fun acquireWithoutWaiting(database: DatabaseHandleApi): ExecuteResult<Unit> =
    database.withOperationScope { ctx ->
        when (
            val acquired = ctx.session(
                acquireSessionAdvisoryLock {
                    resource("application", "cache-refresh")
                    noWait()
                },
            ).execute()
        ) {
            is ExecuteResult.Failure -> acquired

            is ExecuteResult.Success -> when (acquired.result) {
                AcquireSessionLockResult.TimedOut -> {
                    println("Lock is busy; protected work was skipped.")
                    ExecuteResult.Success(Unit)
                }

                AcquireSessionLockResult.Acquired -> {
                    // Protected work belongs here, while this Scope is active.
                    ctx.session(
                        releaseSessionAdvisoryLock {
                            resource("application", "cache-refresh")
                        },
                    ).execute()
                        .success { released ->
                            when (released) {
                                ReleaseSessionLockResult.Released ->
                                    println("Lock released.")

                                ReleaseSessionLockResult.NotOwned ->
                                    println("This Connection did not report ownership of the Lock.")
                            }
                            Unit
                        }
                        .error { failure ->
                            println("Release failed: " + failure.message)
                        }
                }

                null -> ExecuteResult.Failure(
                    IllegalStateException("Operation returned Success without an Acquire result."),
                )
            }
        }
    }
~~~

Release از همان ctx استفاده می‌کند، پس روی Connectionای اجرا می‌شود که Acquire را انجام داده است. فقط پس از Acquired، Release بفرستید. NotOwned نتیجه‌ای معنایی است، اما آزادشدن Lock را تأیید نمی‌کند.

اگر یک عملیات قبلی Failure را در Scope ثبت کرده باشد، Release بعدی از همان Scope ممکن است پیش از JDBC رد شود. بنابراین یک finally block تضمین نمی‌کند که Release صریح اجرا شود. در پایان callback، Operation Connection خودش را می‌بندد؛ بسته‌شدن Connection مالک مسیر cleanup نهایی Lock باقی‌مانده است.

برای بررسی نتیجه و Failure، [مدیریت Result و Exception](handle-results.md) را ببینید.
