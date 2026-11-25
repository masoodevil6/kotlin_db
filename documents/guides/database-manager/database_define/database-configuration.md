# تنظیمات اتصال پایگاه داده

برای هر Registration یک `DatabaseConfig` بسازید. شکل پیشنهادی، جدا نگه‌داشتن مشخصات اتصال، Pool و پلتفرم است:

```kotlin
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePoolConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery

val mainConfig = DatabaseConfig(
    connection = DatabaseConnectionConfig(
        dbDomain = "jdbc:mysql://127.0.0.1",
        dbPort = 3306,
        dbName = "main_app",
        dbUserName = System.getenv("MAIN_DB_USER")
            ?: error("MAIN_DB_USER is required"),
        dbPassword = System.getenv("MAIN_DB_PASSWORD")
            ?: error("MAIN_DB_PASSWORD is required"),
    ),
    pool = DatabasePoolConfig(maxPoolSize = 10),
    platform = DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
)
```

`DatabaseConnectionConfig` این مقادیر را دارد:

- `dbDomain`: پیشوند/نشانی JDBC، برای نمونه `jdbc:mysql://127.0.0.1`.
- `dbPort`: پورت اختیاری، مانند `3306`.
- `dbName`: نام پایگاه داده.
- `dbUserName` و `dbPassword`: مشخصات احراز هویت.

### ساخت JDBC URL

`DatabaseConnectionConfig.getDbUrl()` نشانی را با چسباندن `dbDomain`، پورت اختیاری و نام پایگاه داده می‌سازد: اگر پورت وجود داشته باشد `:<port>` به Domain افزوده می‌شود و سپس `/<dbName>` اضافه می‌شود. این متد نشانی را نرمال نمی‌کند؛ `dbDomain` را بدون slash انتهایی بدهید.

```kotlin
val connection = DatabaseConnectionConfig(
    dbDomain = "jdbc:mysql://127.0.0.1",
    dbPort = 3306,
    dbName = "main_app",
    dbUserName = userName,
    dbPassword = password,
)

val jdbcUrl = connection.getDbUrl()
// jdbc:mysql://127.0.0.1:3306/main_app
```

در Provider پیش‌فرض، `DatabaseConnection.build()` این URL را همراه `dbUserName` و `dbPassword` جداگانه به `DriverManager.getConnection()` می‌دهد. بنابراین اطلاعات ورود را داخل URL یا متن Query قرار ندهید. `dialect` نیز قواعد SQL را برای Render انتخاب می‌کند؛ خودش Driver یا مقصد اتصال را انتخاب نمی‌کند.

`DefaultDatabaseConfig` و سازندهٔ بدون آرگومان برخی Executorهای قدیمی برای سازگاری باقی مانده‌اند، اما `DefaultDatabaseConfig` Deprecated است. برای کد جدید تنظیمات را به Registration بدهید یا یک `DatabaseContext` صریح بسازید. تنظیم واقعی Example ریشه در [راهنمای Example](../../../../example/README.md) توضیح داده شده است؛ Example مستقل `data_base:migration:example` صرفاً نمونه‌های AST را چاپ می‌کند و از این متغیرهای اتصال استفاده نمی‌کند.

`DatabasePoolConfig.maxPoolSize` مقدار تنظیم‌شدهٔ Pool را نگه می‌دارد و مقدار پیش‌فرض آن `10` است؛ اما Provider فعلی از این مقدار برای ساخت یا مدیریت Pool استفاده نمی‌کند و Connectionهای JDBC مستقل باز می‌کند. بنابراین این گزینه را فعلاً تضمین‌کنندهٔ سقف Connectionهای هم‌زمان تلقی نکنید. `DatabasePlatformConfig.dialect` نوع Dialect را تعیین می‌کند؛ مقدار پیش‌فرض `DialectQuery.MY_SQL` است و `DialectQuery.MARIA_DB` نیز در Source تعریف شده است. `targetDatabaseVersion` اختیاری است و فقط وقتی به آن نیاز دارید تنظیمش کنید.

مقادیر واقعی کاربر و گذرواژه را در Source یا مستندات ثبت نکنید. آن‌ها را از متغیر محیطی یا سامانهٔ امن تنظیمات برنامه بخوانید. `toString()` تنظیمات گذرواژه را redacted می‌کند، اما این جایگزین مراقبت از logها و پیام‌های خطا نیست.

`DatabaseConfig` هنوز سازنده و propertyهای تخت قدیمی را برای سازگاری نگه می‌دارد؛ آن propertyها Deprecated هستند. برای کد جدید از `connection`، `pool` و `platform` استفاده کنید.
