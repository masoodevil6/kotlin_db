# UPDATE

~~~kotlin
val query = QueryRenderUpdateBuilder()
    .table { table("user_users").alias("uu") }
    .addValue {
        column("uu", "name")
        setValue {
            paramName("uu_name")
            paramValue("--changed--")
        }
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("uu", "id") }
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
    .success { affectedRows -> println("updated rows: " + affectedRows); affectedRows }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
UPDATE user_users AS uu
SET uu.name = :uu_name
WHERE (uu.id = :id1)
~~~

پارامترها: uu_name = "--changed--"، id1 = 5. نتیجه ExecuteResult<Int> و تعداد ردیف‌های متأثر است.

---

[بازگشت به فهرست این نسخه](README.md)
