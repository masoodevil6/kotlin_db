# Transaction Pipeline

`transactionPipeline` چند Stage را در یک Transaction متعلق به Operation اجرا می‌کند. این extension روی `DatabaseHandleApi` قرار دارد؛ ساخت زنجیره Transaction باز نمی‌کند و اجرای آن با `execute()` شروع می‌شود.

## نمونه

```kotlin
import gog.my_project.data_base_manager.facade.transactionPipeline

val pipeline = database.transactionPipeline<String> { context ->
    val orderId = createOrder(context)
    context.next(orderId)
}.then<Int> { orderId, context ->
    insertOrderLines(context, orderId)
}.then<String> { insertedLines, context ->
    markOrderReady(context, insertedLines)
}

val result = pipeline.execute()
```

توابع `createOrder`، `insertOrderLines` و `markOrderReady` توابع برنامه‌اند. Queryها را از APIهای همان Stage Context اجرا کنید. Context عملیات‌هایی مانند `executeSelect`, `executeRead`, `executeInsert`, `executeUpdate`, `executeDelete` و `executeDdl` را فراهم می‌کند و Connection خام را در اختیار Stage نمی‌گذارد.

## انتقال مقدار و نتیجه

برای ادامه، `context.next(value)` را فراخوانی کنید. برخلاف Connection Pipeline، در این Context انتخاب Connection وجود ندارد؛ همهٔ Stageها در همان Transaction اجرا می‌شوند. Stage آخر نباید `next()` را فراخوانی کند.

در نمونه، نتیجهٔ موفق از نوع زیر است:

```kotlin
ExecuteResult<PipelineCompletion<PipelineCompletion<String, Int>, String>>
```

`PipelineCompletion.Current` خروجی Stage پایانی اجراشده را نگه می‌دارد و `PipelineCompletion.Earlier` محل پایان در یکی از Stageهای پیشین را مشخص می‌کند. اگر یک Stage بدون `next()` برگردد، Stageهای بعدی اجرا نمی‌شوند؛ پایان عادی به‌تنهایی Failure محسوب نمی‌شود.

## Commit، Rollback و Failure

Pipeline زیر یک Manager lease و یک Transaction Scope اجرا می‌شود. در صورت موفقیت Operation، Transaction طبق قرارداد Operation commit می‌شود. اگر Query یا Stage باعث ثبت Failure شود، commit انجام نمی‌شود و rollback و cleanup بر عهدهٔ Operation است. Exception معمولی طبق قرارداد Operation به Failure تبدیل می‌شود؛ `Error` پس از cleanup در مسیر fatal باقی می‌ماند.

`next()` فقط درخواست اجرای Stage بعدی است و موفقیت Query را نشان نمی‌دهد. نتیجهٔ `ExecuteResult` هر Query را بررسی کنید و پس از Failure، ادامه را درخواست نکنید. توقف زودهنگام بدون Failure می‌تواند همچنان اجرای موفق Transaction باشد و commit شود؛ بنابراین فقط وقتی زنجیره را متوقف کنید که تغییرهای انجام‌شده تا آن نقطه نتیجهٔ معتبر و کاملی باشند.

Stageها همگام و به ترتیب تعریف اجرا می‌شوند. Context هر Stage فقط در طول همان Stage و روی thread مالک آن معتبر است؛ آن را نگه ندارید یا از callback ناهمگام استفاده نکنید. Transaction، Connection و cleanup را Operation مدیریت می‌کند.

این Builder برای `DatabaseHandle` مدیریت‌شدهٔ همین Manager قابل اجراست. اجرای زنجیره روی پیاده‌سازی سفارشی `DatabaseHandleApi` که lifecycle مدیریت‌شده ارائه نمی‌کند، با `ExecuteResult.Failure` پایان می‌یابد. رفتار دیگر extensionها روی Handle سفارشی در [راهنمای Executor](README.md) آمده است.

برای عملیات تک‌مرحله‌ای، [راهنمای Transaction](transaction.md) را ببینید. تفاوت این API با Pipelineهای دیگر در [راهنمای Pipeline در Operation](../../operation/pipelines/README.md) آمده است.
