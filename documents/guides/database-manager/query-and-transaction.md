# Connection و Transaction

هر دو callback یک `IQueryBuilderExecutor` scoped می‌گیرند. Queryهای یک callback باید از همان executor استفاده کنند؛ نگه‌داشتن executor برای بعد از خروج از callback یا استفاده از executor ریشه برای دور زدن scope مجاز نیست.

```kotlin
import gog.my_project.data_base_query.ast.enums.DataType

val result = database.connection { executor ->
    executor.queryBuilderSelect { query ->
        query.select {
            addColumn {
                column { columnSelector("id") }
                alias("customer_id")
                execute(DataType.INT)
            }
        }
        query.from { table("manager_customers") }
    }.execute()
}
```

`connection` یک Connection Scope غیرتراکنشی است؛ چند Query داخل callback از یک connection مشترک استفاده می‌کنند و Operation آن را بعد از پایان callback می‌بندد. این مسیر commit یا rollback تراکنشی انجام نمی‌دهد.

```kotlin
val result = database.transaction { tx ->
    tx.queryBuilderSelect { query ->
        query.select {
            addColumn {
                column { columnSelector("id") }
                alias("customer_id")
                execute(DataType.INT)
            }
        }
        query.from { table("manager_customers") }
    }.execute()
}
```

`transaction` از transaction scope متعلق به Operation استفاده می‌کند: موفقیت commit می‌شود و Failure باعث rollback می‌شود. در هر دو متد، `ExecuteResult.Failure` حامل Throwable اصلی است؛ Exception معمولی در مسیر handle به Failure تبدیل می‌شود و `Error` دوباره پرتاب می‌شود. رفتار جزئی خطاهای cleanup تابع قرارداد Operation transaction است.

برای بررسی Failure آن را موفقیت یا مقدار پیش‌فرض نکنید:

```kotlin
when (result) {
    is ExecuteResult.Success -> println("Query completed")
    is ExecuteResult.Failure -> throw result.exception
}
```
