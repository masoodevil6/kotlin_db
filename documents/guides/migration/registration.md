# تعریف و ثبت Migration

قراردادهای تعریف و ثبت Migration فعلاً باقی مانده‌اند. هر `Migration`، `up()` و `down()` را به‌شکل `MigrationDefinition` برمی‌گرداند؛ هر Definition دقیقاً یک `SchemaOperation` نگه می‌دارد. Annotation `@MigrationId` شناسهٔ پایدار Migration را مشخص می‌کند.

ثبت تکی و گروهی با `migrationConfig { ... }` انجام می‌شود:

~~~kotlin
import gog.my_project.data_base.core.annotations.migrations.MigrationId
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_migration.api.builder.migrationConfig
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_schema.Schema

@MigrationId("create_customers")
class CreateCustomers : Migration {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.createTable {
            table { tableName("customers") }
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable { tableName("customers") })
    }
}

val configuration = migrationConfig {
    migration<CreateCustomers>()
}
~~~

`MigrationConfiguration` ترتیب ثبت‌ها را حفظ می‌کند و registrationهای تکی، گروهی و نسخه‌ای را به‌شکل snapshot نگه می‌دارد. شناسه‌های تکراری در کل درخت registration رد می‌شوند.

این قراردادها در حال حاضر فقط ساخت و ثبت را پوشش می‌دهند. اجرای Migration و ذخیرهٔ history تا تکمیل بازنویسی runtime در دسترس نیست.
