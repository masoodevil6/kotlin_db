# Connection Pipeline

`connectionPipeline` زنجیره‌ای از Stageهای typed را روی یک Connection Scope غیرتراکنشی اجرا می‌کند. از extension روی `DatabaseHandleApi` استفاده کنید؛ ساخت زنجیره Connection باز نمی‌کند و اجرا فقط با `execute()` آغاز می‌شود.

## نمونه

```kotlin
import gog.my_project.data_base_manager.facade.connectionPipeline
import gog.my_project.data_base_operation.then.ConnectionDecision

val pipeline = database.connectionPipeline<String> { context ->
    val customerId = findCustomerId(context) // تابع برنامه؛ می‌تواند از context Query اجرا کند.

    if (customerId == null) {
        "customer-not-found" // بدون next، همین Stage پایان می‌یابد.
    } else {
        context.next(customerId).connection(ConnectionDecision.REUSE)
    }
}.then<Int> { customerId, context ->
    countOrders(context, customerId) // خروجی این Stage نتیجهٔ نهایی است.
}

val result = pipeline.execute()
```

`findCustomerId` و `countOrders` توابع برنامه‌اند. اگر در آن‌ها Query اجرا می‌کنید، از APIهای همان `context` استفاده کنید. Context عملیات محافظت‌شده‌ای مانند `executeSelect`, `executeRead`, `executeInsert`, `executeUpdate`, `executeDelete` و `executeDdl` دارد و Connection خام JDBC را افشا نمی‌کند.

در هر Stageای که باید به Stage بعدی برسد، `context.next(value)` را با `connection(ConnectionDecision.REUSE)` کامل کنید. در پیاده‌سازی فعلی `ConnectionDecision.NEW` پشتیبانی نمی‌شود؛ انتخاب آن خطای پروتکل می‌دهد و Connection دیگری باز نمی‌کند. Stage آخر نباید `next()` را صدا بزند، چون مرحلهٔ بعدی ندارد.

## نتیجه و پایان زودهنگام

هر Stage می‌تواند با برنگرداندن `next()` زنجیره را همان‌جا تمام کند. چون خروجی Stageهای مختلف می‌تواند نوع متفاوتی داشته باشد، نوع نتیجهٔ موفق محل پایان را نگه می‌دارد. در مثال بالا نتیجه از نوع زیر است:

```kotlin
ExecuteResult<PipelineCompletion<String, Int>>
```

اگر Stage اول بدون `next()` تمام شود، مقدار `"customer-not-found"` در `PipelineCompletion.Earlier` قرار می‌گیرد. اگر ادامه یابد، خروجی Stage دوم در `PipelineCompletion.Current` خواهد بود. با اضافه‌کردن Stageهای بیشتر، `PipelineCompletion` به‌صورت تو در تو گسترش پیدا می‌کند.

پایان زودهنگام به‌تنهایی Failure نیست. اگر Operation Failure ثبت نشده باشد، Operation با نتیجهٔ Stage پایان‌یافته موفق می‌شود. همچنین `next()` فقط کنترل جریان است و موفقیت Query را تضمین نمی‌کند؛ نتیجهٔ هر عملیات را بررسی کنید و پس از Failure، Stage بعدی را درخواست نکنید.

## Lifecycle و محدودیت‌ها

`execute()` زنجیره را زیر یک Manager lease و یک Operation Connection Scope اجرا می‌کند. همهٔ Stageها از همان Scope و Connection استفاده می‌کنند و این Pipeline Transaction ایجاد نمی‌کند. برای commit/rollback از [Transaction Pipeline](transaction_pipeline.md) استفاده کنید.

Stageها همگام و به ترتیب تعریف اجرا می‌شوند. Context هر Stage فقط هنگام اجرای همان Stage و روی thread مالک آن معتبر است؛ آن را ذخیره نکنید و از callback ناهمگام استفاده نکنید. Operation مالک Connection و cleanup است.

این Builder برای `DatabaseHandle` مدیریت‌شدهٔ همین Manager قابل اجراست. اگر روی پیاده‌سازی سفارشی `DatabaseHandleApi` ساخته شود که اجرای Pipeline مدیریت‌شده ندارد، `execute()` مقدار `ExecuteResult.Failure` برمی‌گرداند. رفتار دیگر extensionها روی Handle سفارشی در [راهنمای Executor](README.md) آمده است.

برای مقایسه با Pipelineهای typed دیگر، توقف پس از Operation Failure و Definitionهای قابل استفادهٔ مجدد، [راهنمای Pipeline در Operation](../../operation/pipelines/README.md) را ببینید. برای اجرای معمولی تک‌مرحله‌ای، [راهنمای Connection](connection.md) را بخوانید.
