# INSERT

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.queryInsert {
            table { table(UserModelV3::class) }
            addValue {
                column(UserModelV3::class, UserModelV3::name)
                setValue {
                    paramName("name")
                    paramValue("Ali")
                }
            }
            addValue {
                column(UserModelV3::class, UserModelV3::family)
                setValue {
                    paramName("family")
                    paramValue("Sadegi")
                }
            }
            addValue {
                column(UserModelV3::class, UserModelV3::age)
                setValue {
                    paramName("age")
                    paramValue(50)
                }
            }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .execute()
    .success { key -> println("generated key: " + key); key }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: INSERT INTO user_users (name, family, age) VALUES (...). نتیجه ExecuteResult<Long> است.

---

[بازگشت به فهرست این نسخه](README.md)
