# Query Builder

این بخش APIهای مشترک Query Builder و سپس مثال‌های نسخه‌های 1.0، 2.0، 3.0 و 3.1 را شرح می‌دهد. قواعد Metadata نیز در [Rules](rules.md) آمده‌اند و به دو بخش مستقل **Model** و **Query** تقسیم شده‌اند. INSERT، UPDATE و DELETE داده را تغییر می‌دهند؛ آن‌ها را فقط روی دیتابیس توسعه یا آزمایشی اجرا کنید.

<a id="query-builder-connection"></a>
## Connection

<a id="query-builder-connection-usage"></a>
### ارتباط‌گیری

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
### روش استخراج

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
### نتیجه

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

## Examples

مثال‌ها در صفحهٔ [فهرست نسخه‌های Query Builder](examples/README.md) و فایل‌های جداگانهٔ نسخه‌های 1.0، 2.0، 3.0 و 3.1 آمده‌اند.
