# DELETE

~~~kotlin
val query = QueryRenderDeleteBuilder()
    .table { table("user_users") }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("", "id") }
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
    .success { affectedRows -> println("deleted rows: " + affectedRows); affectedRows }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE FROM user_users WHERE (id = :id1). پارامتر: id1 = 5. برای این مثال تک‌جدولی target جدا لازم نیست.

---

---

[بازگشت به فهرست این نسخه](README.md)
