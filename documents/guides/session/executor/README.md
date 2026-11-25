# راهنمای Session Executor

این راهنما API مصرف‌کننده، مسیر اجرای Session و محدودیت‌های lifecycle آن را توضیح می‌دهد. مصرف معمول از Operation انجام می‌شود؛ دسترسی مستقیم به JDBC SessionExecutor فقط برای کاربردهای پیشرفته است.

## مسیر معمول مصرف

برای DatabaseHandle از extension عمومی withOperationScope استفاده کنید. ورودی connection فعلی DatabaseHandleApi یک IQueryBuilderExecutor می‌دهد و Session را مستقیماً ارائه نمی‌کند.

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

Builder یک API typed می‌سازد و AST مربوط به فرمان را نگه می‌دارد. ISessionCommandApi به هر API فرمان، نوع AST و نوع نتیجهٔ معنایی را وصل می‌کند. OperationExecutionContext.session(api) یک SessionConsumer<R> deferred می‌سازد. اجرای JDBC تنها هنگام فراخوانی execute() شروع می‌شود.

## مسیر داخلی

~~~text
Session Builder
    -> Typed Session API
    -> OperationExecutionContext.session(api)
    -> Deferred SessionConsumer
    -> اعتبارسنجی Scope
    -> انتخاب Session Dialect
    -> dispatch typed از API
    -> OperationSessionCommandExecutor
    -> SessionExecutor
    -> JDBC
    -> نتیجهٔ معنایی Session
    -> ExecuteResult
~~~

- **Builder:** API مربوط به Acquire یا Release را می‌سازد، Metadata مربوط را مقداردهی و ورودی لازم را اعتبارسنجی می‌کند.
- **Typed API:** ISessionCommandApi<A, R> نوع AST و نتیجه را با هم نگه می‌دارد. dispatch(executor) با قراردادهای ISessionCommandExecutor، Acquire و Release را به‌صورت typed مسیریابی می‌کند.
- **Operation:** Connection و Scope را مالک است، مجاز بودن Scope را بررسی می‌کند، Dialect را از DatabasePlatformConfig انتخاب می‌کند و نتیجه یا Failure را با قرارداد ExecuteResult برمی‌گرداند.
- **OperationSessionCommandExecutor:** Adapter داخلی Operation است. همان Connection قرض‌گرفته‌شده و Dialect انتخاب‌شده را به SessionExecutor می‌رساند؛ Connection را ایجاد یا نمی‌بندد.
- **SessionExecutor:** AST را رندر می‌کند، پارامترها را به ترتیب bind می‌کند، PreparedStatement و ResultSet را اجرا/می‌بندد و پاسخ JDBC را طبق پروتکل Session تفسیر می‌کند.
- **SessionConsumer:** اجرای فرمان را تا execute() عقب می‌اندازد و ExecuteResult<R> typed می‌دهد.

OperationExecutionContextهای عمومی که Session را پیاده‌سازی نکنند، fail-closed هستند. مسیر واقعی OperationExecutionScope فقط ریشهٔ فعال و مدیریت‌شدهٔ Connection Scope را قبول می‌کند. Root Transaction، Transaction Child و Scope مدیریت‌نشده پیش از اجرای JDBC رد می‌شوند. Pipeline stage علاوه بر Scope، lifetime و Thread را نیز هنگام ساخت و اجرای Consumer بررسی می‌کند.

## قرارداد نتیجه و خطا

SQL فرمان‌ها:

~~~sql
SELECT GET_LOCK(?, ?) AS acquired
SELECT RELEASE_LOCK(?) AS released
~~~

برای Acquire پاسخ صحیح ۱ به Acquired و ۰ به TimedOut تبدیل می‌شود؛ SQL NULL، مقدار یا نوع خارج از قرارداد و شکل نامعتبر ResultSet خطای پروتکل است. برای Release پاسخ ۱ به Released و پاسخ ۰ یا SQL NULL به NotOwned تبدیل می‌شود. NotOwned یک نتیجهٔ معنایی است و تأیید Release نیست.

در مسیر Operation، نتیجهٔ معنایی در ExecuteResult.Success قرار می‌گیرد. خطاهای Render، پروتکل و JDBC در ExecuteResult.Failure قرار می‌گیرند. SQLException در SessionExecutionException با cause اصلی حفظ می‌شود. SQLTimeoutException خطای JDBC است و به TimedOut تبدیل نمی‌شود. Error در مسیر generic Operation به‌عنوان fatal ثبت و دوباره پرتاب می‌شود.

ExecuteResult.success مقدار موفق را تبدیل می‌کند و ExecuteResult.error فقط Failure را مشاهده می‌کند؛ error همان ExecuteResult را برمی‌گرداند. خطاهای عادی را با نتیجه‌های معنایی اشتباه نگیرید.

## Deferred execution، mutable AST و یک‌بار اجرا

ساخت Consumer اجرای JDBC نمی‌کند. Consumer در اولین execute() تلاش می‌کند فرمان را اجرا کند. اجرای دوبارهٔ همان Consumer در Scope فعال Failure می‌دهد، بدون JDBC دیگر، و Failure را در Scope latch می‌کند.

API مالک AST mutable است. Operation هنگام ساخت Consumer از AST کپی یا snapshot نمی‌سازد؛ API هنگام execute() dispatch می‌شود. بنابراین تغییر API/AST پیش از execute در فرمان دیده می‌شود. برای جلوگیری از تغییر ناخواسته، API را پس از ساخت Consumer تغییر ندهید و Consumer را فقط یک بار اجرا کنید.

اگر Scope پیش از execute منقضی شود، Consumer نمی‌تواند JDBC اجرا کند. Consumerهای Pipeline نیز پس از پایان stage یا از Thread نامعتبر رد می‌شوند. Session API فقط در managed root Connection Scope مجاز است، نه در Root Transaction یا Transaction Child.

Operation مالک Connection است. Session آن را نمی‌بندد، transaction باز نمی‌کند، commit یا rollback نمی‌زند و auto-commit را تغییر نمی‌دهد. SessionExecutor منابع JDBC متعلق به فرمان خودش، یعنی PreparedStatement و ResultSet را می‌بندد.

اگر Scope Failure را latch کرده باشد، فرمان Release بعدی همان Scope نیز پیش از JDBC رد می‌شود. بنابراین finally تضمین Release صریح نمی‌دهد. Operation در پایان Scope Connection مالک را می‌بندد؛ شواهد Integration برای MariaDB 10.4.28 نشان می‌دهد با بستن Connection مالک، قفل Session-level برای Connection دیگر قابل گرفتن شد.

## ماژول‌ها و وابستگی‌ها

Session core مستقل از Operation، Manager، Query و Schema است. Operation از قرارداد Session API استفاده می‌کند و ماژول Session Executor را در پیاده‌سازی خود مصرف می‌کند.

| ماژول | مسئولیت و وابستگی مستقیم پروژه‌ای |
|---|---|
| session:ast | AST، Metadata، LockResource، LockWaitPolicy و Lock Name Generator؛ بدون ماژول پروژه‌ای |
| session:api | قراردادهای typed API و result؛ api وابسته به session:ast |
| session:builder | DSL و Factoryهای فرمان؛ api وابسته به session:api |
| session:dialect | قرارداد Dialect، Registry، Context و Render State؛ api وابسته به session:ast |
| session:renderer | Rendererهای MySQL/MariaDB؛ implementation وابسته به session:ast و session:dialect |
| session:executor | اجرای Session و Result protocol؛ api وابسته به session:api، session:ast و session:dialect؛ implementation وابسته به session:renderer |
| session:example | نمونهٔ مستقل؛ implementation وابسته به session:builder و session:executor؛ MySQL Connector/J فقط runtime |

در Gradle، Operation Executor، Session API را به‌صورت API dependency می‌دهد و Session Executor را به‌صورت implementation dependency مصرف می‌کند. هیچ وابستگی Session به Query یا Schema وجود ندارد.

## Dialect و پشتیبانی

نسخهٔ فعلی Renderer برای SessionDialect.MYSQL و SessionDialect.MARIA_DB فرمان‌ها را به این SQLها تبدیل می‌کند:

~~~sql
SELECT GET_LOCK(?, ?) AS acquired
SELECT RELEASE_LOCK(?) AS released
~~~

پارامترهای Acquire به‌ترتیب نام Lock و timeout ثانیه‌ای هستند؛ Release فقط نام Lock می‌فرستد. LockWaitPolicy.NoWait برابر صفر ثانیه است. Timeout مقدار صحیح، finite و کامل در بازهٔ ۱ تا ۳۰۰ ثانیه می‌خواهد.

پشتیبانی Source یعنی Renderer برای MySQL و MariaDB موجود است؛ دامنهٔ اعتبارسنجی واقعی محدودتر است. Integration واقعی انجام‌شده در پروژه روی MariaDB 10.4.28 با MySQL Connector/J 8.0.33 بوده است. این نتیجه را به MySQL Server یا MariaDB Connector/J بومی تعمیم ندهید. تست‌های فعلی همچنین رفتار Session lock در برابر commit/rollback، آزادسازی از Connection دیگر و بسته‌شدن Connection مالک را در همان محیط بررسی کرده‌اند. تکرار Acquire همان Lock و شمارش/رفتار reentrant در Integration حاضر پوشش داده نشده و Session در Source سیاستی برای آن تعریف نمی‌کند.

## Lock Resource و نام نهایی

API مقدار منطقی LockResource(namespace, name) را می‌گیرد. Renderer از SessionAdvisoryLockNameGenerator مشترک در Acquire و Release استفاده می‌کند. الگوریتم، فیلدهای UTF-8 با طول uint32 big-endian را برای domain، namespace و name کد می‌کند و نسخهٔ ۱ را به‌صورت int big-endian میان domain و namespace می‌نویسد؛ سپس SHA-256 می‌گیرد و ۲۹ بایت نخست Hash را به ۵۸ رقم hex کوچک تبدیل می‌کند. پیشوند kdb1_ خروجی را ۶۳ کاراکتری می‌کند.

Golden Vector موجود برای LockResource("application", "migration") برابر است با:

~~~text
kdb1_22f00cc29957a0c2cfb8e5b0106ce5b4dd546ef0173cc52970e19a5fa6
~~~

Acquire و Release باید دقیقاً همان namespace/name را دریافت کنند تا به نام یکسان برسند.

## Session Lock و SystemLock در Operation

Session Advisory Lock عمومی نام خود را از namespace و name منطقی می‌سازد و فرمان‌های صریح Acquire/Release دارد. Operation SystemLock مسیر قدیمی جداگانه‌ای است: با domain برابر textqb.system-lock، نسخه و شناسهٔ SystemLock به‌علاوهٔ DatabaseIdentity نامی با پیشوند textqb:syslock:v1: تولید می‌کند و در APIهای callback-based قدیمی Operation مصرف می‌شود. این دو قرارداد نام‌گذاری قابل جایگزینی نیستند.

Operation Legacy API به نام withSessionAdvisoryLock نیز callback-based است و Release را پس از callback درخواست می‌کند؛ state داخلی خودش را دارد. API generic Session فرمان‌های صریح و بی‌حافظهٔ مالکیت ارائه می‌دهد و آن cleanup/state را به ارث نمی‌برد. پس از Failure latch، Release از همان Scope ممکن است رد شود. در صورت باقی‌ماندن Lock، Connection مالک با پایان lifecycle بسته می‌شود.

## مسیر پیشرفته: اجرای مستقیم SessionExecutor

SessionExecutor یک API عمومی برای برنامه‌ای است که خودش AST، Connection و SessionDialect را مدیریت می‌کند. این مسیر برای مصرف‌کنندهٔ معمول Operation نیست:

~~~kotlin
package sample.session

import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.sql.Connection

fun acquireDirectly(
    ast: IAcquireSessionAdvisoryLockAst,
    connection: Connection,
    dialect: SessionDialect,
): AcquireSessionLockResult =
    SessionExecutor().acquire(ast, connection, dialect)
~~~

در این مسیر، caller مسئول تأمین AST معتبر، انتخاب Dialect، مالکیت و بستن Connection، اجرای Release یا بستن Connection مالک و مدیریت خطاهاست. SessionExecutor خود Connection را نمی‌بندد و transaction را مدیریت نمی‌کند. نمونه‌های سطح مصرف‌کننده در [فهرست مثال‌ها](../examples/README.md) فقط مسیر Operation را نشان می‌دهند.
