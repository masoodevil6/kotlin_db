# Register، گروه‌بندی و فهرست migrationها

برای ثبت یک migration، کلاس باید `Migration` را پیاده‌سازی کند و `@MigrationId` با شناسهٔ یکتا داشته باشد. سپس `migration<T>()` آن را به‌صورت یک مورد مستقل در `MigrationConfiguration` قرار می‌دهد. برای گروه‌بندی، `migrationGroup("نام گروه") { ... }` چند migration را با ترتیب نوشته‌شده در یک گروه ثبت می‌کند. می‌توان ثبت مستقل، گروه و ثبت مستقل دیگری را در یک configuration کنار هم قرار داد:

~~~kotlin
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.builder.migrationConfig

object migrationTags {
    const val create_customers = "create_customers"
    const val add_customer_email = "add_customer_email"
}

@MigrationId(migrationTags.create_customers)
class CreateCustomersRegistrationExample : Migration {
    override fun up(): MigrationDefinition = migration { }
    override fun down(): MigrationDefinition = migration { }
}

@MigrationId(migrationTags.add_customer_email)
class AddCustomerEmailRegistrationExample : Migration {
    override fun up(): MigrationDefinition = migration { }
    override fun down(): MigrationDefinition = migration { }
}

fun customerMigrationConfiguration() = migrationConfig {
    migration<CreateCustomersRegistrationExample>()
    migrationGroup("customers") {
        migration<AddCustomerEmailRegistrationExample>()
    }
}
~~~

شکل این registration با `customerMigrationConfiguration()` در `registration/MigrationRegistrationExample.kt` یکسان است؛ آن فایل برای نشان‌دادن ثبت تکی و گروهی است. `MigrationConfiguration.registrations` فهرست top-level را به همان ترتیب نگه می‌دارد. هر عضو یا `SingleMigration` است که یک `RegisteredMigration` دارد، یا `MigrationGroup` که نام گروه و فهرست `migrations` را نگه می‌دارد. فهرست‌ها snapshot تغییرناپذیرند. کلاس‌های مثال registration، `up()` و `down()` خالی دارند و صرفاً شکل ثبت را نشان می‌دهند؛ برای اجرای `MigrationMigrator` هر `up()` باید دقیقاً یک operation داشته باشد.

برای نمایش همهٔ registrationها به شکل یک فهرست خطی، می‌توان گروه‌ها را هنگام خواندن flatten کرد:

~~~kotlin
import gog.my_project.data_base.migration.api.interfaces.MigrationGroup
import gog.my_project.data_base.migration.api.interfaces.SingleMigration

val configuration = customerMigrationConfiguration()
val registeredMigrations = configuration.registrations.flatMap { registration ->
    when (registration) {
        is SingleMigration -> listOf(registration.migration)
        is MigrationGroup -> registration.migrations
    }
}

registeredMigrations.forEach { registered ->
    println(registered.id.value + " -> " + registered.migrationClass.qualifiedName)
}
~~~

این فهرست، کلاس ثبت‌شده و شناسهٔ آن را نشان می‌دهد؛ migration را instantiate نمی‌کند، `up()` یا `down()` را فراخوانی نمی‌کند و SQL اجرا نمی‌کند. شناسه از مقدار `@MigrationId` می‌آید و باید یکتا، غیرخالی، حداکثر ۲۵۵ نویسه و مطابق الگوی `^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$` باشد. ثبت شناسهٔ تکراری در هر نقطه از درخت registration ــ حتی میان یک گروه و یک مورد مستقل ــ با `IllegalArgumentException` رد می‌شود. نام گروه نیز نباید خالی یا فقط whitespace باشد.

`MigrationMigrator.migrate(...)` registrationها را به ترتیب configuration باز می‌کند، شناسه‌هایی را که در تاریخچه ثبت شده‌اند کنار می‌گذارد و `up()` موارد باقی‌مانده را اجرا می‌کند. در مسیر فعلی migrator، هر `up()` باید دقیقاً یک operation داشته باشد و کلاس هنگام اجرا با constructor عمومی بدون آرگومان ساخته می‌شود. تعریف `down()` در API وجود دارد، اما این متد در مسیر `migrate()` فراخوانی نمی‌شود. همچنین API عمومی فعلی فهرست جداگانه‌ای برای migrationهای اعمال‌شده در پایگاه داده ارائه نمی‌کند؛ `configuration.registrations` فهرست تعریف‌شده در کد است، نه تاریخچهٔ اجرای دیتابیس.
