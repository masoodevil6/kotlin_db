# قابلیت‌های کتابخانه

این سند نمای کلی از قابلیت‌هایی است که در کد فعلی مخزن `my_project` پیاده‌سازی شده‌اند. هدف آن معرفی زیرسیستم‌های کتابخانه، APIهای موجود، مسیر اجرای عملیات و مرزهایی است که نباید با قابلیت‌های آینده اشتباه گرفته شوند.

> این مستند بر اساس ساختار و کد موجود در مخزن نوشته شده است؛ به‌تنهایی تضمین‌کنندهٔ انتشار نسخهٔ پایدار، سازگاری با همهٔ نسخه‌های پایگاه‌داده یا پشتیبانی رسمی از همهٔ dialectها نیست.

## نمای کلی

این مخزن یک پروژهٔ چندماژولی Kotlin/JVM است. بخش اصلی کتابخانه در `data_base` قرار دارد و دو زیرسیستم مهم در اختیار مصرف‌کننده می‌گذارد:

1. **Query Builder** برای ساخت، render و اجرای کوئری‌های SQL.
2. **Migration** برای تعریف و اجرای تغییرات schema و نگهداری history migrationها.

در کنار آن‌ها، ماژول‌های پایهٔ پیکربندی و اتصال، مدل‌های اولیه، مثال‌ها و ابزار محلی Help برای اجرای تست‌های تعریف‌شده وجود دارند.

```text
Database configuration
        ├── Query Builder → AST → Renderer → BuiltQuery → Query execution
        └── Migration DSL → AST → Dialect/Renderer → MigrationExecutor
                                                   └── MigrationMigrator / history
```

## ماژول‌های اصلی

| ماژول | مسئولیت |
|---|---|
| `data_base:core` | پیکربندی پایگاه‌داده، dialect و نسخهٔ هدف، مدل‌های پایه، پارامترهای SQL و `BuiltQuery`. |
| `data_base:manager:connection` | ساخت JDBC connection بر اساس `DefaultDatabaseConfig`. |
| `data_base:manager:execute` | مرز اجرای JDBC و عملیات SELECT، INSERT، UPDATE، DELETE و DDL. |
| `data_base:query:api` | قراردادهای DSL مربوط به Query Builder. |
| `data_base:query:ast` | ساختارهای AST برای عملیات query. |
| `data_base:query:builder` | پیاده‌سازی fluent builderها. |
| `data_base:query:dialect` | قراردادهای renderer و مدل‌های دادهٔ render. |
| `data_base:query:renderer` | تبدیل AST کوئری به SQL؛ renderer موجود MySQLمحور است. |
| `data_base:query:executor` | اتصال Query Builder به renderer و executorهای JDBC. |
| `data_base:query:example` | نمونه‌های SELECT، INSERT، UPDATE و DELETE. |
| `data_base:migration:api` | قراردادهای migration، هویت و registration. |
| `data_base:migration:params` | انواع دادهٔ ستون‌ها و پارامترهای migration. |
| `data_base:migration:ast` | AST عملیات migration و اجزای schema. |
| `data_base:migration:builder` | DSL ساخت operation و تعریف migration. |
| `data_base:migration:dialect` | قرارداد dialect و capabilityهای migration renderer. |
| `data_base:migration:renderer` | تولید SQL مربوط به migration و انتخاب dialect. |
| `data_base:migration:executor` | اجرای DDL، Migration State Store و `MigrationMigrator`. |
| `data_base:migration:example` | مثال‌های مستقل برای تعریف و render کردن migrationها. |
| `data_base:models` و `data_base:models:eloquent` | مدل پایه و annotationهای اولیهٔ نگاشت مدل و ستون. |

ساختار کامل Gradle در [`settings.gradle.kts`](../settings.gradle.kts) ثبت شده است.

## پیکربندی و اتصال به پایگاه‌داده

### DatabaseConfig

`DatabaseConfig` شامل آدرس میزبان، پورت اختیاری، نام پایگاه‌داده، نام کاربری، گذرواژه، dialect و نسخهٔ هدف اختیاری است. مقدار جاری برنامه از `DefaultDatabaseConfig.config` خوانده می‌شود.

اتصال JDBC در `DatabaseConnection` با `DriverManager` ساخته می‌شود. در پیاده‌سازی فعلی connection pool عمومی وجود ندارد؛ مقدار `dbPoolSize` به‌تنهایی به معنی فعال بودن pool نیست.

### Dialect و نسخه

مدل پیکربندی، dialectهای MySQL و MariaDB و نوعی برای مشخص‌کردن نسخهٔ هدف database دارد. انتخاب dialect در renderer انجام می‌شود و برخی رفتارهای migration می‌توانند از نسخهٔ هدف تأثیر بگیرند.

برای `RENAME COLUMN`، executor اطلاعات server را از مرز query execution می‌خواند و بر اساس نسخهٔ واقعی و راهبرد dialect، rename بومی یا مسیر سازگار با `CHANGE COLUMN` را انتخاب می‌کند. اگر نسخهٔ هدف صریح تنظیم شده باشد، محصول آن با محصول server مقایسه می‌شود.

**محدودیت سازگاری:** مخزن یک حداقل نسخهٔ رسمی و عمومی برای MySQL یا MariaDB تعیین نکرده است. وجود کلاس dialect به معنی اثبات سازگاری همهٔ operationها با همهٔ نسخه‌ها یا engineها نیست. Query renderer فعلی MySQLمحور است؛ ادعای پشتیبانی یکسان Query Builder از MariaDB در این سند نمی‌شود.

## Query Builder

Query Builder یک DSL مبتنی بر APIهای تایپ‌شده و AST دارد. ساخت query و اجرای آن دو مرحلهٔ جدا هستند: builder ساختار را می‌سازد، renderer SQL و پارامترها را تولید می‌کند، سپس executor آن را به JDBC می‌سپارد.

### SELECT

قابلیت‌های موجود در API شامل موارد زیر است:

* انتخاب ستون، نام‌گذاری alias و استفاده از `tableAttribute` برای عبارت‌های ستونی.
* توابع تجمیعی `SUM`، `COUNT`، `AVG`، `MIN` و `MAX`.
* تعیین جدول و alias آن.
* `INNER JOIN`، `LEFT JOIN` و `RIGHT JOIN` همراه شرط join.
* `WHERE` با گروه‌بندی شرط‌ها و منطق `AND` و `OR`.
* عملگرهای برابری و نابرابری، مقایسه، `LIKE`/`NOT LIKE`، `IN`/`NOT IN`، `BETWEEN`/`NOT BETWEEN` و `IS`/`IS NOT`؛ API همچنین نام `CONTAINS` را ارائه می‌کند و رفتار نهایی هر عملگر تابع renderer است.
* مقدارهای ورودی نام‌دار و مجموعه‌مقدارها برای شرط‌هایی مانند `IN`.
* `GROUP BY`، `ORDER BY` صعودی/نزولی، `LIMIT` و `OFFSET`.
* تعریف CTE با `WITH`؛ ورودی می‌تواند از طریق query تو‌در‌تو یا قرارداد CTE موجود ارائه شود.

`tableAttribute` امکان قراردادن عبارت SQL مانند `concat(...)` در SELECT را نیز می‌دهد. مقدارهای ورودی query را با پارامتر bind کنید و عبارت یا شناسه‌ای را که از ورودی غیرقابل‌اعتماد آمده مستقیماً به این API ندهید.

### INSERT، UPDATE و DELETE

* **INSERT:** تعیین جدول و افزودن مقدار برای ستون‌ها.
* **UPDATE:** تعیین جدول، مقدارهای جدید و شرط `WHERE`.
* **DELETE:** تعیین جدول/هدف حذف و شرط `WHERE`.

### پارامترها و نتیجهٔ اجرا

مقدارهای query در `SqlParameter` و `BuiltQuery` نگهداری می‌شوند و executor آن‌ها را به `PreparedStatement` متصل می‌کند. API اجرای query callback نتیجهٔ typed از نوع `ExecuteResult.Success` یا `ExecuteResult.Failure` می‌دهد:

| عملیات | مقدار نتیجه |
|---|---|
| SELECT | `ResultSet` |
| INSERT | شناسهٔ تولیدشده از نوع `Long`، در صورت ارائه توسط driver |
| UPDATE / DELETE | تعداد ردیف‌های متأثر از نوع `Int` |
| DDL | نتیجهٔ موفقیت از نوع `Boolean` |

`QueryBuilderExecutor` امکان دریافت اطلاعات SQL و پارامترهای renderشده را از طریق callback اطلاعات query فراهم می‌کند.

**نکتهٔ چرخهٔ منابع:** `ResultSet` مربوط به SELECT به statement و connection وابسته است. ردیف‌ها را داخل callback اجرا بخوانید و دادهٔ عادی را برای استفادهٔ بعدی نگه دارید؛ `ResultSet` را از callback خارج نکنید.

### نمونهٔ ساخت SELECT

```kotlin
val query = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column { tableColumn("u", "id") }
            alias("user_id")
        }
    }
    .table { table("users", "u") }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("u", "id") }
                operationEqual()
                sideValue("user_id", 42)
            }
        }
    }
```

برای نمونه‌های اجرایی بیشتر، به `data_base/query/example` مراجعه کنید.

## Migration و تعریف schema

زیرسیستم Migration قرارداد، AST، builder، dialect/renderer و executor را از هم جدا می‌کند. operationهای زیر در API و dispatcher فعلی وجود دارند.

### عملیات schema

* ساخت، حذف و تغییر نام جدول: `CREATE TABLE`، `DROP TABLE` و `RENAME TABLE`.
* افزودن، حذف، تغییر نام و تغییر تعریف ستون: `ADD COLUMN`، `DROP COLUMN`، `RENAME COLUMN` و `MODIFY COLUMN`.
* تعریف ستون با نوع داده، مقدار پیش‌فرض، nullable بودن، auto-increment و primary-key بودن.
* ساخت جدول با `IF NOT EXISTS`.
* تعریف constraintهای جدول: primary key، unique، index، full-text index و foreign key.
* ایجاد و حذف Foreign Key؛ Foreign Key می‌تواند چندستونی باشد و actionهای `CASCADE`، `RESTRICT`، `NO_ACTION` و `SET_NULL` را می‌پذیرد.

### انواع دادهٔ ستون

انواع تعریف‌شده در migration params عبارت‌اند از:

`BooleanType`، `IntType`، `DecimalType`، `VarcharType`، `TextType`، `JsonType`، `DateType`، `TimeType`، `DateTimeType` و `TimestampType`.

`DecimalType` precision و scale می‌گیرد. `DateTimeType` و `TimestampType` precision اعشاری اختیاری در بازهٔ صفر تا شش دارند. پشتیبانی نهایی SQL به dialect و نسخهٔ هدف مربوط است.

### Indexها

* `CREATE INDEX` معمولی روی یک ستون؛ روش اختیاری `BTREE` یا `HASH` با `USING`.
* `DROP INDEX` مستقل از نوع یا تعداد ستون‌های index.
* `CREATE UNIQUE INDEX` مستقل روی یک ستون.
* `CREATE MULTI-COLUMN INDEX` روی دو یا چند ستون؛ ترتیب ستون‌ها حفظ می‌شود.
* `CREATE FULLTEXT INDEX` به‌عنوان operation مستقل.
* `CREATE SPATIAL INDEX` به‌عنوان operation مستقل و تک‌ستونی.

این APIها به یک گزینهٔ عمومی و دلخواه برای indexها تبدیل نشده‌اند؛ قابلیت‌های مختص هر operation جداگانه تعریف می‌شوند. امکان واقعی اجرای index به نوع ستون، engine و نسخهٔ database وابسته است و renderer جای database را برای بررسی این محدودیت‌ها نمی‌گیرد.

### shorthandهای ستون زمانی

در `CREATE TABLE`:

* `timestamps()` دو ستون nullable از نوع `DATETIME` با نام‌های `created_at` و `updated_at` می‌افزاید.
* `softDeletes()` یک ستون nullable از نوع `DATETIME` با نام `deleted_at` می‌افزاید.

این shorthandها فقط تعریف schema را گسترش می‌دهند. زمان را خودکار هنگام INSERT/UPDATE ثبت نمی‌کنند و رفتار ORM برای حذف نرم، restore یا global scope ایجاد نمی‌کنند.

## Migration Identity، Registration و اجرا

### Identity و Registration

Identity هر migration روی declaration خودش با `@MigrationId` مشخص می‌شود؛ ثابت‌های `migrationTags` برای جلوگیری از پراکندگی رشته‌ها در source استفاده می‌شوند:

```kotlin
object migrationTags {
    const val create_users = "create_users"
}

@MigrationId(migrationTags.create_users)
class CreateUsers : Migration {
    override fun up() = migration { /* operation */ }
    override fun down() = migration { /* rollback definition */ }
}
```

`MigrationIdentity` مقدار annotation را بدون trim یا تغییر حروف اعتبارسنجی می‌کند. grammar فعلی شناسه حروف کوچک ASCII، رقم و underscore است: شناسه باید با حرف کوچک شروع شود، underscore تکراری یا انتهایی نداشته باشد و حداکثر ۲۵۵ نویسه باشد.

Registration به‌شکل صریح و به ترتیب پیکربندی انجام می‌شود:

```kotlin
val configuration = migrationConfig {
    migration<CreateUsers>()
    migrationGroup("users") {
        migration<AddUsersEmail>()
    }
}
```

ترتیب registration و ترتیب داخل group حفظ می‌شوند. Group در نسخهٔ فعلی ساختار پیکربندی است و به‌خودی‌خود transaction boundary یا سیاست اجرای جداگانه ایجاد نمی‌کند. ساخت configuration migration instance نمی‌سازد و `up()`/`down()` را اجرا نمی‌کند.

### MigrationMigrator و history

`MigrationMigrator` ابتدا State Store را آماده می‌کند، هویت‌های ثبت‌شده را با history مقایسه می‌کند و migrationهای pending را به ترتیب registration اجرا می‌کند. اگر migration pending وجود داشته باشد، batch بعدی را می‌سازد و همان batch را برای migrationهای موفق همان invocation به‌کار می‌برد. پس از موفقیت operation، هویت و batch در `system_migration` ثبت می‌شوند. شکست اجرای یک migration یا ثبت history ادامهٔ همان invocation را متوقف می‌کند؛ موفقیت‌هایی که پیش‌تر ثبت شده‌اند باقی می‌مانند. `MigrationExecutor` مسئول dispatch و اجرای operation می‌ماند.

در پیاده‌سازی فعلی، برای instantiate کردن migration ثبت‌شده، کلاس باید سازندهٔ عمومی بدون آرگومان داشته باشد. این الزام از Runner می‌آید؛ registration به‌تنهایی سازنده را اجرا نمی‌کند.

`MigrationDefinition` عمومی می‌تواند چند operation را نگه دارد؛ با این حال، Migrator برای `up()` یک migration ثبت‌شده دقیقاً یک operation را لازم می‌داند. این invariant مربوط به اجرای Runner است، نه محدودکردن خود `MigrationDefinition`.

**مرزهای فعلی اجرا:** `down()` در چرخهٔ v1.20 اجرا نمی‌شود؛ وجود آن rollback خودکار را تضمین نمی‌کند. transaction، rollback، locking، retry، checksum، filesystem discovery و DI container در این چرخه ارائه نشده‌اند. اجرای DDL و ثبت history نیز transaction redesign یا تضمین atomic بودن مشترک اضافه نمی‌کند.

### Migration State و سازگاری persistence

`SystemMigrationStateStore` جدول `system_migration` را bootstrap می‌کند، history را می‌خواند و نتیجهٔ موفق را ثبت می‌کند. مقدار migration به‌صورت رشتهٔ identity ذخیره می‌شود؛ نام کلاس، package، فایل یا Group بخشی از identity پایدار نیست.

سازگاری دقیق برابری شناسه‌ها با collationهای server از پیش برای یک compatibility matrix عمومی تضمین نشده است. برای MySQL/MariaDB یا نسخه‌های مختلف، در این README ادعای برابری persistence یا حداقل نسخه ایجاد نمی‌شود.

### نمونهٔ ثبت migration

```kotlin
@MigrationId(migrationTags.create_users)
class CreateUsers : Migration {
    override fun up() = migration {
        createTable {
            table { tableName("users") }
            addColumn {
                name("id")
                dataType(IntType())
                primaryKey()
                autoIncrement()
            }
        }
    }

    override fun down() = migration {
        dropTable { tableName("users") }
    }
}
```

برای registration و اجرای واقعی، مثال‌های `data_base/migration/example` و تست‌های integration مخزن را ببینید.

## مدل‌ها و annotationهای پایه

`data_base:core` قرارداد پایهٔ `IModelBase` و annotationهای `@QBTable` و `@QBColumn` را دارد. `data_base:models:eloquent` نیز `IModel` و `BaseModel` را به‌عنوان پایهٔ مدل ارائه می‌کند.

این قسمت در وضعیت فعلی زیرساخت/نمونهٔ نگاشت مدل است؛ وجود نام `eloquent` به معنی ارائهٔ کامل ORM نیست. این مستند رفتارهای کامل Active Record، رابطه‌های ORM، lifecycle hook یا lazy loading را ادعا نمی‌کند.

## مثال‌ها و ابزار توسعه

### Example modules

`data_base:query:example` نمونه‌های ساخت query و `data_base:migration:example` نمونه‌های migration را جدا از API اصلی نگه می‌دارند. این ماژول‌ها برای دیدن DSL و render خروجی مفیدند و خودشان API production محسوب نمی‌شوند.

### تست‌های integration پایگاه‌داده

ماژول migration تست‌های واحد و integration دارد. integration testهای MySQL با Gradle propertyهای صریح پیکربندی می‌شوند؛ بدون database و property معتبر، compile شدن ماژول به معنی اجرای موفق integration نیست. گزینهٔ refresh متعلق به lifecycle تست است و جزئی از `DatabaseConfig` یا رفتار production migration نیست.

### ابزار محلی Help

پوشهٔ `help/` یک ابزار توسعهٔ محلی است، نه بخشی از API کتابخانه. این ابزار Projectهای Gradle و test definitionهای Help را می‌خواند، تست انتخاب‌شده را از Gradle اجرا می‌کند، JUnit/report را نمایش می‌دهد و در صورت وجود Runtime artifact واقعی، workflow مشاهده‌شده را رندر می‌کند. نمودار runtime از دادهٔ تست می‌آید؛ از متن JUnit یا log، گام‌های اجرا حدس زده نمی‌شوند.

## محدودیت‌ها و نکته‌های مهم

* موتور SQL و schema در کد فعلی MySQLمحور است؛ برای MariaDB فقط مواردی را پشتیبانی‌شده بدانید که dialect/renderer متناظرشان در همان operation حاضر است. SQLite پیاده‌سازی نشده است.
* minimum version و compatibility matrix رسمی database در repository تعریف نشده است.
* قابلیت‌های dialect-specific مانند engine optionهای دلخواه، comment/visibility عمومی index، prefix length، parser، algorithm/lock و expression index را این API عمومی پوشش نمی‌دهد، مگر در operation خاصی که صریحاً وجود دارد.
* Builder یا renderer برای محدودیت واقعی engine، storage engine، schema و صلاحیت دادهٔ موجود introspection انجام نمی‌دهد؛ اعتبار نهایی DDL را server تعیین می‌کند.
* Migration runner فعلی اجرای forward و history را فراهم می‌کند، اما rollback یا تراکنش گروهی ندارد.
* Annotationهای مدل فعلی پایه هستند و نباید آن‌ها را با ORM کامل اشتباه گرفت.
* نام‌گذاری packageها در بخشی از source شامل شکل‌هایی مانند `executer` است؛ هنگام import از package واقعی تعریف‌شده در کد استفاده کنید.

## نقشهٔ پوشهٔ `plans`

این پوشه design recordها و planهای نسخه‌بندی‌شده را نگه می‌دارد. وجود یک plan در اینجا به‌تنهایی به معنی پیاده‌سازی‌شدن قابلیت نیست.

* [`migration/`](migration/) — contract و تکامل زیرسیستم migration، state و integration testهای آن.
* [`test/`](test/) — plan ابزار تست/Help و workflow گزارش اجرا.

برای بررسی وضعیت یک قابلیت، source module، testها و Status همان plan را با هم بخوانید؛ عنوان یا شمارهٔ نسخه به‌تنهایی وضعیت انتشار را نشان نمی‌دهد.

## اجرای build و تست

پروژه از Gradle Wrapper استفاده می‌کند. ماژول‌های Kotlin/JVM این مخزن toolchain نسخهٔ ۲۴ درخواست می‌کنند؛ task دقیق را از Gradle استفاده کنید، برای نمونه:

```powershell
./gradlew.bat :data_base:query:example:test
./gradlew.bat :data_base:migration:example:test
./gradlew.bat :data_base:migration:executor:test
```

برای integration testهای MySQL، propertyهای اتصال و refresh را فقط با مقادیر معتبر محیط تست تنظیم کنید. تست‌های integration را با unit testهای بدون database یکی نگیرید.
