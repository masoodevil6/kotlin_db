# گروه‌بندی Migrationها

## کاربرد Group

`migrationGroup(groupTag) { ... }` چند ثبت را زیر یک برچسب سازمانی درون Version قرار می‌دهد. Group می‌تواند Migration منفرد یا Group تو‌در‌تو داشته باشد و Tag آن برای سازمان‌دهی Configuration است؛ Group به‌عنوان Tag پایدار در جدول Tagهای سیستم ذخیره نمی‌شود.

```kotlin
val migrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V2) {
        migrationGroup(AppMigrationTags.Groups.CUSTOMERS) {
            migration<AddCustomerEmail>()
        }
    }
}
```

## Group تو‌در‌تو و ترتیب اعضا

ثبت‌های مستقیم و اعضای Group می‌توانند کنار هم قرار گیرند. ترتیب همان ترتیب declaration است؛ هنگام آماده‌سازی Version، Groupها به‌ترتیب باز می‌شوند و Migrationهای داخلشان در همان ترتیب وارد فهرست اجرا می‌شوند.

```kotlin
migrationVersion(AppMigrationTags.Versions.V2) {
    migration<AddCustomerEmail>()

    migrationGroup(AppMigrationTags.Groups.CUSTOMERS) {
        migration<AddCustomerPhone>()

        migrationGroup(AppMigrationTags.Groups.CUSTOMER_CONTACTS) {
            migration<AddCustomerAddress>()
        }
    }

    migration<AddCustomerIndex>()
}
```

در مثال، ترتیب مؤثر `AddCustomerEmail`، سپس `AddCustomerPhone`، `AddCustomerAddress` و در پایان `AddCustomerIndex` است. Group ترتیب اجرا را جداگانه یا موازی نمی‌کند و مرز Version جدیدی هم نمی‌سازد.
