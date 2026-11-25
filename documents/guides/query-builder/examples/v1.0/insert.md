# INSERT

~~~kotlin
val query = QueryRenderInsertBuilder()
    .table { table("user_users") }
    .addValue {
        column("name")
        setValue {
            paramName("name")
            paramValue("Ali")
        }
    }
    .addValue {
        column("family")
        setValue {
            paramName("family")
            paramValue("Sadegi")
        }
    }
    .addValue {
        column("age")
        setValue {
            paramName("age")
            paramValue(50)
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { generatedKey -> println("generated key: " + generatedKey); generatedKey }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
INSERT INTO user_users (name, family, age)
VALUES (:name, :family, :age)
~~~

پارامترها: name = "Ali"، family = "Sadegi"، age = 50. نتیجه ExecuteResult<Long> و generated key است.

---

[بازگشت به فهرست این نسخه](README.md)
