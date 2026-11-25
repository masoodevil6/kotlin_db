# تعریف و ترتیب Versionها

## ساختار Version

یک Version با `migrationVersion(versionTag) { ... }` درون `migrationConfig { ... }` تعریف می‌شود. مقدار `MigrationVersionTag` نام Version است؛ مقدار باید non-blank و حداکثر ۲۵۵ نویسه باشد. نام تکراری Versionها در یک Configuration پذیرفته نمی‌شود.

```kotlin
val migrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
    }
    migrationVersion(AppMigrationTags.Versions.V2) {
        migration<AddCustomerEmail>()
    }
}
```

## ترتیب Versionها

ترتیب Versionها از ترتیب فراخوانی `migrationVersion(...)` در Configuration می‌آید. Engine مقدار نام‌ها را به‌صورت عددی یا واژگانی sort نمی‌کند؛ بنابراین Versionها را در ترتیب موردنظر ثبت کنید. این ترتیب مبنای انتخاب UP و DOWN و مقایسهٔ Target با Version فعلی است.

- UP از Version ثبت‌شدهٔ بعدی به سمت جلو حرکت می‌کند.
- `migrate(target)` Versionهای pending را تا خود Target، شامل آن، انتخاب می‌کند.
- DOWN از Version فعلی به عقب حرکت می‌کند.
- `rollback(target)` Versionهای جدیدتر از Target را برمی‌گرداند و خود Target را نگه می‌دارد.
- `rollback()` فقط جدیدترین Version اعمال‌شده را برمی‌گرداند.

```kotlin
// ترتیب این دو فراخوانی تعیین می‌کند v1 قبل از v2 باشد.
val migrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
    }
    migrationVersion(AppMigrationTags.Versions.V2) {
        migration<AddCustomerEmail>()
    }
}
```

Tag Version فقط نام را مشخص می‌کند؛ خود نام، ترتیب را تعیین نمی‌کند. قواعد و انواع Tagها در [راهنمای Tag](../migration_define/tags.md) هستند.
