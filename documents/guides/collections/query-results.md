# استفاده از Collection در نتیجه‌های Query

`executeAs { row -> ... }` در Query Builder ردیف‌های SELECT را به مقادیر typed تبدیل می‌کند و نتیجه را به شکل `ExecuteResult<ICollection<T>>` می‌دهد. برای جزئیات ساخت Query و mapper، [راهنمای نتیجهٔ Query Builder](../query-builder/README.md#query-builder-result) را ببینید.

```kotlin
import gog.my_project.collection.ICollection
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.executor.result.toCollection
import gog.my_project.data_base_query.executor.result.toList

data class UserView(val id: Int, val name: String?)

// فرض شده selectQuery یک SELECT ساخته‌شده با Query Builder است.
val result: ExecuteResult<ICollection<UserView>> = queryManager
    .queryBuilder(selectQuery)
    .executeAs { row ->
        UserView(
            id = row.getValue("user_id") as Int,
            name = row.getValue("user_name") as String?,
        )
    }

val users: ICollection<UserView> = result.toCollection()
val userList: List<UserView> = result.toList()
```

این extensionها Query را دوباره اجرا نمی‌کنند. `toCollection()` همان Collection داخل Success را برمی‌گرداند؛ `toList()` آن را به List تبدیل می‌کند. اگر نتیجه `Failure` باشد، extraction exception اصلی را پرتاب می‌کند؛ Failure به Collection خالی تبدیل نمی‌شود. در Success بدون Collection، `toCollection()` خطای `IllegalStateException` می‌دهد.

این extensionها در ماژول Query Executor قرار دارند و جزو API پایهٔ `collection:core` نیستند.
