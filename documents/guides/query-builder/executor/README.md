# راهنمای اجرای Query با Executor

این راهنما همهٔ قابلیت‌های مصرف‌کنندهٔ Query Executor را در یک صفحه جمع می‌کند: انتخاب Executor، اجرای SELECT و DML، استفاده از Connection و Transaction، بررسی نتیجه، مشاهدهٔ SQL و اتصال به مثال‌های واقعی ماژول Query.

ساخت خود Query و AST خارج از محدودهٔ این صفحه است؛ برای آن به [راهنمای Query Builder](../README.md)، [ساخت Model](../model/README.md)، [ساخت CTE](../cte/README.md) و [قواعد Metadata](../rules/README.md) مراجعه کنید.

## فهرست مطالب

- [۱. انتخاب و ساخت Executor](#executor-choice)
  - [Database Handle مدیریت‌شده](#executor-handle)
  - [QueryBuilderExecutor مستقیم](#executor-direct)
- [۲. قابلیت‌های عمومی Executor](#executor-api)
  - [اتصال Query به Consumer](#executor-consumers)
  - [Render کردن SQL](#executor-render)
- [۳. اجرای SELECT و خواندن نتیجه](#executor-select)
  - [first، execute و executeAs](#executor-select-modes)
  - [QueryRow و الزامات خروجی](#executor-query-row)
- [۴. اجرای INSERT، UPDATE و DELETE](#executor-mutations)
- [۵. Connection و Transaction](#executor-scopes)
- [۶. مدیریت ExecuteResult](#executor-result)
- [۷. SQL و Metadata برای بررسی](#executor-inspection)
- [۸. Eventها و APIهای سطح پایین](#executor-advanced)
- [۹. مثال‌های ماژول Query](#executor-examples)
  - [انتخاب نسخه و تنظیم اجرا](#executor-example-runner)
  - [نمونه‌ها بر اساس نسخه](#executor-example-versions)

<a id="executor-choice"></a>
## ۱. انتخاب و ساخت Executor

Executor Query ساخته‌شده را برای dialect دیتابیس render می‌کند و از مسیر Operation اجرا می‌کند. اگر از Database Manager استفاده می‌کنید، Handle همان Registration را به کار ببرید. اگر `DatabaseContext` را مستقیماً در اختیار دارید، `QueryBuilderExecutor` راه ورود مستقیم است.

<a id="executor-handle"></a>
### Database Handle مدیریت‌شده

برای اجرای یک یا چند Query از Handle استفاده کنید:

```kotlin
val result: ExecuteResult<List<QueryRow>> = databaseHandle.connection { query ->
    query.queryBuilder(selectQuery).execute()
}
```

برای دیتابیس‌های ثبت‌شده در Manager، این روش lifecycle و Operation Scope را به Manager/Operation می‌سپارد. Executorای که callback می‌گیرد به همان Scope متصل است؛ فقط در همان callback از آن استفاده کنید. برای Queryهای داخل Scope از root Executor دیگری استفاده نکنید.

<a id="executor-direct"></a>
### QueryBuilderExecutor مستقیم

اگر برنامه خودش `DatabaseContext` می‌سازد یا دریافت می‌کند:

```kotlin
val queryExecutor = QueryBuilderExecutor(databaseContext)
val result = queryExecutor.queryBuilder(selectQuery).execute()
```

برای وابستگی مستقیم، ماژول `:data_base:operation:query:executor` را اضافه کنید:

```kotlin
dependencies {
    implementation(project(":data_base:operation:query:executor"))
}
```

سازندهٔ بدون آرگومان برای تنظیمات legacy باقی مانده است؛ برای تنظیم صریح دیتابیس از `DatabaseContext` یا Database Manager استفاده کنید. Executor مستقیم نیز `connection {}` و `transaction {}` دارد. اگر یک پیاده‌سازی سفارشی `IQueryBuilderExecutor` این scopeها را پشتیبانی نکند، قرارداد پیش‌فرض با `UnsupportedOperationException` داخل `ExecuteResult.Failure` برمی‌گردد و callback اجرا نمی‌شود.

<a id="executor-api"></a>
## ۲. قابلیت‌های عمومی Executor

این جدول سطح مصرف‌کنندهٔ `IQueryBuilderExecutor` را خلاصه می‌کند:

| نیاز | API | نتیجه یا رفتار |
|---|---|---|
| دسترسی به پیکربندی dialect | `databasePlatformConfig` | پیکربندی platform مورد استفاده برای rendering. |
| دسترسی به Context | `databaseContext` | در Executor دارای Context موجود است؛ Executor ساخته‌شده فقط از Scope ممکن است Context در دسترس نداشته باشد. |
| اتصال Query از قبل ساخته‌شده | `queryBuilder(query)` | بر اساس نوع Query، Consumer متناظر می‌سازد. |
| ساخت و اتصال SELECT | `queryBuilderSelect { ... }` | یک `QueryConsumer` می‌سازد. |
| انتخاب نوع Query در یک callback | `queryBuilder { selection -> ... }` | از `querySelect`, `queryInsert`, `queryUpdate`, `queryDelete` و نام‌های کوتاه `select`, `insert`, `update`, `delete` استفاده می‌کند. |
| اجرای چند Query در Scope | `connection { ... }`, `transaction { ... }` | یک Connection مشترک یا Transaction را از مسیر Operation اجرا می‌کند. |
| SQL سادهٔ Renderشده | `sql(query)` | برای SELECT، INSERT، UPDATE و DELETE یک `String` برمی‌گرداند؛ Query اجرا نمی‌شود. |
| اجرای پایین‌سطح | `first`, `get`, `execute`, `executeOut` | callbackمحور؛ محدودیت ResultSet و managed Scope را در [بخش APIهای پیشرفته](#executor-advanced) بخوانید. |

<a id="executor-consumers"></a>
### اتصال Query به Consumer

`queryBuilder(query)` برای هر نوع AST، Consumer نوع‌دار برمی‌گرداند:

| Query | Consumer | خروجی `execute()` |
|---|---|---|
| SELECT | `QueryConsumer` | `ExecuteResult<List<QueryRow>>` |
| INSERT | `InsertQueryConsumer` | `ExecuteResult<Long>` با کلید تولیدشده |
| UPDATE | `UpdateQueryConsumer` | `ExecuteResult<Int>` با تعداد ردیف‌های تغییرکرده |
| DELETE | `DeleteQueryConsumer` | `ExecuteResult<Int>` با تعداد ردیف‌های حذف‌شده |

```kotlin
val rows = query.queryBuilder(selectQuery).execute()
val insertedId = query.queryBuilder(insertQuery).execute()
val updatedRows = query.queryBuilder(updateQuery).execute()
val deletedRows = query.queryBuilder(deleteQuery).execute()
```

`queryBuilderSelect { ... }` برای ساخت SELECT در محل است. API یکپارچهٔ `queryBuilder { ... }` یک `QueryBuilderSelection` می‌دهد تا Consumer مناسب را در همان بلوک بسازید. این APIها Query را به Executor متصل می‌کنند؛ AST و قواعد DSL را در [راهنمای اصلی Query Builder](../README.md) ببینید.

<a id="executor-render"></a>
### Render کردن SQL

`executor.sql(query)` SQL dialect-specific را به شکل `String` برمی‌گرداند و Query را اجرا نمی‌کند. برای دیدن SQL به همراه bindingها از `sql()` روی Consumer استفاده کنید؛ این موضوع در [SQL و Metadata](#executor-inspection) آمده است.

<a id="executor-select"></a>
## ۳. اجرای SELECT و خواندن نتیجه

`QueryConsumer` ردیف‌های SELECT را در همان مرز اجرا materialize می‌کند؛ در نتیجه `QueryRow`های برگشتی به ResultSet یا Connection وابسته نیستند.

<a id="executor-select-modes"></a>
### first، execute و executeAs

| متد | خروجی | رفتار نتیجهٔ خالی |
|---|---|---|
| `first()` | `ExecuteResult<QueryRow>` | `Success` با مقدار `null` |
| `execute()` | `ExecuteResult<List<QueryRow>>` | فهرست خالی |
| `executeAs { row -> ... }` | `ExecuteResult<ICollection<T>>` | Collection خالی |

```kotlin
val rows: ExecuteResult<List<QueryRow>> = query.queryBuilder(selectQuery).execute()

when (rows) {
    is ExecuteResult.Success -> rows.result.orEmpty().forEach { row ->
        val userId = row.getValue("user_id") as Int
        val displayName = row.getValue("display_name") as String?
        println("$userId: $displayName")
    }
    is ExecuteResult.Failure -> logger.error("SELECT failed", rows.exception)
}
```

برای تبدیل ردیف‌ها به نوع دامنه‌ای خودتان:

```kotlin
data class UserView(val id: Int, val displayName: String?)

val users: ExecuteResult<ICollection<UserView>> = query
    .queryBuilder(selectQuery)
    .executeAs { row ->
        UserView(
            id = row.getValue("user_id") as Int,
            displayName = row.getValue("display_name") as String?,
        )
    }
```

`executeAs` همان SELECT را یک بار اجرا می‌کند و ردیف‌های materializeشده را map می‌کند؛ برای تبدیل مدل، Query دوم اجرا نمی‌شود. اگر mapper یک `Exception` پرتاب کند، Failure شامل خطای mapping و شمارهٔ ردیف برمی‌گردد. `Error` به‌عنوان Failure معمولی پنهان نمی‌شود.

<a id="executor-query-row"></a>
### QueryRow و الزامات خروجی

`QueryRow` با SQL output alias مقداردهی می‌شود:

- هر خروجی SELECT باید alias غیرخالی و یکتا داشته باشد.
- هر خروجی باید `DataType` اجرایی تعریف کند تا مقدار JDBC materialize شود.
- `row.getValue("alias")` با alias ناموجود خطا می‌دهد.
- دسترسی با `PropertyReference` برای property انتخاب‌شده موجود است؛ اگر property چند بار خروجی داده شده، `outputKeyHint` لازم است.

<a id="executor-mutations"></a>
## ۴. اجرای INSERT، UPDATE و DELETE

Consumerهای DML با `execute()` عملیات را اجرا می‌کنند:

```kotlin
val insertResult: ExecuteResult<Long> = query.queryBuilder(insertQuery).execute()
val updateResult: ExecuteResult<Int> = query.queryBuilder(updateQuery).execute()
val deleteResult: ExecuteResult<Int> = query.queryBuilder(deleteQuery).execute()
```

- INSERT کلید تولیدشده را به صورت `Long` برمی‌گرداند؛ اگر کلید تولیدشده از JDBC دریافت نشود، نتیجه Failure است.
- UPDATE و DELETE تعداد ردیف‌های متأثر را به شکل `Int` برمی‌گردانند.
- هر سه Consumer متد `sql()` دارند که SQL و bindingها را برای inspection می‌دهد.

این APIها اجرا می‌کنند؛ ساخت Query با DSL انجام می‌شود. تغییرات را روی دیتابیس مناسب اجرا کنید و برای چند تغییر وابسته، [Transaction](#executor-scopes) را به کار ببرید.

<a id="executor-scopes"></a>
## ۵. Connection و Transaction

Queryهای داخل `connection {}` و `transaction {}` باید از Executorی اجرا شوند که همان callback دریافت می‌کند.

### Connection scope

```kotlin
val result = databaseHandle.connection { query ->
    val selected = query.queryBuilder(selectQuery).execute()
    if (selected is ExecuteResult.Failure) return@connection selected

    query.queryBuilder(updateQuery).execute()
}
```

یک Connection در callback به اشتراک گذاشته و پس از آن توسط Operation آزاد می‌شود. این scope به‌خودی‌خود Transaction نیست و مقدار `autoCommit` را برای ساخت Transaction تغییر نمی‌دهد.

### Transaction scope

```kotlin
val result = databaseHandle.transaction { tx ->
    val inserted = tx.queryBuilder(insertQuery).execute()
    if (inserted is ExecuteResult.Failure) return@transaction inserted

    val updated = tx.queryBuilder(updateQuery).execute()
    if (updated is ExecuteResult.Failure) return@transaction updated

    ExecuteResult.Success("saved")
}
```

Transaction از یک Connection استفاده می‌کند. در پایان موفق، Operation commit می‌کند و در Failure طبق قرارداد Operation rollback انجام می‌شود. Failure هر Query را بررسی و به callback برگردانید؛ Query بعدی پس از Failure نمی‌تواند در همان Scope ادامه پیدا کند. نادیده‌گرفتن Failure باعث commit موفق Transaction نمی‌شود.

همین دو API برای `QueryBuilderExecutor` مستقیم نیز وجود دارند. Scopeهای تودرتو پشتیبانی نمی‌شوند. Executor scoped را بعد از callback نگه ندارید و درون Scope به جای آن از root Executor استفاده نکنید. قرارداد lifecycle کامل در [راهنمای Operation](../../operation/connection_transaction/README.md) و [راهنمای Database Manager](../../database-manager/executor/README.md) است.

<a id="executor-result"></a>
## ۶. مدیریت ExecuteResult

نتیجهٔ اجرای Query از نوع `ExecuteResult<T>` است:

- `Success<T>` مقدار nullable را در `result` نگه می‌دارد.
- `Failure` خطای اصلی را در `exception` نگه می‌دارد.

```kotlin
when (val result = query.queryBuilder(selectQuery).execute()) {
    is ExecuteResult.Success -> result.result.orEmpty().forEach(::println)
    is ExecuteResult.Failure -> logger.error("Query failed", result.exception)
}
```

`toValue()` مقدار غیر-null Success را می‌دهد، همان Throwable مربوط به Failure را پرتاب می‌کند و برای Success بدون مقدار `IllegalStateException` می‌دهد. برای typed SELECT، extensionهای package `gog.my_project.data_base_query.executor.result` عبارت‌اند از `toCollection()` و `toList()`. این تبدیل‌ها Query را دوباره اجرا نمی‌کنند و Failure را به Collection خالی تبدیل نمی‌کنند.

Extensionهای fluent:

- `.success { value -> ... }` فقط transform را برای Success غیر-null اجرا می‌کند؛ Failure و Success-null را بدون تبدیل حفظ می‌کند.
- `.then { value -> ExecuteResult... }` نتیجه‌ها را flatten می‌کند؛ Success-null را به transform می‌دهد و Failure را نگه می‌دارد.
- `.error { throwable -> ... }` خطا را مشاهده می‌کند و همان Failure را برمی‌گرداند؛ آن را حذف یا recover نمی‌کند.

در Transaction، حتی هنگام استفاده از extensionهای fluent، Failure را به callback برگردانید تا Operation نتیجهٔ درست را برای lifecycle ببیند.

<a id="executor-inspection"></a>
## ۷. SQL و Metadata برای بررسی

پیش از اجرا می‌توانید Query را برای بازبینی render کنید:

```kotlin
val select = query.queryBuilder(selectQuery)
val rendered: BuiltQuery = select.sql()
val details: EffectiveQueryMetadata = select.details()

println(rendered.query)
println(rendered.params)
println(details)
```

- `QueryConsumer.sql()` یک `BuiltQuery` شامل SQL نمایشی و پارامترهای binding می‌دهد.
- Consumerهای INSERT، UPDATE و DELETE نیز `sql(): BuiltQuery` دارند.
- `executor.sql(query)` برای هر چهار نوع Query، SQL را به صورت `String` می‌دهد.
- `QueryConsumer.details()`، `EffectiveQueryMetadata` را از provider Query یا analyzer AST می‌گیرد و فقط برای SELECT است.
- این متدها Query را اجرا نمی‌کنند؛ اجرای واقعی با `execute()` یا API اجرا انجام می‌شود.

اگر SELECT سفارشی نه metadata provider سازگار داشته باشد و نه AST آن توسط analyzer قابل تحلیل باشد، `details()` ممکن است خطا بدهد؛ این محدودیت روی `sql()` یا `execute()` اعمال نمی‌شود.

### نمونهٔ SQL و پارامترها

در Exampleهای V3 از `.sql { ... }` و `.details { ... }` برای بررسی Query استفاده شده است: [V3 SELECT 7](../../../../data_base/operation/query/example/src/main/kotlin/v3/queries/select/A7ExampleSelectV3.kt). نمونهٔ DML در [V4 UPDATE](../../../../data_base/operation/query/example/src/main/kotlin/v4/queries/update/A1ExampleUpdateV4.kt) نیز SQL و پارامترها را پیش از اجرا نمایش می‌دهد.

پارامترها در DSL تعریف می‌شوند و Executor bindingهای renderشده را به مسیر JDBC می‌دهد. SQL نمایشی با SQL اجرایی یکی نیست؛ در مسیر پارامترمحور، SQL اجرایی از placeholderهای JDBC استفاده می‌کند. برای قواعد ساخت و تعریف پارامتر به [راهنمای Query Builder](../README.md) و [نمونه‌های V3](../examples/v3.0/query-contract.md) مراجعه کنید.

<a id="executor-advanced"></a>
## ۸. Eventها و APIهای سطح پایین

### مشاهدهٔ رویدادهای Operation

در ساخت مستقیم Executor می‌توانید یک `OperationEventListener` بدهید:

```kotlin
val executor = QueryBuilderExecutor(databaseContext) { event ->
    println("${event.type}: ${event.sql}")
}
```

Listener رویدادهای شروع/موفقیت/شکست SQL و lifecycle مربوط به Connection و Transaction را مشاهده می‌کند. Event جایگزین `ExecuteResult` نیست و SQL event مقدارهای bindشده را منتشر نمی‌کند. قرارداد کامل در [راهنمای Operation Events](../../operation/events/README.md) آمده است. ورودی ماژول Example از Listener استفاده نمی‌کند؛ ساخت Executor در [Main.kt](../../../../data_base/operation/query/example/src/main/kotlin/Main.kt) دیده می‌شود.

### Callbackهای سطح پایین

`IQueryBuilderExecutor` APIهای callbackمحور نیز دارد:

- `first(query, callback)` ردیف نخست را به صورت `QueryRow` می‌دهد.
- `get(query, callback)` همهٔ ردیف‌ها را به صورت `List<QueryRow>` می‌دهد.
- overloadهای `execute(query, callback)` نتیجهٔ نوع‌دار عملیات را تحویل می‌دهند؛ SELECT در overload پایین‌سطح `ResultSet` ارائه می‌کند.
- `executeOut(...)` مسیر legacy برای callback با `IQueryExecute` و `BuiltQuery` است.
- در APIهای callbackمحور می‌توان `blockQueryInfo` داد تا SQL و map پارامترها هنگام اجرا گزارش شوند؛ برای بازبینی پیش از اجرا از `consumer.sql()` استفاده کنید.

در صورت دریافت `ResultSet` آن را داخل همان callback مصرف کنید و از callback یا Scope خارج نگه ندارید. برای `QueryBuilderExecutor`، `executeOut` هنگام managed execution scope در دسترس نیست؛ از Consumer scoped استفاده کنید. در کد معمول برنامه، `QueryConsumer` امن‌تر و ساده‌تر است چون خروجی SELECT را پیش از پایان اجرا materialize می‌کند.

<a id="executor-examples"></a>
## ۹. مثال‌های ماژول Query

ماژول `data_base/operation/query/example` یک برنامهٔ اجرایی و مجموعه‌ای از نمونه‌های Query دارد. این نمونه‌ها از همان `IQueryBuilderExecutor` استفاده می‌کنند و روش‌های مختلف ساخت Query را نشان می‌دهند.

<a id="executor-example-runner"></a>
### انتخاب نسخه و تنظیم اجرا

در [Main.kt](../../../../data_base/operation/query/example/src/main/kotlin/Main.kt)، Example یک `DatabaseContext` و `QueryBuilderExecutor` می‌سازد و سپس `ExampleVersionRunner` را اجرا می‌کند. نسخه می‌تواند `v1`, `v2`, `v3`, `v3.1` یا `v4` باشد. گزینه‌های `query.example.run.select`, `query.example.run.insert`, `query.example.run.update`, `query.example.run.delete` و `query.example.run.transaction` اجرای گروه‌ها را کنترل می‌کنند. SELECT پیش‌فرض روشن و گروه‌های تغییردهنده پیش‌فرض خاموش‌اند.

Runner در [ExampleVersionRunner.kt](../../../../data_base/operation/query/example/src/main/kotlin/runners/ExampleVersionRunner.kt)، V1 تا V3.1 را در Connection scope اجرا می‌کند. V4 Transactionهای خود را داخل مثال‌ها مدیریت می‌کند و در Connection scope دیگری پیچیده نمی‌شود.

<a id="executor-example-versions"></a>
### نمونه‌ها بر اساس نسخه

| نسخه | چه چیزی نشان می‌دهد؟ | راهنما و نمونهٔ سورس |
|---|---|---|
| V1 | نام خام جدول/ستون، alias خروجی و Queryهای SELECT/INSERT/UPDATE/DELETE | [راهنمای V1](../examples/v1.0/README.md)، [سورس SELECT 9](../../../../data_base/operation/query/example/src/main/kotlin/v1/queries/select/A9ExampleSelectV1.kt) |
| V2 | استفاده از Model و property برای Queryها | [راهنمای V2](../examples/v2.0/README.md)، [سورس SELECT](../../../../data_base/operation/query/example/src/main/kotlin/v2/queries/select/A1ExampleSelectV2.kt) |
| V3 | Query declaration، CTE، SQL alias و Metadata مؤثر | [راهنمای V3](../examples/v3.0/README.md)، [سورس SELECT 7](../../../../data_base/operation/query/example/src/main/kotlin/v3/queries/select/A7ExampleSelectV3.kt) |
| V3.1 | اجرای مستقیم typed CTE و خروجی `ICollection<T>` | [راهنمای V3.1](../examples/v3.1/typed-cte-execution.md)، [سورس اجرای CTE](../../../../data_base/operation/query/example/src/main/kotlin/v3_1/queries/execute/CteExecutionExampleV31.kt) |
| V4 | Queryهای CTE-backed، Transactionهای وابسته و بررسی خطا | [سورس SELECT تراکنشی](../../../../data_base/operation/query/example/src/main/kotlin/v4/queries/select/A3ExampleSelectV4.kt)، [سورس UPDATE](../../../../data_base/operation/query/example/src/main/kotlin/v4/queries/update/A1ExampleUpdateV4.kt) |

برای فهرست همهٔ راهنماهای مثال، [فهرست نسخه‌های Query Example](../examples/README.md) را ببینید. مثال‌های V4 فعلاً در سورس ماژول ارائه شده‌اند و در فهرست مستندات versioned صفحهٔ جداگانه ندارند.
