# اجرای عملیات با Connection و Transaction

پس از ساختن `DatabaseManager` و گرفتن Handle یک Registration، برای اجرای کار از `DatabaseHandleApi.connection { ... }` یا `DatabaseHandleApi.transaction { ... }` استفاده کنید. هر دو متد یک `IQueryBuilderExecutor` محدود به callback در اختیار می‌گذارند؛ Connection خام JDBC را دریافت نمی‌کنید.

```kotlin
val database = manager[MainDatabase]

val connectionResult = database.connection { executor ->
    // Queryهای این Scope را با executor اجرا کنید.
    loadRows(executor)
}

val transactionResult = database.transaction { transaction ->
    // Queryهای این Transaction را با همین executor اجرا کنید.
    loadRows(transaction)
}
```

`loadRows` در این قطعه تابع برنامهٔ شماست که با executor داده‌شده Query می‌سازد و `ExecuteResult` برمی‌گرداند. ساخت Query و شکل کامل اجرای آن در [راهنمای Query Builder Executor](../../query-builder/executor/README.md) آمده است؛ تفاوت commit و rollback در [راهنمای Transaction](transaction.md) توضیح داده شده.

نتیجهٔ هر دو API از نوع `ExecuteResult<T>` است: `Success` مقدار nullable دارد و `Failure` خطا را نگه می‌دارد. Exception معمولی به Failure تبدیل می‌شود؛ `Error` پس از cleanup دوباره پرتاب می‌شود. Failure را نادیده نگیرید و نتیجه را به موفقیت یا مقدار پیش‌فرض تبدیل نکنید.

Operation مالک Scope، Connection، Transaction و cleanup است. executor callback را بیرون از همان callback نگه ندارید، برای دور زدن Scope از executor ریشه استفاده نکنید، و Connection را خودتان نبندید.

برای جزئیات سطح Operation دربارهٔ lifecycle اتصال و تراکنش، [راهنمای Operation: Connection و Transaction](../../operation/connection_transaction/README.md) را ببینید. برای تنظیم Handle و کلید دیتابیس نیز به [تعریف پایگاه داده](../database_define/README.md) و [کلیدهای پایگاه داده](../database_keys/README.md) مراجعه کنید.

برای زنجیره‌کردن Stageها، راهنمای [Connection Pipeline](connection_pipeline.md) یا [Transaction Pipeline](transaction_pipeline.md) را ببینید.

برای مشاهدهٔ رویدادهای Connection، SQL، Transaction و Migration روی یک Handle، [راهنمای Watch](watch.md) را ببینید.

برای اتصال Migration به Registration و آغاز `init()` و `migrate()`، [راهنمای شروع Migration از Handle](migration.md) را ببینید.

<a id="direct-query-execute"></a>
## اجرای مستقیم API سطح پایین `QueryExecute`

برای اجرای Query آماده‌شده با `BuiltQuery` یا `BoundQuery`، ماژول `data_base:manager:execute`، interface `IQueryExecute` و پیاده‌سازی `QueryExecute` را فراهم می‌کند. این API سطح پایین‌تر از Query Builder Executor است: SELECT یک `ResultSet` خام JDBC می‌دهد و شما باید آن را در callback مصرف و ببندید. برای کد معمول برنامه، Handle و Query Builder را ترجیح دهید تا Operation مالک lifecycle و Failure handling باشد.

با ساخت `QueryExecute` از `DatabaseContext`، برای هر عملیات Connection از همان Context باز می‌شود و پس از پایان callback بسته می‌شود:

```kotlin
dependencies {
    implementation(project(":data_base:manager:execute"))
}
```

```kotlin
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult

val databaseContext = DatabaseContext("main", mainConfig)
val directExecutor: IQueryExecute = QueryExecute(databaseContext)
val query = BuiltQuery(
    query = "SELECT id AS user_id FROM users WHERE id = :userId",
    params = mutableListOf(SqlParameter.of("userId", 1)),
)
val userIds = mutableListOf<Long>()

directExecutor.executeSelect(query) { result ->
    when (result) {
        is ExecuteResult.Failure -> throw result.exception
        is ExecuteResult.Success -> result.result?.use { rows ->
            while (rows.next()) userIds += rows.getLong("user_id")
        }
    }
}
```

`ResultSet` را از callback بیرون نگه ندارید؛ اگر داده را بعداً لازم دارید، همان‌جا آن را به مدل یا Collection خودتان تبدیل کنید. مقادیر پارامتر را با bindingهای Query تأمین کنید و مقدارها را با string interpolation وارد SQL نکنید. `QueryExecute()` بدون آرگومان مسیر سازگاری مبتنی بر `DefaultDatabaseConfig` را نگه داشته است؛ برای کد جدید از Context صریح استفاده کنید.

سازندهٔ `QueryExecute(connection)` از Connectionای که caller می‌دهد استفاده می‌کند و آن را نمی‌بندد؛ در این حالت مالک Connection باید lifecycle آن را مدیریت کند. این سازنده به‌تنهایی Operation Scope یا Failure latch ایجاد نمی‌کند. `QueryExecute(databaseContext)` نیز اجرای مستقیم دارد و جایگزین `DatabaseHandleApi.connection {}` یا `transaction {}` نیست.

| عملیات `IQueryExecute` | نتیجهٔ موفقیت | قرارداد |
|---|---|---|
| `executeSelect` | `ExecuteResult<ResultSet>` | ردیف‌ها در callback JDBC قابل خواندن‌اند؛ ResultSet را همان‌جا ببندید. |
| `executeInsert` | `ExecuteResult<Long>` | کلید تولیدشده را می‌دهد؛ نبودن ردیف متأثر یا کلید تولیدشده Failure است. |
| `executeUpdate` و `executeDelete` | `ExecuteResult<Int>` | تعداد ردیف‌های متأثر را می‌دهند؛ صفر می‌تواند نتیجهٔ موفق باشد. |
| `executeTable` | `ExecuteResult<Boolean>` | پس از اجرای موفق statement مقدار `true` می‌دهد؛ مقدار boolean خام JDBC را برنمی‌گرداند. |

SQLExceptionهای اجرای Prepared Statement در `ExecuteResult.Failure` تحویل داده می‌شوند. شکست ساخت Connection می‌تواند هنگام بازکردن آن به caller پرتاب شود؛ Exceptionای هم که callback خودتان پرتاب کند، به‌عنوان خطای JDBC بازتفسیر نمی‌شود و به caller می‌رسد.

## سازگاری Handleهای سفارشی

`DatabaseHandleApi` قرارداد پایهٔ `key` و اجرای `connection` و `transaction` را فراهم می‌کند. پیاده‌سازی سفارشی باید همین اعضا و lifecycle callbackهایشان را مدیریت کند؛ صرف پیاده‌سازی interface، `DatabaseContext`، Manager lease، Operation Scope یا قابلیت‌های دیگر Manager را ایجاد نمی‌کند.

```kotlin
fun loadSummary(database: DatabaseHandleApi): ExecuteResult<Summary> =
    database.connection { executor ->
        loadSummaryWith(executor)
    }
```

`loadSummaryWith` تابع برنامه است. executor را فقط مطابق قرارداد همان Handle و داخل callback استفاده کنید.

برخی قابلیت‌ها به interface جدا یا Handle مدیریت‌شدهٔ ساخته‌شده توسط `DatabaseManager` نیاز دارند:

| قابلیت | پیش‌نیاز | رفتار برای Handle سفارشی فاقد قابلیت |
|---|---|---|
| خواندن Registration Attribute | پیاده‌سازی `DatabaseHandleAttributes` | interface پایه این قابلیت را تضمین نمی‌کند؛ cast امن را بررسی کنید. |
| `withOperationScope { ... }` | Handle مدیریت‌شدهٔ Manager | `ExecuteResult.Failure` با `UnsupportedOperationException` برمی‌گرداند. |
| `watch(listener)` | قابلیت Watch در Handle مدیریت‌شده | هنگام فراخوانی extension، `UnsupportedOperationException` پرتاب می‌شود. |
| `connectionPipeline(...).execute()` و `transactionPipeline(...).execute()` | lifecycle Pipeline مدیریت‌شده | Builder ساخته می‌شود، اما `execute()` مقدار Failure برمی‌گرداند. |
| `executeConnectionDefinition(...)` و `executeTransactionDefinition(...)` | Handle مدیریت‌شدهٔ Manager | `ExecuteResult.Failure` برمی‌گردد. |
| `migration { ... }` | Registration Attribute حاوی `MigrationConfiguration` و Operation Scope مدیریت‌شده | نبود Attribute یا نبود Scope مدیریت‌شده با Failure رد می‌شود. |

این extensionها بر اساس receiver و پیاده‌سازی خودشان اجرا می‌شوند. اضافه‌کردن متدی هم‌نام به یک Handle سفارشی باعث نمی‌شود extension این پروژه از آن متد استفاده کند.

اگر فقط به Connection و Transaction نیاز دارید، می‌توانید با `DatabaseHandleApi` کار کنید و lifecycle پیاده‌سازی خودتان را رعایت کنید. برای Operation Scope، Watch، Pipelineهای Handle یا Migration متصل به Registration از Handle ساخته‌شده توسط `DatabaseManager` استفاده کنید. برای مثال‌های جزئی، [راهنمای Watch](watch.md)، [Connection Pipeline](connection_pipeline.md)، [Transaction Pipeline](transaction_pipeline.md) و [شروع Migration](migration.md) را ببینید.
