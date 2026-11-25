# سازگاری و محدودهٔ قواعد Metadata

قواعد این پوشه دربارهٔ Resolve و اعتبارسنجی Metadata هستند. تغییر در Metadata نباید ناخواسته قراردادهای مستقل AST، اجرای Query یا composition را تغییر دهد.

این محدوده دربارهٔ Metadata است. مسیر AST-based پارامتر و اجرای positional که در قراردادهای Query Builder تعریف شده‌اند، موضوعی مستقل‌اند و با این قواعد جایگزین نمی‌شوند.

مگر آنکه مانع فنی مشخصی تغییر دیگری را ضروری کند:

- تغییر محدود به Metadata نباید AST، Executor، `QueryRow`، Materializer یا امضاهای عمومی نامرتبط را تغییر دهد.
- قرارداد `Cte` و semantics composition آن در تغییرات Metadata تغییر نمی‌کند.
- مسیر مستقل و تأییدشدهٔ V3.0، ورودی صریح را جایگزین `ICteDeclaration.params`، constructor پارامترپذیر `CteDeclaration`، `execute(queryManager)` و overload `cte(declaration)` می‌کند. overloadهای `with(Cte)` و `with(withName, Cte)` و معنای نام CTE و alias occurrence حفظ می‌شوند.
- صرفاً برای یکسان‌سازی ظاهری Metadataهای Model و Query، abstraction عمومی تازه اضافه نمی‌شود.
- رفتار فعلی رشتهٔ خالی در Annotationهای Model تغییر نمی‌کند.
- `QBQuery.name` نباید نام صریح caller را که به factory ساخت CTE داده شده بازنویسی کند.
- Resolve کردن Metadata از اجرای Query و استخراج مقدار Property مستقل باقی می‌ماند.

این موارد مرز سازگاری ثبت‌شده برای این قرارداد هستند؛ هر تغییر خارج از آن‌ها باید جداگانه بررسی و توجیه شود.
