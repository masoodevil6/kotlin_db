# نمونه‌های اجرای Fluent برای CRUD

این نمونه‌ها query را از ابتدا با `queryManager.queryBuilder { ... }` می‌سازند. هر operation از طریق API متناظر خود انتخاب می‌شود. متد `sql` متن SQL رندرشده و فهرست پارامترها را از طریق `BuiltQuery` به callback می‌دهد؛ مقدار پارامترها داخل SQL جای‌گذاری نمی‌شوند.

```kotlin
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
```

## SELECT

`execute()` همهٔ ردیف‌ها را به‌صورت eager به `QueryRow` تبدیل می‌کند. نتیجه از نوع `ExecuteResult<List<QueryRow>>` است.

```kotlin
fun selectUsers(queryManager: IQueryBuilderExecutor) {
    queryManager
        .queryBuilder { operations ->
            operations.querySelect { query ->
                query
                    .select {
                        addColumn {
                            column { tableColumn("uu", "id") }
                            alias("user_id")
                        }
                        addColumn {
                            column { tableColumn("uu", "name") }
                            alias("user_name")
                        }
                        addColumn {
                            column { tableColumn("uu", "family") }
                            alias("user_family")
                        }
                    }
                    .table {
                        table("user_users", "uu")
                    }
                    .where {
                        conditions {
                            addCondition {
                                logicalAnd()
                                sideSelector { tableColumn("uu", "id") }
                                operationEqual()
                                sideValue("user_id", 5)
                            }
                        }
                    }
            }
        }
        .sql { (query, params) ->
            println("SELECT\nquery: $query\nparams: $params")
        }
        .execute()
        .success { rows ->
            rows.forEach { row ->
                println(
                    "user_id=${row["user_id"]}, " +
                    "name=${row["user_name"]}, " +
                    "family=${row["user_family"]}"
                )
            }
            rows
        }
        .error { failure ->
            println("SELECT error: $failure")
        }
}
```

SQL تولیدشده:

```sql
SELECT uu.id AS user_id, uu.name AS user_name, uu.family AS user_family
FROM user_users AS uu
WHERE (uu.id = :user_id)
```

پارامتر:

```text
user_id = 5
```

## INSERT

`execute()` مقدار نخستین generated key را در `ExecuteResult<Long>` برمی‌گرداند.

```kotlin
fun insertUser(queryManager: IQueryBuilderExecutor) {
    queryManager
        .queryBuilder { operations ->
            operations.queryInsert {
                table {
                    table("user_users", "uu")
                }
                addValue {
                    column("name", "Ali")
                }
                addValue {
                    column("family", "Sadegi")
                }
                addValue {
                    column("age", 50)
                }
            }
        }
        .sql { (query, params) ->
            println("INSERT\nquery: $query\nparams: $params")
        }
        .execute()
        .success { generatedKey ->
            println("generated key: $generatedKey")
            generatedKey
        }
        .error { failure ->
            println("INSERT error: $failure")
        }
}
```

SQL تولیدشده:

```sql
INSERT INTO user_users (name, family, age)
VALUES (:name, :family, :age)
```

پارامترها:

```text
name = "Ali"
family = "Sadegi"
age = 50
```

## UPDATE

`execute()` تعداد ردیف‌های متأثر را در `ExecuteResult<Int>` برمی‌گرداند.

```kotlin
fun updateUser(queryManager: IQueryBuilderExecutor) {
    queryManager
        .queryBuilder { operations ->
            operations.queryUpdate {
                table {
                    table("user_users", "uu")
                }
                addValue {
                    column("uu", "name", "Ali Updated")
                }
                where {
                    conditions {
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableColumn("uu", "id")
                            }
                            operationEqual()
                            sideValue("user_id", 5)
                        }
                    }
                }
            }
        }
        .sql { (query, params) ->
            println("UPDATE\nquery: $query\nparams: $params")
        }
        .execute()
        .success { affectedRows ->
            println("updated rows: $affectedRows")
            affectedRows
        }
        .error { failure ->
            println("UPDATE error: $failure")
        }
}
```

SQL تولیدشده:

```sql
UPDATE user_users AS uu
SET uu.name = :uu_name
WHERE (uu.id = :user_id)
```

پارامترها:

```text
uu_name = "Ali Updated"
user_id = 5
```

## DELETE

`execute()` تعداد ردیف‌های حذف‌شده را در `ExecuteResult<Int>` برمی‌گرداند.

```kotlin
fun deleteUser(queryManager: IQueryBuilderExecutor) {
    queryManager
        .queryBuilder { operations ->
            operations.queryDelete {
                table {
                    table("user_users", "uu")
                }
                where {
                    conditions {
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableColumn("uu", "id")
                            }
                            operationEqual()
                            sideValue("user_id", 5)
                        }
                    }
                }
            }
        }
        .sql { (query, params) ->
            println("DELETE\nquery: $query\nparams: $params")
        }
        .execute()
        .success { affectedRows ->
            println("deleted rows: $affectedRows")
            affectedRows
        }
        .error { failure ->
            println("DELETE error: $failure")
        }
}
```

SQL تولیدشده:

```sql
DELETE FROM user_users AS uu
WHERE (uu.id = :user_id)
```

پارامتر:

```text
user_id = 5
```

این مثال تک‌جدولی است و به target جداگانه نیاز ندارد.

> نمونه‌های INSERT، UPDATE و DELETE دادهٔ پایگاه داده را تغییر می‌دهند. آن‌ها را فقط روی پایگاه دادهٔ توسعه/آزمایشی مناسب اجرا کنید.


-----

#هدف


```kotlin
fun selectUsers(queryManager: IQueryBuilderExecutor) {
    queryManager
        .queryBuilder { operations ->
            operations.querySelect { query ->
                query
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
                    }
                    .table {
                        table("user_users", "uu")
                    }
                    .where {
                        conditions {
                            addCondition {
                                logicalAnd()
                                sideSelector {
                                    tableColumn("uu", "id")
                                }
                                operationEqual()
                                sideValue("user_id", 5)
                            }
                        }
                    }
            }
        }
        .sql { (query, params) ->
            println("SELECT\nquery: $query\nparams: $params")
        }
        .execute()
        .success { rows ->
            rows.forEach { row ->
                val userId: Long = row["user_id"] // long
                val userName: String = row["user_name"] // string
                val userFamily: String = row["user_family"] // string

                println("$userId - $userName $userFamily")
            }

            rows
        }
        .error { failure ->
            println("SELECT error: $failure")
        }
}
```


```
SELECT OUTPUT
─────────────────────────────
alias          DataType
─────────────────────────────
user_id        LONG
user_name      STRING
user_family    STRING
```

```
ResultSet
    │
    ├── user_id     → getLong("user_id")
    ├── user_name   → getString("user_name")
    └── user_family → getString("user_family")
             │
             ▼
          QueryRow
             │
             ├── ["user_id"]     → Long
             ├── ["user_name"]   → String
             └── ["user_family"] → String
```

```
.success { rows ->
    rows.forEach { row ->
        val userId = row["user_id"]       // Long
        val userName = row["user_name"]   // String
        val userFamily = row["user_family"] // String
    }

    rows
}
```

