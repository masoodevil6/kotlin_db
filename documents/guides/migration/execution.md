# انجام عملیات Migration

برای اجرای migrationها، ابتدا تنظیم اتصال را در DefaultDatabaseConfig قرار دهید؛ بعد configurationای را که migrationهای واقعی را ثبت کرده به MigrationMigrator بدهید. نمونهٔ زیر از سازوکار تنظیمات اتصال و callback نتیجهٔ خود پروژه استفاده می‌کند:

~~~kotlin
import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.executor.manager.MigrationMigrator

fun main() {
    DefaultDatabaseConfig.config = DatabaseConfig(
        dbDomain = System.getenv("DB_DOMAIN") ?: "jdbc:mysql://127.0.0.1",
        dbPort = (System.getenv("DB_PORT") ?: "3306").toInt(),
        dbName = System.getenv("DB_NAME") ?: error("DB_NAME is required"),
        dbUserName = System.getenv("DB_USER") ?: error("DB_USER is required"),
        dbPassword = System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD is required"),
        dialect = DialectQuery.MARIA_DB,
        targetDatabaseVersion = MARIA_DB.Version(10, 4, 28),
    )

    val migrations = customerMigrationConfiguration()

    MigrationMigrator(migrations).migrate { result ->
        when (result) {
            is ExecuteResult.Success -> println("Migrations completed")
            is ExecuteResult.Failure -> throw result.exception
        }
    }
}
~~~

در برنامهٔ شما، customerMigrationConfiguration() باید configurationای برگرداند که migrationهای واقعی را ثبت کرده باشد؛ تابعی با همین نام در همان source set یا با import از package برنامه در دسترس باشد. مقدارهای DB_DOMAIN، DB_PORT، DB_NAME، DB_USER و DB_PASSWORD را برای محیط مقصد فراهم کنید و گذرواژه را در source code یا فایل tracked ننویسید. Dialect و targetDatabaseVersion را نیز با نسخهٔ واقعی پایگاه داده هماهنگ کنید. جزئیات ساخت DatabaseConfig در [راهنمای اتصال](../connection.md) آمده است.

MigrationMigrator در صورت ندادن executor، MigrationExecutor پیش‌فرض را می‌سازد. migrate مقدار را مستقیماً return نمی‌کند؛ نتیجهٔ نهایی از callback با نوع ExecuteResult<Unit> می‌رسد. Success یعنی migrationهای pending با موفقیت اجرا و ثبت شده‌اند؛ در Failure، exception اصلی در result.exception قرار دارد. نمونه عمداً Failure را دوباره پرتاب می‌کند تا فراخواننده آن را نادیده نگیرد.

Migrator شناسه‌های ثبت‌شده در history را دوباره اجرا نمی‌کند. هر migration اجرایی باید @MigrationId یکتا داشته باشد و up() آن دقیقاً یک operation تعریف کند. migrate() فقط up() را اجرا می‌کند؛ تعریف down() به‌تنهایی باعث اجرای rollback schema نمی‌شود و اجرای down مسیر جداگانه می‌خواهد.

هشدار: customerMigrationConfiguration() در فایل نمونهٔ MigrationRegistrationExample.kt صرفاً ساختار ثبت تکی و گروهی را نشان می‌دهد و up()/down() کلاس‌های آن خالی است؛ بنابراین آن fixture را برای اجرای واقعی به کار نبرید. برای اجرا، همان الگوی ثبت را روی migrationهای دارای operation واقعی اعمال کنید. تعریف Schema در [صفحهٔ Schemas](README.md#schemas) و خانواده‌های AST در [مثال‌های AST و Migration](ast-examples.md) آمده‌اند.
