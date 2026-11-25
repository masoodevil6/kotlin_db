# تعریف و استفاده از Migration Tagها

## انواع Tag

سه نوع Tag در Source تعریف شده است:

| نوع | کاربرد | اعتبارسنجی و ذخیره‌سازی |
|---|---|---|
| `MigrationVersionTag` | نام یک Version در Configuration | مقدار نباید خالی باشد و حداکثر ۲۵۵ نویسه است؛ در Tagهای سیستمی ذخیره می‌شود. |
| `MigrationItemTag` | هویت یک Migration | مقدار باید با قواعد `MigrationIdentity` سازگار باشد؛ در Tagهای سیستمی ذخیره می‌شود. |
| `MigrationGroupTag` | گروه‌بندی Migrationها در Configuration | مقدار نباید خالی باشد؛ برای سازمان‌دهی است و به‌عنوان Tag پایدار ذخیره نمی‌شود. |

هویت Migration از `MigrationItemTag`ای می‌آید که به سازندهٔ کلاس Migration داده می‌شود؛ نام کلاس یا `@MigrationId` هویت آن را تعیین نمی‌کند.

## تعریف Tagها

معمولاً Tagها را در یک `object` و به‌شکل enum نگه می‌دارند:

```kotlin
import gog.my_project.data_base_manager.facade.MigrationTagDefinition
import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

object AppMigrationTags : MigrationTagDefinition {
    enum class Versions(override val value: String) : MigrationVersionTag {
        V1("customers_v1"),
        V2("customer_email_v2"),
    }

    enum class Groups(override val value: String) : MigrationGroupTag {
        CUSTOMERS("customers"),
    }

    enum class Items(override val value: String) : MigrationItemTag {
        CREATE_CUSTOMERS("create_customers"),
        ADD_CUSTOMER_EMAIL("add_customer_email"),
    }

    override val versions get() = Versions.entries
    override val groups get() = Groups.entries
    override val items get() = Items.entries
}
```

مقدار `MigrationItemTag` باید با الگوی زیر منطبق باشد و بیش از ۲۵۵ نویسه نباشد:

```text
^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$
```

نمونه‌های معتبر: `create_customers`، `add_email2`. نمونه‌های نامعتبر: `CreateCustomers`، `_create_customers`، `create__customers`.

Tagهای یک دسته باید یکتا باشند. همچنین هر شناسهٔ Migration در کل `MigrationConfiguration` باید فقط یک بار ثبت شود.
