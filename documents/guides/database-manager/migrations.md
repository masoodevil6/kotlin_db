# Migration و DatabaseManager

قراردادهای تعریف Migration و `migrationConfig { ... }` در ماژول Migration باقی مانده‌اند، اما در بازنویسی زیرساخت اجرا، اتصال آن‌ها به DatabaseManager موقتاً برداشته شده است.

در وضعیت فعلی:

- `DatabaseRegistrationBuilder` ویژگی `migrations` ندارد.
- `DatabaseHandleApi` متد `migrate()` ندارد.
- DatabaseManager عملیات Query و Transaction را ارائه می‌کند؛ Migration اجرا نمی‌کند و history نمی‌سازد.
- برای دیدن نمونه‌های تعریف و رندر Schema، از ماژول `data_base:migration:example` استفاده کنید. این نمونه‌ها به دیتابیس متصل نمی‌شوند.

این صفحه پس از آماده‌شدن runtime جدید Migration به‌روزرسانی خواهد شد.
