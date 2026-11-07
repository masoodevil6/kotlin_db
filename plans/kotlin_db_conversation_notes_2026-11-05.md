# مستند تجمیعی مکالمه درباره پروژه `kotlin_db`

> تاریخ تهیه: 2026-11-05  
> زبان: فارسی  
> هدف: انتقال زمینه و تصمیم‌های فنی مکالمه به یک فایل مستقل Markdown برای ادامه کار در گفت‌وگوهای بعدی.

## نکته درباره کامل‌بودن سند

این فایل یک بازسازی ساختاریافته از اطلاعات و محتوای مکالمه قبلی است که در زمینه فعلی در دسترس بوده است؛ **رونوشت کلمه‌به‌کلمه تمام پیام‌ها نیست**. کدهایی که در زمینه موجود بوده‌اند تا حد ممکن عیناً آورده شده‌اند. در مواردی که فقط خلاصه‌ای از بحث قبلی در دسترس بوده، موضوع و نتیجه ثبت شده و از ساختن جزئیات یا کدهای ناموجود خودداری شده است.

---

# 1. هدف و جهت کلی پروژه

نام پروژه: `kotlin_db`

پروژه یک کتابخانه Kotlin برای کار با دیتابیس است. هدف کاربر صرفاً ساخت یک نمونه برای مصاحبه یا یک پروژه موقت نیست؛ هدف این است که کتابخانه‌ای قابل استفاده مجدد و بادوام ساخته شود که در پروژه‌های آینده Kotlin، از جمله پروژه‌های Android یا Backend، قابل استفاده باشد.

جهت‌گیری‌های اصلی:

- Query Builder باید از نظر شکل نوشتار تا حد ممکن به SQL نزدیک باشد.
- معماری باید opinionated، قابل نگهداری و دارای مرزبندی روشن باشد.
- ماژول‌ها و مسئولیت‌های آن‌ها تا حد زیادی از قبل تعریف شده‌اند.
- کاربر می‌خواهد قبل از انتشار عمومی، تصمیم‌های معماری با دقت بررسی شوند؛ چون اصلاح معماری پس از مصرف‌کننده‌دارشدن کتابخانه می‌تواند پرهزینه باشد.
- هدف فعلی «تمام‌کردن و جمع‌کردن» پروژه است، نه افزودن abstractionهای بیشتر یا بازطراحی گسترده.
- پیشنهادهای فنی باید بر اساس کد و معماری واقعی موجود باشند، نه بر اساس الگوهای عمومی یا حدس.
- نباید صرفاً به دلیل شباهت دو قابلیت، آن‌ها را به یک abstraction مشترک تبدیل کرد.
- نباید کار را با promptهای بیش‌ازحد گسترده به انجین یا هوش مصنوعی سپرد؛ درخواست‌ها باید محدود، قابل ارزیابی و متناسب با هدف همان مرحله باشند.

کاربر سابقه کار با PHP، JavaScript، Kotlin، Java و C++ دارد و تجربه Full-stack، معماری نرم‌افزار و محصول را مطرح کرده است. ترجیح حرفه‌ای او نقش‌های مهندسی و ساخت سیستم است.

---

# 2. اصول طراحی و نگرانی‌های معماری

## 2.1. نزدیک‌بودن Query Builder به SQL

یکی از دلایل اصلی ساخت Query Builder اختصاصی، syntax نزدیک به SQL است. کاربر در مقایسه با کتابخانه‌هایی مانند Exposed گفته که syntax فعلی پروژه‌اش به چیزی که می‌خواهد نزدیک‌تر است.

در بحث مقایسه با Exposed، هدف این نبود که صرفاً از کتابخانه دیگری تقلید شود؛ هدف ارزیابی صادقانه این بود که آیا معماری و syntax فعلی ارزش ادامه‌دادن دارد یا بهتر بود از راهکار آماده استفاده شود.

## 2.2. پرهیز از abstraction زودهنگام

اصل مهم کاربر این است:

> شباهت ظاهری دو Node به‌تنهایی دلیل کافی برای ساخت یک abstraction عمومی مشترک نیست.

هر Schema Node باید نیازهای خودش را داشته باشد. اگر چند Node قابلیتی مشابه دارند، لزوماً نباید API، metadata یا ساختار والد آن‌ها به یک interface یا mixin عمومی تبدیل شود.

این اصل به معنی ممنوعیت استفاده مجدد از یک Node فرزند مستقل نیست. تفاوت این دو موضوع در بخش `IfExist` توضیح داده شده است.

## 2.3. ترس از انتشار با معماری اشتباه

یکی از نگرانی‌های اصلی کاربر این است که کتابخانه منتشر شود و مصرف‌کننده پیدا کند، اما بعداً مشخص شود تصمیم معماری اشتباه بوده است. در آن مرحله تغییر قراردادهای عمومی و ساختار داخلی می‌تواند دشوار و پرهزینه باشد.

بنابراین ارزیابی‌ها باید:
- دقیق و انتقادی باشند؛
- بین ایراد واقعی و ترجیح سلیقه‌ای تفاوت بگذارند؛
- ریسک تغییرات آینده را توضیح دهند؛
- از اطمینان‌بخشی بی‌پایه خودداری کنند.

---

# 3. Query Builder و CTE

کاربر درباره قابلیت CTE و اتصال رابطه‌ها/کوئری‌ها به یکدیگر صحبت کرده است.

ایده مطرح‌شده این بود که یک construct شبیه Relation در Query Builder بتواند:
- خودش مانند یک CTE یا query قابل استفاده باشد؛
- به CTE بزرگ‌تری متصل شود؛
- یا از یک ساختار استخراج و در query بزرگ‌تری قرار داده شود.

سؤال کاربر این بود که آیا کتابخانه‌های دیگر هم می‌توانند CTEای بسازند که به CTE دیگر متصل شود یا در query بزرگ‌تر قرار بگیرد.

در اطلاعات در دسترس، تصمیم نهایی یا طراحی کامل این قسمت ثبت نشده است؛ بنابراین نباید درباره پیاده‌سازی نهایی آن ادعایی مطرح شود.

---

# 4. ساختار ماژول‌ها و مرزبندی مسئولیت‌ها

## 4.1. Operation

ساختار کلی اعلام‌شده:

- `data_base:operation`
  - `query`
  - `schema`
  - `executor`

در بخش executor، مسیرهای اجرای Query و Schema وجود دارند؛ از جمله:

- `data_base:operation:query:executor`
- `data_base:operation:schema:executor`

در بسته‌ای که برای بخشی از کد Operation ارائه شده بود:

```kotlin
package gog.my_project.data_base_operation.executor.transaction
```

## 4.2. Config

مالکیت برخی تنظیمات به ماژول زیر منتقل شده است:

`data_base:operation:config`

مواردی که در این بخش قرار گرفته‌اند:

- `DialectQuery`
- `DatabasePlatformConfig`
- `ProductDatabaseVersion`

اما `DatabaseConfig` و API عمومی پیکربندی Manager در محل قبلی خود باقی می‌مانند.

نمونه پیکربندی:

```kotlin
DatabaseConfig(
    connection = DatabaseConnectionConfig(...),
    platform = DatabasePlatformConfig(dialect = DialectQuery.MY_SQL)
)
```

Manager پیکربندی را فراهم می‌کند و Operation Execution Context آن را در اختیار مصرف‌کننده‌های Query و Schema قرار می‌دهد.

---

# 5. Schema Nodeها و قابلیت `IfExist`

## 5.1. اصل استقلال Nodeها

در طراحی Schema، هر Node مستقل و self-contained در نظر گرفته شده است. هر Node می‌تواند اجزای اختصاصی خودش را داشته باشد، از جمله:

- API Interface
- AST Interface
- Metadata
- AST implementation
- Builder
- Capability یا Renderer
- Dialect registration

نباید صرفاً به دلیل وجود متدی با نام مشابه، API یا metadata والدها را به یک abstraction عمومی تبدیل کرد.

مثلاً وجود `ifExists()` در `DropColumn` و `DropTable` به‌تنهایی اثبات نمی‌کند که باید interface عمومی مانند `ISchemaIfExists` برای والدها ساخته شود.

## 5.2. تفاوت مهم: abstraction والدها در برابر Node فرزند مستقل

در ادامه بحث، کاربر هدف دقیق‌تری را روشن کرد:

- کاربر می‌خواهد یک Node مستقل با عنوان `IfExist` داشته باشد.
- Nodeهای والد واجد شرایط، آن را به‌عنوان child در AST خود متصل کنند.
- والدها همچنان API و AST مستقل خودشان را نگه دارند.
- این طراحی نباید با ساخت یک interface یا metadata عمومی برای والدها اشتباه گرفته شود.

پس دو مفهوم باید جدا بمانند:

1. **قابلیت عمومی در والدها:** مثلاً یک interface یا mixin که چند والد از آن ارث‌بری کنند. این راهکار لزوماً مطلوب نیست.
2. **Node فرزند مستقل و قابل استفاده مجدد:** یک Node به نام `IfExist` که والدهای واجد شرایط آن را به‌عنوان child نگه می‌دارند. این همان جهت موردنظر کاربر است.

## 5.3. دامنه احتمالی `IF EXISTS`

مواردی که برای بررسی مطرح شدند:

- DROP TABLE
- DROP COLUMN
- DROP INDEX
- DROP VIEW
- DROP DATABASE
- DROP SCHEMA
- DROP TRIGGER
- DROP PROCEDURE
- DROP FUNCTION
- DROP EVENT
- DROP SEQUENCE
- DROP TYPE
- DROP DOMAIN
- DROP ROLE
- DROP USER
- DROP OPERATOR
- DROP EXTENSION
- DROP MATERIALIZED VIEW
- DROP PACKAGE

این فهرست برای بررسی syntax و پشتیبانی Dialect بود، نه دستور ساخت Node برای همه موارد.

قاعده:
- فقط Nodeهایی که واقعاً در پروژه وجود دارند و از نظر SQL/Dialect واجد شرایط‌اند باید `IfExist` را به‌عنوان child بگیرند.
- نباید برای Nodeهای ناموجود یا مواردی که قابلیت در آن‌ها معنی ندارد، صرفاً به خاطر فهرست، پیاده‌سازی اضافه شود.
- `IF NOT EXISTS` قابلیت متفاوتی است و در این مرحله خارج از scope بود.

## 5.4. وضعیت پیاده‌سازی گزارش‌شده

در بررسی قبلی مشخص شده بود که پیاده‌سازی قبلی `ifExists` برای `DropColumn` و `DropTable` به شکل Boolean مستقل در metadata والد قرار گرفته بود.

کاربر تأکید کرد این مدل هدف او نیست و باید به Node مستقل `IfExist` تغییر کند که در AST والد به‌عنوان child قرار می‌گیرد.

برای اصلاح این قسمت، prompt محدودکننده‌ای تهیه شده بود. سپس کاربر اعلام کرد این قسمت تمام شده و وارد پاک‌سازی Schema Executor شده است.

**نکته:** در اطلاعات فعلی، گزارش نهایی انجین از تمام تغییرات انجام‌شده و کد نهایی `IfExist` موجود نیست. بنابراین نباید جزئیات پیاده‌سازی نهایی را حدس زد.

---

# 6. پاک‌سازی Schema Executor

## 6.1. اصل مسئولیت Schema Executor

اصل صریح کاربر:

> کتابخانه میزبان دیتابیس نیست، بلکه مصرف‌کننده SQL است و فقط باید SQL را اجرا کند؛ اگر خطایی وجود دارد، موتور SQL باید آن را به کتابخانه تحویل دهد، نه اینکه کتابخانه خودش خطاها را تحلیل یا مدیریت معنایی کند.

بر اساس این اصل، Schema Builder/AST و Renderer مسئول ساخت و تولید SQL هستند. Schema Executor نباید وضعیت دیتابیس را با introspection بررسی کند یا معنای خطاهای دیتابیس را خودش تفسیر کند.

## 6.2. مسئولیت‌هایی که باید حذف شوند

موارد زیر از Schema Executor خارج از مسئولیت تشخیص داده شدند:

- `SELECT VERSION()`
- `SHOW CREATE TABLE`
- استفاده از `INFORMATION_SCHEMA` برای بررسی وضعیت schema
- بررسی وجود Table یا Column قبل از اجرای DDL
- تحلیل MySQL error codeها
- تبدیل یک خطای SQL به Success
- انتخاب strategy بر اساس نسخه واقعی دیتابیس از طریق پرس‌وجو
- parser برای استخراج تعریف ستون از خروجی `SHOW CREATE TABLE`
- هرگونه introspection یا تحلیل وضعیت دیتابیس

## 6.3. مسئولیت‌هایی که باید باقی بمانند

- دریافت Schema Operation
- انتخاب Dialect از مسیر موجود Schema Renderer
- Render کردن SQL
- ارسال SQL برای اجرا از مسیر Operation
- استفاده از Scope موجود
- Connection و Transaction lifecycle از طریق Operation
- Root operation coordinator
- حفظ callbackها
- حفظ Query Info reporting
- انتقال صحیح Failure
- حفظ public API تا جای ممکن

## 6.4. محدودیت تغییرات در فاز پاک‌سازی

هدف این فاز، پاک‌سازی مسئولیت‌های اضافی بود؛ نه بازطراحی معماری.

بنابراین:
- refactor گسترده انجام نشود؛
- API جدید اضافه نشود؛
- ماژول‌های دیگر تغییر نکنند؛
- قرارداد عمومی تا حد ممکن حفظ شود؛
- فقط مسئولیت‌هایی که با اصل «اجرای SQL بدون introspection و تفسیر معنایی» مغایرند حذف شوند.

---

# 7. کد SchemaExecutionIntegration پس از سبک‌سازی

کد زیر در مکالمه ارسال شده بود:

```kotlin
package gog.my_project.data_base_schema.executor.integration

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools

/** Narrow module-integration entry point for Schema consumers that already own an Operation scope. */
object SchemaExecutionIntegration {
    private val dialectSelector = DialectSelector()

    @JvmStatic
    fun executeScoped(
        operation: ISchemaApi<*>,
        scope: OperationExecutionContext,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = try {
        val platform = scope.databasePlatformConfig
        val dialect = dialectSelector.select(platform.dialect, platform.targetDatabaseVersion)
        val sql = requireNotNull(dialect.render(operation.ast)) {
            "Schema renderer returned no SQL for the supplied operation"
        }

        onQueryInfo?.invoke(StringTools.formatSql(sql), emptyMap())
        scope.executeDdl(sql).latchFailure(scope)
    } catch (error: Error) {
        scope.failed<Boolean>(error)
        throw error
    } catch (exception: Exception) {
        scope.failed(exception)
    }

    private fun <T> ExecuteResult<T>.latchFailure(scope: OperationExecutionContext): ExecuteResult<T> =
        if (this is ExecuteResult.Failure) scope.failed(exception) else this
}
```

### برداشت از مسیر اجرا

1. `OperationExecutionContext` پیکربندی پلتفرم دیتابیس را فراهم می‌کند.
2. `DialectSelector` بر اساس dialect و target database version، Dialect مناسب را انتخاب می‌کند.
3. Schema Dialect از AST عملیات SQL می‌سازد.
4. Query Info callback با SQL فرمت‌شده و پارامترهای خالی فراخوانی می‌شود.
5. SQL از طریق `scope.executeDdl(sql)` اجرا می‌شود.
6. Failure به Scope گزارش می‌شود.

در این کد، `parameters` برای Schema خالی است. این به‌خودی‌خود به معنی نیاز Schema به Parameter Binding نیست؛ باید نیاز واقعی عملیات و قرارداد SQL آن بررسی شود.

---

# 8. کد SchemaExecutor و SchemaTransaction پس از سبک‌سازی

کد ارسال‌شده:

```kotlin
package gog.my_project.data_base_schema.executor.manager

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionCoordinator
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.executor.integration.SchemaExecutionIntegration
import gog.my_project.data_base_schema.renderer.manager.DialectSelector

/** Executes schema operations; transaction lifecycle is delegated to the shared Operation executor. */
class SchemaExecutor(
    queryExecutor: IQueryExecute? = null,
    private val dialectSelector: DialectSelector = DialectSelector(),
    transactionExecutor: OperationTransactionExecutor? = null,
    val databaseContext: DatabaseContext = DatabaseContext.legacyDefault(),
) {
    private val queryExecutor: IQueryExecute = queryExecutor ?: QueryExecute(databaseContext)
    private val transactionExecutor: OperationTransactionExecutor =
        transactionExecutor ?: OperationTransactionExecutor(databaseContext)

    constructor(
        queryExecutor: IQueryExecute,
        dialectSelector: DialectSelector,
        transactionExecutor: OperationTransactionExecutor,
    ) : this(queryExecutor, dialectSelector, transactionExecutor, DatabaseContext.legacyDefault())

    private val executionCoordinator = OperationExecutionCoordinator("root schema operation")

    fun <T> transaction(
        block: (SchemaTransaction) -> ExecuteResult<T>,
    ): ExecuteResult<T> = transactionExecutor.transaction(executionCoordinator) { scope ->
        block(SchemaTransaction(this, scope))
    }

    fun execute(
        operation: ISchemaApi<*>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) {
        val scopedQueryInfo = blockQueryInfo?.let { callback ->
            { sql: String, parameters: Map<String, Any?> -> callback(sql, parameters.toMutableMap()) }
        }
        var callbackDelivered = false
        var callbackResult: ExecuteResult<Boolean>? = null
        var callbackFailure: Throwable? = null
        val outcome = OperationExecutionExecutor(databaseContext).connection(executionCoordinator) { scope ->
            val result = SchemaExecutionIntegration.executeScoped(
                operation,
                scope,
                scopedQueryInfo,
            )
            callbackDelivered = true
            callbackResult = result
            try {
                blockExecute(result)
            } catch (failure: Throwable) {
                callbackFailure = failure
                throw failure
            }
            result
        }
        if (!callbackDelivered) {
            blockExecute(outcome)
        } else if (outcome is ExecuteResult.Failure && callbackResult !is ExecuteResult.Failure) {
            throw outcome.exception
        }
        callbackFailure?.let { throw it }
    }

    /** Executes already-rendered schema SQL using the low-level executor's normal owned-connection path. */
    fun executeRendered(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        withRootOperation(blockExecute) {
            executeRenderedOwned(query, blockExecute)
        }
    }

    private fun executeRenderedOwned(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        queryExecutor.executeTable(query, blockExecute)
    }

    private fun withRootOperation(
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        operation: () -> Unit,
    ) {
        val failure = IllegalStateException(
            "Root schema execution cannot run independently while a managed execution scope is active",
        )
        when (val admission = executionCoordinator.acquireRootOperation(failure)) {
            is OperationExecutionCoordinator.OperationAdmission.Rejected -> {
                try {
                    blockExecute(ExecuteResult.Failure(admission.failure))
                } catch (throwable: Throwable) {
                    executionCoordinator.recordRejectedCallbackThrowable(throwable)
                    throw throwable
                }
            }
            is OperationExecutionCoordinator.OperationAdmission.Allowed -> try {
                operation()
            } finally {
                admission.permit.release()
            }
        }
    }

    /** Executes a rendered schema operation through manager:execute on the supplied scope connection. */
    fun executeRendered(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        val rejection = scope.rejectOperation()
        if (rejection != null) {
            val failure = ExecuteResult.Failure(rejection)
            scope.recordResult(failure)
            blockExecute(failure)
            return
        }

        queryExecutor.executeTable(scope.connection, query) { result ->
            scope.recordResult(result)
            blockExecute(result)
        }
    }

    /** Executes an operation on an existing Operation scope without taking ownership of its lifecycle. */
    fun execute(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) {
        val scopedQueryInfo = blockQueryInfo?.let { callback ->
            { sql: String, parameters: Map<String, Any?> -> callback(sql, parameters.toMutableMap()) }
        }
        blockExecute(
            SchemaExecutionIntegration.executeScoped(
                operation,
                scope,
                scopedQueryInfo,
            ),
        )
    }
}

/** A transaction-bound schema executor. All calls share the transaction's Operation scope. */
class SchemaTransaction internal constructor(
    private val schemaExecutor: SchemaExecutor,
    private val scope: OperationExecutionScope,
) {
    fun execute(
        operation: ISchemaApi<*>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) = schemaExecutor.execute(operation, scope, blockExecute, blockQueryInfo)
}
```

## 8.1. نکات قابل مشاهده در کد

- `SchemaExecutor` یک `OperationExecutionCoordinator` دارد.
- Transaction از طریق `OperationTransactionExecutor` اجرا می‌شود.
- اجرای scoped از طریق `SchemaExecutionIntegration.executeScoped` انجام می‌شود.
- اجرای معمولی `execute` یک Operation connection scope می‌گیرد.
- اجرای Schema روی Scope موجود نیز پشتیبانی شده است.
- `SchemaTransaction` به Scope موجود متصل است و lifecycle مستقلی ایجاد نمی‌کند.
- در مسیر `executeRendered`، کد از `BuiltQuery` و `IQueryExecute` استفاده می‌کند.
- این مسیر low-level با مسیر اصلی `ISchemaApi` و Schema Renderer یکسان نیست و باید در بررسی بعدی مشخص شود دقیقاً چه کاربردی دارد و آیا قرارداد آن لازم است.

وجود `QueryExecute` در این کد به‌تنهایی اثبات نمی‌کند که Schema باید به Query Executor وابسته باشد. باید مسیر `executeRendered` و دلیل استفاده از `BuiltQuery` جداگانه بررسی شود.

---

# 9. Manager و Migration: مرزبندی عمومی API

این بخش با توضیح صریح کاربر اصلاح شد و یکی از مهم‌ترین تصمیم‌های معماری مکالمه است.

## 9.1. Migration به Manager داده می‌شود

Migrationها هنگام پیکربندی Database به Manager معرفی می‌شوند.

نمونه ساختار:

```kotlin
val manager: DatabaseManagerApi = DatabaseManager {
    database(mainDatabase) {
        config = config("MAIN", "main_example")
        migrations = mainDatabaseMigrations()
    }

    database(analyticsDatabase) {
        config = config("ANALYTICS", "analytics_example")
        migrations = analyticsDatabaseMigrations()
    }
}
```

سپس:

```kotlin
val main = manager[mainDatabase]
val mainMigrationResult = runMigration(main)
```

و اجرای Migration از طریق API مربوط به Database Handle انجام می‌شود:

```kotlin
database.migrate { result ->
    // ...
}
```

## 9.2. API عمومی مصرف‌کننده فقط Query را expose می‌کند

کاربر تأکید کرده که مصرف‌کننده عمومی Manager از طریق `connection()` و `transaction()` فقط Query API دریافت می‌کند.

مثال:

```kotlin
main.connection { executor ->
    selectCustomers(executor)
}

main.transaction { transaction ->
    selectCustomers(transaction)
}
```

در این API، `executor` و `transaction` برای مصرف‌کننده عمومی، Query API هستند.

**Schema از طریق `main.connection { ... }` یا `main.transaction { ... }` به مصرف‌کننده عمومی ارائه نمی‌شود.**

Schema فقط از مسیر مدیریت Migration در دسترس است.

این مرزبندی باید در همه تحلیل‌های بعدی حفظ شود.

## 9.3. مسئولیت Migration

Migration module مصرف‌کننده Operation و Schema است و دستورات مدیریتی Migration را مدیریت می‌کند.

دستورات مطرح‌شده:

| دستور | مسئولیت |
|---|---|
| `migrate()` | اجرای تمام Migrationهای اجرا‌نشده |
| `rollback()` | rollback آخرین Migration قابل rollback |
| `rollback(steps)` | rollback به تعداد مشخصی از مراحل |
| `rollbackTo(version)` | rollback تا نسخه مشخص |
| `status()` | نمایش وضعیت Migrationها |
| `history()` | نمایش تاریخچه اجرا |
| `pending()` | نمایش Migrationهای اجرا‌نشده |
| `refresh()` | بازسازی دیتابیس با rollback و سپس migrate، در صورت پشتیبانی و تأیید صریح |

`refresh()` یک عملیات مخرب بالقوه است و نباید بدون پشتیبانی و تأیید صریح انجام شود.

Migration config نمونه:

```kotlin
migrationConfig {
    migration<CreateManagerCustomers>()
}
```

## 9.4. رابطه Manager، Migration، Operation، Query و Schema

مدل مفهومی که از توضیحات کاربر به دست می‌آید:

- **Manager:** مدیریت Databaseها، پیکربندی، ثبت Migrationها و ارائه Database Handle.
- **Database Handle:** API عمومی اتصال و Transaction برای Query و API اجرای Migration.
- **Migration:** مصرف‌کننده Operation و ابزارهای Query و Schema برای اجرای مراحل Migration.
- **Schema:** تولید و اجرای عملیات DDL در مسیر Migration.
- **Query:** اجرای Queryهای عمومی و همچنین Queryهایی که در Migration لازم هستند.
- **Operation:** زیرساخت اجرای مشترک، Scope، Connection و Transaction lifecycle.

نکته کلیدی: نباید گفت مصرف‌کننده عمومی Manager مستقیماً Schema را از `connection()` یا `transaction()` دریافت می‌کند. این برداشت اشتباه است.

## 9.5. نمونه اجرای Migration

در یک Migration ممکن است مراحل مختلفی انجام شود:

1. ساخت Table با Schema.
2. درج داده اولیه با Query.
3. به‌روزرسانی داده با Query.
4. ساخت Index با Schema.
5. ذخیره اطلاعات Migration و History.

این مثال نشان می‌دهد Migration ممکن است به هر دو قابلیت Query و Schema نیاز داشته باشد.

اینکه تمام این مراحل در یک Scope یا Transaction مشترک اجرا شوند، باید با کد واقعی Migration و Operation تأیید شود؛ نباید صرفاً از مثال مفهومی، رفتار قطعی lifecycle را نتیجه گرفت.

همچنین بعضی دیتابیس‌ها، از جمله MySQL در برخی عملیات DDL، implicit commit دارند. بنابراین وجود Transaction در سطح کتابخانه تضمین نمی‌کند که همه DDLها در همه Dialectها rollbackپذیر باشند.

---

# 10. Operation Execution و Lifecycle

اطلاعات زیر از کدهای قبلاً ارائه‌شده درباره Operation در دسترس است.

## 10.1. مدل شکست DDL

```kotlin
enum class DdlFailureDisposition {
    FATAL,
    RECOVERABLE
}

fun interface DdlFailurePolicy {
    fun classify(failure: Throwable): DdlFailureDisposition
}

sealed interface DdlExecutionOutcome {
    data class Success(val value: Boolean) : DdlExecutionOutcome

    data class Failure(
        val exception: Throwable,
        val disposition: DdlFailureDisposition,
    ) : DdlExecutionOutcome
}
```

`OperationExecutionContext.executeDdlWithPolicy(...)` در حالت پیش‌فرض fail-closed عمل می‌کند: اگر پیاده‌سازی مناسب در دسترس نباشد، `UnsupportedOperationException` را از طریق `failed(...)` ثبت می‌کند و Failure از نوع Fatal برمی‌گرداند.

## 10.2. OperationExecutionCoordinator

Coordinator یک state machine دارد:

```text
IDLE
STARTING
ACTIVE
FINALIZING
```

و stateهای مهمی مانند:

- `activeRootOperations`
- `activeScope`
- `pendingFailure`
- `pendingFatal`

### رفتارهای مطرح‌شده

- `acquireRootOperation(rejection)` فقط در وضعیت `IDLE` اجازه می‌دهد.
- در `STARTING`، failure را به‌عنوان pending ثبت می‌کند.
- در `ACTIVE`، failure را در Scope جاری ثبت می‌کند.
- در `FINALIZING`، عملیات جدید را رد می‌کند.
- `tryStartScope(nestedFailure)` در برابر nested scope رفتار fail-fast دارد.
- Nested connection scope پشتیبانی نمی‌شود و خطای زیر ثبت می‌شود:

```text
Nested connection scopes are not supported
```

- شروع managed scope در حالی که یک ordinary root operation فعال است، رد می‌شود.
- `activate(scope)` failureهای pending و وضعیت fatal را به Scope فعال منتقل می‌کند.
- `seal(scope)`، `scope.end()` را اجرا کرده و state را به `FINALIZING` می‌برد.
- `finishScope()` وضعیت را به `IDLE` برمی‌گرداند.
- Root permit با `AtomicBoolean` تضمین می‌کند release دقیقاً یک‌بار انجام شود.

## 10.3. OperationExecutionExecutor

APIهای عمومی گزارش‌شده:

```kotlin
connection(block)
connection(coordinator, block)

transaction(block)
transaction(coordinator, block)
```

رفتار Constructorها:

- Constructor بدون آرگومان از `DatabaseContext.legacyDefault()` استفاده می‌کند.
- Constructor با `DatabaseContext` از `databaseContext::openConnection` و `databaseContext.platformConfig` استفاده می‌کند.
- Constructor قدیمی connectionFactory deprecated شده و از legacy default platform config استفاده می‌کند.

`OperationTransactionExecutor(connectionFactory, databasePlatformConfig)` مسئول اجرای Transaction است.

### رفتار بستن Connection

اگر بستن Connection شکست بخورد:
- Exception اصلی حفظ می‌شود؛
- خطای close به‌صورت suppressed اضافه می‌شود.

اگر فقط close شکست بخورد و commit/rollback انجام نشده باشد، Failure با این هشدار گزارش می‌شود:

```text
completed without commit or rollback… prior changes may already be durable, so do not retry blindly
```

معنای مهم این پیام این است که ممکن است تغییرات قبلی durable شده باشند؛ بنابراین retry کورکورانه امن نیست.

## 10.4. OperationExecutionContext و Scope

`OperationExecutionContext` یک API opaque و lifecycle-neutral برای Scope فعال است. هدف این است که Facadeهای Query و Schema مستقیماً JDBC را دریافت نکنند.

قابلیت‌های گزارش‌شده:

- `databasePlatformConfig`
- `executeDdl`
- `executeDdlWithPolicy`
- `executeRead`
- `failed`

مدل پارامتر:

```kotlin
OperationParameter(
    name = ...,
    value = ...,
    sqlTypeCode = ...
)
```

این پارامترها در داخل به `QueryParameterBinding` در `BuiltQuery` تبدیل می‌شوند.

اجرای Query داخلی از مسیرهایی مانند این انجام می‌شود:

```kotlin
QueryExecute().executeTable(...)
QueryExecute().executeSelect(...)
```

## 10.5. OperationQueryRows

`OperationQueryRows` یک wrapper برای خواندن ردیف‌ها با استفاده از نام ستون SQL است.

Getterهای گزارش‌شده:

- `getString`
- `getInt`
- `getLong`
- `getBoolean`
- `getDouble`
- `getBigDecimal`
- `getBytes`

Cursor wrapper پس از پایان callback نامعتبر می‌شود.

## 10.6. OperationExecutionScope

Scope موارد زیر را نگه می‌دارد:

- owner thread
- active flag
- `firstFailure`
- `fatalError`

قواعد:

- استفاده بعد از پایان Scope مجاز نیست.
- استفاده از threadی غیر از thread callback مجاز نیست.
- `Error` به‌عنوان fatal ثبت می‌شود.
- خطای classifier در DDL policy مدیریت می‌شود و failure اصلی به شکل suppressed حفظ می‌شود.

---

# 11. قابلیت‌های QueryExecutor و امکان استفاده در Schema

در گفت‌وگو، ابتدا چند قابلیت QueryExecutor به‌عنوان مواردی که شاید برای SchemaExecutor مفید باشند مطرح شدند:

1. Parameter Binding
2. `BuiltQuery`
3. Query Info Reporting
4. Execution Logging
5. Error Context
6. Dialect Resolution

اما پس از توضیح مرزبندی Manager و Migration، لازم شد این فهرست با دقت بیشتری بررسی شود.

## 11.1. Parameter Binding

Parameter Binding در درجه اول باید بر اساس نیاز واقعی هر نوع Operation بررسی شود.

Schema DDL فعلی در `SchemaExecutionIntegration` یک SQL string و پارامترهای خالی دارد:

```kotlin
scope.executeDdl(sql)
```

نباید صرفاً برای یکسان‌سازی با Query، Parameter Binding را به Schema تحمیل کرد.

اگر بعضی DDLها واقعاً به پارامتر نیاز داشته باشند، باید محدودیت‌های SQL و Dialect و JDBC بررسی شوند.

## 11.2. BuiltQuery

نباید `BuiltQuery` متعلق به Query را بدون بررسی به Operation عمومی منتقل کرد.

در کد SchemaExecutor یک مسیر `executeRendered(query: BuiltQuery, ...)` وجود دارد که از `IQueryExecute` استفاده می‌کند. باید مشخص شود:
- این overload چرا وجود دارد؛
- چه مصرف‌کننده‌ای دارد؛
- آیا واقعاً بخشی از Schema contract است؛
- آیا به دلیل legacy باقی مانده است؛
- آیا می‌توان آن را بدون تغییر API حذف یا محدود کرد.

در صورت نیاز به abstraction مشترک، ابتدا باید قرارداد واقعی اجرای statement مشخص شود؛ نه اینکه صرفاً نام `BuiltQuery` را به لایه بالاتر منتقل کنیم.

## 11.3. Query Info Reporting

گزارش SQL و پارامترها ممکن است قابلیت مشترک اجرای Operation باشد، اما باید ownership و محل فراخوانی callback بررسی شود.

در Schema فعلی:
- callback از طریق `onQueryInfo` اجرا می‌شود؛
- SQL با `StringTools.formatSql` فرمت می‌شود؛
- map پارامترها خالی است؛
- در SchemaExecutor، map به `MutableMap` تبدیل می‌شود تا با API callback فعلی سازگار بماند.

## 11.4. Execution Logging

Logging می‌تواند در سطح اجرای مشترک قرار بگیرد، ولی نباید بدون بررسی قرارداد فعلی Query و Schema به یک abstraction جدید تبدیل شود.

## 11.5. Error Context

لایه مشترک می‌تواند context اجرایی را به خطا اضافه کند، اما باید:
- Exception اصلی حفظ شود؛
- Failure به Success تبدیل نشود؛
- خطای واقعی دیتابیس تفسیر معنایی نشود؛
- context اضافی باعث پنهان‌شدن علت اصلی نشود.

## 11.6. Dialect Resolution

Dialect Resolution در حال حاضر به Rendererهای Query و Schema مربوط است.

هر دو می‌توانند `DatabasePlatformConfig` را از Operation دریافت کنند، اما این به معنی آن نیست که Query و Schema باید یک Renderer مشترک داشته باشند.

انتخاب Dialect باید با ساختار فعلی هر Renderer و نیازهای خاص آن سازگار بماند.

---

# 12. وضعیت کلی Query subsystem از اطلاعات قبلی

در خلاصه مکالمه‌های قدیمی‌تر، Query subsystem شامل قابلیت‌هایی مانند موارد زیر گزارش شده بود:

- SELECT
- INSERT
- UPDATE
- DELETE
- JOIN
- WHERE
- GROUP BY
- ORDER BY
- LIMIT
- OFFSET
- Aggregate functions
- CTE
- AST → Renderer → Executor

همچنین ساختارهایی مانند Query consumers، `QueryRow`، `QueryBuilderSelection`، `ExecuteResult` extensions، parameter rendering، materialization و execution scopes در کدهای قبلی مطرح شده بودند.

`IQueryBuilderExecutor` و `QueryBuilderExecutor` مسئولیت‌های Query-specific دارند. مواردی مانند materialization و parameter rendering نباید صرفاً به دلیل وجود در Query به Schema یا Operation عمومی منتقل شوند.

در اطلاعات قبلی، Query Executor شامل بخش‌هایی مانند:
- connection/transaction scope API
- query consumers
- render SQL
- first/get
- materialization
- اجرای SELECT/INSERT/UPDATE/DELETE
- `executeOut`
- OperationExecutionExecutor
- TransactionGuard/RootExecutionCoordinator
- Dialect و QueryParameterRendering
- legacy/identity-aware rendering
- BoundQuery
- QueryExecute
- scope handling و error handling

بود.

این موارد یک فهرست سطح بالا هستند و برای داوری درباره طراحی فعلی، کدهای واقعی باید دوباره بررسی شوند.

---

# 13. سابقه تصمیم‌های Migration و Versioning

در اطلاعات بازیابی‌شده از گفت‌وگوهای قبلی‌تر:

- پروژه Migration با الگویی شبیه Laravel مطرح شده بود.
- در یک مقطع، Migration subsystem عملاً کامل، مستند و تست‌شده تلقی شده بود.
- بخش‌هایی مانند identity/registration/history/migrator/state store مطرح شده بودند.
- در تصمیم معماری versioning، نسخه به Migration/Schema تعلق می‌گیرد (`from → to`)، نه به هر operation.
- `MigrationExecutor` مسئول اجرای یک Migration و `MigrationRunner` مسئول حرکت در مسیر نسخه‌ها معرفی شده بودند.
- MySQL محور اصلی پیاده‌سازی آن مقطع بوده است.
- SQLite هنوز پیاده‌سازی نشده بود و compatibility target رسمی مشخص نشده بود.

این موارد از خلاصه گفت‌وگوهای قدیمی‌تر بازیابی شده‌اند و لزوماً توصیف وضعیت فعلی branch پروژه نیستند.

---

# 14. مسیر پیشنهادی بررسی بعدی

با توجه به هدف فعلی کاربر، ادامه کار باید مرحله‌ای و محدود باشد.

## مرحله اول: تثبیت مسیر اجرای مشترک Operation

ابتدا کد کامل و فعلی این بخش‌ها بررسی شود:

- `OperationExecutionContext`
- `OperationExecutionScope`
- `OperationExecutionExecutor`
- `OperationTransactionExecutor`
- `OperationExecutionCoordinator`

هدف:
- مشخص‌شدن قرارداد واقعی Scope؛
- تعیین مسئولیت Connection و Transaction؛
- بررسی Failure propagation؛
- تشخیص اینکه کدام قابلیت‌ها واقعاً مشترک‌اند و کدام صرفاً Query-specific یا Schema-specific هستند.

## مرحله دوم: بررسی مسیر اجرای Query

مسیر واقعی از Query Builder تا اجرای SQL بررسی شود، به‌خصوص:
- render؛
- parameter binding؛
- `BuiltQuery`؛
- `QueryExecute`؛
- Scope و Transaction؛
- Query Info و Logging؛
- Failure handling.

هدف این نیست که Query را بازنویسی کنیم؛ هدف فهم دقیق قرارداد فعلی است.

## مرحله سوم: بررسی مسیر اجرای Schema

مسیر Schema از `ISchemaApi` تا Renderer و `SchemaExecutionIntegration` و سپس Operation بررسی شود.

به‌صورت جداگانه overloadهای `executeRendered(BuiltQuery, ...)` بررسی شوند تا دلیل وابستگی به QueryExecutor روشن شود.

## مرحله چهارم: بررسی Migration به‌عنوان مصرف‌کننده

Migration باید از نظر ترتیب و ownership عملیات بررسی شود:
- چگونه Schema را مصرف می‌کند؛
- چگونه Query را مصرف می‌کند؛
- Scope را از کجا می‌گیرد؛
- چه کسی Transaction را آغاز و پایان می‌دهد؛
- Migration metadata/history چگونه ثبت می‌شود؛
- رفتار rollback و failure چیست.

## مرحله پنجم: تصمیم درباره قابلیت‌های مشترک

فقط پس از مشاهده مسیرهای واقعی، درباره این موارد تصمیم گرفته شود:
- Parameter Binding
- BuiltQuery یا قرارداد statement مشترک
- Query Info
- Logging
- Error Context
- Dialect Resolution

هیچ abstraction جدیدی صرفاً برای «تمیزترشدن» ظاهر کد اضافه نشود. هر abstraction باید یک نیاز واقعی و مصرف‌کننده مشخص داشته باشد.

---

# 15. قواعد همکاری در ادامه توسعه

برای ادامه کار روی این پروژه، قواعد زیر باید رعایت شوند:

1. **اول کد، بعد نظر:** تا وقتی کد واقعی بخش مورد بحث دیده نشده، درباره طراحی آن حکم قطعی صادر نشود.
2. **محدوده محدود:** هر مرحله فقط یک مسئولیت یا ماژول مشخص را تغییر دهد.
3. **بدون refactor ناخواسته:** درخواست پاک‌سازی به بازطراحی معماری تبدیل نشود.
4. **حفظ API:** تا وقتی تغییر API صریحاً هدف نیست، public API حفظ شود.
5. **تفکیک واقعیت از پیشنهاد:** تصمیم قطعی کاربر، وضعیت فعلی کد، پیشنهاد دستیار و سؤال باز باید جدا مشخص شوند.
6. **بدون abstraction زودهنگام:** اشتراک واقعی باید اثبات شود.
7. **بدون تفسیر معنایی خطای دیتابیس در Schema Executor:** موتور SQL مرجع تشخیص خطای SQL است.
8. **حفظ مرز Manager:** API عمومی `connection()` و `transaction()` در Database Handle فقط Query را expose می‌کند؛ Schema از مسیر Migration مصرف می‌شود.
9. **Migration مصرف‌کننده Operation است:** Migration مسئول مدیریت عملیات Migration است و از زیرساخت Operation و قابلیت‌های Query/Schema استفاده می‌کند.
10. **احتیاط در DDL و Transaction:** رفتار rollback و implicit commit به دیتابیس و Dialect وابسته است.
11. **پرهیز از promptهای بزرگ برای انجین:** prompt باید دقیقاً مشخص کند چه چیزی تغییر کند، چه چیزی نباید تغییر کند و معیار پایان کار چیست.
12. **نقد صریح:** اگر تصمیمی ریسک معماری دارد، مستقیم و با دلیل گفته شود؛ صرفاً برای دلگرمی تأیید نشود.

---

# 16. موارد باز و اطلاعاتی که هنوز باید از کد تأیید شوند

این موارد در اطلاعات فعلی پاسخ قطعی ندارند:

- پیاده‌سازی نهایی Node مستقل `IfExist` و فهرست دقیق والدهایی که آن را به‌عنوان child گرفته‌اند.
- وضعیت فعلی تست‌های Schema Executor پس از حذف introspection.
- کاربرد دقیق overloadهای `executeRendered(BuiltQuery, ...)` در SchemaExecutor.
- اینکه چرا SchemaExecutor در این مسیر از `IQueryExecute` استفاده می‌کند و آیا این مسیر هنوز لازم است.
- قرارداد کامل و فعلی `OperationExecutionContext` و `OperationExecutionScope`.
- اینکه Query و Schema در Migration دقیقاً چگونه یک Scope را به اشتراک می‌گذارند.
- مالکیت دقیق Transaction در جریان Migration.
- قرارداد دقیق Error Context، Logging و Query Info در همه لایه‌ها.
- وضعیت فعلی پشتیبانی Dialectها و نسخه‌های دیتابیس.
- وضعیت فعلی CTE و امکان ترکیب Relationها/CTEها.

برای پاسخ به این موارد باید کد جاری یا گزارش تست/تغییرات مربوطه دیده شود.

---

# 17. جمع‌بندی نهایی

تصویر فعلی معماری بر اساس توضیحات کاربر چنین است:

```text
DatabaseManager
    |
    +-- Database Handle
    |      |
    |      +-- connection { Query API }
    |      |
    |      +-- transaction { Query API }
    |      |
    |      +-- migrate { Migration management }
    |
    +-- Migration registration/configuration


Migration
    |
    +-- Operation execution infrastructure
    |      |
    |      +-- Connection / Scope / Transaction lifecycle
    |      +-- Failure propagation
    |      +-- Database platform config
    |
    +-- Query capabilities
    |
    +-- Schema capabilities
           |
           +-- Schema AST
           +-- Schema Renderer / Dialect
           +-- SQL execution through Operation
```

این نمودار مفهومی است و جایگزین بررسی dependency graph و کد واقعی نیست.

مهم‌ترین نکته این است که پروژه قرار نیست یک ORM یا میزبان دیتابیس باشد. کتابخانه Query و Schema را می‌سازد و SQL را از مسیرهای تعریف‌شده اجرا می‌کند. Manager API عمومی و Migration API را مدیریت می‌کند؛ Migration برای انجام عملیات تغییر ساختار و داده از Query، Schema و Operation استفاده می‌کند.

در ادامه، اولویت منطقی این نیست که قابلیت‌های بیشتری به Operation اضافه کنیم. اول باید مسیر واقعی اجرای Query، Schema و Migration کنار هم دیده شود و فقط اشتراک‌های واقعی استخراج شوند.
