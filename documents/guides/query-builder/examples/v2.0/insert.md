# INSERT

~~~kotlin
val query = QueryRenderInsertBuilder()
    .table { table(UserModelV2::class) }
    .addValue {
        column(UserModelV2::class, UserModelV2::name)
        setValue {
            paramName("name")
            paramValue("Ali")
        }
    }
    .addValue {
        column(UserModelV2::class, UserModelV2::family)
        setValue {
            paramName("family")
            paramValue("Sadegi")
        }
    }
    .addValue {
        column(UserModelV2::class, UserModelV2::age)
        setValue {
            paramName("age")
            paramValue(50)
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { key -> println("generated key: " + key); key }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: INSERT INTO user_users (name, family, age) VALUES (...). پارامترها همان مقدارهای Ali، Sadegi و 50 هستند؛ نتیجه ExecuteResult<Long> است.

---

[بازگشت به فهرست این نسخه](README.md)
