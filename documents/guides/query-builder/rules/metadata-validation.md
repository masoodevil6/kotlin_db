# اعتبارسنجی Metadata

اعتبارسنجی باید روی Metadata Resolve‌شده انجام شود؛ مقدار Property خروجی Typed Query نباید برای کشف Metadata خوانده شود.

## قواعد اعتبارسنجی

- یکتایی SQL aliasهای خروجی باید حفظ شود.
- یکتایی نام‌های منطقی خروجی در میان خروجی‌های Typed متعلق به قرارداد Query بررسی شود.
- تعلق خروجی به Query بر اساس رابطهٔ موجود `declarationOwner` تعیین شود، نه فقط کلاسی که Property در آن تعریف شده است.
- Property معتبر به‌ارث‌رسیده نباید فقط به دلیل تعریف در کلاس والد کنار گذاشته شود.
- Propertyهای منبع Model نباید به‌عنوان خروجی‌های Query شناسایی شوند.
- Getter Property برای اعتبارسنجی یا Resolve فراخوانی نشود.
- اعتبارسنجی Metadata پس از تکمیل Declaration مربوط به SELECT و پیش از ساخت `BuiltCteComposition` انجام شود.

## تمایز خطاهای Metadata

فیلد تعریف‌نشده می‌تواند از مقدار پیش‌فرض یا fallback استفاده کند؛ مقدار خالی صریح، اگر آن فیلد باید مقدار داشته باشد، باید رد شود. جزئیات این تمایز در [نقشهٔ Resolve](metadata-resolution.md) و ترتیب فیلدها در [Metadata Query و CTE](query-metadata.md) آمده است.
