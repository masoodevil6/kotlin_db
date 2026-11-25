# استفاده از Session در Operation

API معمول Session فرمان typed را به Operation تحویل می‌دهد؛ مصرف‌کننده AST را استخراج نمی‌کند، SessionExecutor نمی‌سازد و Connection یا Dialect را انتخاب نمی‌کند.

برای DatabaseHandle، ورودی فعلی Operation Scope یک extension عمومی است:

~~~kotlin
package sample.session

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock

fun runAdvisoryLock(database: DatabaseHandleApi): ExecuteResult<Unit> =
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
                null -> ExecuteResult.Failure(
                    IllegalStateException("Operation returned Success without an Acquire result."),
                )

                AcquireSessionLockResult.TimedOut -> {
                    println("Lock is busy; protected work was skipped.")
                    ExecuteResult.Success(Unit)
                }

                AcquireSessionLockResult.Acquired -> {
                    // Run the protected work here, before this Operation Scope ends.
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
                                    println("Release was not confirmed for this Connection.")
                            }
                            Unit
                        }
                        .error { failure ->
                            println("Release failed: " + failure.message)
                        }
                }
            }
        }
    }
~~~

در صورت داشتن OperationExecutionContext از مسیر دیگری، همان فرمان را می‌توان مستقیماً به ctx.session(api).execute() داد. OperationExecutionContext.session یک SessionConsumer typed و deferred برمی‌گرداند. ساخت Consumer اجرای JDBC را آغاز نمی‌کند؛ execute() آن را اجرا می‌کند. Consumer برای یک بار اجرا طراحی شده است.

## چرا withOperationScope؟

امضای فعلی DatabaseHandleApi.connection { ... } یک IQueryBuilderExecutor به callback می‌دهد. این API برای Query است و OperationExecutionContext تحویل نمی‌دهد. برای Session در DatabaseHandle داخلی از DatabaseHandleApi.withOperationScope { ctx -> ... } استفاده کنید. این extension در پیاده‌سازی‌های سفارشی DatabaseHandleApi که Scope مدیریت‌شده ندارند، Failure می‌دهد. اگر برنامه مستقیماً OperationExecutionExecutor را به کار می‌برد، متد connection آن نیز OperationExecutionContext را به callback می‌دهد.

## Scope، Connection و Dialect

Operation فقط فرمان Session را در ریشهٔ فعال و مدیریت‌شدهٔ Connection Scope اجرا می‌کند. Root Transaction، Transaction Child و Scope مدیریت‌نشده پیش از آماده‌سازی SQL رد می‌شوند. Operation Dialect را از DatabasePlatformConfig می‌خواند و MySQL و MariaDB را به SessionDialect متناظر نگاشت می‌کند؛ Dialectهای دیگر Failure می‌گیرند.

Operation همان Connection داخلی Scope را به Session می‌دهد و lifecycle آن را نگه می‌دارد. Session فرمان Connection تازه نمی‌سازد، Connection را نمی‌بندد و commit، rollback یا auto-commit را تغییر نمی‌دهد. Context یا Consumer را پس از پایان callback نگه ندارید. در Pipeline، بررسی lifetime و Thread هم هنگام ساخت Consumer و هم هنگام execute اعمال می‌شود.

## Failure و Release

Failure قبلی Scope باعث می‌شود فرمان Session بعدی پیش از JDBC رد شود. بنابراین اگر کار محافظت‌شده Scope را به Failure برده باشد، فراخوانی Release در finally تضمین نمی‌کند که SQL مربوط به Release اجرا شود. Connection مالک در پایان lifecycle Operation بسته می‌شود و طبق semantics قفل Session-level دیتابیس، بستن آن Connection راه cleanup نهایی است.

Operation Legacy API به نام withSessionAdvisoryLock قرارداد جداگانه‌ای دارد: callback را میان Acquire و Release خودکار اجرا می‌کند و state داخلی خودش را دارد. API generic بالا فرمان‌های صریح Acquire و Release است؛ آن state یا cleanup خودکار را به ارث نمی‌برد. withSystemLock نیز API Legacy Operation با قرارداد نام‌گذاری خودش است. جزئیات این تفاوت در [راهنمای Session Executor](../executor/README.md) آمده است.
