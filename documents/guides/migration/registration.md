# تعریف Migration و ثبت Configuration

## وابستگی و Importها

برای مصرف API عمومی به Aggregate `:data_base:migration` وابسته شوید. در یک build چندماژوله:

```kotlin
dependencies {
    implementation(project(":data_base:migration"))
}
```

مختصات Artifact بیرونی از تنظیمات این Repository قابل تأیید نیست. نمونهٔ Importهای موردنیاز:

```kotlin
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_manager.facade.MigrationTagDefinition
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.facade.migration
import gog.my_project.data_base_migration.facade.migrationConfiguration
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
```

`@MigrationId` در API فعلی برای تعیین هویت استفاده نمی‌شود؛ هویت Migration از `MigrationItemTag`ای می‌آید که به سازندهٔ `Migration` داده می‌شود. شناسه باید با قواعد `MigrationIdentity` سازگار باشد. Constructor بدون آرگومانِ کلاس Migration هنگام ساخت Configuration برای خواندن هویت فراخوانی می‌شود و Instance اجرایی نیز پیش از اجرای عملیات آماده می‌شود؛ بنابراین Constructor باید بدون اثر جانبی باشد. `up()` و `down()` در زمان ساخت Configuration اجرا نمی‌شوند.

## تعریف Tagها و Migration

Version tag، item tag و group tag نوع‌های جدا هستند. Version و item tag در جداول سیستمی ثبت می‌شوند؛ group فقط برای سازمان‌دهی ثبت‌هاست و به‌عنوان Tag پایدار ذخیره نمی‌شود.

```kotlin
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
        ADD_EMAIL("add_customer_email"),
    }

    override val versions get() = Versions.entries
    override val groups get() = Groups.entries
    override val items get() = Items.entries
}

class CreateCustomers : Migration(AppMigrationTags.Items.CREATE_CUSTOMERS) {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(SchemaDataType.IntType)
                notNull()
                autoIncrement()
                primaryKey()
            }
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable { tableName("customers") })
    }
}

class AddCustomerEmail : Migration(AppMigrationTags.Items.ADD_EMAIL) {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.addColumn {
            tableName("customers")
            name("email")
            dataType(SchemaDataType.VarcharType(255))
            notNull()
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropColumn {
            tableName("customers")
            name("email")
        })
    }
}
```

هر `MigrationDefinition` فعلی یک `SchemaOperation` دارد. `migration { ... }` تعریف operation را می‌سازد؛ اجرای آن را به‌تنهایی آغاز نمی‌کند.

## Versionها و Groupها

ترتیب Versionها و Migrationهای درون هر Version از ترتیب Registration گرفته می‌شود. DSL ثبت می‌تواند Migration منفرد یا گروهی داشته باشد:

```kotlin
val customerMigrations: MigrationConfiguration = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
    }
    migrationVersion(AppMigrationTags.Versions.V2) {
        migrationGroup(AppMigrationTags.Groups.CUSTOMERS) {
            migration<AddCustomerEmail>()
        }
    }
}
```

Configuration ترتیب ثبت‌ها را نگه می‌دارد و هویت‌های تکراری را رد می‌کند. هر Migration باید constructor بدون آرگومان داشته باشد تا DSL بتواند هویت آن را بخواند.

## اتصال Configuration به Registration

Configuration به همان Database Registration متصل می‌شود. برای هر دیتابیس Configuration مستقل بسازید؛ هنگام گرفتن Handle از Registration همان Configuration خوانده می‌شود.

```kotlin
object MainDatabase : DatabaseKey
data class AnalyticsDatabase(val region: String) : DatabaseKey

val mainMigrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migration<CreateCustomers>()
    }
}

val analyticsMigrations = migrationConfig {
    migrationVersion(AppMigrationTags.Versions.V1) {
        migrationGroup(AppMigrationTags.Groups.CUSTOMERS) {
            migration<CreateCustomers>()
        }
    }
}

val manager: DatabaseManagerApi = DatabaseManager {
    migrationTags = AppMigrationTags
    database(MainDatabase) {
        config = mainDatabaseConfig
        migrationConfiguration = mainMigrations
    }
    database(AnalyticsDatabase("eu-west")) {
        config = analyticsDatabaseConfig
        migrationConfiguration = analyticsMigrations
    }
}

val mainHandle = manager[MainDatabase]
val analyticsHandle = manager[AnalyticsDatabase("eu-west")]
```

`migrationConfiguration` یک property نوشتنی روی registration builder است. مسیر معمول مصرف‌کننده از overload بدون Configuration استفاده می‌کند:

```kotlin
val result = mainHandle.migration {
    migrate()
}.toValue().toValue()
```

دو بار `toValue()` لازم است چون `migration {}` نتیجهٔ block را در `ExecuteResult` بیرونی می‌پیچد و خود فرمان نیز `ExecuteResult` برمی‌گرداند. Overload دارای Configuration صریح هم وجود دارد، اما برای Configuration متصل‌شده به Registration لازم نیست.

نمونهٔ کامل دو Database Registration با Tagها و Configurationهای مستقل در [example](../../../example/README.md) است. وضعیت قراردادهای عمومی و محدودهٔ API را در [راهنمای اجرا](execution.md) بخوانید.
