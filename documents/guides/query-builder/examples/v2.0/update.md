# UPDATE

~~~kotlin
val query = QueryRenderUpdateBuilder()
    .table { table(UserModelV2::class) }
    .addValue {
        column(UserModelV2::class, UserModelV2::name)
        setValue {
            paramName("name")
            paramValue("--changed--")
        }
    }
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
    .success { count -> println("updated rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: UPDATE user_users AS uu SET uu.name = ... WHERE (uu.id = ...). پارامترها --changed-- و id1 = 5؛ نتیجه ExecuteResult<Int> است.

---

[بازگشت به فهرست این نسخه](README.md)
