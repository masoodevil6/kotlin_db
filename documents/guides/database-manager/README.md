# Database Manager و چند پایگاه داده

این راهنما API مصرف‌کنندهٔ `:data_base:manager` را معرفی می‌کند. برنامه از `DatabaseManager` برای ثبت پایگاه‌های داده استفاده می‌کند و در کد مصرف‌کننده بهتر است آن را با قرارداد `DatabaseManagerApi` نگه دارد. هر registration با یک `DatabaseKey` انتخاب می‌شود و Handle مسیرهای Connection و Transaction همان پایگاه داده را ارائه می‌کند. برای افزودن Migration به Handle، به Aggregate ماژول `:data_base:migration` وابسته شوید؛ این ماژول extension عمومی `DatabaseHandleApi.migration` را عرضه می‌کند. Query Builder عملیات Query را می‌سازد و اجرا می‌کند؛ Schema برای تعریف DDL استفاده می‌شود و اعمال آن از مسیر Migration انجام می‌گیرد.

هر راهنما روی بخشی از گردش کار تمرکز دارد:

- [تعریف اولیه و تنظیم چند پایگاه داده](database_define/README.md)
- [کلیدهای پایگاه داده و دسترسی به Registrationها](database_keys/README.md)
- [Attributeهای اختصاصی Registration](registration_attributes/README.md)
- [ساخت Connection و Transaction با Handle](executor/README.md)
- [شروع به کار](../../../README.md#شروع)
- [ثبت Migration در Registration](database_define/migrations.md)
- [Lifecycle، هم‌زمانی، خطا و محدودیت‌های سراسری](../limitations/lifecycle-and-errors.md)

مثال‌های رسمی در [`data_base/manager/example`](../../../data_base/manager/example/README.md) قرار دارند. اجرای پیش‌فرض مثال‌ها فقط registrationها را می‌سازد؛ برای اجرای SQL باید opt-in و تنظیمات دیتابیس توسعه فراهم شوند.
