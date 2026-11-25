# نقشهٔ Metadata و قواعد Resolve

Metadata منبع، selector ستون پایه و Metadata خروجی SELECT در ساختارهای جدا نگه‌داری می‌شوند. یک مقدار مشترک میان دو ساختار، نقش آن‌ها را یکی نمی‌کند.

| ساختار | نمونهٔ مقادیر | کاربرد یا Resolve |
|---|---|---|
| `QueryTableAstMetadata` | `nameFromModel`، `nameFromAnnotation`، `nameFromAst` و فیلدهای مشابه alias | `resolveName()` و `resolveAlias()` به ترتیب AST، Annotation، Model را بررسی می‌کنند. |
| `QueryColumnsBaseAstMetadata` | `tableAliasFrom...`، `columnNameFrom...`، `columnAliasFromModel` و `columnAliasFromAnnotation` | نام ستون و alias منبع از AST، Annotation، Model؛ alias پیش‌فرض ستون از Annotation، Model. |
| `QueryColumnsAstMetadata` | `ColumnAlias`، `ExecutionType`، `IsExecutionTypeExplicit`، `PropertyReference`، `ColumnMethod` | Metadata خروجی SELECT؛ مستقل از نام و alias منبع. |

در منبع derived SELECT، AST نگه‌دارندهٔ `derivedQuery` است و فیلدهای نام منبع خالی می‌مانند؛ alias هر occurrence جداگانه Resolve می‌شود. `cte(String)` نام CTE را در Metadata منبع می‌گذارد؛ `cte(declaration)` هویت relation را در context composition نگه می‌دارد.

در API فعلی `execute(DataType)` روی خروجی SELECT (`IQueryColumnsApi`) اعمال می‌شود، نه روی selector ستون پایه. مقدار صریح آن فقط `ExecutionType` خروجی را override می‌کند و نوع Property یا Metadata منبع را تغییر نمی‌دهد.

`relationName` بخشی از قرارداد Query نیست. نام runtime پیش‌فرض CTE از `QBQuery.name` و سپس convention مجاز resolve می‌شود؛ نام صریح factory بر آن مقدم است. جزئیات در [Metadata Query و CTE](query-metadata.md) آمده است.

## ترتیب عمومی

1. ابتدا مقدارهای پیش‌فرض از convention یا نوع Property تعیین می‌شوند.
2. Annotation فقط فیلدهای متناظر خود را override می‌کند.
3. تنظیم صریح DSL در مرحلهٔ آخر، فقط فیلدی را تغییر می‌دهد که آن عملیات برای override تعریف کرده است.
4. نام منطقی، نام منبع، نام CTE، SQL alias و نوع داده مستقل می‌مانند.

جزئیات اولویت در [Metadata مدل](model-metadata.md) و [Metadata Query و CTE](query-metadata.md) آمده است. تفاوت مقدار تعریف‌نشده و رشتهٔ خالی در ادامهٔ همین صفحه شرح داده می‌شود.

## مقدار تعریف‌نشده و رشتهٔ خالی

مقدار تعریف‌نشده و رشتهٔ خالی صریح الزاماً یک معنا ندارند. برای فیلدهای جدید Annotationهای Query می‌توان از مقدار قراردادی `QB_METADATA_UNSET` استفاده کرد تا نبود مقدار از رشتهٔ خالی قابل تشخیص باشد:

- **تعریف‌نشده:** مقداری ارائه نشده و fallback یا مقدار پیش‌فرض متناظر می‌تواند استفاده شود.
- **رشتهٔ خالی صریح:** caller عمداً مقدار خالی داده است؛ اگر آن فیلد باید غیرخالی باشد، اعتبارسنجی باید آن را رد کند.

رفتار Annotationهای موجود Model را صرفاً برای یکسان‌سازی با Query تغییر ندهید. به‌ویژه، مقدارهای پیش‌فرض رشتهٔ خالی در `QBTable` و `QBColumn` نباید بی‌بررسی تغییر کنند. استفاده از `QB_METADATA_UNSET` نیز نباید بدون بررسی سازگاری به فیلدهای موجود Model تعمیم داده شود.
