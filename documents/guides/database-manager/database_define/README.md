# تعریف اولیهٔ پایگاه داده‌ها

این بخش نحوهٔ ساخت تنظیمات اتصال، تعریف کلیدهای پایدار و ثبت یک یا چند پایگاه داده در `DatabaseManager` را توضیح می‌دهد. هر فراخوانی `database(key) { ... }` یک Registration مستقل می‌سازد؛ برای دسترسی بعدی، همان کلید را به کار ببرید.

## مسیر مطالعه

- [تعریف کلید و ساخت Object دسترسی](database-keys.md)
- [ساخت تنظیمات اتصال و پلتفرم](database-configuration.md)
- [ثبت چند پایگاه داده و گرفتن Handleها](multiple-databases.md)

این راهنما روی تعریف و دسترسی تمرکز دارد. شروع اجرای Query و Transaction در [راهنمای Executor](../executor/README.md)، جزئیات در [راهنمای Connection](../executor/connection.md) و [راهنمای Transaction](../executor/transaction.md)، و تنظیم Migration برای هر Registration در [راهنمای Migrationها](migrations.md) آمده است.
