# پلن Transaction نسخهٔ 1.0

وضعیت: Phase 1 در `manager:execute` و پیاده‌سازی/lifecycle مربوط به Phase 2 در `query:executor` انجام شده‌اند. در Phase 3 یک مسیر واقعی دورزدن Connection شناسایی و مسدود و تست‌های proxy و ماژول‌های مرتبط اجرا شدند؛ integration واقعی Transaction و آزمون قفل `FOR UPDATE` به‌دلیل نبود پیکربندی دیتابیس ایزوله تأیید نشده‌اند. سه تست نامرتبط `query:builder` نیز در اجرای جامع فعلی شکست دارند و باید جداگانه پیگیری شوند. کارهای lock و compatibility نسخه همچنان بازند.
مبنای بررسی: سورس فعلی مخزن در زمان تهیهٔ این سند. این سند طرح Transaction V1.0 و وضعیت فازهای ثبت‌شده را نگه می‌دارد؛ تکمیل کل Transaction فقط با معیارهای Phaseهای بعد سنجیده می‌شود و نتیجهٔ build/test فقط برای اجراهایی معتبر است که صریحاً در همین سند گزارش شده‌اند.

## 1. هدف و محدودهٔ نسخهٔ 1.0

هدف، فراهم‌کردن یک محدودهٔ تراکنشی برای اجرای چند Query متوالی روی **همان Connection فیزیکی JDBC** است؛ به‌گونه‌ای که کد مصرف‌کننده با DSL فعلی ساخته شود و اجرای بیرون از تراکنش همچنان رفتار فعلی خود را حفظ کند.

API عمومی روی `IQueryBuilderExecutor` پیاده‌سازی شده است: `fun <T> transaction(block: (tx: IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T>`. Callback یک executor محدود به عمر scope می‌گیرد و باید `ExecuteResult<T>` برگرداند تا نتیجهٔ عملیاتی مستقیماً نتیجهٔ تراکنش باشد و `ExecuteResult<ExecuteResult<T>>` ساخته نشود.

نسخهٔ 1.0 شامل اجرای عملیات‌های فعلی SELECT، INSERT، UPDATE و DELETE است، به شرط آنکه builder و dialect فعلی آن operation را پشتیبانی کنند. Transaction به‌تنهایی صحت منطق تجاری، کافی‌بودن موجودی، جلوگیری از مقدار منفی یا idempotency را تضمین نمی‌کند.

نمونهٔ مفهومی مقصد:

```kotlin
val result: ExecuteResult<Long> = queryManager.transaction { tx ->
    when (val order = tx.queryBuilder(insertOrderQuery).execute()) {
        is ExecuteResult.Failure -> order
        is ExecuteResult.Success -> when (
            val wallet = tx.queryBuilder(updateWalletQuery).execute()
        ) {
            is ExecuteResult.Failure -> wallet
            is ExecuteResult.Success -> tx.queryBuilder(insertWalletTransactionQuery).execute()
        }
    }
}
```

این نمونه فقط وقتی قابل اجراست که هر سه query و خروجی‌هایشان با API واقعی builder سازگار باشند. API فعلی UPDATE مقدار پارامترشده را با `setValue` می‌پذیرد؛ عبارت‌هایی مانند `balance = balance - amount` یا `decrement()` در contract بررسی‌شده پیدا نشدند. بنابراین مثال Laravel برای کاهش اتمیک موجودی، در این نسخه صرفاً قیاس مفهومی است و نباید به‌عنوان قابلیت موجود یا مثال اجرایی کامل ارائه شود. افزودن update expression/decrement، اگر لازم باشد، تصمیم و دامنهٔ جداگانه می‌خواهد.

## 2. وضعیت فعلی معماری و یافته‌های سورس

### 2.1 مسیر ساخت و اجرای Query

- `data_base/query/executor/src/main/kotlin/interfaces/IQueryBuilderExecutor.kt` قرارداد `IQueryBuilderExecutor` را تعریف می‌کند. overloadهای `queryBuilder(...)` به‌ترتیب `QueryConsumer`، `InsertQueryConsumer`، `UpdateQueryConsumer` و `DeleteQueryConsumer` می‌سازند. این consumerها executor را دریافت می‌کنند؛ بنابراین callback تراکنش می‌تواند همان APIهای fluent را با یک executor محدود به تراکنش به‌کار ببرد.
- همین interface برای SELECT متدهای `first()` و `get()` را با materialization هم‌زمان `ResultSet` پیاده می‌کند و overloadهای callbackمحور `execute(...)` را برای SELECT/INSERT/UPDATE/DELETE تعریف می‌کند.
- `data_base/query/executor/src/main/kotlin/manager/QueryBuilderExecutor.kt` renderer را فراخوانی می‌کند و در حال حاضر برای اجرای هر operation یک `QueryExecute()` جدید می‌سازد. شاخهٔ identity-aware یک `BoundQuery` و شاخهٔ legacy یک `BuiltQuery` می‌سازند.
- `data_base/query/executor/src/main/kotlin/result/QueryConsumer.kt` اجرای SELECT را با `executor.get()` یا `executor.first()` انجام می‌دهد. `execute()` خروجی خام materialize‌شدهٔ `ExecuteResult<List<QueryRow>>` و `executeAs()` خروجی typed از نوع `ExecuteResult<ICollection<T>>` می‌دهد. `sql()` و `details()` introspection هستند و اجرای JDBC نمی‌کنند.
- `data_base/query/executor/src/main/kotlin/result/MutationQueryConsumers.kt` اجرای mutation را به overload متناظر `IQueryBuilderExecutor.execute(...)` می‌سپارد؛ consumerهای mutation نیز `sql()` دارند.

### 2.2 مالکیت فعلی Connection و منابع

- `data_base/manager/connection/src/main/kotlin/manager/DatabaseConnection.kt` در هر `build()` از `DriverManager.getConnection(...)` استفاده می‌کند. `data_base/manager/connection/src/main/kotlin/interfaces/IDatabaseConnection.kt` فقط `build(): Connection` دارد.
- `data_base/manager/execute/src/main/kotlin/manager/QueryExecute.kt` در مسیرهای `executeSelect(BoundQuery, ...)` و mutationهای identity-aware، Connection را خودش با `DatabaseConnection().build()` می‌گیرد و با `connection.use` می‌بندد؛ statement نیز در `statement.use` بسته می‌شود. مسیرهای `BuiltQuery` از `IQueryExecute.execute(...)` عبور می‌کنند که به همین شکل Connection مستقل می‌گیرد و در پایان می‌بندد.
- برای SELECT، `QueryExecute` نتیجه را داخل callback به consumer می‌دهد؛ `IQueryBuilderExecutor.get()/first()` باید `ResultSet` را در همان callback پیمایش و ببندد، درحالی‌که statement و Connection بیرونی هنوز باز هستند. اجرای transaction نمی‌تواند این `use` داخلی Connection را حفظ کند؛ مالکیت Connection باید به سطح تراکنش منتقل شود و مسیرهای query فقط statement و ResultSet خود را ببندند.
- `DatabaseConfig.dbPoolSize` و `DatabaseConfigBuilder.poolSize(...)` در `data_base/core/src/main/kotlin/data_base/DatabaseConfig.kt` و `data_base/core/src/main/kotlin/data_base/DatabaseConfigBuilder.kt` وجود دارند، اما در مسیر اتصال بررسی‌شده pool یا `DataSource` دیده نشد؛ `DatabaseConnection` مستقیماً `DriverManager` را صدا می‌زند. در نتیجه نباید رفتار pool یا بازگرداندن Connection به pool را به وضعیت فعلی نسبت داد. اگر در آینده provider/pool اضافه شد، reset وضعیت Connection هنگام بازگرداندن lease باید جداگانه تعریف شود.

### 2.3 Result و خطا

- `data_base/manager/execute/src/main/kotlin/tools/ExecuteResult.kt` یک `sealed class ExecuteResult<out T>` دارد: `Success<out T>(result: T?)` که payload آن nullable است، و `Failure(exception: Throwable)`.
- `data_base/query/executor/src/main/kotlin/result/ExecuteResultFluentExtensions.kt` extensionهای `success` و `error` را تعریف می‌کند. `success` فقط payload غیرnull را تبدیل می‌کند، Success با payload null را همان‌طور نگه می‌دارد؛ `error` فقط failure را مشاهده می‌کند و همان result را برمی‌گرداند، پس به‌تنهایی recovery یا rollback ایجاد نمی‌کند.
- `QueryConsumer.execute()/executeAs()` و `MutationQueryConsumers` اجرای هم‌زمان را در `ExecuteResult` می‌بندند؛ اما خطا ممکن است به‌صورت `Failure` برگردد، نه اینکه از callback تراکنش throw شود. Transaction باید هر دو شکل شکست را بشناسد.
- `data_base/manager/execute/src/test/kotlin/manager/QueryExecuteContractTest.kt` قراردادهای فعلی binding، callback یک‌باره، خطای JDBC، callback exception و بستن statement/connection را با driver و proxy ثبت‌کننده می‌آزماید. `data_base/query/executor/src/test/kotlin/manager/IdentityAwareSelectExecutionTest.kt` نمونه‌های ثبت تعداد callback و بسته‌شدن منابع ResultSet/statement/connection را دارد.

### 2.4 `FOR UPDATE`

در working tree فعلی، API `lockForUpdate` در `data_base/query/api/src/main/kotlin/interfaces/api/select_api/query_render_select/IQueryRenderSelectApi.kt`، فیلد اختیاری آن در `data_base/query/ast/src/main/kotlin/interfaces/select_interface/query_render_select/IQueryRenderSelectAst.kt` و `data_base/query/ast/src/main/kotlin/schema/select_schema/query_render_select/QueryRenderSelectAst.kt` و setter آن در `data_base/query/builder/src/main/kotlin/ast/select_builder/query_render_select/QueryRenderSelectBuilder.kt` دیده می‌شود. `data_base/query/renderer/src/main/kotlin/nodes/select_nodes/query/MySqlQueryCapability.kt` node قفل را در انتهای SELECT render می‌کند و `data_base/query/renderer/src/main/kotlin/dialects/MySqlDialect.kt` مقدار `_prefixSelectLock = "for update"` دارد؛ capability متن مربوط را در `data_base/query/renderer/src/main/kotlin/nodes/select_nodes/select_lock_row/MySqlQuerySelectLockRowCapability.kt` برمی‌گرداند.

این شواهد وجود مسیر AST/SQL برای MySQL را نشان می‌دهند، نه اثبات اثر قفل روی دیتابیس یا تضمین پشتیبانی dialectهای دیگر. Transaction باید همان query/AST را روی Connection خودش اجرا کند، اما منطق آغاز/پایان تراکنش نباید وارد AST یا SQL renderer شود. تست render باید هم حالت بدون node قفل و هم حالت درخواست‌شده را بررسی کند؛ اثر واقعی قفل فقط با integration test مناسب اثبات می‌شود.

## 3. API عمومی پیشنهادی

پیشنهاد نسخهٔ 1.0 افزودن متد زیر به `IQueryBuilderExecutor` است:

```kotlin
fun <T> transaction(
    block: (tx: IQueryBuilderExecutor) -> ExecuteResult<T>,
): ExecuteResult<T>
```

Lambda می‌تواند با شکل مصرفی زیر فراخوانی شود:

```kotlin
val result: ExecuteResult<Long> = queryManager.transaction { tx ->
    val order = tx.queryBuilder(insertOrderQuery).execute()
    if (order is ExecuteResult.Failure) return@transaction order

    val wallet = tx.queryBuilder(updateWalletQuery).execute()
    if (wallet is ExecuteResult.Failure) return@transaction wallet

    tx.queryBuilder(insertWalletTransactionQuery).execute()
}
```

`ExecuteResult.Failure` به‌دلیل covariance می‌تواند از branchهای شکست با payloadهای operation متفاوت برگردد؛ خروجی موفق callback نوع نهایی `T` را تعیین می‌کند. امضای نهایی باید در مرحلهٔ API با Kotlin compiler بررسی شود.

قرارداد انتخابی پیشنهادی:

- `tx` یک executor مخصوص همان scope است، نه الزاماً همان object ورودی `queryManager`.
- `tx.queryBuilder(...)`، `queryBuilderSelect(...)`، `sql(...)` و متدهای اجرای عادی از همان سطح API استفاده می‌کنند.
- اگر callback `Success(value)` برگرداند و هیچ operation داخل scope شکست نخورده باشد، نتیجهٔ تراکنش `Success(value)` است؛ مقدار callback، از جمله payload تهی، حفظ می‌شود.
- اگر callback `Failure` برگرداند، Transaction آن را failure می‌داند، rollback می‌کند و Failure اصلی را حفظ می‌کند.
- Transaction باید شکست هر query را حتی اگر callback آن را نادیده بگیرد ثبت کند؛ failure ثبت‌شده اجازهٔ commit نمی‌دهد. پس از failure ثبت‌شده، پیشنهاد می‌شود اجرای queryهای بعدی در همان scope fail-fast شود.
- این قرارداد عمداً callback را به `ExecuteResult<T>` محدود می‌کند تا خروجی query نهایی بدون nested `ExecuteResult` به caller برگردد. اگر تیم callback با خروجی plain `T` می‌خواهد، باید قرارداد جایگزین جداگانه انتخاب شود؛ overload مبهم یا implicit unwrap اضافه نشود.

## 4. قرارداد دقیق موفقیت و شکست

| رویداد | نتیجهٔ تراکنش پیشنهادی | رفتار Connection |
|---|---|---|
| گرفتن Connection و شروع موفق؛ callback برابر `Success(value)` و بدون failure داخلی | `Success(value)` پس از commit موفق | یک Connection برای کل scope؛ بسته‌شدن در پایان |
| callback برابر `Failure(e)` | rollback و بازگرداندن همان `Failure(e)` | rollback یک‌بار، سپس بستن Connection |
| operation در `tx`، از جمله خطای render/materialization، `Failure(e)` برمی‌گرداند | failure در scope latch شود؛ commit ممنوع؛ rollback؛ همان خطای اصلی به caller برسد | queryهای بعدی یا اجرا نشوند یا با همان failure خاتمه یابند؛ تصمیم اجرایی در Phase 0 تثبیت شود |
| callback exception پرتاب می‌کند | پیشنهاد: rollback و `Failure(exception)`؛ اگر `Throwable` غیر `Exception` گرفته می‌شود، rollback سپس rethrow شود تا خطاهای fatal به نتیجهٔ عادی تبدیل نشوند | یک rollback، آزادسازی منابع |
| گرفتن Connection یا `setAutoCommit(false)` شکست می‌خورد | `Failure(startFailure)`؛ callback اجرا نشود | اگر Connection گرفته شده، بسته شود؛ خطای close به‌صورت suppressed حفظ شود |
| commit شکست می‌خورد | `Failure(commitFailure)`؛ تلاش کنترل‌شده برای rollback؛ خطای rollback suppressed به commit failure اضافه شود | Connection بسته شود؛ موفقیت گزارش نشود |
| rollback درحالی شکست اصلی وجود دارد | همان خطای عملیاتی، exception یا `Failure`، primary بماند؛ rollback failure به‌صورت suppressed حفظ شود | بستن Connection ادامه یابد |
| بستن Connection پس از commit موفق شکست می‌خورد | **تصمیم باز:** `Failure` ساده می‌تواند caller را به retry تراکنش commit‌شده ترغیب کند؛ نتیجه باید commit‌شدن را صریحاً از خطای cleanup متمایز کند | نتیجهٔ commit قابل برگشت نیست؛ خطای close حفظ شود |

**قاعدهٔ اصلی:** commit فقط وقتی مجاز است که callback موفق باشد و هیچ Failure ثبت‌شده‌ای از queryهای scope وجود نداشته باشد. `ExecuteResult.Failure` یک نتیجهٔ عادی Kotlin است و نباید به‌خاطر throw نشدن، commit ناخواسته رخ دهد. `success/error` فقط transformer/observerهای فعلی هستند و نباید به‌عنوان کنترل تراکنش تلقی شوند.

Callback تراکنش و هر callback اجرایی باید حداکثر یک بار اجرا شوند. exception callback نباید باعث re-entry callback یا اجرای دوبارهٔ SQL شود.

## 5. معماری داخلی و مرز مسئولیت‌ها

پیشنهاد جداسازی لایه‌ها:

1. **`data_base:manager:connection`** مالک ایجاد Connection بماند. برای آزمون و مدیریت lifecycle، مسیر transaction باید بتواند provider اتصال را تزریق یا mock کند؛ تغییر نباید pool فرضی اضافه کند.
2. **`data_base:manager:execute`** اجرای statement روی یک Connection از قبل گرفته‌شده را پشتیبانی کند. این مسیر باید statement، ResultSet و generated keys متعلق به operation را ببندد ولی Connection مشترک را نبندد. مسیرهای فعلی که خودشان Connection می‌گیرند و می‌بندند برای non-transactional callers حفظ شوند.
3. **`data_base:query:executor`** مسئول API تراکنش، rendering query و ساخت scoped `IQueryBuilderExecutor` باشد. executor scope از همان rendering/binding path فعلی استفاده کند، اما operationها را به backend connection-bound بفرستد، نه به `QueryExecute()` مستقلی که Connection تازه می‌گیرد.
4. برای رسیدن به یک Connection مشترک، dependency جهت‌دار باقی بماند: `query:executor` → `manager:execute` → `manager:connection`. Transaction نباید به AST یا renderer وابسته شود و lock metadata نباید مسئولیت commit/rollback بگیرد.

**نکتهٔ طراحی مهم:** صرف override کردن overloadهای callback `execute(...)` کافی نیست، چون خطاهای preflight/render یا materialization نیز می‌توانند از `QueryConsumer` به‌صورت `Failure` برگردند. scoped executor یا consumer adapter باید terminal result را در هر دو مسیر SELECT (`first/get` و در نتیجه `QueryConsumer.executeAs`) و mutation ثبت کند. raw callbackهای `execute(...)` همچنان باید قرارداد زنده‌بودن ResultSet و بستن آن در callback را رعایت کنند.

## 6. مدیریت Connection و چرخهٔ عمر

1. Transaction یک Connection را از connection provider می‌گیرد و مالک آن می‌شود.
2. قبل از فراخوانی callback، تراکنش را روی همان Connection آغاز می‌کند. روش پیشنهادی JDBC، تنظیم `autoCommit=false` روی Connection تازه‌گرفته‌شده است؛ اگر implementation بخواهد وضعیت قبلی را حفظ کند، آن را پیش از تغییر ثبت کند.
3. تمام queryهای `tx` روی همان object فیزیکی اجرا می‌شوند. هیچ کدام نباید `DatabaseConnection().build()` را از مسیر معمول `QueryExecute` فراخوانی کنند.
4. هر PreparedStatement با `use` بسته شود. ResultSet و generated keys نیز پیش از خروج operation بسته شوند؛ به‌خصوص SELECT باید materialize خود را پیش از بسته‌شدن statement انجام دهد.
5. در پایان موفقیت، commit و سپس بستن/آزادسازی Connection. در شکست، rollback و سپس بستن/آزادسازی Connection.
6. scoped executor یک پرچم lifecycle داشته باشد. بعد از پایان callback، `execute`, `get`, `first` یا mutation از executor ذخیره‌شده نباید Connection تازه بگیرد یا روی Connection بسته کار کند؛ باید شکست مشخصی بدهد.
7. Scope هم‌زمان/thread-safe یا asynchronous نیست. تا وقتی API coroutine/async وجود ندارد، callback باید synchronous اجرا شود و `tx` فقط در همان scope و thread مجاز است.
8. مسیر فعلی `DatabaseConnection.build()` اتصال جدید از `DriverManager` می‌سازد و use آن را می‌بندد؛ در سورس فعلی pool اجرایی پیدا نشد، هرچند `dbPoolSize` در config وجود دارد. اگر Connectionهای poolشده بعداً افزوده شوند، close باید به معنی بازگرداندن lease باشد و reset کردن `autoCommit`, isolation و stateهای دیگر به contract provider واگذار شود.

## 7. ارتباط با ExecuteResult و FOR UPDATE

### 7.1 ExecuteResult

- خروجی عمومی Transaction از نوع `ExecuteResult<T>` باشد.
- `Success` nullable است؛ Transaction نباید `Success(null)` را بی‌دلیل به failure یا مقدار دیگری تبدیل کند.
- `Failure` همان Throwable عملیات را نگه می‌دارد. در صورت خطای rollback/close همزمان، خطای اصلی primary بماند و خطاهای ثانویه suppressed باشند.
- callback `error {}` فعلی فقط Failure را مشاهده می‌کند و همان result را برمی‌گرداند. recovery از failure با این extension اتفاق نمی‌افتد.
- callback موفقیت نباید چند بار اجرا شود و mapping/materialization query نیز در مرز تراکنش تکرار نشود.

### 7.2 FOR UPDATE

- API `lockForUpdate` و AST آن مستقل از Transaction و صرفاً بخشی از تعریف/render query باقی بمانند.
- قفل فقط وقتی درخواست شده باشد که AST مربوط وجود داشته باشد؛ هیچ Transaction به‌تنهایی نباید `FOR UPDATE` به SQL اضافه کند.
- query قفل‌دار وقتی از `tx` اجرا می‌شود، همان Connection و تراکنش را مصرف می‌کند.
- مسیر فعلی renderer که قابل مشاهده است MySQL را هدف می‌گیرد و prefix آن `for update` است. پشتیبانی از MariaDB یا نسخه‌های خاص، semantics قفل، و امکان قفل در همهٔ query شکل‌ها از این کد به‌تنهایی نتیجه نمی‌شود؛ باید با مستندات driver/database و integration test همان نسخه‌ها تعیین شود.
- برای اثبات اثر قفل، تست SQL rendering کافی نیست. در صورت داشتن دو Connection و DB مناسب، integration test رقابت/انتظار lock طراحی شود؛ تا آن زمان فقط ساخت SQL قابل تأیید است.

## 8. ماژول‌ها و فایل‌های احتمالی درگیر

مسیرهای موجود و تأییدشده از سورس:

| لایه | مسیر موجود | نقش در طراحی |
|---|---|---|
| Query execution API | `data_base/query/executor/src/main/kotlin/interfaces/IQueryBuilderExecutor.kt` | API عمومی `transaction` پیاده‌سازی شده و overloadهای query فعلی حفظ شده‌اند |
| Query execution implementation | `data_base/query/executor/src/main/kotlin/manager/QueryBuilderExecutor.kt` | فراهم‌کردن renderer و executor scope وابسته به connection transaction |
| SELECT consumer | `data_base/query/executor/src/main/kotlin/result/QueryConsumer.kt` | حفظ `execute`, `executeAs`, `sql`, `details`; ثبت failure نهایی SELECT در scope |
| Mutation consumers | `data_base/query/executor/src/main/kotlin/result/MutationQueryConsumers.kt` | حفظ `execute()` و `sql()` mutationها و ثبت failure نهایی |
| JDBC execution | `data_base/manager/execute/src/main/kotlin/manager/QueryExecute.kt` | افزودن یا استخراج عملیات connection-bound، بدون بستن Connection تراکنش در هر query |
| JDBC contract | `data_base/manager/execute/src/main/kotlin/interfaces/IQueryExecute.kt` | تعیین مسیر compatibility برای built/bound queryها و عملیات اجرا |
| Connection provider | `data_base/manager/connection/src/main/kotlin/interfaces/IDatabaseConnection.kt` و `data_base/manager/connection/src/main/kotlin/manager/DatabaseConnection.kt` | تزریق/انتزاع مسیر دریافت connection برای تست و transaction |
| Config | `data_base/core/src/main/kotlin/data_base/DatabaseConfig.kt` | تنظیم فعلی URL/credential؛ `dbPoolSize` در مسیر فعلی pool ایجاد نمی‌کند |
| اجرای JDBC tests | `data_base/manager/execute/src/test/kotlin/manager/QueryExecuteContractTest.kt` | الگوی Recording JDBC برای failure، callback و resource lifecycle |
| Query executor tests | `data_base/query/executor/src/test/kotlin/manager/IdentityAwareSelectExecutionTest.kt` و `QueryConsumerTest.kt` | حفظ parameter order، metadata، query callback و بسته‌شدن منابع |
| تست جدید transaction | پیشنهاد: `data_base/query/executor/src/test/kotlin/manager/TransactionExecutionTest.kt` | fake connection یکپارچه، commit/rollback، scope و نتیجه |
| rendering/lock tests | `data_base/query/renderer/src/test/kotlin/parameters/QueryParameterRenderingTest.kt` یا test class تخصصی جدید | حفظ SQL و parameter order با/بدون lock |
| integration اختیاری | پیشنهاد: `data_base/query/executor/src/test/kotlin/manager/TransactionMySqlIntegrationTest.kt` | تأیید رفتار واقعی commit/rollback/lock با دیتابیس تنظیم‌شده |

وابستگی‌های Gradle در `data_base/query/executor/build.gradle.kts`, `data_base/manager/execute/build.gradle.kts` و `data_base/manager/connection/build.gradle.kts` بررسی شدند. Phase 2 یک dependency مستقیم `implementation(project(":data_base:manager:connection"))` به executor افزود، چون `QueryBuilderExecutor` مستقیماً `DatabaseConnection` را برای provider پیش‌فرض می‌سازد و dependency آن از `manager:execute` به‌صورت implementation منتشر می‌شود. این تغییر API dependency عمومی را گسترش نمی‌دهد؛ dependency/plugin دیگری اضافه نشد.

افزودن عضو abstract به `IQueryBuilderExecutor` می‌توانست source compatibility پیاده‌سازی‌کنندگان را بشکند. قرارداد نهایی از یک default صریح استفاده می‌کند که `Failure(UnsupportedOperationException)` می‌دهد و نه callback و نه اجرای مستقل را فراخوانی می‌کند. تنها implementation production فعلی `QueryBuilderExecutor` است؛ fixtureهای موجود در `SmartQueryResultTest.kt`, `QueryConsumerTest.kt`, `QueryConsumerExecuteAsTest.kt`, `CteDeclarationExecutionTest.kt` و `TypedQueryOutputFactoryTest.kt` بدون override اجباری کامپایل می‌شوند. تست `TransactionExecutionTest` default unsupported را مستقیماً می‌سنجد.

## 9. مراحل اجرایی نسخهٔ 1.0

### Phase 0 — تثبیت قرارداد و تصمیم‌های معماری (تکمیل‌شده)

**خروجی:** قرارداد API، رفتار failure، خطای cleanup و nested transaction تثبیت شد و در پیاده‌سازی Phase 2 منعکس شده است.

**تصمیم‌های ثبت‌شده:** callback از نوع `ExecuteResult<T>` است؛ Failure نادیده‌گرفته‌شده commit را متوقف می‌کند؛ `Exception` به Failure تراکنش تبدیل می‌شود و `Error` پس از cleanup دوباره پرتاب می‌شود؛ commit/rollback/close تقدم مشخص دارند؛ استفاده پس از scope و nested transaction رد می‌شوند. قابلیت decrement به API جداگانه نیاز دارد و بخشی از این نسخه نیست.

**تست/بررسی:** امضای API و semantics در `IQueryBuilderExecutor` و تست‌های `TransactionExecutionTest` اعتبارسنجی شده‌اند.

**معیار پذیرش:** نتیجهٔ پایان تراکنش تعیین‌شده است؛ commit فقط پس از callback موفق و بدون failure ثبت‌شده مجاز است؛ قرارداد از `ExecuteResult` فعلی قابل بیان است.

### Phase 1 — اجرای JDBC روی Connection تزریق‌شده

**وضعیت:** پیش‌نیاز لایهٔ manager پیاده‌سازی و اعتبارسنجی هدفمند آن موفق شده است.

**ماژول/فایل‌های تغییرکرده:**
- `data_base/manager/execute/src/main/kotlin/interfaces/IQueryExecute.kt`
- `data_base/manager/execute/src/main/kotlin/manager/QueryExecute.kt`
- `data_base/manager/execute/src/test/kotlin/manager/QueryExecuteContractTest.kt`
- همین سند برای ثبت قرارداد و وضعیت فاز.

**قرارداد پیاده‌سازی‌شده:** برای operationهای پشتیبانی‌شده، overloadهای دارای `Connection` برای `BuiltQuery` و `BoundQuery` اضافه شده‌اند؛ `executeTable` همچنان فقط مسیر موجود `BuiltQuery` را دارد. `QueryExecute` این overloadها را روی همان Connection ورودی اجرا می‌کند و آن را نمی‌بندد. مسیر معمول هر operation همچنان Connection خودش را می‌گیرد و می‌بندد و از منطق statement مشترک با مسیر تزریق‌شده استفاده می‌کند. `IDatabaseConnection` و `DatabaseConnection` تغییر نکرده‌اند؛ این فاز Connection آماده را به‌عنوان ورودی می‌گیرد و provider تازه‌ای لازم ندارد.

برای سازگاری implementerهای فعلی `IQueryExecute`، overloadهای جدید default implementation دارند که callback را دقیقاً یک‌بار با `ExecuteResult.Failure(UnsupportedOperationException)` فراخوانی می‌کنند. آن‌ها به overload معمولی برنمی‌گردند و Connection دوم نمی‌گیرند. `QueryExecute` این overloadها را به‌طور واقعی پیاده‌سازی می‌کند.

binding همچنان از `readyParamsInQuery` موجود انجام می‌شود: `BuiltQuery` نام placeholderها را به ترتیب occurrence در SQL resolve می‌کند و `BoundQuery` هر binding occurrence را با index ترتیبی bind می‌کند. خطاهای JDBC به یک `Failure` در callback تبدیل می‌شوند؛ callback بیرون از catch خطای JDBC اجرا می‌شود و exception آن دوباره به‌عنوان خطای JDBC وارد callback نمی‌شود. SELECT، ResultSet را هنگام callback زنده نگه می‌دارد؛ statement پس از callback بسته می‌شود و ResultSet مطابق قرارداد JDBC و رفتار مصرف‌کنندهٔ callback آزاد می‌شود. generated keys پیش از callback بسته می‌شوند. Connection تزریق‌شده در هیچ‌یک از مسیرها با `use` بسته نمی‌شود.

**تست‌های افزوده‌شده در `QueryExecuteContractTest.kt`:** جفت مسیر Built/Bound برای SELECT، INSERT، UPDATE و DELETE؛ مسیر DDL/Table موجود؛ اجرای چند operation متوالی روی یک Connection identity با ترتیب bind مستقل هر statement؛ خطاهای prepare، bind و execute و callback یک‌باره؛ exception callback به‌عنوان خطای اصلی با خطای close به‌صورت suppressed؛ و default failure برای implementer قدیمی بدون fallback به Connection مستقل. تست‌های پیشین lifecycle مسیر عادی نیز حفظ شده‌اند.

**معیارهای پذیرش این پیش‌نیاز:** مسیر عادی همچنان Connection خود را می‌گیرد/می‌بندد؛ هر مسیر تزریق‌شده فقط Connection ورودی را مصرف می‌کند و نمی‌بندد؛ statement و generated keys بسته می‌شوند؛ ResultSet در طول callback SELECT قابل‌استفاده است و با بستن statement آزاد می‌شود؛ callback یک‌بار فراخوانی می‌شود؛ ترتیب binding حفظ می‌شود؛ و خطای callback با خطای close پوشانده نمی‌شود.

**اعتبارسنجی ثبت‌شده:**

```powershell
$env:JAVA_HOME = 'D:\Dev\tools\jdk-24'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle')
.\gradlew.bat :data_base:manager:execute:test --tests "gog.my_project.data_base.manager.execute.manager.QueryExecuteContractTest" --rerun-tasks --console=plain
```

نتیجهٔ اجرای نهایی: `BUILD SUCCESSFUL`; XML کلاس `QueryExecuteContractTest` گزارش `26 tests, 0 failures, 0 errors, 0 skipped` داد. taskهای این اجرای `--rerun-tasks` همگی اجرا شدند (`8 actionable tasks: 8 executed`). اجرای نخست با Gradle User Home پیش‌فرض پیش از شروع taskها به‌دلیل `Access is denied` روی `gradle-8.14-bin.zip.lck` متوقف شد؛ اجرای موفق از cache محلی `.gradle` مخزن و JDK `24.0.2` استفاده کرد. هیچ قفل یا cacheای دستی حذف یا تغییر داده نشد.

برای اطمینان از source compatibility implementerهای قدیمی `IQueryExecute` در migration fixtures نیز این compile-only اجرا شد:

```powershell
$env:JAVA_HOME = 'D:\Dev\tools\jdk-24'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle')
.\gradlew.bat :data_base:migration:executor:compileTestKotlin --rerun-tasks --console=plain
```

نتیجه: `BUILD SUCCESSFUL` و `42 actionable tasks: 42 executed`. این task test sourceها را کامپایل کرد و تست‌های migration را اجرا نکرد.

**هشدارها و محدودیت‌های اعتبارسنجی:** Kotlin daemon در این محیط نتوانست فایل موقت خود را زیر `C:\Users\Gog\AppData\Local\kotlin\daemon` بسازد و Gradle با fallback بدون daemon ادامه داد؛ این موضوع اجرای نهایی را متوقف نکرد. Kotlin فعلی target مربوط به JDK 24 را به JVM 22 برگرداند و Gradle هشدار ناسازگاری `compileJava` (24) با `compileKotlin` (22) داد. هشدار قدیمی redundant projection در `BoundQuery.kt` نیز دیده شد. تنظیمات JVM target یا فایل‌های نامرتبط در این فاز تغییر نکردند.

**نتیجه و مرز این فاز:** این فاز فقط اجرای operation روی Connection تزریق‌شده را فراهم کرد؛ آغاز/commit/rollback، failure latch و محدودکردن عمر executor در Phase 2 پیاده‌سازی شده‌اند. `QueryConsumer` برای SELECT نتیجه را در callback به‌طور کامل materialize می‌کند؛ نگه‌داشتن ResultSet پس از بازگشت از callback پشتیبانی نمی‌شود. اثبات commit/rollback با دیتابیس واقعی همچنان به integration ایزولهٔ Phase 3 تعلق دارد.

**خارج از Phase 1:** در این فاز API در `IQueryBuilderExecutor` اضافه نشد؛ API عمومی `transaction {}`, transaction scope, autocommit, commit/rollback, failure latch و عمر executor در Phase 2 اضافه شدند.

### Phase 2 — transaction scope، scoped executor و lifecycle (پیاده‌سازی و اعتبارسنجی‌شده)

**ماژول/فایل‌های تغییرکرده:** `data_base/query/executor`: `build.gradle.kts`، `interfaces/IQueryBuilderExecutor.kt`، `manager/QueryBuilderExecutor.kt`، `manager/TransactionExecutionScope.kt`، `manager/TransactionExceptions.kt` و `src/test/kotlin/manager/TransactionExecutionTest.kt`. وابستگی مستقیم `manager:connection` در build اضافه شد چون executor برای provider پیش‌فرض `DatabaseConnection` مستقیماً به آن وابسته است و وابستگی پیاده‌سازی‌شدهٔ `manager:execute` آن را در API compile این ماژول منتشر نمی‌کند. قرارداد connection-bound در `data_base/manager/execute` که در Phase 1 تکمیل شده بود مصرف می‌شود؛ در این Phase آن ماژول و `ICteDeclaration` تغییر نمی‌کنند. دو exception عمومی outcome تراکنش را برای commit و close پس از commit مشخص می‌کنند؛ scope و guard داخلی‌اند.

**قرارداد تثبیت‌شده:** امضای عمومی `transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T>` است و `Success` callback، از جمله payload تهی، بدون unwrap یا بازسازی برمی‌گردد. callback حداکثر یک‌بار اجرا می‌شود. پیاده‌سازی‌های `IQueryBuilderExecutor` که transaction connection-bound ندارند از default صریح `Failure(UnsupportedOperationException)` می‌گیرند؛ callback اجرا نمی‌شود و هیچ fallback مستقلی رخ نمی‌دهد. تنها implementation production فعلی `QueryBuilderExecutor` است؛ fakeهای تستی به‌دلیل default جدید نیاز به پیاده‌سازی اجباری ندارند.

**رفتار پیاده‌سازی:** یک Connection از provider گرفته و `autoCommit=false` می‌شود؛ SELECT/INSERT/UPDATE/DELETE از overloadهای connection-injected Phase 1 روی همان Connection اجرا می‌شوند. `get()/first()` نتیجهٔ نهایی materialized را هم برای failure latch بررسی می‌کنند. اولین Query `Failure` ثبت می‌شود، queryهای بعدی رد می‌شوند و در پایان rollback انجام می‌شود، حتی اگر callback آن Failure را نادیده بگیرد. `sql()/details()` مسیر introspection هستند و به‌تنهایی JDBC یا تغییر وضعیت تراکنش ندارند. `executeOut()` در scope رد می‌شود تا `QueryExecute` مستقل نتواند تضمین تراکنش را دور بزند.

**پایان تراکنش و خطاها:** callback `Failure`، Query `Failure` و `Exception` callback به rollback و `Failure` منجر می‌شوند؛ خطای اولیه حفظ و خطای rollback/close به‌صورت suppressed افزوده می‌شود. `Error` پس از cleanup دوباره پرتاب می‌شود و به Failure عادی تبدیل نمی‌شود. خطای commit با `TransactionCommitException` گزارش و rollback آن تلاش می‌شود. اگر close پس از commit موفق شکست بخورد، `TransactionCompletionException(committed=true)` برمی‌گردد و rollback انجام نمی‌شود؛ برای close از نوع `Error` نیز `TransactionCompletionError(committed=true)` پرتاب می‌شود. شکست acquisition/start callback را اجرا نمی‌کند و Connection گرفته‌شده بسته می‌شود. nested transaction روی executorهای QueryBuilderExecutor در همان thread رد می‌شود و failure به scope بیرونی latch می‌شود. executor/consumer ذخیره‌شده بعد از scope پیش از دسترسی به JDBC رد می‌شود.

**تست‌ها:** ۲۲ تست `TransactionExecutionTest` با proxyهای JDBC مسیر commit/rollback، Failure نادیده‌گرفته‌شده و fail-fast، callback `Failure`/exception/`Error`, acquisition/start/commit/rollback/close failure، حفظ nullable Success و هویت خطا، SELECT/INSERT/UPDATE/DELETE روی Connection واحد، ترتیب binding و بستن statement/generated keys/ResultSet، lifetime، nested transaction روی executor دیگر، شکست materialization latch‌شده، introspection بدون اجرا، مسدودسازی `executeOut()` و رفتار عادی خارج از Transaction را بررسی می‌کنند. یک fake بدون پشتیبانی transaction نیز بررسی می‌کند default interface بدون اجرای callback یا query، Failure صریح بدهد.

**معیار پذیرش:** هر Query داخل scope دقیقاً از Connection متعلق به scope استفاده کند؛ هیچ عملیات بعد از failure یا پایان scope به JDBC نرسد؛ commit فقط برای callback Success بدون Query failure انجام شود؛ rollback در شکست انجام و Connection فقط یک‌بار بسته شود؛ خطاهای ثانویه خطای اصلی را نپوشانند؛ نتیجهٔ callback عیناً حفظ شود؛ مسیر عادی خارج از transaction تغییر نکند.

**اعتبارسنجی ثبت‌شده:**

```powershell
$env:JAVA_HOME = 'D:\Dev\tools\jdk-24'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle')
.\gradlew.bat :data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --console=plain
```

نتیجهٔ اجرای نهایی پس از آخرین تغییر fixture: `BUILD SUCCESSFUL`; XML مربوط به `TransactionExecutionTest` گزارش `22 tests, 0 failures, 0 errors, 0 skipped` داد. در این اجرا دو task اجرا شدند و ۲۶ task دیگر `UP-TO-DATE` بودند (`28 actionable tasks: 2 executed, 26 up-to-date`). یک اجرای پیشین همین suite با `--rerun-tasks` نیز موفق بود؛ خروجی آن مربوط به نسخهٔ قبلی fixture با ۲۱ تست بود و جایگزین نتیجهٔ نهایی ۲۲ تستی نیست.

سپس کل تست‌های executor اجرا شدند:

```powershell
$env:JAVA_HOME = 'D:\Dev\tools\jdk-24'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle')
.\gradlew.bat :data_base:query:executor:test --console=plain
```

نتیجهٔ اجرای نهایی پس از آخرین تغییر fixture: `BUILD SUCCESSFUL`; گزارش‌های XML شامل `88 tests, 0 failures, 0 errors, 0 skipped` در هشت suite بودند. در این اجرا task `:data_base:query:executor:test` اجرا شد و ۲۷ task دیگر `UP-TO-DATE` بودند (`28 actionable tasks: 1 executed, 27 up-to-date`).

**هشدارهای اعتبارسنجی:** در اجراهایی که test sourceها را کامپایل کردند، Kotlin daemon نتوانست فایل marker موقت را زیر `C:\Users\Gog\AppData\Local\kotlin\daemon` بسازد؛ Gradle به fallback بدون daemon رفت و build/test با موفقیت پایان یافت. Gradle نیز گزارش داد Kotlin برای JDK target 24 هنوز پشتیبانی ندارد و JVM target را به 22 برگرداند. چند هشدار deprecated builder API و هشدار قدیمی redundant projection در `BoundQuery.kt` نیز دیده شدند؛ هیچ‌کدام مانع موفقیت taskها نشدند و در این Phase تغییر داده نشدند.

### Phase 3 — اعتبارسنجی یکپارچهٔ transaction (منطق اجرایی در Phase 2 جذب شد)

**وضعیت:** lifecycle مربوط به commit/rollback، ثبت Failure، cleanup و اعتبار executor scoped در Phase 2 پیاده‌سازی شده بود. بازبینی Phase 3 یک نقص سازگاری پیدا کرد: callback می‌توانست executor اصلی را از closure صدا بزند و آن executor روی همان thread Connection مستقل بگیرد؛ `executeOut()` از همان مسیر نیز می‌توانست executor اصلی را دور بزند. در `TransactionExecutionScope` دسترسی به scope فعال همان thread اضافه شد و `QueryBuilderExecutor` اکنون اجرای عادی از executor اصلی و `executeOut()` را هنگام وجود transaction فعال رد و Failure را در scope ثبت می‌کند. تست‌های جدید اثبات می‌کنند Connection دوم یا PreparedStatement مستقل ساخته نمی‌شود و خطای ردشده commit را مسدود می‌کند. اجرای executor scoped همچنان فقط از Connection تزریق‌شده استفاده می‌کند.

**فایل‌های تغییرکرده در این Phase 3:** `data_base/query/executor/src/main/kotlin/manager/TransactionExecutionScope.kt`، `data_base/query/executor/src/main/kotlin/manager/QueryBuilderExecutor.kt` و `data_base/query/executor/src/test/kotlin/manager/TransactionExecutionTest.kt`؛ این‌ها به تغییرهای قبلی Phase 2 افزوده شدند. همین سند نیز برای ثبت نتیجه به‌روز شد. هیچ فایل `LockForUpdate` یا فایل دیگری از ماژول‌های builder/renderer برای رفع شکست‌های نامرتبط تغییر نکرد.

**بازبینی مسیرها:** overloadهای SELECT، INSERT، UPDATE و DELETE در `QueryBuilderExecutor` در حضور `transactionScope` به overloadهای connection-bound در `QueryExecute` می‌روند؛ آن overloadها statement، generated keys و ResultSet خود را می‌بندند ولی Connection تزریق‌شده را نمی‌بندند. `get()`/`first()` materialization را داخل callback انجام می‌دهند. `executeOut()` روی executor scoped و روی executor اصلی همان thread رد می‌شود. `sql()`/`details()` مسیر render و callback اطلاعاتی‌اند و به‌تنهایی JDBC را اجرا نمی‌کنند. بعد از اولین Failure، scope اجرای بعدی را رد می‌کند؛ executor نگه‌داشته‌شده بعد از scope نیز قابل اجرا نیست. در repository فقط `QueryBuilderExecutor` implementer تولیدی `IQueryBuilderExecutor` است؛ default متد `transaction()` برای implementer فاقد پشتیبانی، Failure صریح می‌دهد و callback را اجرا نمی‌کند.

**اعتبارسنجی Phase 3:** همهٔ اجراها با JDK 24، `GRADLE_USER_HOME` موقت روی `.gradle` مخزن و `--rerun-tasks` مگر جایی که فرمان صریح خلاف آن را دارد انجام شدند. شمارها از XML تست‌ها خوانده شده‌اند.

| فرمان | نتیجهٔ Gradle و تست‌ها |
|---|---|
| `.\gradlew.bat :data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest.original executor cannot run an independent query inside transaction" --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest.executeOut through original executor cannot bypass transaction" --console=plain` | `BUILD SUCCESSFUL`; 2 تست، 0 failure/error/skipped؛ 5 task اجرا و 32 `UP-TO-DATE`. |
| `.\gradlew.bat :data_base:query:executor:test --tests "gog.my_project.data_base.query.executer.manager.TransactionExecutionTest" --rerun-tasks --console=plain` | `BUILD SUCCESSFUL`; کل suite تراکنش 24 تست، 0 failure/error/skipped؛ 28 task اجرا شدند. |
| `.\gradlew.bat :data_base:query:executor:test --rerun-tasks --console=plain` | `BUILD SUCCESSFUL`; 90 تست در 8 suite، 0 failure/error/skipped؛ 28 task اجرا شدند. |
| `.\gradlew.bat :data_base:manager:connection:test :data_base:manager:execute:test --rerun-tasks --console=plain` | `BUILD SUCCESSFUL`; connection: 0 تست تعریف‌شده؛ manager:execute: 27 تست، 0 failure/error و 1 skipped. تست MySQL اختیاری `QueryExecuteMySqlIntegrationTest` به‌علت نبود `execute.test.db.*` skip شد و Transaction را نمی‌آزماید؛ 17 task اجرا شدند. |
| `.\gradlew.bat :data_base:query:api:test :data_base:query:ast:test :data_base:query:builder:test :data_base:query:dialect:test :data_base:query:renderer:test --rerun-tasks --console=plain` | `BUILD FAILED` در `builder:test`; API: 9 موفق؛ AST: 18 موفق؛ renderer: 28 موفق؛ dialect: `NO-SOURCE`; builder: 165 تست، 3 failure، 0 error و 1 skipped؛ 37 task اجرا شدند. سه شکست موجود در `EffectiveQueryMetadataProviderTest`، `InlineCteParameterBindingTest` و `QueryRelationContractTest` از مسیر Transaction عبور نمی‌کنند و در فایل‌های آن‌ها در این Phase تغییری داده نشد. assertion اول null در برابر `published_with_name` داشت؛ دومی هنگام دسترسی به `parent.withs` NPE داشت؛ سومی تعداد dependency را 1 انتظار داشت ولی 2 دید. skipped، تست اختیاری `QueryRelationMySqlIntegrationTest` بود. |
| `.\gradlew.bat :data_base:query:cte:test --rerun-tasks --console=plain` | `BUILD SUCCESSFUL`; 9 تست در 2 suite، 0 failure/error/skipped؛ 37 task اجرا شدند. |

**زیرساخت دیتابیس و گزینهٔ کم‌تغییر:** در مخزن Testcontainers، H2، compose یا Transaction integration fixture وجود ندارد. `gradle.properties` مخزن، Gradle properties کاربر و environmentهای `ORG_GRADLE_PROJECT_execute.test.db.*` هیچ پیکربندی test DB نداشتند. سرویس MySQL محلی فعال بود، اما هیچ schema جداشده‌ای به‌عنوان test target معرفی نشده بود؛ MariaDB متوقف بود و Docker CLI به Docker daemon وصل نشد. بنابراین به MySQL محلی متصل نشدیم و schema یا داده‌ای را تغییر ندادیم. کم‌تغییرترین مسیر بعدی استفاده از همان Connector/J و الگوی opt-in موجود `execute.test.db.*` با یک schema اختصاصی MySQL است؛ `executor` باید همان properties را به test worker منتقل کند و fixture تراکنش جدول با نام یکتا را بیرون از scope بسازد و در `finally` پاک کند. این مسیر dependency جدید نمی‌خواهد، ولی تا زمانی که test DB ایزوله فراهم نشود، تست واقعی اجراشدنی نیست. تست read-only فعلی manager:execute اثبات transaction نیست.

**FOR UPDATE:** سورس AST و renderer فعلی node قفل MySQL را render می‌کند، ولی در test sourceها تست اختصاصی دو-Connection یا آزمون مشخصی برای قفل واقعی پیدا نشد. اجرای دو connection، انتظار محدود با latch و اثبات آزادشدن قفل بعد از commit/rollback به همان MySQL test schema ایزوله وابسته است؛ بنابراین هیچ ادعایی دربارهٔ اثر runtime قفل یا dialectهای دیگر تأیید نشده است.

### Phase 4 — lock integration، مثال‌ها و compatibility verification

**ماژول/فایل:** تست‌های rendering در `data_base/query/renderer/src/test/kotlin/parameters/`، test integration اختیاری در `data_base/query/executor/src/test/kotlin/manager/` و مثال محدود در `data_base/query/example/src/main/kotlin/`.

**کارها:** API قفل موجود را از transaction مستقل نگه دارید؛ مثال فقط از operationهایی استفاده کند که builder فعلی واقعاً می‌تواند render کند. اگر مثال به decrement expression نیاز دارد، آن قابلیت را خارج از این change set ثبت کنید.

**تست‌ها:** SQL بدون lock فاقد clause قفل و SQL دارای lock مطابق dialect موجود باشد؛ lock query از `tx` همان Connection را مصرف کند؛ integration concurrency فقط با دیتابیس واقعی و پیکربندی قابل‌تکرار اجرا شود. تست‌های موجود executor و manager execute نیز برای عدم regression اجرا شوند.

**معیار پذیرش:** transaction هیچ قفل ضمنی اضافه نکند؛ query قفل‌دار داخل transaction روی همان connection اجرا شود؛ رفتار non-transactional قبلی، SQL callbacks، parameter order و resource closure حفظ شوند. موفقیت compile به‌تنهایی اثبات اثر واقعی database lock نیست.

## 10. تست‌های نسخهٔ 1.0 و روش اعتبارسنجی

### Unit/contract tests بدون دیتابیس واقعی

با `Connection`, `PreparedStatement`, `ResultSet` proxy و driver ثبت‌کننده، حداقل موارد زیر آزموده شوند:

1. callback موفق و commit یک‌باره؛
2. چند query با Connection identity مشترک و یک acquisition؛
3. query failure و rollback بدون commit؛
4. callback صریحاً `Failure` برمی‌گرداند و rollback می‌شود؛
5. query `Failure` را تولید می‌کند ولی callback آن را نادیده می‌گیرد؛ باز هم commit رخ نمی‌دهد؛
6. callback exception و حفظ cause طبق تصمیم Phase 0؛
7. connection acquisition و begin (`getAutoCommit`/`setAutoCommit`) failure، عدم اجرای callback و آزادسازی؛
8. commit failure، rollback attempt و preserving primary/suppressed errors؛
9. rollback failure همراه خطای اولیه؛
10. cleanup/close failure پس از commit، با outcome صریح و بدون وانمودکردن rollback؛
11. مقدار بازگشتی callback، از جمله success با payload null، بدون تبدیل؛
12. هیچ callback یا SQL دوباره اجرا نشود؛
13. `sql {}`/`details {}` در scope اطلاعات درست بدهند ولی connection/query اجرا نکنند؛
14. استفاده از tx consumer پس از پایان scope شکست دهد و connection تازه نسازد؛
15. raw `ResultSet` فقط در callback زنده باشد و مسیرهای `get()/first()/executeAs()` منابع را پیش از commit ببندند؛
16. ترتیب پارامترهای positional در چند operation مستقل حفظ شود.

### Integration tests

- با MySQL واقعی و schema ایزوله: ثبت تغییرات بعد از commit؛ عدم ماندگاری تغییر بعد از rollback؛ اجرای دو operation متکی به هم در یک transaction.
- برای `FOR UPDATE`: دو session/Connection و سناریوی رقابت یا انتظار lock لازم است. تست render جایگزین این اثبات نیست.
- Integration test باید پیش‌نیازهای host/port/db/user/password، version و timeout را اعلام کند و در نبود config به‌شکل شفاف skip یا fail شود؛ skip نباید به‌عنوان اثبات رفتار database گزارش شود.
- **وضعیت این اجرا:** هیچ Transaction integration تست واقعی اضافه یا اجرا نشد؛ test DB ایزوله پیکربندی نشده بود. `QueryExecuteMySqlIntegrationTest` تنها integration موجودی بود که در این اجرا بررسی شد و با نبود propertyها skipped ماند؛ آن تست فقط binding نوع‌ها را بررسی می‌کند.

## 11. محدودیت‌ها و خارج از محدوده

- Nested transaction، savepoint و rollback جزئی خارج از V1.0؛ پیشنهاد: رد کنترل‌شدهٔ nested call با خطای مشخص.
- coroutine، async، استفادهٔ هم‌زمان از یک tx executor، و انتقال Connection به thread دیگر خارج از محدوده.
- Connection pool، retry خودکار، deadlock retry، isolation-level API و تنظیم lock timeout در V1.0 اضافه فرض نمی‌شوند.
- DDL و رفتار auto-commit مخصوص database خارج از scope اصلی‌اند، مگر پس از تصمیم روشن و integration test.
- increment/decrement یا SQL arithmetic expression در builder فعلی مشاهده نشد؛ مثال مالی نباید این API را فرض کند.
- `FOR UPDATE` فقط طبق dialect/DB واقعی ادعا شود؛ کد MySQL renderer به‌تنهایی رفتار همهٔ serverها را ثابت نمی‌کند.
- Transaction تضمین business invariant، جلوگیری از overspend یا idempotency نیست.
- nested `ExecuteResult`, swallow شدن Failure، یا تبدیل failure به مقدار موفق/Collection خالی مجاز نیست.

## 12. ریسک‌های معماری، ابهام‌ها و تصمیم‌های لازم

| مورد | شاهد فعلی | پیشنهاد برای Phase 0 / ریسک |
|---|---|---|
| Contract نوع callback | `ExecuteResult<T>` nullable و fluent extensionهای فعلی | تثبیت شد: callback نوع `ExecuteResult<T>` دارد؛ payload تهی حفظ می‌شود و overload با `T` اضافه نشده است. |
| خطای render/materialization که به صورت Failure است | `IQueryBuilderExecutor.get()/first()` خطا را به Failure تبدیل می‌کنند؛ `QueryConsumer` نیز نتیجه را capture می‌کند | در Phase 2، `get()/first()` scoped override می‌شوند تا Failure نهایی materialization نیز latch شود؛ hook صرفاً JDBC استفاده نشده است. |
| operation بعد از اولین Failure | Queryها synchronous و Failure مقدار عادی است | تثبیت شد: اولین Failure latch می‌شود و queryهای بعدی fail-fast رد می‌شوند؛ commit ممنوع است حتی اگر callback Failure را نادیده بگیرد. |
| exception callback | `Failure` حامل `Throwable` است؛ مسیرهای فعلی callback exceptions را بسته به سطح capture یا propagate می‌کنند | تثبیت شد: `Exception` به Failure تراکنش تبدیل می‌شود؛ `Error` پس از rollback و cleanup دوباره پرتاب می‌شود و در Failure عادی پنهان نمی‌ماند. |
| خطای rollback/close | JDBC close/operation می‌تواند بعد از result callback هم شکست بخورد؛ تست‌های manager این مورد را پوشش می‌دهند | تثبیت شد: خطای اصلی حفظ و خطاهای ثانویه suppressed می‌شوند. close پس از commit با `TransactionCompletionException(committed=true)` (یا نوع `Error` متناظر) مشخص می‌شود و rollback انجام نمی‌شود. |
| Start transaction | `DatabaseConnection.build()` فعلی SQLException را RuntimeException می‌کند | تثبیت شد: خطای acquisition/begin به Failure تبدیل می‌شود؛ اگر Connection گرفته شده باشد در شکست begin بسته می‌شود و callback اجرا نمی‌شود. |
| تغییر `IQueryBuilderExecutor` | یک پیاده‌سازی production و fake/fixtureهای تستی آن را پیاده می‌کنند | در repository فقط `QueryBuilderExecutor` پیاده‌سازی production است؛ default جدید در interface برای implementer بدون پشتیبانی Failure صریح می‌دهد و callback/اتصال مستقل را اجرا نمی‌کند. |
| مالک connection | هر operation فعلی Connection مستقل می‌گیرد و می‌بندد | Phase 1 connection-bound overloadها را فراهم کرده؛ scoped `QueryBuilderExecutor` آن‌ها را برای همهٔ operationهای پشتیبانی‌شده مصرف می‌کند و Connection را نمی‌بندد. |
| Pool | فیلد `dbPoolSize` موجود، ولی اتصال واقعی `DriverManager` است | وجود pool را ادعا نکنید. اگر provider آینده pool شد، reset state/lease contract بررسی شود. |
| Nested transaction | API فعلی transaction نداشت | تثبیت شد: ThreadLocal guard مشترک، nested call روی هر `QueryBuilderExecutor` در همان thread رد می‌کند و Failure را در scope فعال latch می‌کند. Savepoint و nested transaction میان threadها خارج از scope هستند. |
| FOR UPDATE | API/AST و renderer MySQL در working tree وجود دارند | دامنهٔ MySQL/MariaDB/version و test server واقعی روشن شود؛ SQL clause به‌تنهایی تضمین lock runtime نیست. |
| decrement موجودی | update API فقط `setValue` پارامتر را تعریف می‌کند؛ arithmetic expression دیده نشد | مثال کامل decrement به API جدید نیاز دارد یا باید خارج از scope بماند؛ از pre-read بدون lock به‌عنوان atomic راهکار یاد نشود. |
| دسترسی raw ResultSet | callbackمحور `IQueryBuilderExecutor.execute()` عمومی است | قرارداد عمر ResultSet داخل callback و منع نگهداری آن پس از statement/transaction روشن شود. |

تصمیم‌های مربوط به API callback، fail-fast، Exception/Error، cleanup و compatibility interface تثبیت، پیاده‌سازی و با تست‌های ماژول executor اعتبارسنجی شدند. هر تغییر تازه در dependency، public API یا رفتار ناسازگار همچنان نیازمند تعیین دامنهٔ جداگانه است.

## 13. چک‌لیست پذیرش نسخهٔ 1.0

- [x] API transaction روی `IQueryBuilderExecutor` و نوع result آن تصویب شده است.
- [x] callback با `tx` scoped همان `queryBuilder`, `sql`, `details` و execution APIهای پشتیبانی‌شده را دارد.
- [x] هر query داخل scope به همان Connection فیزیکی متصل است و اتصال مجزا باز نمی‌کند؛ تست‌ها یک acquisition برای چند DML و اجرای SELECT را پوشش می‌دهند.
- [x] نتیجهٔ `Failure` هر query—even اگر callback آن را نادیده بگیرد—commit را ناممکن می‌کند.
- [x] success با callback `Success(value)` commit می‌شود و value/null حفظ می‌شود.
- [x] Failure، callback exception، begin/commit/rollback/close failure دارای تقدم و cause روشن هستند.
- [x] statementها، generated keys و ResultSet در مسیرهای تراکنشی آزمایش‌شده بسته می‌شوند؛ تست چند operation تعداد closeهای statement/generated keys و تست SELECT ترتیب بستن ResultSet/statement را می‌سنجد. contract گسترده‌تر مسیرهای manager در Phase 1 نیز جداگانه تست شده است.
- [x] scoped executor بعد از callback نمی‌تواند query اجرا کند یا Connection تازه بگیرد.
- [x] nested transaction در همان thread به‌شکل روشن رد می‌شود.
- [x] SQL/details callbackها و ترتیب binding پارامترها حفظ شده‌اند و callback دوباره اجرا نمی‌شود.
- [ ] `FOR UPDATE` مستقل از Transaction باقی مانده، بدون درخواست تولید نمی‌شود و ادعای dialectی با شواهد تست می‌شود.
- [ ] Unit/contract tests بدون DB از integration tests واقعی تفکیک شده‌اند؛ سناریوهای commit/rollback، SELECT materialization، خطای SQL، Connection واحد، ignored Failure و resource closure روی DB واقعی هنوز به‌دلیل نبود test schema اجرا نشده‌اند.
- [ ] مثال V1.0 فقط از syntax موجود استفاده می‌کند و decrement ناموجود را وعده نمی‌دهد.
- [ ] build و test همهٔ ماژول‌های متأثر و یک consumer compile مستقل پس از تصمیم‌گیری dependency/API موفق شده‌اند.


## 14. Phase 3C: تست‌های Integration تراکنش روی MySQL

### تغییرات این مرحله

- در data_base/query/executor/build.gradle.kts، propertyهای Gradle برای فعال‌سازی Integration و پنج property اتصال به JVM تست منتقل می‌شوند. Connector/J یا dependency جدیدی اضافه نشده است؛ executor از dependency موجود data_base:manager:connection استفاده می‌کند.
- تست جدید data_base/query/executor/src/test/kotlin/manager/TransactionMySqlIntegrationTest.kt فقط با property دقیق execute.test.db.transaction.enabled=true وارد مسیر اتصال می‌شود. در نبود opt-in، پنج تست دیتابیسی پیش از ساخت Connection با پیام روشن skip می‌شوند. اگر opt-in فعال باشد، نبود هرکدام از host، port، name، username یا password خطای پیکربندی می‌دهد؛ برای port و password مقدار پیش‌فرض وجود ندارد و password خالی فقط در صورت ارائهٔ صریح property پذیرفته می‌شود.
- نام schema پیش از هر اتصال با الگوی qb_tx_test_[A-Za-z0-9_]+ اعتبارسنجی می‌شود و اتصال با SELECT DATABASE() بررسی می‌کند که schema مؤثر همان schema تنظیم‌شده باشد. تست‌ها هرگز CREATE DATABASE یا DROP DATABASE اجرا نمی‌کنند.
- هر تست دیتابیسی یک جدول مستقل InnoDB با پسوند UUID می‌سازد و فقط همان جدول تولیدشده را در finally حذف می‌کند. نام جدول از UUID و پیشوند ثابت ساخته می‌شود و به‌عنوان bind parameter استفاده نمی‌شود. تنظیم سراسری DefaultDatabaseConfig.config در finally به مقدار قبلی برمی‌گردد.
- کلاس Integration روی یک thread اجرا می‌شود، برای تنظیم سراسری database config از ResourceLock استفاده می‌کند و Gradle test task یک fork هم‌زمان دارد.

### سناریوهای پیاده‌سازی‌شده

| سناریو | آزمون |
|---|---|
| چند تغییر موفق داخل Transaction و مشاهده از Connection تازه پس از commit | commit_makes_multiple_changes_visible_to_a_fresh_connection |
| خطای واقعی unique constraint، rollback تغییر قبلی و حفظ همان SQLException driver | real_sql_failure_rolls_back_prior_change_and_preserves_driver_error_and_cleanup |
| نادیده گرفتن Failure و جلوگیری از commit | ignored_query_failure_still_prevents_commit_and_is_visible_as_failure |
| اجرای چند Query روی یک Connection و بستن یک‌بارهٔ آن | multiple_queries_use_one_transaction_connection_and_close_it_once |
| materialization نتیجهٔ SELECT پیش از بسته‌شدن ResultSet و Statement و Connection | select_is_materialized_before_result_resources_and_transaction_connection_close |
| نبود opt-in و رد پیکربندی ناقص یا schema نامعتبر بدون اتصال | no_opt_in_produces_no_database_settings_or_connection_attempt, enabled_configuration_requires_all_five_connection_properties_and_test_schema |

در آزمون SQL نامعتبر، exception ثبت‌شده از اجرای Query با exception نهایی Transaction از نظر identity مقایسه می‌شود؛ SQLState خطای constraint و rollback، بسته‌شدن Statement، ResultSet کلید تولیدشده و Connection نیز بررسی می‌شوند. Probe مبتنی بر proxy فقط Connection واقعی scope را می‌پیچد و مسیر اجرایی production را تغییر نمی‌دهد.

### اعتبارسنجی Phase 3C

در محیط این اجرا، هیچ property آزمایشی MySQL ارائه نشده بود؛ بنابراین هیچ Connection واقعی به MySQL باز نشد و هیچ Integration واقعی موفق گزارش نمی‌شود.

| فرمان | نتیجه |
|---|---|
| اجرای اولیه :data_base:query:executor:test --tests TransactionMySqlIntegrationTest با Gradle user home پیش‌فرض | پیش از شروع taskها با Access is denied روی قفل توزیع gradle-8.14-bin.zip.lck متوقف شد. |
| اجرای focused همان کلاس با JDK 24 و Gradle user home محلی .gradle مخزن، بدون propertyهای دیتابیس | BUILD SUCCESSFUL؛ گزارش XML: 7 تست، 0 failure، 0 error، 5 skipped. دو آزمون پیکربندی اجرا شدند؛ پنج سناریوی نیازمند MySQL به‌علت نبود opt-in skip شدند. |
| :data_base:query:executor:test با JDK 24 و Gradle user home محلی، بدون propertyهای دیتابیس | BUILD SUCCESSFUL؛ 97 تست، 0 failure، 0 error، 5 skipped در 9 suite. پنج مورد skip شده همان سناریوهای واقعی MySQL هستند. |

در هر دو اجرای موفق، Kotlin نتوانست marker موقت daemon را در مسیر AppData بنویسد و با fallback بدون daemon ادامه داد؛ build کامل شد. همچنین Kotlin برای JVM target 24 هشدار داد و به target 22 برگشت. این هشدارها به‌عنوان محدودیت محیط ثبت می‌شوند و نتیجهٔ تست‌ها را متوقف نکردند.

### محدودیت‌ها و گام بعد

- کاربر schema آزمایشی ازپیش‌ساخته‌شده با نام دارای پیشوند اجباری، دسترسی محدود به همان schema و هر پنج property را فراهم نکرده است. در نتیجه commit، rollback، خطای SQL و مشاهدهٔ مستقل هنوز روی MySQL واقعی اعتبارسنجی نشده‌اند.
- اجرای FOR UPDATE با دو Connection مستقل به دلیل شرط مرحله‌بندی اضافه نشد: سناریوهای پایه هنوز روی MySQL واقعی موفق نشده‌اند. وجود کد رندر یا تست compile به‌تنهایی شاهد قفل runtime نیست.
- اجرای MySQL باید فقط با propertyهای صریح زیر و schema ایزوله انجام شود: execute.test.db.transaction.enabled=true, execute.test.db.host, execute.test.db.port, execute.test.db.name, execute.test.db.username, execute.test.db.password. نبود هر property اتصال را به دیتابیس دیگری fallback نمی‌دهد.
- نتایج proxy/unit قبلی از Integration واقعی تفکیک می‌شوند؛ پنج skip این مرحله شاهد رفتار دیتابیس نیست.
