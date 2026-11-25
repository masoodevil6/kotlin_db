# ترتیب ثبت و اجرای Migrationها

## جای‌گذاری Migration در Version

هر بار `migration<MigrationClass>()` یک ثبت جدید به همان نقطه از فهرست Version اضافه می‌کند. Migrationهای مستقیم و Migrationهای داخل Group در یک فهرست ترتیبی قرار می‌گیرند. هر Migration باید هویت یکتای خود را از `MigrationItemTag` بگیرد و کلاس آن سازندهٔ بدون آرگومان داشته باشد؛ جزئیات تعریف کلاس در [راهنمای تعریف Migration](../migration_define/define-migration.md) است.

```kotlin
val migrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
        migration<AddCustomerEmail>()
    }
}
```

## ترتیب UP و DOWN

در UP، Migrationهای هر Version دقیقاً به ترتیب ثبت اجرا می‌شوند. در DOWN، همان فهرست برعکس می‌شود تا آخرین تغییر ثبت‌شده ابتدا برگردد.

```text
Configuration:  CreateCustomers → AddCustomerEmail → AddCustomerIndex
UP:             CreateCustomers → AddCustomerEmail → AddCustomerIndex
DOWN:           AddCustomerIndex → AddCustomerEmail → CreateCustomers
```

گروه‌بندی این ترتیب را تغییر نمی‌دهد؛ فقط اعضا را به‌شکل درختی نگه می‌دارد و هنگام آماده‌سازی به ترتیب declaration پیمایش می‌شود. Migrationهای درون یک Version در این مسیر ترتیبی‌اند.

## یکتایی و تکرار

`MigrationConfiguration` نام تکراری Version را رد می‌کند و شناسهٔ تکراری Migration را در کل Configuration رد می‌کند؛ این بررسی شامل Migrationهای مستقیم، گروهی و Groupهای تو‌در‌تو است. شناسهٔ Migration از نام کلاس استخراج نمی‌شود، بلکه مقدار `MigrationItemTag` سازنده است.
