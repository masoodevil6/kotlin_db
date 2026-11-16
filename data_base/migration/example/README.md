# مثال‌های Schema و Migration

این ماژول `SchemaOperation`های نمونه را می‌سازد و SQL را برای Dialect و نسخهٔ دیتابیس انتخاب‌شده رندر می‌کند. `Main.kt` نمونه‌های MariaDB را آماده و رندر می‌کند؛ هیچ Connection یا دیتابیسی باز نمی‌شود و DDL اجرا نمی‌شود.

فایل‌های `v1/migrations/registration/MigrationRegistrationExample.kt` و `v1/migrations/DataBaseMigrationTags.kt` نمونهٔ `Migration`، Version tag، Group tag، Item tag و ساخت `MigrationConfiguration` با `migrationConfig { ... }` را نشان می‌دهند. آن نمونه Configuration را به Database Registration وصل نمی‌کند.

برای مسیر کامل مصرف‌کننده—تعریف Tagها، ساخت Configuration جدا برای هر Registration، اتصال Configuration، گرفتن Handle و اجرای `handle.migration { ... }`—به [نمونهٔ دو دیتابیس](../../../example/README.md) و [راهنمای Migration](../../../documents/guides/migration/README.md) مراجعه کنید. اجرای واقعی آن مثال ریشه‌ای نیز به‌طور پیش‌فرض غیرفعال است و فقط با property صریح فعال می‌شود.
