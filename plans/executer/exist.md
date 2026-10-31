# مستندات ماژول‌های پایگاه داده

این راهنما محتوای پروژه را در چهار حوزهٔ اصلی سازمان‌دهی می‌کند: اتصال به پایگاه داده، Collection، Migration و Query Builder. در بخش Query Builder، مثال‌ها زیر نسخه‌های 1.0، 2.0، 3.0 و 3.1 دسته‌بندی شده‌اند.

## فهرست

- [1. Connection](#1-connection)
  - [ارتباط با پایگاه داده](#ارتباط-با-پایگاه-داده)
  - [چند درخواست در یک اتصال](#چند-درخواست-در-یک-اتصال)
  - [استخراج](#استخراج)
- [2. Collection](#2-collection)
- [3. Migration](#3-migration)
  - [Schemas](#migration-schemas)
  - [Register، گروه‌بندی و فهرست migrationها](#migration-register)
  - [انجام عملیات migration](#migration-execution)
- [4. Query Builder](#4-query-builder)
  - [Connection](#query-builder-connection)
    - [ارتباط‌گیری](#query-builder-connection-usage)
    - [روش استخراج](#query-builder-extraction)
    - [نتیجه](#query-builder-result)
  - [Examples](#query-builder-examples)
    - [1.0 — نام خام جدول و ستون](#query-builder-example-1-0)
    - [2.0 — Model-backed Query](#query-builder-example-2-0)
    - [3.0 — CTE-backed SELECT و Model-backed Mutation](#query-builder-example-3-0)
    - [3.1 — اجرای typed CTE](#query-builder-example-3-1)
    - [مقایسهٔ نسخه‌ها](#query-builder-version-comparison)
  - [Rules](#rules)
    - [Model](#rules-model)
    - [Query](#rules-query)

## 1. Connection

### ارتباط با پایگاه داده

مثال‌های Migration و Query از `DatabaseConfig` و شیء سراسری `DefaultDatabaseConfig.config` برای تنظیم اتصال استفاده می‌کنند. `DatabaseConnection.build()` این تنظیم را می‌خواند و با `DriverManager` یک JDBC connection می‌سازد. `DatabaseConfig.getDbUrl()` آدرس را از `dbDomain`، پورت اختیاری و `dbName` می‌سازد؛ برای نمونه، ترکیب `jdbc:mysql://127.0.0.1`، پورت `3306` و نام schema برابر `dev_db` به URL زیر می‌رسد:

~~~text
jdbc:mysql://127.0.0.1:3306/dev_db
~~~

نام کاربری و گذرواژه جدا از URL به JDBC داده می‌شوند. `dialect` برای انتخاب قواعد رندر SQL است و جایگزین تنظیم مقصد اتصال نیست.

### تنظیمات مثال Migration

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


### چند درخواست در یک اتصال

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

### استخراج

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

### همان SELECT با متدچین Query Builder

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

### نتیجهٔ عملیات

`ExecuteResult<T>` دو حالت دارد: `Success<T>` با payload nullable و `Failure` با `Throwable` اصلی. Failure را به فهرست خالی یا مقدار پیش‌فرض تبدیل نکنید. متد `toValue()` payload موفق غیرnull را استخراج می‌کند؛ در Failure همان Throwable را پرتاب می‌کند و در `Success(null)`، `IllegalStateException` می‌دهد.

| عملیات `IQueryExecute` | نوع نتیجه | مقدار موفقیت |
|---|---|---|
| `executeSelect` | `ExecuteResult<ResultSet>` | cursor JDBC برای خواندن ردیف‌ها در callback |
| `executeInsert` | `ExecuteResult<Long>` | کلید تولیدشده؛ نبودن کلید یا اثرنداشتن INSERT به شکل Failure گزارش می‌شود |
| `executeUpdate` | `ExecuteResult<Int>` | تعداد ردیف‌های تغییرکرده؛ صفر نیز می‌تواند موفقیت باشد |
| `executeDelete` | `ExecuteResult<Int>` | تعداد ردیف‌های حذف‌شده |
| `executeTable` | `ExecuteResult<Boolean>` | نتیجهٔ اجرای دستور جدولی/DDL |

callback هر عملیات یک بار با Success یا Failure فراخوانی می‌شود. Exceptionای که callback خودش پرتاب کند خطای JDBC تلقی نمی‌شود و به caller می‌رسد. این API سطح پایین برای اجرای SQL و خواندن مستقیم JDBC است؛ مسیر ساخت Query از DSL و تبدیل به `QueryRow` یا خروجی typed در بخش [Query Builder](#4-query-builder) توضیح داده شده است.

## 2. Collection

ماژول Collection یک مجموعهٔ generic با قرارداد `ICollection<T>` و پیاده‌سازی `Collection<T>` ارائه می‌کند. این نوع، API کوچک و مستقل خودش را دارد؛ برای نمونه، `ICollection<T>` با `List<T>` یکی نیست و فقط هنگام نیاز می‌توان آن را با `toList()` به فهرست استاندارد Kotlin تبدیل کرد.

کلاس نمونهٔ زیر در مثال‌ها استفاده می‌شود. فیلد `email` nullable است تا دسترسی به propertyهای معمولی و nullable در پیمایش هم دیده شود:

~~~kotlin
class UserModel(
    var id: Int,
    var name: String,
    var email: String?,
)
~~~

### قرارداد `ICollection<T>`

| API | رفتار |
|---|---|
| `add(element)` | عنصر را به همان مجموعه اضافه می‌کند. |
| `forEach(action)` | action را برای هر عنصر اجرا می‌کند. |
| `filter(predicate)` | مجموعه‌ای تازه از عناصر منطبق، با همان نوع `T`، برمی‌گرداند. |
| `map(transform)` | مجموعه‌ای تازه از مقادیر تبدیل‌شده با نوع `R` برمی‌گرداند: `ICollection<R>`. |
| `firstOrNull(predicate)` | نخستین عنصر منطبق را برمی‌گرداند؛ اگر موردی پیدا نشود، `null` می‌دهد. |
| `toList()` | عناصر را به `List<T>` تبدیل می‌کند. |
| `get(index)` یا `collection[index]` | عنصر جایگاه مشخص‌شده را می‌دهد؛ index نامعتبر مطابق پیاده‌سازی فعلی `IndexOutOfBoundsException` ایجاد می‌کند. |
| `size` | تعداد فعلی عناصر را می‌دهد. |

پیاده‌سازی فعلی `Collection<T>` ترتیب درج را حفظ می‌کند. `filter()` و `map()` نتیجه را در یک Collection تازه می‌سازند؛ تغییر آن نتیجه، مجموعهٔ مبدأ را جایگزین نمی‌کند. `toList()` یک کپی سطحی از فهرست عناصر می‌سازد؛ خود اشیای mutable داخل مجموعه clone نمی‌شوند.

### مثال کامل قابل اجرا

نمونهٔ زیر یک Collection می‌سازد و تمام عملیات اصلی را روی همان داده‌ها اجرا می‌کند. می‌توان آن را در یک فایل Kotlin با dependency به `:collection:core` اجرا کرد:

~~~kotlin
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

class UserModel(
    var id: Int,
    var name: String,
    var email: String?,
)

fun main() {
    val users: ICollection<UserModel> = Collection()
    users.add(UserModel(1, "Ada", "ada@example.com"))
    users.add(UserModel(2, "Grace", null))
    users.add(UserModel(3, "Alan", "alan@example.com"))

    println("Added ${users.size} users")

    users.forEach { user ->
        println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
    }

    val firstUserName = users[0].name
    println("First user: $firstUserName")

    val usersWithAName = users.filter { user -> user.name.startsWith("A") }
    println("Users with names starting with A: ${usersWithAName.size}")
    usersWithAName.forEach { user -> println(user.name) }

    val names: ICollection<String> = users.map { user -> user.name }
    names.forEach { name -> println("Mapped name: $name") }

    val firstMatchingUser = users.firstOrNull { user -> user.name.startsWith("A") }
    println("First matching user: ${firstMatchingUser?.name}")

    val userList: List<UserModel> = users.toList()
    userList.forEach { user -> println("List item: ${user.id} - ${user.name}") }
}
~~~

خروجی این داده‌ها به‌ترتیب شامل سه عضو، سه خط پیمایش، نام `Ada` برای index صفر، دو نتیجهٔ فیلتر، سه نام mapشده، نخستین نتیجهٔ `Ada` و سه عضو تبدیل‌شده به `List` است. مقدار `email` برای `Grace` برابر `null` باقی می‌ماند.

### ساخت و افزودن عنصر

`Collection<T>` را می‌توان مستقیماً ساخت و از طریق قرارداد `ICollection<T>` نگه داشت. `add()` مجموعهٔ موجود را تغییر می‌دهد:

~~~kotlin
import examples.UserModel
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

val users: ICollection<UserModel> = Collection()
users.add(UserModel(1, "Ada", "ada@example.com"))
users.add(UserModel(2, "Grace", null))

println("Added ${users.size} users")
~~~

با دو بار افزودن، مقدار `size` برابر ۲ می‌شود؛ در مثال کامل بالا سه عنصر اضافه شده‌اند و مقدار آن ۳ است.

### پیمایش و دسترسی اندیسی

`forEach` عناصر را برای خواندن یا پردازش یکی‌یکی در اختیار می‌گذارد. مقدار nullable مانند `email` همان nullable باقی می‌ماند و مصرف‌کننده باید آن را متناسب با نیاز مدیریت کند:

~~~kotlin
users.forEach { user ->
    println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
}

val firstUserName = users[0].name
~~~

در مثال کامل، پیمایش هر سه کاربر را چاپ می‌کند و `users[0]` کاربر نخست را می‌دهد. index از صفر آغاز می‌شود و باید در بازهٔ `0 until size` باشد.

### فیلتر و شمارش

`filter` شرط را روی هر عنصر اعمال می‌کند و یک `ICollection<T>` تازه از موارد منطبق می‌سازد. `size` تعداد نتیجه را می‌دهد:

~~~kotlin
val usersWithAName = users.filter { user -> user.name.startsWith("A") }
println("Matching users: ${usersWithAName.size}")
~~~

در مثال کامل، شرط `name.startsWith("A")` دو کاربر `Ada` و `Alan` را برمی‌گرداند. `size` روی مجموعهٔ فیلترشده مقدار ۲ و روی مجموعهٔ اصلی مقدار ۳ دارد.

### تبدیل با `map`

`map` نوع عناصر را می‌تواند تغییر دهد. نتیجه همچنان از قرارداد `ICollection` پیروی می‌کند:

~~~kotlin
val names: ICollection<String> = users.map { user -> user.name }
names.forEach { name -> println(name) }
~~~

در مثال کامل، `ICollection<UserModel>` به `ICollection<String>` تبدیل می‌شود و نام هر سه کاربر چاپ می‌شود. این تبدیل مجموعهٔ تازه‌ای می‌سازد و به‌خودی‌خود مجموعهٔ اصلی را تغییر نمی‌دهد.

### جست‌وجوی نخستین مورد

`firstOrNull` برای یافتن نخستین عنصر منطبق مناسب است و در صورت نبودن نتیجه، مقدار nullable برمی‌گرداند:

~~~kotlin
val firstMatchingUser = users.firstOrNull { user -> user.name.startsWith("A") }
println("First matching user: ${firstMatchingUser?.name}")
~~~

در مثال کامل، نخستین نام منطبق `Ada` است. از آنجا که نتیجه ممکن است `null` باشد، دسترسی امن مانند `?.` یا بررسی صریح null لازم است.

### تبدیل به `List`

اگر API دیگری `List<T>` لازم داشته باشد، `toList()` عناصر را به فهرست استاندارد Kotlin تبدیل می‌کند:

~~~kotlin
val userList: List<UserModel> = users.toList()
userList.forEach { user -> println("${user.id}: ${user.name}") }
~~~

در مثال کامل، نوع خروجی صریحاً `List<UserModel>` است. تبدیل، نوع خروجی را از `ICollection<T>` به `List<T>` عوض می‌کند؛ بنابراین فقط زمانی از آن استفاده کنید که قرارداد مصرف‌کننده `List` بخواهد.

### اجرای مثال‌ها

ماژول `collection:examples` به `collection:core` وابسته است. `main.kt` هشت مثال را با ترتیب `add`، `forEach`، `filter`، `size`، `map`، `firstOrNull`، `toList` و `get` اجرا می‌کند. مقدار پیش‌فرض همهٔ گزینه‌ها `true` است؛ هر مثال را می‌توان جداگانه با Gradle project property خاموش یا روشن کرد:


## 3. Migration

<a id="migration-schemas"></a>
### Schemas

در این ماژول، «Schema» به تعریف و تغییر ساختار جدول‌ها در پایگاه دادهٔ انتخاب‌شده اشاره دارد. AST فعلی عملیاتی برای ساخت یا حذف خود database/schema ندارد؛ مقصد اتصال از `DefaultDatabaseConfig.config` می‌آید و در بخش [ارتباط با پایگاه داده](#ارتباط-با-پایگاه-داده) توضیح داده شده است. قابلیت‌های Migration روی table، column، constraint و index کار می‌کنند.

DSL عملیات را به ASTهای مستقل تبدیل می‌کند. در یک migration چند عملیات به ترتیب نوشته می‌شوند؛ dialect از AST دستور DDL می‌سازد و `IMigrationExecutor` آن را اجرا می‌کند. مثال‌های موجود در `data_base:migration:example` زیر V1 قرار دارند. بیشتر مثال‌ها دو مسیر جدا دارند: `render()` برای دیدن SQL و `execute(...)` برای اجرای صریح. قبل از اجرای DDL، مقصد اتصال و SQL تولیدشده را بررسی کنید؛ مثال‌های این بخش به‌خودی‌خود schema آزمایشی نمی‌سازند.

#### ساخت جدول و تعریف ستون

نمونهٔ زیر از `A1ExampleCreateTableV1` است. نام جدول و ستون‌ها، نوع داده، nullability، مقدار پیش‌فرض و ویژگی‌های کلید در بلوک DSL تعریف می‌شوند:

~~~kotlin
createTable {
    table { tableName("users") }
    ifNotExists()

    addColumn {
        name("id")
        dataType(IntType())
        notNull()
        autoIncrement()
        primaryKey()
    }
    addColumn {
        name("name")
        dataType(VarcharType(120))
        notNull()
    }
    addColumn {
        name("display_name")
        dataType(VarcharType(120))
        default("New user")
    }
}
~~~

`ifNotExists()` گزینهٔ `IF NOT EXISTS` را در AST جدول ثبت می‌کند. ویژگی‌های هر ستون در `IMigrationColumnAst` نگه‌داری می‌شوند: نام، `MigrationColumnDataType`، default، nullable بودن، auto increment و primary بودن. نوع‌های تعریف‌شده در ماژول params عبارت‌اند از `BooleanType`، `IntType`، `VarcharType(length)`، `TextType`، `DecimalType(precision, scale)`، `JsonType`، `DateType`، `TimeType`، `DateTimeType` و `TimestampType`. طول VARCHAR باید مثبت باشد؛ precisionهای fractional برای TIME، DATETIME و TIMESTAMP در بازهٔ 0 تا 6 اعتبارسنجی می‌شوند و برای DECIMAL نیز scale نمی‌تواند از precision بیشتر باشد.

#### کلیدها و constraintهای جدول

در مثال `A1ExampleCreateTableConstraintsV1` چند نوع تعریف جدولی کنار هم آمده‌اند. primary key می‌تواند چندستونی باشد؛ unique، index، full-text و foreign key نیز از طریق builderهای جداگانه به AST تعریف جدول اضافه می‌شوند:

~~~kotlin
createTable {
    table { tableName("orders") }
    addColumn { name("tenant_id"); dataType(IntType()); notNull() }
    addColumn { name("order_id"); dataType(IntType()); notNull() }
    addColumn { name("user_id"); dataType(IntType()); notNull() }
    addColumn { name("email"); dataType(VarcharType(255)); notNull() }
    addColumn { name("description"); dataType(TextType()); notNull() }

    primaryKey { columns("tenant_id", "order_id") }
    unique {
        name("uq_orders_email")
        columns("email")
    }
    index {
        name("idx_orders_tenant_user")
        columns("tenant_id", "user_id")
        using(IndexMethod.BTREE)
    }
    fullTextIndex {
        name("ft_orders_description")
        columns("description")
    }
    foreignKey {
        name("fk_orders_user")
        columns("user_id")
        referencesTable("users")
        referencesColumns("id")
        onDelete(ForeignKeyAction.CASCADE)
        onUpdate(ForeignKeyAction.RESTRICT)
    }
}
~~~

AST constraintها در `IMigrationCreateTableDefinitionAst` و تعریف‌های primary key، unique، index، full-text و foreign key نگه‌داری می‌شوند. `IndexMethod` در AST دو مقدار `BTREE` و `HASH` دارد. `ForeignKeyAction` نیز `CASCADE`، `RESTRICT`، `NO_ACTION` و `SET_NULL` را تعریف می‌کند. اعتبار نهایی این گزینه‌ها به dialect و موتور پایگاه داده وابسته است.

#### ستون‌های زمانی کمکی و نوع‌های زمانی

`timestamps()` دو ستون nullable به نام‌های `created_at` و `updated_at` با نوع `DateTimeType` می‌افزاید. `softDeletes()` یک ستون nullable به نام `deleted_at` می‌افزاید؛ این shorthand خودش رفتار حذف نرم را اجرا نمی‌کند و فقط ستون را در CREATE TABLE تعریف می‌کند.

~~~kotlin
createTable {
    table { tableName("posts") }
    addColumn {
        name("id")
        dataType(IntType())
        notNull()
        autoIncrement()
        primaryKey()
    }
    timestamps()
    softDeletes()
}
~~~

مثال `A1ExampleTemporalDataTypesV1` انواع زمانی را صریح تعریف می‌کند:

~~~kotlin
createTable {
    table { tableName("temporal_events") }
    addColumn { name("event_date"); dataType(DateType()); notNull() }
    addColumn { name("elapsed_time"); dataType(TimeType(precision = 3)) }
    addColumn { name("local_event_time"); dataType(DateTimeType()); notNull() }
    addColumn { name("recorded_at"); dataType(TimestampType(precision = 6)); notNull() }
}
~~~

#### تغییر ساختار جدول موجود

این DSL عملیات جداگانه‌ای برای افزودن، تغییر، تغییرنام و حذف ستون دارد. مثال‌های زیر شکل استفادهٔ واقعی آن‌ها را نشان می‌دهند:

~~~kotlin
// A1ExampleAddColumnV1
addColumn {
    tableName("users")
    name("email")
    dataType(VarcharType(255))
    notNull()
}

// A1ExampleModifyColumnV1
modifyColumn {
    tableName("users")
    name("name")
    dataType(VarcharType(255))
    notNull()
    default("Unknown")
    autoIncrement(false)
}

// A1ExampleRenameColumnV1
renameColumn {
    tableName("users")
    name("display_name")
    to("full_name")
}

// A1ExampleDropColumnV1
dropColumn {
    tableName("users")
    name("display_name")
    ifExists()
}
~~~

در AST مربوط به `modifyColumn`، nullability و default به‌صورت حالت‌های مشخص ثبت می‌شوند: `NULL` یا `NOT_NULL` و یکی از بدون DEFAULT، `DEFAULT NULL` یا مقدار پیش‌فرض. این تفکیک اجازه می‌دهد نبودن clause با default صریح null اشتباه نشود. `ifExists()` در حذف ستون اختیاری است. چون renderer MySQL برای این حالت `ALTER TABLE ... DROP COLUMN` معمولی می‌سازد، `MigrationExecutor` پیش از DDL با `INFORMATION_SCHEMA.COLUMNS` وجود ستون را در database انتخاب‌شده بررسی می‌کند؛ اگر ستون موجود نباشد عملیات را موفق تمام می‌کند و اگر موجود باشد DDL را اجرا می‌کند.

تغییرنام جدول با `renameTable { fromTableName("users"); toTableName("customers") }` ساخته می‌شود. حذف جدول با `dropTable { tableName("users"); ifExists() }` انجام می‌شود. همانند حذف ستون، `ifExists()` برای حذف جدول اختیاری است. هر دو عملیات فقط نام‌های جدول را در AST ثبت می‌کنند و وجود وابستگی‌های خارجی یا اثر DDL روی داده را خودکار حل نمی‌کنند.

#### Indexها و Foreign Keyهای مستقل

علاوه بر constraintهای inline در `createTable`، AST عملیات مستقل برای ساخت و حذف index و foreign key هم دارد:

~~~kotlin
createIndex {
    tableName("users")
    name("idx_users_email")
    column("email")
}

createUniqueIndex {
    tableName("users")
    name("uq_users_email")
    column("email")
}

createMultiColumnIndex {
    tableName("users")
    name("idx_users_name_email")
    columns("last_name", "first_name", "email")
}

createFullTextIndex {
    tableName("posts")
    name("ft_posts_title_body")
    columns("title", "body")
}

createSpatialIndex {
    tableName("places")
    name("spx_places_geom")
    column("geom")
}
~~~

`createSpatialIndex` فقط index فضایی را تعریف می‌کند؛ ستون نمونهٔ `places.geom` باید از قبل وجود داشته و برای index فضایی در دیتابیس هدف مناسب باشد. API و مثال، نوع فضایی مستقلی برای ساخت آن ستون در فهرست typeهای migration تعریف نمی‌کنند. برای حذف index از `dropIndex { tableName("users"); name("idx_users_email") }` استفاده می‌شود.

Foreign key مستقل، نام constraint و جفت ستون‌های جدول و مرجع را در AST ثبت می‌کند:

~~~kotlin
createForeignKey {
    tableName("orders")
    name("fk_orders_tenant_user")
    columns("tenant_id", "user_id")
    referencesTable("users")
    referencesColumns("tenant_id", "id")
    onDelete(ForeignKeyAction.CASCADE)
    onUpdate(ForeignKeyAction.RESTRICT)
}

dropForeignKey {
    tableName("orders")
    name("fk_orders_tenant_user")
}
~~~

در عملیات مستقل، تعداد و ترتیب ستون‌های محلی و مرجع باید با هم متناظر باشد. مثال‌های `A1ExampleCreateForeignKeyV1` و `A1ExampleDropForeignKeyV1` این دو جهت را جدا نشان می‌دهند. ایجاد و حذف foreign key در مثال‌ها opt-in است و با فراخوانی صریح `execute(...)` انجام می‌شود.

#### Migration چندعملیاتی و ثبت نسخه

برای گروه‌بندی تغییرات، کلاس `Migration` متدهای `up()` و `down()` را برمی‌گرداند. مثال `CreateCustomersMigrationV1` در `up` جدول و index را می‌سازد و در `down` ابتدا index و سپس جدول را حذف می‌کند:

~~~kotlin
class CreateCustomersMigrationV1 : Migration {
    override fun up() = migration {
        createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("email")
                dataType(VarcharType(255))
                notNull()
            }
        }
        createIndex {
            tableName("customers")
            name("idx_customers_email")
            column("email")
        }
    }

    override fun down() = migration {
        dropIndex { tableName("customers"); name("idx_customers_email") }
        dropTable { tableName("customers") }
    }
}
~~~

ثبت و گروه‌بندی migrationها با `@MigrationId` و `migrationConfig` انجام می‌شود؛ نمونهٔ کامل و روش مشاهدهٔ فهرست در بخش [Register، گروه‌بندی و فهرست migrationها](#migration-register) آمده است. Builder هنگام ثبت، وجود `@MigrationId` و معتبر بودن مقدار آن را بررسی می‌کند. Migration گروهی ترتیب ثبت‌شده را نگه می‌دارد؛ عملیات `down` را با توجه به وابستگی‌ها در ترتیب معکوس طراحی کنید.

#### Register، گروه‌بندی و فهرست migrationها

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

<a id="migration-execution"></a>
#### انجام عملیات migration

تنظیم اتصال و فهرست migrationها دو شیء جدا هستند. ابتدا مقصد اتصال را با `DefaultDatabaseConfig.config` طبق بخش [ارتباط با پایگاه داده](#ارتباط-با-پایگاه-داده) تعیین کنید. سپس خروجی `migrationConfig { ... }` را به `MigrationMigrator` بدهید؛ آن را داخل `DatabaseConfig` قرار نمی‌دهیم. `MigrationMigrator` به‌طور پیش‌فرض `MigrationExecutor` را می‌سازد و migrationهای ثبت‌شده را با همان تنظیم اتصال جاری اجرا می‌کند.

نمونهٔ زیر یک migration اجرایی با یک operation در `up()` ثبت می‌کند و نتیجهٔ اجرای batch را بررسی می‌کند:

~~~kotlin
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.builder.migrationConfig
import gog.my_project.data_base.migration.executor.manager.MigrationMigrator
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

@MigrationId("create_customers")
class CreateCustomersTableMigration : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
        }
    }

    override fun down(): MigrationDefinition = migration {
        dropTable { tableName("customers") }
    }
}

fun runCustomerMigrations() {
    // DefaultDatabaseConfig.config باید پیش‌تر با مشخصات اتصال مقصد مقداردهی شده باشد.
    val migrationConfiguration = migrationConfig {
        migration<CreateCustomersTableMigration>()
    }

    MigrationMigrator(migrationConfiguration).migrate { result ->
        when (result) {
            is ExecuteResult.Success -> println("Migration completed")
            is ExecuteResult.Failure -> throw result.exception
        }
    }
}
~~~

در این جریان، `migrationConfiguration` کلاس‌ها و ترتیب ثبت را مشخص می‌کند؛ `DefaultDatabaseConfig.config` مقصد دیتابیس را مشخص می‌کند. اجرای `MigrationMigrator.migrate(...)` از تاریخچه برای ردکردن شناسه‌های قبلاً اعمال‌شده استفاده می‌کند و `up()` موارد pending را اجرا می‌کند. خطای هر operation به callback نهایی به‌شکل `ExecuteResult.Failure` می‌رسد. در این API، `migrate()` متد `down()` را اجرا نمی‌کند.

کلاس‌های `CreateCustomersRegistrationExample` و `AddCustomerEmailRegistrationExample` در بخش ثبت، `up()` خالی دارند و فقط شکل registration را نمایش می‌دهند؛ آن‌ها را مستقیماً به `MigrationMigrator` ندهید. برای اجرای واقعی، هر migration باید `@MigrationId` معتبر، constructor عمومی بدون آرگومان و دقیقاً یک operation در `up()` داشته باشد. اگر چند تغییر مستقل لازم است، آن‌ها را به migrationهای جدا تقسیم و به ترتیب مناسب ثبت کنید.

#### پوشش AST و مثال‌های ماژول

AST عمومی schema در `data_base/migration/ast` روی `IMigrationAst` بنا شده است. جدول زیر خانواده‌های AST را با مثال‌های متناظر وصل می‌کند؛ ردیف پایانی API مدل migration و ثبت نسخه را جداگانه نشان می‌دهد:

| خانوادهٔ AST | اطلاعات اصلی ثبت‌شده | مثال ماژول |
|---|---|---|
| `IMigrationRenderCreateTableAst`، `IMigrationTableAst` و `IMigrationColumnAst` | جدول، ستون‌ها، نوع، default، nullability، auto increment، primary key و `ifNotExists` | `create_table/A1ExampleCreateTableV1.kt` |
| تعریف‌های `IMigrationCreateTableDefinitionAst` | primary key، unique، index، full-text و foreign key در تعریف جدول | `create_table_constraints/A1ExampleCreateTableConstraintsV1.kt` |
| shorthandهای ستون زمانی | ستون‌های `created_at`، `updated_at` و `deleted_at` | `create_table_timestamps/A1ExampleCreateTableTimestampsV1.kt` |
| typeهای زمانی | DATE، TIME، DATETIME و TIMESTAMP با precisionهای نمونه | `temporal_data_types/A1ExampleTemporalDataTypesV1.kt` |
| `IMigrationAddColumnAst` و `IMigrationModifyColumnAst` | افزودن ستون و تغییر نوع، nullability، default و auto increment | `add_column/A1ExampleAddColumnV1.kt`، `modify_column/A1ExampleModifyColumnV1.kt` |
| `IMigrationRenameColumnAst` و `IMigrationDropColumnAst` | تغییرنام و حذف ستون؛ حذف می‌تواند `ifExists` داشته باشد | `rename_column/A1ExampleRenameColumnV1.kt`، `drop_column/A1ExampleDropColumnV1.kt` |
| `IMigrationRenameTableAst` و `IMigrationDropTableAst` | تغییرنام و حذف جدول؛ حذف می‌تواند `ifExists` داشته باشد | `rename_table/A1ExampleRenameTableV1.kt`، `drop_table/A1ExampleDropTableV1.kt` |
| `IMigrationCreateIndexAst`، `IMigrationCreateUniqueIndexAst` و `IMigrationCreateMultiColumnIndexAst` | index عادی با روش `BTREE` یا `HASH`، unique و index چندستونی | `create_index/A1ExampleCreateIndexV1.kt`، `create_unique_index/A1ExampleCreateUniqueIndexV1.kt`، `create_multi_column_index/A1ExampleCreateMultiColumnIndexV1.kt` |
| `IMigrationCreateFullTextIndexAst` و `IMigrationCreateSpatialIndexAst` | full-text چندستونی و spatial تک‌ستونی | `create_full_text_index/A1ExampleCreateFullTextIndexV1.kt`، `create_spatial_index/A1ExampleCreateSpatialIndexV1.kt` |
| `IMigrationCreateForeignKeyAst` و `IMigrationDropForeignKeyAst` | constraint، ستون‌های محلی و مرجع، و actionهای حذف/به‌روزرسانی | `create_foreign_key/A1ExampleCreateForeignKeyV1.kt`، `drop_foreign_key/A1ExampleDropForeignKeyV1.kt` |
| `IMigrationDropIndexAst` | نام جدول و index برای حذف | `drop_index/A1ExampleDropIndexV1.kt` |
| APIهای `Migration`، `MigrationDefinition` و `MigrationConfiguration` (چرخهٔ ثبت، نه AST schema) | فهرست عملیات `up/down` و ثبت migration منفرد/گروهی | `composite/CreateCustomersMigrationV1.kt`، `registration/MigrationRegistrationExample.kt` |

مثال‌های مستقل ADD/MODIFY/RENAME/DROP و indexها متد `migration()` را برای ساخت AST ارائه می‌کنند، سپس `render(dialect)` را برای مشاهدهٔ DDL و `execute(executor)` را برای اجرای صریح به‌کار می‌برند. بعضی مثال‌ها تنها AST یا SQL را برای نمایش می‌سازند؛ نتیجهٔ `render` را با اجرای واقعی روی دیتابیس یکی نگیرید. نمونه‌های این صفحه رفتار را شرح می‌دهند و اجرای Migration را در محیط کاربر انجام نمی‌دهند.

## 4. Query Builder

این بخش APIهای مشترک Query Builder و سپس مثال‌های نسخه‌های 1.0، 2.0، 3.0 و 3.1 را شرح می‌دهد. قواعد Metadata نیز در [Rules](#rules) آمده‌اند و به دو بخش مستقل **Model** و **Query** تقسیم شده‌اند. INSERT، UPDATE و DELETE داده را تغییر می‌دهند؛ آن‌ها را فقط روی دیتابیس توسعه یا آزمایشی اجرا کنید.

<a id="query-builder-connection"></a>
### Connection

<a id="query-builder-connection-usage"></a>
#### ارتباط‌گیری

برای اجرای Queryها از `QueryBuilderExecutor` استفاده می‌کنیم. `connection { ctx -> ... }` یک Connection را برای همهٔ Queryهای داخل callback به اشتراک می‌گذارد؛ `transaction { ctx -> ... }` نیز همهٔ Queryهای آن محدوده را روی یک Connection تراکنشی اجرا می‌کند. در هر دو حالت Query را از `ctx` بسازید و اجرا کنید، نه از root `queryManager`.

در این قطعه فرض شده `selectQuery`، `insertQuery` و `updateQuery` پیش‌تر با DSL ساخته شده‌اند. هر دو scope نتیجه‌ای از نوع `ExecuteResult<T>` برمی‌گردانند:

~~~kotlin
import gog.my_project.data_base.query.executer.manager.QueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

val queryManager = QueryBuilderExecutor()

// فرض شده selectQuery، insertQuery و updateQuery با DSL ساخته شده‌اند.

val selectResult: ExecuteResult<List<QueryRow>> = queryManager.connection { ctx ->
    ctx.queryBuilder(selectQuery).execute()
}

val transactionResult: ExecuteResult<String> = queryManager.transaction { ctx ->
    val inserted = ctx.queryBuilder(insertQuery).execute()
    if (inserted is ExecuteResult.Failure) return@transaction inserted

    val updated = ctx.queryBuilder(updateQuery).execute()
    if (updated is ExecuteResult.Failure) return@transaction updated

    ExecuteResult.Success("insert and update completed")
}
~~~

`connection {}` Connection را پس از پایان scope می‌بندد، اما `autoCommit` را تغییر نمی‌دهد و commit یا rollback خودکار ندارد. `transaction {}`، در مقابل، `autoCommit` را غیرفعال می‌کند، در موفقیت commit و در Failure rollback می‌کند. Failure اجرای Query در state scope ثبت می‌شود؛ Queryهای بعدی رد می‌شوند و Transaction حتی اگر Failure نادیده گرفته شود commit نخواهد شد. بنابراین در مثال، Failure هر مرحله صریحاً به callback برگردانده می‌شود.

<a id="query-builder-extraction"></a>
#### روش استخراج

`execute()` برای SELECT، نتیجه را به شکل `ExecuteResult<List<QueryRow>>` می‌دهد. این مسیر ستون‌ها را بر اساس aliasهای خروجی materialize می‌کند. `executeAs { row -> ... }` همان SELECT را اجرا می‌کند، هر `QueryRow` را با mapper شما به نوع دلخواه تبدیل می‌کند و `ExecuteResult<ICollection<T>>` برمی‌گرداند:

~~~kotlin
import gog.my_project.collection.ICollection
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

data class UserView(val id: Int, val name: String?)

// فرض شده selectQuery یک IQueryRenderSelectApi ساخته‌شده با DSL است.

val rawResult: ExecuteResult<List<QueryRow>> = queryManager
    .queryBuilder(selectQuery)
    .execute()

val typedResult: ExecuteResult<ICollection<UserView>> = queryManager
    .queryBuilder(selectQuery)
    .executeAs { row ->
        UserView(
            id = row.getValue("user_id") as Int,
            name = row.getValue("user_name") as String?,
        )
    }
~~~

`executeAs` نتیجه را از همان اجرای Query می‌سازد؛ برای materialize کردن داده، Query جداگانه‌ای اجرا نمی‌کند. `QueryRow.getValue()` را با aliasای بخوانید که در SELECT تعریف شده است.

<a id="query-builder-result"></a>
#### نتیجه

برای استخراج مقدار نهایی از `ExecuteResult` این متدها را به انتهای زنجیره اضافه کنید:

~~~kotlin
import gog.my_project.collection.ICollection
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.toCollection
import gog.my_project.data_base.query.executer.result.toList
import gog.my_project.data_base.manager.execute.tools.ExecuteResult

// فرض شده selectQuery، insertQuery، updateQuery و deleteQuery با DSL ساخته شده‌اند.

// SELECT خام: List<QueryRow>
val rows: List<QueryRow> = queryManager
    .queryBuilder(selectQuery)
    .execute()
    .toList()

// SELECT تایپ‌شده را یک بار اجرا کنید؛ هر دو شکل استخراج از همین نتیجه انجام می‌شود.
val typedResult: ExecuteResult<ICollection<UserView>> = queryManager
    .queryBuilder(selectQuery)
    .executeAs { row ->
        UserView(
            id = row.getValue("user_id") as Int,
            name = row.getValue("user_name") as String?,
        )
    }

val users: ICollection<UserView> = typedResult.toCollection()
val typedList: List<UserView> = typedResult.toList()

// INSERT کلید تولیدشده و UPDATE/DELETE تعداد ردیف‌های اثرگرفته را می‌دهند.
val insertedId: Long = queryManager.queryBuilder(insertQuery).execute().toValue()
val updatedRows: Int = queryManager.queryBuilder(updateQuery).execute().toValue()
val deletedRows: Int = queryManager.queryBuilder(deleteQuery).execute().toValue()
~~~

`toCollection()` همان instance از `ICollection<T>` را برمی‌گرداند؛ Collection را کپی نمی‌کند. `toList()` هم روی نتیجهٔ خام `execute()` و هم روی نتیجهٔ typed `executeAs()` قابل استفاده است؛ در هر دو حالت Query را دوباره اجرا نمی‌کند. برای خروجی typed، `toList()` عناصر `ICollection` را به `List` استاندارد تبدیل می‌کند؛ اگر هم Collection اصلی و هم List را می‌خواهید، همان `ExecuteResult` را نگه دارید و هر دو استخراج را از آن انجام دهید. `toValue()` برای مقدارهای INSERT و تعداد ردیف‌های UPDATE/DELETE به کار می‌رود. این متدها در `Success` مقدار را برمی‌گردانند، در `Failure` همان Throwable اصلی را پرتاب می‌کنند و اگر `Success` مقدار `null` داشته باشد، `IllegalStateException` می‌دهند؛ Failure به فهرست خالی یا مقدار پیش‌فرض تبدیل نمی‌شود.

می‌توانید پیش از استخراج، مقدار موفق را با `.success { ... }` تبدیل کنید؛ نوع تبدیل‌شده از `toCollection()`، `toList()` یا `toValue()` استخراج می‌شود. `.error { ... }` فقط Failure را مشاهده می‌کند و آن را بازیابی یا پنهان نمی‌کند؛ بنابراین استخراج پس از آن همچنان Failure اصلی را پرتاب خواهد کرد.

<a id="query-builder-examples"></a>
### Examples

<a id="query-builder-example-1-0"></a>
#### 1.0 — نام خام جدول و ستون

در V1 نام جدول و ستون به‌صورت String داده می‌شود. SELECT outputها alias و DataType صریح دارند.

##### SELECT

~~~kotlin
val query = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column { tableColumn("uu", "id") }
            alias("user_id")
            execute(DataType.LONG)
        }
        addColumn {
            column { tableColumn("uu", "name") }
            alias("user_name")
            execute(DataType.STRING)
        }
        addColumn {
            column { tableColumn("uu", "family") }
            alias("user_family")
            execute(DataType.STRING)
        }
        addColumn {
            column { tableColumn("up", "phone") }
            alias("user_phone")
            execute(DataType.STRING)
        }
    }
    .from { table("user_users").alias("uu") }
    .joins {
        addJoin {
            innerJoin()
            table { table("user_phones").alias("up") }
            condition {
                logicalOn()
                addCondition {
                    sideSelector { tableColumn("uu", "id") }
                    operationEqual()
                    sideValueColumn { tableColumn("up", "user_id") }
                }
            }
        }
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("uu", "id") }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(1)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery ->
        println(builtQuery.query)
        println(builtQuery.params)
    }
    .details { metadata ->
        println("meta_data: $metadata")
    }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("user_id=" + row.getValue("user_id"))
            println("user_name=" + row.getValue("user_name"))
            println("user_family=" + row.getValue("user_family"))
            println("user_phone=" + row.getValue("user_phone"))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL تولیدشده از نظر ساختار:

~~~sql
SELECT uu.id AS user_id, uu.name AS user_name,
       uu.family AS user_family, up.phone AS user_phone
FROM user_users AS uu
INNER JOIN user_phones AS up ON uu.id = up.user_id
WHERE (uu.id = :id1)
~~~

پارامتر: id1 = 1

خروجی SELECT:

~~~text
alias       DataType
user_id     LONG
user_name   STRING
user_family STRING
user_phone  STRING
~~~

استخراج بر اساس ترتیب SELECT و index انجام می‌شود؛ alias کلید QueryRow است:

~~~text
SELECT item #0 + user_id + LONG
    → ResultSet.getLong(1)
    → QueryRow["user_id"] = Long
~~~

Executor نتیجه را پیش از بسته‌شدن `ResultSet` به‌صورت eager می‌سازد. تعداد ستون‌ها و label هر ستون باید با خروجی SELECT و `ColumnAlias` متناظر برابر باشد؛ `ExecutionType` نوع خواندن JDBC را تعیین می‌کند. `QueryRow` با SQL aliasها کلیدگذاری می‌شود و دسترسی typed با `getValue(property)` از `PropertyReference` و alias همان خروجی استفاده می‌کند.

##### INSERT

~~~kotlin
val query = QueryRenderInsertBuilder()
    .table { table("user_users") }
    .addValue {
        column("name")
        setValue {
            paramName("name")
            paramValue("Ali")
        }
    }
    .addValue {
        column("family")
        setValue {
            paramName("family")
            paramValue("Sadegi")
        }
    }
    .addValue {
        column("age")
        setValue {
            paramName("age")
            paramValue(50)
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { generatedKey -> println("generated key: " + generatedKey); generatedKey }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
INSERT INTO user_users (name, family, age)
VALUES (:name, :family, :age)
~~~

پارامترها: name = "Ali"، family = "Sadegi"، age = 50. نتیجه ExecuteResult<Long> و generated key است.

##### UPDATE

~~~kotlin
val query = QueryRenderUpdateBuilder()
    .table { table("user_users").alias("uu") }
    .addValue {
        column("uu", "name")
        setValue {
            paramName("uu_name")
            paramValue("--changed--")
        }
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("uu", "id") }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(5)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { affectedRows -> println("updated rows: " + affectedRows); affectedRows }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
UPDATE user_users AS uu
SET uu.name = :uu_name
WHERE (uu.id = :id1)
~~~

پارامترها: uu_name = "--changed--"، id1 = 5. نتیجه ExecuteResult<Int> و تعداد ردیف‌های متأثر است.

##### DELETE

~~~kotlin
val query = QueryRenderDeleteBuilder()
    .table { table("user_users") }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("", "id") }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(5)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { affectedRows -> println("deleted rows: " + affectedRows); affectedRows }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE FROM user_users WHERE (id = :id1). پارامتر: id1 = 5. برای این مثال تک‌جدولی target جدا لازم نیست.

---

<a id="query-builder-example-2-0"></a>
#### 2.0 — Model-backed Query

در V2 نام جدول و ستون از Model و propertyها می‌آید. SELECTهای property-backed از metadata موجود Model برای نام خروجی و نوع استخراج استفاده می‌کنند.

##### Modelهای V2

دو Model مورد استفاده در مثال‌ها:

~~~kotlin
@QBTable(name = "user_users", alias = "uu")
class UserModelV2 : IModelBase {
    @QBColumn(name = "id", alias = "user_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "name", alias = "user_name")
    val name: String? = null

    @QBColumn(name = "family", alias = "user_family")
    val family: String? = null

    @QBColumn(name = "age", alias = "user_age")
    val age: Int? = null
}

@QBTable(name = "user_phones", alias = "up")
class UserPhoneModelV2 : IModelBase {
    @QBColumn(name = "id", alias = "user_phone_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "phone", alias = "user_phone")
    val phone: String? = null

    @QBColumn(name = "user_id", alias = "phone_user_id")
    val userId: Int = 0
}
~~~

معنای metadata این Modelها:

~~~text
UserModelV2      → table user_users، source alias پیش‌فرض uu
UserPhoneModelV2 → table user_phones، source alias پیش‌فرض up
UserModelV2::id  → column id، SELECT output alias user_id، Kotlin type Int
UserModelV2::name → column name، SELECT output alias user_name، Kotlin type String?
UserModelV2::family → column family، SELECT output alias user_family، Kotlin type String?
UserModelV2::age → column age، SELECT output alias user_age، Kotlin type Int?
UserPhoneModelV2::phone → column phone، SELECT output alias user_phone، Kotlin type String?
~~~

در declaration، `model(UserModelV2::class)` منبع را انتخاب می‌کند و `column { tableSelector(...); columnSelector(...) }` ستون property-backed را برمی‌گزیند. Annotation، alias خروجی SELECT و نوع استخراج پیش‌فرض را فراهم می‌کند؛ alias و نوع صریح در خود SELECT می‌توانند آن خروجی‌ها را override کنند. overload قدیمی `column(KClass, property)` همچنان برای سازگاری وجود دارد، اما deprecated است.

##### SELECT

~~~kotlin
val query = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::id)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::name)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::family)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::age)
            }
        }
        addColumn {
            column {
                tableSelector(UserPhoneModelV2::class)
                columnSelector(UserPhoneModelV2::phone)
            }
        }
    }
    .from { model(UserModelV2::class) }
    .joins {
        addJoin {
            innerJoin()
            table { table(UserPhoneModelV2::class) }
            condition {
                logicalOn()
                addCondition {
                    sideSelector {
                        tableSelector(UserModelV2::class)
                        columnSelector(UserModelV2::id)
                    }
                    operationEqual()
                    sideValueColumn {
                        tableSelector(UserPhoneModelV2::class)
                        columnSelector(UserPhoneModelV2::userId)
                    }
                }
            }
        }
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector {
                    tableSelector(UserModelV2::class)
                    columnSelector(UserModelV2::id)
                }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(1)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .details { metadata -> println("meta_data: $metadata") }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("id=" + row.getValue(UserModelV2::id))
            println("name=" + row.getValue(UserModelV2::name))
            println("phone=" + row.getValue(UserPhoneModelV2::phone))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
SELECT uu.id AS user_id, uu.name AS user_name,
       uu.family AS user_family, uu.age AS user_age, up.phone AS user_phone
FROM user_users AS uu
INNER JOIN user_phones AS up ON uu.id = up.user_id
WHERE (uu.id = :id1)
~~~

پارامتر: id1 = 1.

خروجی SELECT:

~~~text
property                 output alias  DataType
UserModelV2::id          user_id       INT
UserModelV2::name        user_name     STRING
UserModelV2::family      user_family   STRING
UserModelV2::age         user_age      INT
UserPhoneModelV2::phone  user_phone    STRING
~~~

نتیجه را با row.getValue(UserModelV2::id) و propertyهای متناظر می‌خوانیم. برای expression یا ستون خام که property metadata ندارد، alias و execute(DataType.X) باید صریحاً تعریف شوند؛ type از schema یا نام ستون حدس زده نمی‌شود.

##### INSERT

~~~kotlin
val query = QueryRenderInsertBuilder()
    .table { table(UserModelV2::class) }
    .addValue {
        column(UserModelV2::class, UserModelV2::name)
        setValue {
            paramName("name")
            paramValue("Ali")
        }
    }
    .addValue {
        column(UserModelV2::class, UserModelV2::family)
        setValue {
            paramName("family")
            paramValue("Sadegi")
        }
    }
    .addValue {
        column(UserModelV2::class, UserModelV2::age)
        setValue {
            paramName("age")
            paramValue(50)
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { key -> println("generated key: " + key); key }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: INSERT INTO user_users (name, family, age) VALUES (...). پارامترها همان مقدارهای Ali، Sadegi و 50 هستند؛ نتیجه ExecuteResult<Long> است.

##### UPDATE

~~~kotlin
val query = QueryRenderUpdateBuilder()
    .table { table(UserModelV2::class) }
    .addValue {
        column(UserModelV2::class, UserModelV2::name)
        setValue {
            paramName("name")
            paramValue("--changed--")
        }
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector {
                    tableSelector(UserModelV2::class)
                    columnSelector(UserModelV2::id)
                }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(5)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { count -> println("updated rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: UPDATE user_users AS uu SET uu.name = ... WHERE (uu.id = ...). پارامترها --changed-- و id1 = 5؛ نتیجه ExecuteResult<Int> است.

##### DELETE

~~~kotlin
val query = QueryRenderDeleteBuilder()
    .addTarget("uu")
    .table { table(UserModelV2::class) }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector {
                    tableSelector(UserModelV2::class)
                    columnSelector(UserModelV2::id)
                }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(5)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { count -> println("deleted rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE uu FROM user_users AS uu WHERE (uu.id = ...). پارامتر: id1 = 5؛ نتیجه ExecuteResult<Int> است.

---

<a id="query-builder-example-3-0"></a>
#### 3.0 — CTE-backed SELECT و Model-backed Mutation

در V3، SELECTها از declarationهای Query استفاده می‌کنند که به CTE و sourceهای ترکیبی تبدیل می‌شوند. نام پیش‌فرض CTEهای declaration-based از `QBQuery.name` می‌آید؛ `QBQuery.alias` alias پیش‌فرض occurrence است. در API فعلی، declaration با `cte(declaration)` داخل بلوک `from { ... }` انتخاب می‌شود و `alias("...")` همان source occurrence را نام‌گذاری می‌کند. `cte(name)` نیز برای انتخاب CTE بر اساس نام باقی می‌ماند. هر خروجی با `QBQuerySelect` نام منطقی و SQL alias خودش را مشخص می‌کند. INSERT، UPDATE و DELETE همچنان با `UserModelV3` ساخته می‌شوند.

##### قرارداد فعلی SELECT FROM

ورودی عمومی منبع SELECT یکپارچه است: `.from { ... }`. داخل این بلوک، source از طریق `IQueryTableApi` انتخاب می‌شود:

~~~kotlin
// جدول با نام خام
from {
    table("user_users")
    alias("uu")
}

// منبع Model
from {
    model(UserModelV3::class)
}

// Query declaration با پارامترهای متصل‌شده به نمونه
from {
    cte(UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters(userId = 5)))
    alias("details")
}
~~~

`cte(String)` و `alias(String)` نیز روی همان receiver در دسترس‌اند. اگر برای Query alias صریح ندهیم، alias metadata تعریف Query به‌عنوان alias پیش‌فرض occurrence استفاده می‌شود؛ alias داخل بلوک فقط همان occurrence را override می‌کند.

برای SELECT تو‌در‌تو، `table` یک lambda با receiver از نوع `IQueryRenderSelectApi` می‌گیرد و یک derived table می‌سازد؛ آن را به CTE تبدیل نمی‌کند:

~~~kotlin
from {
    table {
        select {
            addColumn {
                column { tableColumn("uu", "id") }
                alias("id")
                execute(DataType.INT)
            }
        }
        from {
            table("user_users")
            alias("uu")
        }
    }
    alias("q")
}
~~~

این مثال sourceای به شکل `(SELECT uu.id AS id FROM user_users AS uu) AS q` می‌سازد. پارامترهای SELECT داخلی به ترتیب placeholderهای SQL نهایی حفظ می‌شوند. `table(KClass)` همچنان برای مسیرهای مشترک CRUD/JOIN باقی است؛ در sourceهای Model در SELECT از `model(KClass)` استفاده می‌شود. CTE declaration در `from { ... }` با `cte(declaration)` انتخاب می‌شود و Builder آن را به مسیر composition موجود می‌سپارد.

##### Queryهای V3

V3 چهار Query declaration نمونه دارد. هر declaration نام runtime، alias پیش‌فرض source، فیلترهای ورودی و propertyهای خروجی typed را مشخص می‌کند. Propertyهای خروجی فقط metadata و identity هستند؛ getter آن‌ها عمداً خطا می‌دهد و مقدار واقعی از SELECT materialize می‌شود.

| Query | `QBQuery.name` / نام CTE | `QBQuery.alias` پیش‌فرض | ورودی‌ها | خروجی‌های typed |
|---|---|---|---|---|
| `UserStatsQueryV3` | `user_stats_query_v3` | `v3_user_stats` | `userId` و `minAge` اختیاری | `id`, `name`, `family`, `age` |
| `UserPhoneQueryV3` | `user_phone_query_v3` | `v3_user_phone_details` | `userId` اختیاری | `id`, `name`, `family`, `age`, `phone` |
| `UserPageQueryV3` | `user_page_query_v3` | `v3_user_page` | `limit` پیش‌فرض 2 و `offset` پیش‌فرض 0 | `id`, `name`, `family`, `age`; از UserStats عبور داده می‌شوند |
| `UserDisplayQueryV3` | `user_display_query_v3` | `v3_user_display` | `userId` و `minAge` اختیاری | `id`, `fullName`, `age` |

برای هر property خروجی، `QBQuerySelect.name` نام منطقی و `QBQuerySelect.alias` نام SQL منتشرشده است:

| Query property | نام منطقی | SQL alias |
|---|---|---|
| `UserStatsQueryV3::id/name/family/age` | همان نام property | همان نام property |
| `UserPhoneQueryV3::id` | `id` | `user_id` |
| `UserPhoneQueryV3::name` | `name` | `user_name` |
| `UserPhoneQueryV3::family` | `family` | `user_family` |
| `UserPhoneQueryV3::age` | `age` | `user_age` |
| `UserPhoneQueryV3::phone` | `phone` | `user_phone` |
| `UserPageQueryV3::id/name/family/age` | همان نام property | همان نام property |
| `UserDisplayQueryV3::id` | `id` | `id` |
| `UserDisplayQueryV3::fullName` | `full_name` | `fullName` |
| `UserDisplayQueryV3::age` | `age` | `age` |

همهٔ propertyهای خروجی چهار Query در source با `QBQuerySelect` مشخص شده‌اند. نمونهٔ `UserStatsQueryV3` بالا declaration کامل را نشان می‌دهد؛ این قطعه، metadata کامل سه Query دیگر را خلاصه می‌کند:

~~~kotlin
@QBQuery(name = "user_phone_query_v3", alias = "v3_user_phone_details")
class UserPhoneQueryV3 {
    @QBQuerySelect(name = "id", alias = "user_id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "user_name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "user_family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "user_age")
    val age: Int? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "phone", alias = "user_phone")
    val phone: String? get() = error("Typed output property must not be read")
}

@QBQuery(name = "user_page_query_v3", alias = "v3_user_page")
class UserPageQueryV3 {
    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")
}

@QBQuery(name = "user_display_query_v3", alias = "v3_user_display")
class UserDisplayQueryV3 {
    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "full_name", alias = "fullName")
    val fullName: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")
}
~~~

نمونهٔ declaration در UserStatsQueryV3:

~~~kotlin
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3

@QBQuery(name = "user_stats_query_v3", alias = "v3_user_stats")
class UserStatsQueryV3(
    override val params: RelationFilters,
) : ICteDeclaration<UserStatsQueryV3.RelationFilters> {

    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")

    override fun createCte(params: RelationFilters): Cte =
        buildCte {
            select {
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::id)
                    }
                    alias(UserStatsQueryV3::id)
                }
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::name)
                    }
                    alias(UserStatsQueryV3::name)
                }
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::family)
                    }
                    alias(UserStatsQueryV3::family)
                }
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::age)
                    }
                    alias(UserStatsQueryV3::age)
                }
            }
            from { model(UserModelV3::class) }
        }
}
~~~

در سورس فعلی V3، سه declaration مستقیماً `ICteDeclaration<...>` را پیاده می‌کنند و `UserDisplayQueryV3` از کلاس پایهٔ اختیاری `CteDeclaration<...>` استفاده می‌کند. این توضیح، وضعیت فعلی سورس را ثبت می‌کند. `alias(Query::property)` خروجی SELECT را به property typed متصل می‌کند، `cteOutput(Query::property, qualifier)` آن خروجی را از CTE فعال می‌خواند و `row.getValue(Query::property)` مقدار materialize‌شده را برمی‌گرداند؛ resolver getter را برای خواندن metadata اجرا نمی‌کند.

###### قرارداد مقصد قطعی V3.0 برای Query و پارامترها

در معماری مقصد، خود Query با constructor بدون آرگومان ساخته می‌شود و پارامترهای اجرایی روی آن ذخیره نمی‌شوند. `UserStatsQueryV3` که اجرای مستقل نیاز دارد از `CteDeclaration<RelationFilters>()` ارث می‌برد و `createCte(params)` ورودی را صریح می‌گیرد. برای هر row، mapper نمونهٔ تازهٔ همین کلاس را می‌سازد و فقط propertyهای خروجی دارای getter/setter واقعی را مقدار می‌دهد.

نمونهٔ مرجع برای declaration و composition در `A2ExampleSelectV3` پس از migration:

~~~kotlin
val cte = UserStatsQueryV3()
val cteParams = UserStatsQueryV3.RelationFilters(userId = 5)
val withName = "active_user_stats_v3"

val withQuery = QueryRenderSelectBuilder().withs {
    addWith { with(withName, cte(cteParams)) }
}

val directResult = cte.execute(queryManager, cteParams)
~~~

`cte(cteParams)` از operator factory استفاده می‌کند و یک `Cte` می‌سازد؛ overload موجود `with(String, Cte)` composition را انجام می‌دهد. برای FROM، معادل آن `from { cte(declaration(params)) }` است که از overload موجود `cte(Cte)` استفاده می‌کند. هر دو مسیر، params را فقط در همان ساخت/اجرا مصرف می‌کنند؛ `cte` هیچ property پارامتری ندارد. `directResult` همچنان قرارداد legacy یعنی `ExecuteResult<List<QueryRow>>` را دارد.

`QBQuerySelect.name` نام منطقی خروجی است و `QBQuerySelect.alias` نام منتشرشدهٔ SQL است. برای نمونه، در `UserPhoneQueryV3`، `id` نام منطقی دارد ولی با alias `user_id` منتشر می‌شود؛ reference typed هم از metadata همین alias استفاده می‌کند.

جزئیات خروجی‌ها:

~~~text
UserStatsQueryV3
    table source: UserModelV3
    outputs: id, name, family, age (logical name = SQL alias)

UserPhoneQueryV3
    table source: UserModelV3
    INNER JOIN: UserPhoneModelV3
    outputs: id→user_id, name→user_name, family→user_family,
             age→user_age, phone→user_phone (logical name → SQL alias)

UserPageQueryV3
    source: UserStatsQueryV3
    outputs: id, name, family, age (logical name = SQL alias)
    applies limit and offset from Query filters

UserDisplayQueryV3
    table source: UserModelV3
    outputs: id, fullName, age
    fullName: logical name full_name, published SQL alias fullName
    expression: concat(name, '-', family)
~~~

هر چهار declaration با `QBQuery.name` نام پیش‌فرض CTE خود را می‌گیرند. برای استفاده در SELECT، declaration به `cte(declaration)` داخل `from { ... }` داده می‌شود؛ Builder از پارامترهای متصل‌شده استفاده می‌کند، `createCte(params)` را برای ساخت تعریف CTE فراخوانی می‌کند و نتیجه را به مسیر composition داخلی می‌سپارد. کلاس پایهٔ `CteDeclaration<P>` اختیاری است؛ پیاده‌سازی مستقیم `ICteDeclaration<P>` نیز پشتیبانی می‌شود.

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.querySelect { query ->
            query
                .from {
                    cte(UserStatsQueryV3(UserStatsQueryV3.RelationFilters(userId = 5)))
                    alias("stats")
                }
                .select {
                    addColumn { cteOutput(UserStatsQueryV3::id, "stats") }
                    addColumn { cteOutput(UserStatsQueryV3::name, "stats") }
                }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .details { metadata -> println("meta_data: $metadata") }
    .execute()
~~~

در این نمونه alias صریح `stats` در بلوک `from` برای source occurrence تنظیم شده و همان qualifier به `cteOutput` داده شده است. در نمونهٔ بعدی، `details` همین نقش را برای UserPhone دارد. نام CTE از `QBQuery.name` می‌آید و با alias occurrence یکی نیست.

وابستگی Query چندلایه در UserPageQueryV3 به این شکل است:

~~~text
UserStatsQueryV3
    → UserPageQueryV3
        → SELECT consumer
~~~

UserPage خروجی‌های Stats را با همان propertyهای typed عبور می‌دهد و `limit`/`offset` را روی query داخلی اعمال می‌کند. UserPhone با join بین `UserModelV3` و `UserPhoneModelV3` خروجی `phone` را اضافه می‌کند. UserDisplay خروجی `fullName` را از expression محاسبه‌شدهٔ concat نام و نام خانوادگی می‌سازد.

##### SELECT

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.querySelect { query ->
            query
                .from {
                    cte(UserPhoneQueryV3(UserPhoneQueryV3.RelationFilters()))
                    alias("details")
                }
                .select {
                    addColumn { cteOutput(UserPhoneQueryV3::id, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::name, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::family, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::age, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::phone, "details") }
                }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .details { metadata -> println("meta_data: $metadata") }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("id=" + row.getValue(UserPhoneQueryV3::id))
            println("name=" + row.getValue(UserPhoneQueryV3::name))
            println("family=" + row.getValue(UserPhoneQueryV3::family))
            println("age=" + row.getValue(UserPhoneQueryV3::age))
            println("phone=" + row.getValue(UserPhoneQueryV3::phone))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
WITH user_phone_query_v3 AS (
    SELECT uu.id AS user_id, uu.name AS user_name, uu.family AS user_family,
           uu.age AS user_age, up.phone AS user_phone
    FROM user_users AS uu
    INNER JOIN user_phones AS up ON uu.id = up.user_id
)
SELECT details.user_id AS user_id, details.user_name AS user_name,
       details.user_family AS user_family, details.user_age AS user_age,
       details.user_phone AS user_phone
FROM user_phone_query_v3 AS details
~~~

خروجی SELECT:

~~~text
property                    logical name  SQL alias   DataType
UserPhoneQueryV3::id        id            user_id     INT
UserPhoneQueryV3::name      name          user_name   STRING
UserPhoneQueryV3::family    family        user_family STRING
UserPhoneQueryV3::age       age           user_age    INT
UserPhoneQueryV3::phone     phone         user_phone  STRING
~~~

Qualifier `details` به‌صورت صریح در `from` و `cteOutput` آمده است. مثال‌های A2 تا A7 نیز `whereIn`، `whereLike`، شرط null، شرط مقداری، Query صفحه‌بندی‌شده و خروجی `fullName` را نشان می‌دهند.

##### افزودن CTE به `WITH`

`IQueryWithsItemApi` دو overload برای افزودن CTE جدید دارد. `with(cte)` از نام مؤثر خود شیء `Cte` استفاده می‌کند؛ `with(withName, cte)` نام صریح caller را برای عبارت SQL `WITH` انتخاب می‌کند:

~~~kotlin
val statsDeclaration = UserStatsQueryV3(UserStatsQueryV3.RelationFilters())
val statsCte = statsDeclaration.createCte(statsDeclaration.params)

val withResolvedName = QueryRenderSelectBuilder().withs {
    addWith { with(statsCte) }
}

val withExplicitName = QueryRenderSelectBuilder().withs {
    addWith { with("published_stats", statsCte) }
}
~~~

در overload نام‌دار، `published_stats` بر `statsCte.name` اولویت دارد، اما مقدار `statsCte.name` را تغییر نمی‌دهد. Builder نام انتخاب‌شده را در تعریف WITH و ارجاع‌های وابستگی به‌صورت هماهنگ به کار می‌برد. نام CTE از alias پیش‌فرض occurrence یعنی `QBQuery.alias` مستقل است؛ alias منبع همچنان از metadata یا `alias("...")` در همان occurrence می‌آید. نام صریح خالی و نام مؤثر تکراری رد می‌شود. APIهای قدیمی `with(name, ICte)` و `with(name, block)` نیز حفظ شده‌اند.

##### INSERT

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.queryInsert {
            table { table(UserModelV3::class) }
            addValue {
                column(UserModelV3::class, UserModelV3::name)
                setValue {
                    paramName("name")
                    paramValue("Ali")
                }
            }
            addValue {
                column(UserModelV3::class, UserModelV3::family)
                setValue {
                    paramName("family")
                    paramValue("Sadegi")
                }
            }
            addValue {
                column(UserModelV3::class, UserModelV3::age)
                setValue {
                    paramName("age")
                    paramValue(50)
                }
            }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .execute()
    .success { key -> println("generated key: " + key); key }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: INSERT INTO user_users (name, family, age) VALUES (...). نتیجه ExecuteResult<Long> است.

##### UPDATE

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.queryUpdate {
            table { table(UserModelV3::class) }
            addValue {
                column(UserModelV3::class, UserModelV3::name)
                setValue {
                    paramName("name")
                    paramValue("--changed--")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserModelV3::class)
                            columnSelector(UserModelV3::id)
                        }
                        operationEqual()
                        sideValueParam {
                            paramName("id1")
                            paramValue(5)
                        }
                    }
                }
            }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .execute()
    .success { count -> println("updated rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: UPDATE user_users AS uu SET uu.name = ... WHERE (uu.id = ...). نتیجه ExecuteResult<Int> است.

##### DELETE

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.queryDelete {
            addTarget("uu")
            table { table(UserModelV3::class) }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserModelV3::class)
                            columnSelector(UserModelV3::id)
                        }
                        operationEqual()
                        sideValueParam {
                            paramName("id1")
                            paramValue(5)
                        }
                    }
                }
            }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .execute()
    .success { count -> println("deleted rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE uu FROM user_users AS uu WHERE (uu.id = ...). نتیجه ExecuteResult<Int> است.

---

<a id="query-builder-example-3-1"></a>
#### 3.1 — اجرای typed CTE

در V3.1، declarationهای CTE علاوه بر استفاده به‌عنوان منبع SELECT، مستقیماً از مسیر `CteDeclaration.execute(queryManager, params)` هم اجرا می‌شوند. نتیجه typed به صورت `ExecuteResult<ICollection<T>>` است و می‌توان آن را با `toCollection()` استخراج کرد:

~~~kotlin
import gog.my_project.collection.ICollection
import gog.my_project.data_base.query.example.v3_1.queries.relations.UserPageQueryV31
import gog.my_project.data_base.query.executer.result.toCollection

val rows: ICollection<UserPageQueryV31> = UserPageQueryV31()
    .execute(queryManager, UserPageQueryV31.RelationFilters())
    .toCollection()

rows.forEach { row ->
    println("id=${row.id}, name=${row.name}, family=${row.family}, age=${row.age}")
}
~~~

`UserPageQueryV31` خروجی‌های خودش را از `UserStatsQueryV31` می‌گیرد و فیلترهای `limit` و `offset` دارد. مثال‌های V3.1 همچنین `UserPhoneQueryV31` را برای join با تلفن و `UserDisplayQueryV31` را برای ساخت نام کامل نشان می‌دهند.

<a id="query-builder-version-comparison"></a>
#### خلاصه تفاوت نسخه‌ها

| نسخه | SELECT output و دسترسی | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| V1 | alias و DataType صریح؛ خواندن با alias | generated key: Long | affected rows: Int | affected rows: Int |
| V2 | Model property؛ خواندن با Model::property | Model | Model | Model |
| V3 | Query property و SQL alias اعلام‌شده؛ خواندن با `Query::property` | Model V3 | Model V3 | Model V3 |
| V3.1 | اجرای typed CTE و دریافت `ICollection<T>`؛ خواندن با propertyهای declaration | — | — | — |

`QueryConsumer.execute()` همهٔ ردیف‌ها را به‌صورت eager و با نوع `ExecuteResult<List<QueryRow>>` برمی‌گرداند؛ `first()` فقط ردیف اول را می‌خواند. متد `execute()` در Consumerهای INSERT، UPDATE و DELETE نیز همان لحظه عملیات را اجرا می‌کند و به‌ترتیب `ExecuteResult<Long>` یا `ExecuteResult<Int>` می‌دهد. متد `sql()` فقط SQL و bindingهای آن را می‌سازد و Query را اجرا نمی‌کند.

پارامترهای جدید به‌صورت AST تعریف می‌شوند: شرط‌ها از `sideValueParam { paramName(...); paramValue(...) }`، مقدارهای INSERT/UPDATE از `setValue { ... }` و مجموعه‌ها از `sideValueCollection { addParam { ... } }` استفاده می‌کنند. اگر `paramName` داده نشود، Builder نام تعیین می‌کند؛ نوع SQL از مقدار پارامتر resolve می‌شود. تعریف‌های هم‌نام فقط وقتی مقدار و SQL type یکسان داشته باشند در scope مشترک reuse می‌شوند؛ تفاوت مقدار یا نوع به binding مستقل می‌انجامد. Renderer پارامترمحور، SQL نمایشی با نام‌هایی مانند `:id1` و SQL JDBC با `?` تولید می‌کند و binding هر placeholder را در ترتیب ظهور SQL نگه می‌دارد. Executor این دنباله را از طریق `BoundQuery` به‌صورت positional bind می‌کند؛ بنابراین ترتیب نهایی SQL، از جمله برای SELECT تو‌در‌تو و CTE، مرجع ترتیب binding است. مسیر قدیمی `BuiltQuery` با placeholder نام‌دار نیز برای ورودی‌های legacy حفظ شده است.

پیش از اجرای SELECT، alias و `ExecutionType` تمام خروجی‌ها اعتبارسنجی می‌شوند. هنگام materialization نیز تعداد خروجی‌های ResultSet و label هر ستون با SELECT تطبیق داده می‌شود؛ مقدار هر خانه با `ExecutionType` متناظر خوانده می‌شود و `QueryRow` برای دسترسی رشته‌ای از SQL alias و برای دسترسی typed از `PropertyReference` همان خروجی استفاده می‌کند.

##### مشاهدهٔ Metadata مؤثر SELECT با `details`

`QueryConsumer.details()`، Metadata مؤثر SELECT را از Provider همان Query دریافت می‌کند. نسخهٔ callback یعنی `details { metadata -> ... }` Metadata را به callback می‌دهد و همان Consumer را برمی‌گرداند؛ بنابراین می‌توان آن را میان `.sql { ... }` و `.execute()` زنجیره کرد:

~~~kotlin
import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .details { metadata -> println(formatEffectiveQueryMetadata(metadata)) }
    .execute()
~~~

تابع formatEffectiveQueryMetadata در ماژول Example اطلاعات را در دو جدول کنسولی، یکی برای Relationها و دیگری برای خروجی‌ها، نمایش می‌دهد. مقدار null با `<null>`، رشتهٔ خالی با `<empty>` و فهرست بدون عضو با `<none>` نمایش داده می‌شود. Metadata فعلی CTE و Query را گزارش می‌کند؛ sourceهای عادی Model یا جدول در این فهرست Relationها قرار نمی‌گیرند، بنابراین SELECT بدون CTE در هر دو بخش `<none>` نشان می‌دهد.

مقدار `metadata` از نوع `EffectiveQueryMetadata` است و فهرست Relationها و خروجی‌های مؤثر را نگه می‌دارد؛ از جمله نام منطقی Query، نام runtime مربوط به CTE، نام SQL در `WITH`، alias مربوط به occurrence، نام منطقی خروجی، alias منتشرشده در SQL و `executionType`. فیلدهای nullable در صورت نبود مقدار `null` باقی می‌مانند و خروجی‌ها و Relationها ترتیب Query را حفظ می‌کنند.

فراخوانی `details` به‌تنهایی SQL را render نمی‌کند و Query را اجرا نمی‌کند. اگر SELECT سفارشی Provider مربوط به `EffectiveQueryMetadata` را نداشته باشد، `details()` با `IllegalStateException` روشن خطا می‌دهد؛ این محدودیت بر `.sql()` و `.execute()` اثری ندارد. این API متعلق به SELECT است و برای Consumerهای INSERT، UPDATE و DELETE ارائه نشده است.

### Rules

#### ساختار Metadata در AST فعلی

Metadata منبع، selector ستون پایه و خروجی SELECT در ساختارهای جدا نگه‌داری می‌شوند:

| ساختار | مقادیر | ترتیب Resolve یا کاربرد |
|---|---|---|
| `QueryTableAstMetadata` | `nameFromModel`، `nameFromAnnotation`، `nameFromAst`؛ و فیلدهای مشابه `aliasFrom...` | `resolveName()` و `resolveAlias()` هر دو AST ← Annotation ← Model |
| `QueryColumnsBaseAstMetadata` | `tableAliasFrom...`، `columnNameFrom...`، `columnAliasFromModel` و `columnAliasFromAnnotation` | alias منبع و نام ستون: AST ← Annotation ← Model؛ alias پیش‌فرض ستون: Annotation ← Model |
| `QueryColumnsAstMetadata` | `ColumnAlias`، `ExecutionType`، `IsExecutionTypeExplicit`، `PropertyReference` و `ColumnMethod` | alias/نوع/هویت خروجی SELECT؛ مستقل از نام و alias منبع |

در منبع derived SELECT، AST نگه‌دارندهٔ `derivedQuery` است و فیلدهای نام منبع خالی می‌مانند؛ alias occurrence جداگانه Resolve می‌شود. `cte(String)` نام CTE را در Metadata منبع می‌گذارد، درحالی‌که `cte(declaration)` هویت relation را در context composition نگه می‌دارد. در API فعلی `execute(DataType)` روی خروجی SELECT (`IQueryColumnsApi`) اعمال می‌شود، نه روی selector ستون پایه؛ مقدار صریح آن فقط `ExecutionType` خروجی را override می‌کند و نوع Property یا Metadata منبع را تغییر نمی‌دهد.

<a id="rules-model"></a>
#### Model

##### ۱.۱. Metadata سطح کلاس

تعریف Model:

```kotlin
@QBTable(name = "user_users", alias = "uu")
class UserModel : IModelBase
```

###### مرحله اول: مقادیر پیش‌فرض

Engine مقادیر اولیه را از نام کلاس استخراج می‌کند:

```text
tableName  = user
tableAlias = user
```

در این مثال، پسوند انتهایی `Model` از نام کلاس حذف و نام باقی‌مانده به `snake_case` تبدیل می‌شود.

###### مرحله دوم: اعمال `QBTable`

مقادیر Annotation جایگزین مقادیر پیش‌فرض متناظر می‌شوند:

```text
tableName  = user_users
tableAlias = uu
```

- `QBTable.name` نام جدول منبع را مشخص می‌کند.
- `QBTable.alias`، alias پیش‌فرض جدول را مشخص می‌کند.

###### مرحله سوم: اعمال تنظیمات DSL

```kotlin
table {
    table(UserModel::class)
    alias("uu1")
}
```

مقادیر نهایی:

```text
tableName  = user_users
tableAlias = uu1
```

alias صریح DSL بر alias تعریف‌شده در Annotation اولویت دارد، اما نام جدول را تغییر نمی‌دهد.

###### ترتیب اولویت در سطح کلاس

| فیلد | ترتیب Resolve |
|---|---|
| `tableName` | نام پیش‌فرض حاصل از کلاس ← `QBTable.name` |
| `tableAlias` | alias پیش‌فرض ← `QBTable.alias` ← alias صریح DSL |

در این مثال، چون override مستقیمی برای `tableName` در DSL وجود ندارد، مقدار Annotation حفظ می‌شود.

##### ۱.۲. Metadata سطح Property

تعریف Property:

```kotlin
@QBColumn(
    name = "user_id",
    alias = "user_id",
    primaryKey = true
)
val id: Int = 0
```

فرض می‌کنیم Metadata جدول قبلاً Resolve شده است.

###### مرحله اول: مقادیر پیش‌فرض

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = id
columnAlias    = id
columnDataType = INT
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- `columnName` از نام Property گرفته می‌شود.
- `columnAlias` به‌صورت پیش‌فرض برابر نام Property است.
- `columnDataType` از نوع تعریف‌شده Property، یعنی `Int`، استنباط می‌شود.

###### مرحله دوم: اعمال `QBColumn`

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = user_id
columnAlias    = user_id
columnDataType = INT
```

در این مرحله:

- `QBColumn.name` نام ستون منبع را override می‌کند.
- `QBColumn.alias`، alias پیش‌فرض ستون را override می‌کند.
- نوع داده همچنان `INT` باقی می‌ماند، زیرا Annotation در این مثال نوع داده را تغییر نمی‌دهد.

همچنین Metadata مربوط به `primaryKey = true` باید مستقل از نام و alias ستون حفظ شود.

###### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    column {
        tableSelector(UserModel::class)
        columnSelector(UserModel::id)
    }
    alias("user_id_out")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = user_id
columnAlias    = user_id_out
columnDataType = LONG
```

نتیجه:

- `columnName` برابر `user_id` باقی می‌ماند.
- `columnAlias` به `user_id_out` تغییر می‌کند.
- `columnDataType` به دلیل فراخوانی `execute(DataType.LONG)` برابر `LONG` می‌شود.

تغییر نوع خروجی Query نباید نوع تعریف‌شده Property یا Metadata اصلی Model را تغییر دهد.

###### ترتیب اولویت در سطح Property

| فیلد | ترتیب Resolve |
|---|---|
| `columnName` | نام پیش‌فرض Property ← `QBColumn.name` |
| `columnAlias` | alias پیش‌فرض Property ← `QBColumn.alias` ← alias صریح DSL |
| `columnDataType` | نوع استنباط‌شده از Property ← نوع صریح DSL از طریق `execute(DataType)` |

---

<a id="rules-query"></a>
#### Query

در Query، چند مفهوم مستقل وجود دارند که نباید با یکدیگر ادغام شوند:

- نام منطقی تعریف Query
- نام CTE مورد استفاده در SQL
- alias پیش‌فرض Query
- alias یک occurrence مشخص از CTE در Query
- نام منطقی خروجی Property
- SQL alias خروجی
- نوع داده خروجی

این مقادیر حتی اگر در یک مثال برابر باشند، الزاماً معنای یکسانی ندارند.

##### ۲.۱. Metadata سطح کلاس

تعریف Query:

```kotlin
@QBQuery(
    name = "user_display_query_v3",
    alias = "v3_user_display"
)
class UserDisplayQueryV3(
    params: UserDisplayQueryV3.RelationFilters,
) : CteDeclaration<UserDisplayQueryV3.RelationFilters>(params) {
    data class RelationFilters(val userId: Int? = null)
}
```

###### مرحله اول: مقادیر پیش‌فرض

Engine نام‌های اولیه را از قرارداد نام‌گذاری کلاس استخراج می‌کند:

```text
cteName             = user_display_query_v3
defaultOccurrenceAlias = user_display_query_v3
```

در این نام کلاس، نام به `snake_case` تبدیل می‌شود؛ قرارداد حذف پسوند `Query` فقط هنگامی اعمال می‌شود که نام کلاس واقعاً به آن پسوند ختم شود.

###### مرحله دوم: اعمال `QBQuery`

```text
cteName               = user_display_query_v3
defaultOccurrenceAlias = v3_user_display
```

در این مرحله:

- `QBQuery.name` نام پیش‌فرض runtime برای CTE را تعیین می‌کند.
- `QBQuery.alias`، alias پیش‌فرض occurrence منبع را تعیین می‌کند.

این مقادیر مستقل‌اند: نام CTE هویت SQL در `WITH` را مشخص می‌کند و alias فقط source occurrence را نام‌گذاری می‌کند.

###### مرحله سوم: ساخت CTE و اعمال DSL

```kotlin
from {
    cte(UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters()))
    alias("cte_user_display")
}
```

مقادیر نهایی CTE و occurrence در این مثال:

```text
runtime CTE name       = user_display_query_v3
source occurrence alias = cte_user_display
```

توضیح:

- نام runtime CTE از `QBQuery.name` resolve می‌شود؛ در این مسیر declaration نام صریح مستقلی ندارد.
- alias صریح `cte_user_display`، alias همان source occurrence را override می‌کند.
- مقدار `v3_user_display` همچنان alias پیش‌فرض occurrence است، اما در این نمونه به دلیل وجود override صریح DSL استفاده نمی‌شود.

**نکتهٔ نام‌گذاری:** helper ساخت CTE از Declaration، `QBQuery.name` و سپس convention کلاس را به‌کار می‌گیرد. Factoryای که نام runtime را صریحاً دریافت می‌کند، آن نام را بر metadata مقدم می‌کند. `QBQuery.alias` فقط alias پیش‌فرض occurrence است و `alias("...")` در بلوک `from { cte(declaration); ... }` فقط همان occurrence را override می‌کند. `with(cte)` از `Cte.name` استفاده می‌کند؛ `with(withName, cte)` نام صریح caller را برای SQL `WITH` و ارجاع‌های وابستگی مقدم می‌کند، بدون تغییر `Cte.name` یا alias occurrence.

###### ترتیب اولویت در سطح کلاس

| فیلد یا مفهوم | ترتیب Resolve |
|---|---|
| نام پیش‌فرض CTE از Declaration | convention کلاس ← `QBQuery.name` |
| نام CTE در factory دارای نام صریح | convention کلاس ← `QBQuery.name` ← `name` صریح factory |
| نام CTE در `WITH` | `Cte.name` برای `with(cte)` ← `withName` صریح برای `with(withName, cte)` |
| alias پیش‌فرض occurrence | convention کلاس ← `QBQuery.alias` |
| alias occurrence در DSL | alias صریح `from { ... }` ← alias حاصل از metadata |

نام `Cte.name` از نام انتخاب‌شده هنگام ساخت CTE پیروی می‌کند. `with(withName, cte)` نام شیء CTE را تغییر نمی‌دهد؛ فقط نام SQL همان مورد WITH و ارجاع‌های وابسته را با نام صریح caller هماهنگ می‌کند.

###### افزودن CTE به `WITH`

`IQueryWithsItemApi` دو ورودی برای CTE جدید دارد. Declaration ابتدا به یک شیء `Cte` تبدیل می‌شود؛ سپس یکی از دو overload زیر آن را به `WITH` اضافه می‌کند:

~~~kotlin
val declaration = UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters())
val cte = declaration.createCte(declaration.params)

val useCteName = QueryRenderSelectBuilder().withs {
    addWith { with(cte) }
}

val useExplicitName = QueryRenderSelectBuilder().withs {
    addWith { with("published_user_display", cte) }
}
~~~

`with(cte)` از `Cte.name` استفاده می‌کند. `with(withName, cte)` نام صریح را برای تعریف SQL `WITH` به کار می‌برد و آن را در ارجاع‌های dependency نیز هماهنگ می‌کند؛ خود `Cte.name` تغییر نمی‌کند. این نام‌گذاری از `QBQuery.alias` و alias occurrence مستقل است. نام خالی و نام مؤثر تکراری در composition پذیرفته نمی‌شوند. overloadهای legacy `with(name, ICte)` و `with(name, block)` حفظ شده‌اند.

##### ۲.۲. Metadata سطح Property

تعریف Property خروجی:

```kotlin
@QBQuerySelect(
    name = "full_name",
    alias = "fullName"
)
val fullName: String?
    get() = error("Typed output property must not be read")
```

Getter عمداً خطا ایجاد می‌کند تا مشخص شود Engine باید Metadata را از Declaration، Reflection و Annotation استخراج کند، نه از خواندن مقدار Property.

###### مرحله اول: مقادیر پیش‌فرض

با فرض اینکه CTE و source occurrence قبلاً Resolve شده باشند:

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = full_name
columnDataType = STRING
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- نام منطقی و SQL alias پیش‌فرض از Property یعنی `fullName` به `snake_case` یعنی `full_name` تبدیل می‌شوند.
- `columnDataType` از نوع تعریف‌شده Property یعنی `String?` استنباط می‌شود و به `STRING` نگاشت می‌شود.

###### مرحله دوم: اعمال `QBQuerySelect`

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = fullName
columnDataType = STRING
```

در این مرحله:

- `QBQuerySelect.name` نام منطقی خروجی Property را تعیین می‌کند.
- `QBQuerySelect.alias`، SQL alias منتشرشدهٔ خروجی را تعیین می‌کند.
- نوع داده همچنان `STRING` باقی می‌ماند.

نام منطقی خروجی و SQL alias دو فیلد مستقل هستند؛ در این مثال `full_name` و `fullName` مقادیر متفاوتی دارند.

###### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    cteOutput(UserDisplayQueryV3::fullName)
    alias("name")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = name
columnDataType = LONG
```

نتیجه:

- نام منطقی خروجی `full_name` باقی می‌ماند؛ زیرا alias صریح خروجی آن را تغییر نمی‌دهد.
- SQL alias خروجی به `name` تغییر می‌کند؛ زیرا alias صریح DSL بر `QBQuerySelect.alias` اولویت دارد.
- `columnDataType` به `LONG` تغییر می‌کند؛ زیرا `execute(DataType.LONG)` نوع خروجی را صریحاً تعیین می‌کند.

این تغییر نوع فقط به خروجی Query مربوط است و نباید نوع تعریف‌شده Property یا Metadata اصلی Query را تغییر دهد.

**توجه:** این مثال صرفاً برای توضیح اولویت‌بندی Metadata است. با توجه به اینکه Property از نوع `String?` است، تعیین `DataType.LONG` لزوماً با نوع واقعی مقدار خروجی سازگار نیست. بررسی سازگاری نوع داده موضوعی مستقل از اولویت‌بندی Metadata است.

Engine همچنین نباید Getter مربوط به `fullName` را برای Resolve کردن Metadata فراخوانی کند.

###### ترتیب اولویت در سطح Property

| فیلد | ترتیب Resolve |
|---|---|
| نام منطقی خروجی | نام پیش‌فرض Property ← `QBQuerySelect.name` |
| SQL alias خروجی | alias پیش‌فرض Property ← `QBQuerySelect.alias` ← alias صریح DSL |
| نوع داده خروجی | نوع استنباط‌شده از Property ← نوع صریح DSL از طریق `execute(DataType)` |

تغییر SQL alias نباید نام منطقی خروجی را تغییر دهد. به همین ترتیب، تغییر نوع خروجی نباید نوع تعریف‌شده Property را بازنویسی کند.

---

#### ۳. تفاوت مقدار تعریف‌نشده و رشته خالی

مقدار تعریف‌نشده و رشته خالی الزاماً معنای یکسانی ندارند.

برای فیلدهای جدید Annotationهای Query، در صورت نیاز به تشخیص این دو حالت، می‌توان از یک مقدار قراردادی مانند `QB_METADATA_UNSET` استفاده کرد.

- **تعریف‌نشده:** مقداری برای فیلد ارائه نشده است؛ بنابراین مقدار پیش‌فرض یا fallback متناظر قابل استفاده است.
- **رشته خالی صریح:** مقدار فیلد عمداً خالی تعیین شده است؛ اگر آن فیلد الزاماً باید مقدار غیرخالی داشته باشد، باید اعتبارسنجی آن را رد کند.

رفتار فعلی Annotationهای Model باید حفظ شود. به‌ویژه، مقادیر پیش‌فرض رشته خالی در `QBTable` و `QBColumn` نباید صرفاً برای یکسان‌سازی با Query تغییر کنند.

استفاده از `QB_METADATA_UNSET` نباید بدون بررسی سازگاری به فیلدهای موجود Model تعمیم داده شود.

---

#### ۴. قواعد عمومی Resolve کردن Metadata

قواعد زیر برای فرآیند Resolve کردن Metadata اعمال می‌شوند:

1. مقادیر پیش‌فرض پیش از Annotationها تعیین شوند.
2. هر Annotation فقط فیلدهای متناظر خود را تغییر دهد.
3. تنظیمات صریح DSL در مرحله آخر اعمال شوند و فقط فیلدهایی را تغییر دهند که آن عملیات برای override کردنشان تعریف شده است.
4. نام منطقی، نام منبع، نام CTE، SQL alias و نوع داده مستقل باقی بمانند.
5. یکسان بودن مقدار دو فیلد به معنای یکسان بودن مفهوم آن‌ها نیست.
6. Getter مربوط به Propertyهای خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
7. `relationName` بخشی از قرارداد Query نیست؛ نام runtime پیش‌فرض CTE از `QBQuery.name` و سپس convention مجاز resolve می‌شود، درحالی‌که نام صریح factory بر آن مقدم است.
8. رفتار موجود Model در مواردی که با قواعد جدید Query متفاوت است، حفظ شود.
9. override نوع خروجی از نوع تعریف‌شده Property مستقل باقی بماند.
10. اعتبارسنجی Metadata پس از تکمیل Declaration مربوط به SELECT و پیش از ساخت `BuiltCteComposition` انجام شود.

##### الزامات اعتبارسنجی

- اعتبارسنجی یکتایی SQL aliasها حفظ شود.
- یکتایی نام‌های منطقی خروجی در میان خروجی‌های Typed متعلق به قرارداد فعلی Query بررسی شود.
- تعلق یک خروجی به Query بر اساس رابطه موجود `declarationOwner` تعیین شود، نه صرفاً بر اساس کلاسی که Property در آن تعریف شده است.
- Property معتبر به‌ارث‌رسیده نباید صرفاً به دلیل تعریف‌شدن در کلاس والد حذف شود.
- Propertyهای منبع Model نباید به‌عنوان خروجی‌های Query شناسایی شوند.
- برای اعتبارسنجی، مقدار Property نباید خوانده شود.

---

#### ۵. سازگاری و محدوده تغییرات

این سند قرارداد Resolve کردن و اولویت‌بندی Metadata را مشخص می‌کند. محدودیت‌های این بخش فقط دامنهٔ تغییرات Metadata را مشخص می‌کنند؛ مسیر AST-based پارامتر و اجرای positional که جداگانه در بالای سند آمده، بخشی مستقل از این قرارداد است.

مگر اینکه یک مانع فنی مشخص، تغییر دیگری را ضروری کند:

- برای تغییرات محدود به Metadata، AST، Executor، `QueryRow`، Materializer و امضاهای API عمومیِ نامرتبط تغییر نکنند.
- در تغییرات محدود به Metadata، قرارداد `Cte` و semantics composition آن تغییر نکند. مهاجرت مستقل و تأییدشدهٔ V3.0 عمداً `ICteDeclaration.params`، constructor پارامترپذیر `CteDeclaration`، `execute(queryManager)` و overload `cte(declaration)` را به ورودی صریح منتقل می‌کند؛ overloadهای `with(Cte)` و `with(withName, Cte)` و معنای نام CTE/alias occurrence حفظ می‌شوند.
- صرفاً برای یکسان‌سازی ظاهری Metadataهای Model و Query، abstraction عمومی جدیدی اضافه نشود.
- رفتار فعلی رشته خالی در Annotationهای Model تغییر نکند.
- وجود `QBQuery.name` نباید نام صریحی را که caller به factory ساخت CTE داده است بازنویسی کند.
- Resolve کردن Metadata از اجرای Query و استخراج مقدار Property مستقل باقی بماند.

هر تغییر در این محدوده‌ها باید جداگانه توجیه و تأیید شود، نه اینکه به‌صورت ضمنی وارد بازنویسی Metadata شود.

---

#### ۶. معیارهای پذیرش

پیاده‌سازی زمانی با این قرارداد سازگار است که:

1. مثال کلاس Model به `tableName = user_users` و `tableAlias = uu1` برسد.
2. مثال Property در Model به `columnName = user_id`، `columnAlias = user_id_out` و `columnDataType = LONG` برسد.
3. نام منطقی و alias تعریف‌شده در Annotation مربوط به Query حفظ شوند.
4. مثال Query به `cteName = user_display_query_v3` و `occurrenceAlias = cte_user_display` برسد: نام runtime از `QBQuery.name` و alias از override همان occurrence می‌آید.
5. مثال خروجی Query به نام منطقی `full_name`، SQL alias برابر `name` و نوع خروجی `LONG` برسد.
6. هر override فقط فیلد متناظر خود را تغییر دهد.
7. Getter مربوط به Property خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
8. نام منطقی Query، نام runtime CTE و alias occurrence مستقل باقی بمانند؛ نام `withName` صریح فقط در SQL `WITH` override شود.
9. رفتار موجود Model و APIهای عمومی Query و CTE حفظ شوند.
10. اعتبارسنجی Metadata پیش از ساخت `BuiltCteComposition` انجام شود.
