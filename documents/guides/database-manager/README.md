# Database Manager و چند پایگاه داده

این راهنما API مصرف‌کنندهٔ `:data_base:manager` را معرفی می‌کند. برنامه از `DatabaseManager` برای ثبت و مالکیت پایگاه‌های داده استفاده می‌کند و در کد مصرف‌کننده بهتر است آن را با قرارداد `DatabaseManagerApi` نگه دارد. هر registration با یک `DatabaseKey` انتخاب می‌شود و `DatabaseHandleApi` مسیرهای Connection Scope، Transaction Scope و Migration همان پایگاه داده را ارائه می‌کند. Query Builder عملیات Query را می‌سازد و اجرا می‌کند؛ Schema برای تعریف DDL استفاده می‌شود و اعمال آن از طریق Migration انجام می‌گیرد.

هر راهنما روی بخشی از گردش کار تمرکز دارد:

- [شروع به کار و نمونهٔ end-to-end](getting-started.md)
- [Catalog، registration و کلیدها](catalog-and-keys.md)
- [Query و Transaction](query-and-transaction.md)
- [تعریف Schema از طریق Migration](schema-operations.md)
- [ثبت و اجرای Migration](migrations.md)
- [Lifecycle، نتیجه و مدیریت خطا](lifecycle-and-errors.md)

مثال‌های رسمی در [`data_base/manager/example`](../../../data_base/manager/example/README.md) قرار دارند. اجرای پیش‌فرض مثال‌ها فقط registrationها را می‌سازد؛ برای اجرای SQL باید opt-in و تنظیمات دیتابیس توسعه فراهم شوند.
