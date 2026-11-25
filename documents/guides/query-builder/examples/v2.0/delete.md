# DELETE

~~~kotlin
val query = QueryRenderDeleteBuilder()
    .addTarget("uu")
    .table { table(UserModelV2::class) }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector {
                    tableSelector(UserModelV2::class)
                    columnSelector(UserModelV2::id)
                }
                operationEqual()
                sideValueParam {
                    paramName("id1")
                    paramValue(5)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { count -> println("deleted rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE uu FROM user_users AS uu WHERE (uu.id = ...). پارامتر: id1 = 5؛ نتیجه ExecuteResult<Int> است.

---

---

[بازگشت به فهرست این نسخه](README.md)
