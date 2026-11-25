# UPDATE

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.queryUpdate {
            table { table(UserModelV3::class) }
            addValue {
                column(UserModelV3::class, UserModelV3::name)
                setValue {
                    paramName("name")
                    paramValue("--changed--")
                }
            }
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
    .success { count -> println("updated rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: UPDATE user_users AS uu SET uu.name = ... WHERE (uu.id = ...). نتیجه ExecuteResult<Int> است.

---

[بازگشت به فهرست این نسخه](README.md)
