# Release کردن Lock

برای Release، همان namespace و name فرمان Acquire را به Builder بدهید. فرمان باید روی همان Operation Scope و همان Connectionای اجرا شود که Lock را گرفته است.

~~~kotlin
package sample.session

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock

fun releaseOnCurrentScope(
    ctx: OperationExecutionContext,
    namespace: String,
    name: String,
): ExecuteResult<ReleaseSessionLockResult> =
    ctx.session(
        releaseSessionAdvisoryLock {
            resource(namespace, name)
        },
    ).execute()
~~~

این تابع را داخل همان callbackی فراخوانی کنید که Acquire را اجرا کرده است. بازکردن Scope جدید با Database Handle، مالکیت Lock قبلی را منتقل نمی‌کند و به معنی همان Connection نیست.

نتیجه‌ها:

- Released یعنی پاسخ Session نشان داد Release موفق بوده است.
- NotOwned یعنی این Connection مالک Lock نبود یا Release موفق گزارش نشد. این نتیجهٔ معنایی عادی است، نه Exception.

برای بررسی نتیجه با ExecuteResult.success/error و تفاوت Failure با NotOwned، [مدیریت Result و Exception](handle-results.md) را ببینید. اگر Scope قبلاً Failure داشته باشد، Operation ممکن است فرمان Release بعدی را پیش از JDBC رد کند.
