# Query Builder

Query Builder برای ساخت Queryهای SQL با DSL و اتصال آن‌ها به API اجراست. این صفحه مسیرهای اصلی ساخت و ترکیب Query را معرفی می‌کند؛ راهنمای جداگانهٔ [Executor](executor/README.md) اجرای Query، Connection/Transaction و مدیریت نتیجه را از دید مصرف‌کننده توضیح می‌دهد.

## راهنماها

- [ساخت Model و تعریف جدول و ستون](model/README.md)
- [ساخت CTE و خروجی‌های typed](cte/README.md)
- [قواعد Metadata در Model و Query](rules/README.md)
- [راهنمای یکپارچهٔ اجرای Query](executor/README.md)
- [مثال‌های Query Builder بر اساس نسخه](examples/README.md): [1.0](examples/v1.0/README.md)، [2.0](examples/v2.0/README.md)، [3.0](examples/v3.0/README.md)، [3.1](examples/v3.1/README.md)

<a id="query-builder-connection"></a>
<a id="query-builder-connection-usage"></a>
<a id="connection"></a>
## اجرای Query

Query را پس از ساخت با DSL به consumer متناظر بدهید. برای انتخاب Executor و شروع اجرا، [راهنمای Executor](executor/README.md#executor-choice) را ببینید.

<a id="query-builder-extraction"></a>
<a id="query-builder-result"></a>
### استخراج نتیجه

`ExecuteResult`، `QueryRow` و تبدیل به Collection/List در [بخش مدیریت نتیجه‌های Executor](executor/README.md#executor-result) مستند شده‌اند.

## نکتهٔ ایمنی

`INSERT`، `UPDATE` و `DELETE` دادهٔ دیتابیس را تغییر می‌دهند. آن‌ها را ابتدا روی دیتابیس توسعه یا آزمایشی اجرا کنید و برای چند تغییر وابسته از [Transaction](executor/README.md#executor-scopes) استفاده کنید.
