# DELETE

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.queryDelete {
            addTarget("uu")
            table { table(UserModelV3::class) }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector {
                            tableSelector(UserModelV3::class)
                            columnSelector(UserModelV3::id)
                        }
                        operationEqual()
                        sideValueParam {
                            paramName("id1")
                            paramValue(5)
                        }
                    }
                }
            }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .execute()
    .success { count -> println("deleted rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE uu FROM user_users AS uu WHERE (uu.id = ...). نتیجه ExecuteResult<Int> است.

---

---

[بازگشت به فهرست این نسخه](README.md)
