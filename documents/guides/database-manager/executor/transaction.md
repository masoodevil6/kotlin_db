# اجرای کار با Transaction

`DatabaseHandleApi.transaction { ... }` چند Query را در یک Transaction Scope متعلق به Operation اجرا می‌کند. callback یک executor به نام دلخواه می‌گیرد؛ همهٔ Queryهای آن Transaction باید با همان executor اجرا شوند.

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.executor.result.QueryRow

val result: ExecuteResult<List<QueryRow>> = database.transaction { tx ->
    tx.queryBuilderSelect { query ->
        query.select {
            addColumn {
                column { columnSelector("id") }
                alias("customer_id")
                execute(DataType.INT)
            }
            addColumn {
                column { columnSelector("display_name") }
                alias("display_name")
                execute(DataType.STRING)
            }
        }
        query.from { table("customers") }
    }.execute()
}
```

Operation پس از موفقیت callback Transaction را commit می‌کند. شکست Query یا بازگشت `ExecuteResult.Failure` باعث rollback طبق قرارداد Transaction می‌شود. اگر callback Exception معمولی پرتاب کند، نتیجه به Failure تبدیل می‌شود و cleanup تراکنش انجام می‌گیرد؛ `Error` پس از cleanup با همان مسیر fatal دوباره پرتاب می‌شود.

برای چند تغییر وابسته، همه را در همان callback قرار دهید و نتیجهٔ هر مرحله را بررسی کنید. با اولین Failure، آن را برگردانید تا مسیر Transaction بتواند rollback را انجام دهد؛ Query را با نادیده گرفتن Failure ادامه ندهید. جزئیات cleanup و خطاهای ثانویه تابع قرارداد Operation است.

executor فقط در طول callback معتبر است. Transaction یا Connection دیگری را از داخل همین callback باز نکنید و executor را بیرون callback نگه ندارید. Operation مالک آغاز، commit/rollback و بستن منابع است.

اگر لازم است یک عملیات Connection غیرتراکنشی باشد، راهنمای [Connection](connection.md) را ببینید. API سطح Operation در [راهنمای Connection و Transaction](../../operation/connection_transaction/README.md) شرح داده شده است.

برای اجرای چند Stage در یک Transaction مشترک، [راهنمای `transactionPipeline`](transaction_pipeline.md) را ببینید.
