# Connection و Transaction در Operation

Operation مالک بازکردن و بستن JDBC Connection و اجرای callback است. از APIهای `DatabaseHandleApi.connection {}` و `DatabaseHandleApi.transaction {}` برای کار معمول برنامه استفاده کنید؛ این APIها executor مربوط به QueryBuilder را به callback می‌دهند، نه Connection خام. executor دریافتی فقط در همان callback معتبر است.

## انتخاب بین Connection و Transaction

| API | رفتار | مناسب برای |
|---|---|---|
| `connection { executor -> ... }` | یک Connection باز می‌کند، callback را روی همان Connection اجرا می‌کند و در پایان Connection را می‌بندد. `commit()`، `rollback()` یا تغییر `autoCommit` انجام نمی‌دهد. | اجرای چند Query با یک Connection، بدون مرز تراکنش مدیریت‌شده توسط Operation. |
| `transaction { executor -> ... }` | یک Connection باز می‌کند، `autoCommit` را خاموش می‌کند، callback را اجرا می‌کند و در پایان بر اساس نتیجه commit یا rollback می‌کند؛ سپس Connection را می‌بندد. | مجموعه‌ای از عملیات که باید در یک تراکنش دیتابیس موفق یا rollback شوند. |

`connection {}` و `transaction {}` هر دو `ExecuteResult<T>` برمی‌گردانند؛ تفاوت اصلی‌شان مرز commit/rollback است. صرف استفاده از Connection Scope، عملیات را اتمیک نمی‌کند.

## استفاده‌ی معمول از DatabaseHandle

Handle را از Registration مدیریت‌شده بگیرید. هر فراخوانی یک Operation مستقل است:

در نمونه‌ی زیر `manager` و `databaseKey` از Registration برنامه می‌آیند و `logger` logger برنامه است؛ `operation_demo` را با جدولی جایگزین کنید که در دیتابیس شما وجود دارد.

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_query.ast.enums.DataType

val database: DatabaseHandleApi = manager[databaseKey]

val rowsResult = database.connection { executor ->
    executor.queryBuilderSelect { query ->
        query.select {
            addColumn {
                column { columnSelector("id") }
                alias("id")
                execute(DataType.INT)
            }
        }
        query.from { table("operation_demo") }
    }.execute()
}

when (rowsResult) {
    is ExecuteResult.Success -> {
        rowsResult.result.orEmpty().forEach { row ->
            println(row.getValue("id"))
        }
    }
    is ExecuteResult.Failure -> {
        logger.error("Read failed", rowsResult.exception)
    }
}
```

در این نمونه `operation_demo` باید از قبل در دیتابیس وجود داشته باشد. QueryBuilder از executor داخل callback استفاده می‌کند؛ همان executor چند Query متوالی را روی Connection همان Scope اجرا می‌کند.

در مسیر Database Manager، callback و cleanup هر فراخوانی داخل lease همان عملیات اجرا می‌شوند.

برای Transaction، تمام Queryهایی را که باید یک واحد کاری باشند در یک callback قرار دهید:

`insertOrder` و `reserveInventory` در این نمونه توابع برنامه‌اند؛ هر دو باید از executor ورودی استفاده کنند و یک `ExecuteResult` برگردانند.

```kotlin
val result = database.transaction { tx ->
    val inserted = insertOrder(tx) // تابع برنامه؛ ExecuteResult برمی‌گرداند
    when (inserted) {
        is ExecuteResult.Failure -> inserted
        is ExecuteResult.Success -> reserveInventory(tx)
    }
}

when (result) {
    is ExecuteResult.Success -> println("Transaction committed: ${result.result}")
    is ExecuteResult.Failure -> logger.error("Transaction failed", result.exception)
}
```

`insertOrder` و `reserveInventory` باید از همان `tx` استفاده کنند و نتیجه‌ی واقعی خود را برگردانند. اگر یکی از Queryها Failure برگرداند یا callback Exception پرتاب کند، Operation تراکنش را rollback می‌کند. Exception عادی به `ExecuteResult.Failure` تبدیل می‌شود؛ `Error` پس از تلاش برای rollback و cleanup دوباره پرتاب می‌شود.

اگر callback پس از مشاهده‌ی Failure ادامه پیدا کند و در پایان `Success` برگرداند، Failure ثبت‌شده در Scope پاک نمی‌شود: نتیجه همچنان Failure است و Transaction commit نمی‌شود. معمولاً پس از Failure همان‌جا نتیجه را برگردانید.

## ترتیب Lifecycle

### Connection Scope

```text
فتح Connection
    → ساخت Scope و executor محدود به همان Scope
    → اجرای callback و Queryها
    → بستن Scope و Connection
    → برگرداندن ExecuteResult
```

Connection Scope، Transaction شروع نمی‌کند و خودش commit یا rollback نمی‌زند. اگر Connection بستن آن شکست بخورد، Operation Failure گزارش می‌کند؛ اگر callback موفق شده بود، پیام خطا هشدار می‌دهد که تغییرات قبلی ممکن است قبلاً durable شده باشند. پس از چنین خطایی فرض نکنید که همه‌چیز rollback شده است.

Connection Scope مقدار `autoCommit` را نمی‌خواند یا تغییر نمی‌دهد. اگر یک Connection provider سفارشی Connectionای با Transaction خارجی و باز برگرداند، Operation برای آن مرز commit/rollback تعریف نمی‌کند؛ Connectionهای تحت کنترل Transaction خارجی را به این API نسپارید.

### Transaction Scope

```text
فتح Connection
    → Connection.autoCommit = false
    → اجرای callback و Queryها
       ├─ موفقیت بدون Failure ثبت‌شده → commit
       └─ Failure یا Exception → rollback
    → بستن Connection
    → برگرداندن ExecuteResult یا پرتاب Error
```

Connection مربوط به Transaction توسط Operation از factory می‌گیرد و می‌بندد. برنامه نباید در callback خودش `commit()`، `rollback()`، `close()` یا تغییر `autoCommit` انجام دهد؛ callback فقط با Queryها کار می‌کند. این API برای Connectionای است که Operation lifecycle آن را در اختیار دارد، نه Connection قرضی که مالک دیگری آن را مدیریت می‌کند.

## خطاهای Transaction و نتیجه

- `ExecuteResult.Success(value)`: callback موفق شده و Failure در Scope ثبت نشده است؛ Operation commit را انجام می‌دهد. فیلد `result` می‌تواند `null` باشد.
- `ExecuteResult.Failure(exception)`: Operation rollback را انجام می‌دهد و Failure را نگه می‌دارد.
- Exception عادی که از callback بیرون بیاید به Failure تبدیل می‌شود و rollback/cleanup انجام می‌گیرد.
- `Error` با همان هویت پس از cleanup پرتاب می‌شود؛ خطای rollback یا close طبق precedence موجود می‌تواند به‌صورت خطای ثانویه حفظ شود یا Error cleanup را در اولویت قرار دهد.
- اگر خود JDBC `commit()` خطا بدهد، `OperationTransactionCommitException` برگردانده می‌شود؛ rollback تلاش می‌شود، اما نتیجه‌ی commit ممکن است نامعلوم باشد.
- اگر commit موفق شود ولی بستن Connection شکست بخورد، `OperationTransactionCompletionException` با `committed = true` برمی‌گردد. این وضعیت را با Transaction commit‌نشده یکی نگیرید و کورکورانه همان عملیات را تکرار نکنید.

`ExecuteResult` نتیجه‌ی موردنیاز برنامه است؛ صرفاً Exception پرتاب‌نشدن به معنی commit موفق نیست. همیشه Failure نتیجه را بررسی کنید.

## Transaction فرزند روی Connection Scope

در سطح پایین‌تر، `OperationExecutionScope.transaction {}` می‌تواند یک Transaction فرزند روی Connection متعلق به Scope والد اجرا کند. این مسیر Connection دوم باز نمی‌کند؛ Transaction فرزند `autoCommit` قبلی را می‌خواند، آن را برای تراکنش تغییر می‌دهد، بعد از commit/rollback مقدار قبلی را برمی‌گرداند و Connection را برای Scope والد باز می‌گذارد.

```kotlin
val result = database.withOperationScope { parent ->
    parent.transaction { child ->
        child.executeRead("SELECT 1 AS value") { rows ->
            if (rows.next()) rows.getInt("value") else null
        }
    }
}
```

برای این نمونه، `withOperationScope` را از `gog.my_project.data_base_manager.facade` import کنید. این extension فقط روی Handle مدیریت‌شده‌ی پروژه Scope می‌دهد؛ پیاده‌سازی سفارشی `DatabaseHandleApi` که این قابلیت را نداشته باشد Failure می‌دهد.

Transaction فرزند فقط وقتی مجاز است که Connection والد از قبل در Transaction دیگری نباشد. Nested Transaction و Savepoint پشتیبانی نمی‌شوند. چند Transaction فرزند می‌توانند به‌ترتیب روی همان Connection اجرا شوند؛ اما اگر یکی Failure دهد، Failure پس از پایان rollback و بازگرداندن وضعیت Connection به والد منتقل می‌شود و والد دیگر نمی‌تواند آن را با `Success` پاک کند.

این قابلیت سطح پایین را با فراخوانی دوباره‌ی `database.transaction {}` یا `database.connection {}` از داخل callback اشتباه نگیرید. Scopeهای ریشه تو‌در‌تو پشتیبانی نمی‌شوند و Operation پیش از بازکردن Connection دوم آن‌ها را رد می‌کند.

## عمر و مالکیت Scope

- callbackها synchronous هستند و Scope به همان threadای تعلق دارد که callback را اجرا کرده است.
- executor یا Scope را در متغیر بلندعمر ذخیره نکنید، از callback خارج نکنید و روی thread دیگری به کار نبرید.
- بعد از پایان callback، Scope منقضی می‌شود و استفاده‌ی دوباره رد می‌شود.
- داخل callback از همان executor ارائه‌شده استفاده کنید؛ ساختن executor ریشه‌ی دیگری برای دورزدن Scope یا اجرای Query پس از پایان آن پشتیبانی نمی‌شود.
- Connection خام از `DatabaseHandleApi` افشا نمی‌شود؛ Operation مالک lifecycle و cleanup باقی می‌ماند.
- `OperationExecutionScope` برای سازگاری یک property قدیمی و Deprecated به نام `connection` دارد؛ در کد جدید از آن استفاده نکنید و برای Query از `executeRead`، `executeSelect`، `executeInsert`، `executeUpdate`، `executeDelete` یا `executeDdl` استفاده کنید.

## چه زمانی Connection یا Transaction Scope تو‌در‌تو رد می‌شود؟

یک Operation ریشه را در callback خودش دوباره آغاز نکنید:

```kotlin
database.connection { executor ->
    // نادرست: شروع Operation ریشه‌ی دیگری در callback فعال
    database.transaction { nested ->
        ExecuteResult.Success(Unit)
    }
    ExecuteResult.Success(Unit)
}
```

Operation از ورود ریشه‌ی تو‌در‌تو جلوگیری می‌کند؛ نتیجه‌ی تلاش نامعتبر Failure می‌شود و Failure ممکن است روی Operation والد هم latch شود. برای چند Query از executor جاری استفاده کنید. برای Transaction فرزند در مسیر low-level از `OperationExecutionScope.transaction {}` استفاده کنید؛ برای یک Transaction مستقل، `database.transaction {}` را از بیرون هر callback فعال فراخوانی کنید.

## محدوده‌ی اتمیک‌بودن

Operation lifecycle مربوط به Connection و Transaction را مدیریت می‌کند؛ اما rollbackپذیری و durability نهایی به قابلیت دیتابیس، نوع statement و تنظیم Connection بستگی دارد. به‌خصوص صرف قرارگرفتن DDL در `transaction {}` تضمین نمی‌کند که همه‌ی DDLها در همه‌ی دیتابیس‌ها با rollback برگردند. برای DDL، محدودیت‌های همان دیتابیس را جداگانه بررسی کنید.

## آزمون‌ها و پیاده‌سازی

رفتار Connection در `OperationExecutionExecutorTest` و رفتار commit/rollback، خطاهای callback و cleanup و Transaction فرزند در `OperationTransactionExecutorTest` پوشش داده شده است. قراردادهای عمومی مصرف‌کننده در `DatabaseHandleApi` آمده و lifecycle در `OperationExecutionExecutor`, `OperationTransactionExecutor` و `OperationExecutionScope` پیاده شده است.

## راهنمای مرتبط

- [مشاهده‌ی رویدادهای Operation](../events/README.md)
- [راهنمای QueryBuilder](../../query-builder/README.md)
