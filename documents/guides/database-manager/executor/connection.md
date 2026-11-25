# اجرای کار با Connection

`DatabaseHandleApi.connection { ... }` یک Connection Scope غیرتراکنشی از مسیر Operation اجرا می‌کند و executor همان Scope را به callback می‌دهد:

```kotlin
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.executor.result.QueryRow

val result: ExecuteResult<List<QueryRow>> = database.connection { executor ->
    executor.queryBuilderSelect { query ->
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

نام جدول و ستون‌ها باید در پایگاه دادهٔ انتخاب‌شده وجود داشته باشند. چند Query که در callback با همین `executor` اجرا شوند از همان Connection استفاده می‌کنند. پس از بازگشت callback، Operation Scope را می‌بندد و منابع را آزاد می‌کند.

این API قرارداد commit/rollback تراکنشی ارائه نمی‌کند. اگر چند تغییر باید به‌عنوان یک واحد موفق یا ناموفق مدیریت شوند، از `transaction { ... }` استفاده کنید.

نتیجه را بررسی کنید:

```kotlin
when (result) {
    is ExecuteResult.Success -> println("Rows: ${result.result?.size ?: 0}")
    is ExecuteResult.Failure -> throw result.exception
}
```

`executor` فقط تا پایان callback معتبر است. آن را در property، collection یا coroutine برای استفادهٔ بعدی ذخیره نکنید و برای Queryهای این callback از executor دیگری استفاده نکنید. Connection متعلق به Operation است؛ آن را از Handle مطالبه یا دستی نبندید.

برای اجرای یک Query منفرد از Handle، همچنان همین Scope صریح لازم است. ایجاد Handle با `manager[key]` به‌تنهایی Connection باز نمی‌کند؛ عملیات هنگام فراخوانی `connection` آغاز می‌شود.

برای اجرای چند Stage روی یک Connection مشترک، [راهنمای `connectionPipeline`](connection_pipeline.md) را ببینید.
