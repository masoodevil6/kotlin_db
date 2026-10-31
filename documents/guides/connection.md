# Connection

## ارتباط با پایگاه داده

مثال‌های Migration و Query از `DatabaseConfig` و شیء سراسری `DefaultDatabaseConfig.config` برای تنظیم اتصال استفاده می‌کنند. `DatabaseConnection.build()` این تنظیم را می‌خواند و با `DriverManager` یک JDBC connection می‌سازد. `DatabaseConfig.getDbUrl()` آدرس را از `dbDomain`، پورت اختیاری و `dbName` می‌سازد؛ برای نمونه، ترکیب `jdbc:mysql://127.0.0.1`، پورت `3306` و نام schema برابر `dev_db` به URL زیر می‌رسد:

~~~text
jdbc:mysql://127.0.0.1:3306/dev_db
~~~

نام کاربری و گذرواژه جدا از URL به JDBC داده می‌شوند. `dialect` برای انتخاب قواعد رندر SQL است و جایگزین تنظیم مقصد اتصال نیست.

## تنظیمات مثال Migration

`data_base:migration:example` در `Main.kt` مقدار `DefaultDatabaseConfig.config` را از متغیرهای محیطی زیر می‌سازد:

| متغیر محیطی | کاربرد |
|---|---|
| `DB_DOMAIN` | بخش میزبان JDBC؛ پیش‌فرض کد `jdbc:mysql://127.0.0.1` است. |
| `DB_PORT` | پورت JDBC؛ پیش‌فرض کد `3306` است. |
| `DB_NAME` | نام schema مقصد؛ پیش‌فرض کد `kotlin_db` است. |
| `DB_USER` | نام کاربر اتصال؛ در کد مقدار fallback توسعه‌ای دارد. |
| `DB_PASSWORD` | گذرواژه؛ در کد مقدار fallback خالی دارد. |

نمونهٔ مقداردهی `DefaultDatabaseConfig.config` در `Main.kt` مثال Migration:

~~~kotlin
DefaultDatabaseConfig.config = DatabaseConfig(
    dbDomain = System.getenv("DB_DOMAIN") ?: "jdbc:mysql://127.0.0.1",
    dbPort = (System.getenv("DB_PORT") ?: "3306").toInt(),
    dbName = System.getenv("DB_NAME") ?: "kotlin_db",
    dbUserName = System.getenv("DB_USER") ?: "root",
    dbPassword = System.getenv("DB_PASSWORD") ?: "",
    dialect = DialectQuery.MARIA_DB,
    targetDatabaseVersion = MARIA_DB.Version(10, 4, 28),
)
~~~

این مقادیر در کد tracked هستند و fallbackهای فعلی برای محیط توسعه‌اند؛ آن‌ها را برای محیط واقعی کپی نکنید. پیش از اجرا، میزبان، schema و کاربر محدود را صریحاً در محیط محلی یا Run Configuration تنظیم کنید. گذرواژه را در source، فایل tracked، آرگومان قابل‌ثبت در history یا گزارش قرار ندهید.

نمونهٔ تنظیمات غیرمحرمانه در PowerShell:

~~~powershell
$env:DB_DOMAIN = "jdbc:mysql://127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "<development_or_test_schema>"
$env:DB_USER = "<restricted_database_user>"
# گذرواژه را جداگانه از منبع امن محلی به محیط فرایند بدهید.
.\gradlew.bat :data_base:migration:example:run
~~~


## چند درخواست در یک اتصال

برای اجرای چند Query روی یک JDBC Connection، از `QueryBuilderExecutor.connection { ctx -> ... }` استفاده کنید. `QueryBuilderExecutor()` تنظیمات فعلی `DefaultDatabaseConfig.config` را می‌خواند. داخل scope، همهٔ درخواست‌ها را با `ctx.queryBuilder(...)` اجرا کنید؛ `ctx` به همان Connection متصل است. از root یعنی `queryManager` برای اجرای Query داخل این callback استفاده نکنید.

نمونهٔ کامل زیر ابتدا کاربر را می‌خواند و سپس تلفن‌های همان کاربر را می‌خواند. هر دو SELECT در یک Connection اجرا می‌شوند:

~~~kotlin
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.manager.QueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

private fun userById(userId: Int): IQueryRenderSelectApi = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column { tableSelector("uu"); columnSelector("id") }
            alias("user_id")
            execute(DataType.INT)
        }
        addColumn {
            column { tableSelector("uu"); columnSelector("name") }
            alias("user_name")
            execute(DataType.STRING)
        }
    }
    .from {
        table("user_users")
        alias("uu")
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableSelector("uu"); columnSelector("id") }
                operationEqual()
                sideValueParam {
                    paramName("userId")
                    paramValue(userId)
                }
            }
        }
    }

private fun phonesByUserId(userId: Int): IQueryRenderSelectApi = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column { tableSelector("up"); columnSelector("user_id") }
            alias("phone_user_id")
            execute(DataType.INT)
        }
        addColumn {
            column { tableSelector("up"); columnSelector("phone") }
            alias("user_phone")
            execute(DataType.STRING)
        }
    }
    .from {
        table("user_phones")
        alias("up")
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableSelector("up"); columnSelector("user_id") }
                operationEqual()
                sideValueParam {
                    paramName("phoneUserId")
                    paramValue(userId)
                }
            }
        }
    }

fun main() {
    val queryManager = QueryBuilderExecutor()

    val result: ExecuteResult<Pair<List<QueryRow>, List<QueryRow>>> = queryManager.connection { ctx ->
        val users = when (val queryResult = ctx.queryBuilder(userById(1)).execute()) {
            is ExecuteResult.Failure -> return@connection queryResult
            is ExecuteResult.Success -> queryResult.result
                ?: return@connection ExecuteResult.Failure(
                    IllegalStateException("User SELECT completed without materialized rows"),
                )
        }

        val phones = when (val queryResult = ctx.queryBuilder(phonesByUserId(1)).execute()) {
            is ExecuteResult.Failure -> return@connection queryResult
            is ExecuteResult.Success -> queryResult.result
                ?: return@connection ExecuteResult.Failure(
                    IllegalStateException("Phone SELECT completed without materialized rows"),
                )
        }

        ExecuteResult.Success(users to phones)
    }

    result
        .success { (users, phones) ->
            users.forEach { row ->
                println("user_id=${row.getValue("user_id")}, user_name=${row.getValue("user_name")}")
            }
            phones.forEach { row ->
                println("phone_user_id=${row.getValue("phone_user_id")}, user_phone=${row.getValue("user_phone")}")
            }
            users to phones
        }
        .error { failure -> println("Query scope failed: $failure") }
}
~~~

`connection {}` یک Connection را برای کل callback می‌گیرد و پس از پایان آن می‌بندد. این scope `autoCommit` را تغییر نمی‌دهد و commit یا rollback خودکار انجام نمی‌دهد؛ بنابراین برای atomic بودن چند تغییر داده از `transaction {}` استفاده کنید. اگر Query با Failure تمام شود، Failure در scope ثبت می‌شود و Queryهای بعدی اجرا نمی‌شوند؛ به همین دلیل مثال در هر مرحله Failure را به scope برمی‌گرداند. جدول‌های `user_users` و `user_phones` باید از قبل در schema تنظیم‌شده وجود داشته باشند.

## استخراج

ماژول `data_base:manager:execute` اجرای مستقیم JDBC را با `IQueryExecute` و پیاده‌سازی `QueryExecute` فراهم می‌کند. در این سطح، SELECT نتیجه را به شکل `ExecuteResult<ResultSet>` می‌دهد؛ داده‌ها هنوز به `QueryRow` یا مدل Kotlin تبدیل نشده‌اند و خواندن ستون‌ها با API خود JDBC انجام می‌شود.

نمونهٔ زیر یک پارامتر نام‌دار را bind می‌کند، ResultSet را در همان callback می‌خواند و مقادیر لازم را برای استفاده پس از callback در یک فهرست نگه می‌دارد:

~~~kotlin
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

fun main() {
    val query = BuiltQuery(
        query = "SELECT id AS user_id, name AS user_name FROM user_users WHERE id = :userId",
        params = mutableListOf(SqlParameter.of("userId", 1)),
    )
    val users = mutableListOf<Pair<Long, String?>>()

    QueryExecute().executeSelect(query) { result ->
        when (result) {
            is ExecuteResult.Failure -> throw result.exception
            is ExecuteResult.Success -> {
                val resultSet = result.result
                if (resultSet == null) {
                    println("SELECT completed without a ResultSet")
                } else {
                    resultSet.use { rows ->
                        while (rows.next()) {
                            users += rows.getLong("user_id") to rows.getString("user_name")
                        }
                    }
                }
            }
        }
    }

    users.forEach { (id, name) -> println("user_id=$id, user_name=$name") }
}
~~~

در این نمونه، `BuiltQuery` placeholder `:userId` را به placeholder JDBC تبدیل می‌کند و مقدار `SqlParameter` جداگانه bind می‌شود؛ مقدار پارامتر را با چسباندن رشته‌ای وارد SQL نکنید. نامی که به `ResultSet.getLong()` یا `getString()` می‌دهید، label خروجی SELECT است؛ اینجا aliasهای `user_id` و `user_name` هستند.

در overload مستقل `executeSelect(builtQuery, callback)`، `QueryExecute` برای هر عملیات connection خودش را می‌گیرد. callback هنگام زنده‌بودن Statement و ResultSet اجرا می‌شود؛ `ResultSet` را در همان callback با `use` ببندید و برای استفادهٔ بعدی نگه ندارید. connection مستقل هم پس از پایان عملیات بسته می‌شود. اگر داده باید بیرون callback بماند، آن را داخل callback به DTO یا مجموعه‌ای مانند `List` تبدیل کنید. چون payload موفقیت nullable است، مقدار `result.result` را بررسی کنید.

اگر overload دارای `Connection` را فراخوانی کنید، connection متعلق به caller می‌ماند و `QueryExecute` آن را نمی‌بندد؛ Statement و ResultSet مربوط به همان عملیات همچنان باید در محدودهٔ callback مصرف و بسته شوند.

## همان SELECT با متدچین Query Builder

`manager:execute` برای اجرای مستقیم `BuiltQuery` متدچین `success/error` ندارد؛ callback آن `ExecuteResult<ResultSet>` را هنگام بازبودن منابع JDBC دریافت می‌کند. اگر متدچین می‌خواهید، از لایهٔ `data_base:query:executor` استفاده کنید. در این مسیر Query با DSL ساخته می‌شود و `execute()` ردیف‌ها را پیش از بستن منابع به `List<QueryRow>` تبدیل می‌کند:

~~~kotlin
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

fun findUser(queryManager: IQueryBuilderExecutor, userId: Long) {
    val query: IQueryRenderSelectApi = QueryRenderSelectBuilder()
        .select {
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("id")
                }
                alias("user_id")
                execute(DataType.LONG)
            }
            addColumn {
                column {
                    tableSelector("uu")
                    columnSelector("name")
                }
                alias("user_name")
                execute(DataType.STRING)
            }
        }
        .from {
            table("user_users")
            alias("uu")
        }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector("uu")
                        columnSelector("id")
                    }
                    operationEqual()
                    sideValueParam {
                        paramName("userId")
                        paramValue(userId)
                    }
                }
            }
        }

    queryManager
        .queryBuilder(query)
        .sql { builtQuery ->
            println("SQL: ${builtQuery.query}")
            println("Parameters: ${builtQuery.params}")
        }
        .details { metadata -> println(metadata) }
        .execute()
        .success { rows ->
            rows.forEach { row ->
                println("user_id=${row.getValue("user_id")}, user_name=${row.getValue("user_name")}")
            }
            rows
        }
        .error { failure -> println("error: $failure") }
}
~~~

در این زنجیره، `sql` و `details` اطلاعات Query را مشاهده می‌کنند و `success` نتیجهٔ `List<QueryRow>` را دریافت می‌کند؛ `error` خطا را مشاهده می‌کند و خودش نتیجه را بازیابی یا تبدیل نمی‌کند. اجرای Query یا mapping را داخل callbackها دوباره انجام ندهید. این نمونه از نظر خروجی با نمونهٔ JDBC خام یکسان نیست: در نسخهٔ خام باید `ResultSet` را داخل callback بخوانید، اما اینجا ردیف‌ها پیش از ورود به `success` materialize شده‌اند.

## نتیجهٔ عملیات

`ExecuteResult<T>` دو حالت دارد: `Success<T>` با payload nullable و `Failure` با `Throwable` اصلی. Failure را به فهرست خالی یا مقدار پیش‌فرض تبدیل نکنید. متد `toValue()` payload موفق غیرnull را استخراج می‌کند؛ در Failure همان Throwable را پرتاب می‌کند و در `Success(null)`، `IllegalStateException` می‌دهد.

| عملیات `IQueryExecute` | نوع نتیجه | مقدار موفقیت |
|---|---|---|
| `executeSelect` | `ExecuteResult<ResultSet>` | cursor JDBC برای خواندن ردیف‌ها در callback |
| `executeInsert` | `ExecuteResult<Long>` | کلید تولیدشده؛ نبودن کلید یا اثرنداشتن INSERT به شکل Failure گزارش می‌شود |
| `executeUpdate` | `ExecuteResult<Int>` | تعداد ردیف‌های تغییرکرده؛ صفر نیز می‌تواند موفقیت باشد |
| `executeDelete` | `ExecuteResult<Int>` | تعداد ردیف‌های حذف‌شده |
| `executeTable` | `ExecuteResult<Boolean>` | نتیجهٔ اجرای دستور جدولی/DDL |

callback هر عملیات یک بار با Success یا Failure فراخوانی می‌شود. Exceptionای که callback خودش پرتاب کند خطای JDBC تلقی نمی‌شود و به caller می‌رسد. این API سطح پایین برای اجرای SQL و خواندن مستقیم JDBC است؛ مسیر ساخت Query از DSL و تبدیل به `QueryRow` یا خروجی typed در بخش [Query Builder](query-builder/README.md) توضیح داده شده است.
