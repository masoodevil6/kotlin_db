# معیارهای پذیرش Metadata

این معیارها خروجی مورد انتظار مثال‌های Resolve را مشخص می‌کنند:

| # | نتیجهٔ مورد انتظار |
|---:|---|
| 1 | مثال Model به `tableName = user_users` و `tableAlias = uu1` برسد. |
| 2 | مثال Property مدل به `columnName = user_id`، `columnAlias = user_id_out` و `columnDataType = LONG` برسد. |
| 3 | نام منطقی و alias تعریف‌شده در Annotation مربوط به Query حفظ شوند. |
| 4 | مثال Query به `cteName = user_display_query_v3` و `occurrenceAlias = cte_user_display` برسد؛ نام runtime از `QBQuery.name` و alias از override همان occurrence بیاید. |
| 5 | مثال خروجی Query به نام منطقی `full_name`، SQL alias برابر `name` و نوع خروجی `LONG` برسد. |
| 6 | هر override فقط فیلد متناظر خود را تغییر دهد. |
| 7 | Getter Property خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود. |
| 8 | نام منطقی Query، نام runtime CTE و alias occurrence مستقل بمانند؛ `withName` صریح فقط نام SQL در `WITH` را override کند. |
| 9 | رفتار موجود Model و APIهای عمومی Query و CTE حفظ شوند. |
| 10 | اعتبارسنجی Metadata پیش از ساخت `BuiltCteComposition` انجام شود. |

تعریف اولویت‌های هر مقدار در [Metadata مدل](model-metadata.md) و [Metadata Query و CTE](query-metadata.md) آمده است؛ قواعد خطا در [اعتبارسنجی Metadata](metadata-validation.md) شرح داده می‌شود.
