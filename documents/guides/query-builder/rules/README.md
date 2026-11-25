# قواعد Metadata در Query Builder

این پوشه قواعد فعلی Resolve و اعتبارسنجی Metadata در Model و Query را به تفکیک موضوع نگه می‌دارد. نام منبع، نام منطقی، CTE، aliasها و نوع داده ممکن است در یک مثال مقدار یکسان داشته باشند، اما نقش‌های مستقلی دارند.

## دسته‌بندی

| صفحه | موضوع |
|---|---|
| [نقشهٔ Metadata و Resolve](metadata-resolution.md) | ساختارهای Metadata، ترتیب اولویت و تفاوت مقدار تعریف‌نشده با رشتهٔ خالی |
| [Metadata مدل](model-metadata.md) | قواعد کلاس و Property مدل، `QBTable`، `QBColumn` و overrideهای DSL |
| [Metadata Query و CTE](query-metadata.md) | نام CTE، alias occurrence، خروجی‌های `QBQuerySelect` و نام‌گذاری `WITH` |
| [اعتبارسنجی Metadata](metadata-validation.md) | یکتایی alias و نام منطقی، مالکیت Declaration و زمان اعتبارسنجی |
| [سازگاری و محدوده](compatibility.md) | مرز تغییرات Metadata و قراردادهای CTE و Model |
| [معیارهای پذیرش](acceptance-criteria.md) | نتیجه‌ای که مثال‌های Resolve باید تولید کنند |

## مسیر مطالعه

برای فهم ترتیب کلی، ابتدا [نقشهٔ Resolve](metadata-resolution.md) را بخوانید. سپس صفحهٔ [Model](model-metadata.md) یا [Query و CTE](query-metadata.md) را بر اساس نوع Metadata دنبال کنید. قواعد تشخیص خطا در [اعتبارسنجی](metadata-validation.md) آمده‌اند.

این راهنما قواعد Metadata را پوشش می‌دهد. برای تعریف Model به [راهنمای ساخت Model](../model/README.md) و برای ساخت CTE به [راهنمای CTE](../cte/README.md) مراجعه کنید.
