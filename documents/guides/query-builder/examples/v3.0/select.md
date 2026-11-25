# SELECT

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.querySelect { query ->
            query
                .from {
                    cte(UserPhoneQueryV3(UserPhoneQueryV3.RelationFilters()))
                    alias("details")
                }
                .select {
                    addColumn { cteOutput(UserPhoneQueryV3::id, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::name, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::family, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::age, "details") }
                    addColumn { cteOutput(UserPhoneQueryV3::phone, "details") }
                }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .details { metadata -> println("meta_data: $metadata") }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("id=" + row.getValue(UserPhoneQueryV3::id))
            println("name=" + row.getValue(UserPhoneQueryV3::name))
            println("family=" + row.getValue(UserPhoneQueryV3::family))
            println("age=" + row.getValue(UserPhoneQueryV3::age))
            println("phone=" + row.getValue(UserPhoneQueryV3::phone))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
WITH user_phone_query_v3 AS (
    SELECT uu.id AS user_id, uu.name AS user_name, uu.family AS user_family,
           uu.age AS user_age, up.phone AS user_phone
    FROM user_users AS uu
    INNER JOIN user_phones AS up ON uu.id = up.user_id
)
SELECT details.user_id AS user_id, details.user_name AS user_name,
       details.user_family AS user_family, details.user_age AS user_age,
       details.user_phone AS user_phone
FROM user_phone_query_v3 AS details
~~~

خروجی SELECT:

~~~text
property                    logical name  SQL alias   DataType
UserPhoneQueryV3::id        id            user_id     INT
UserPhoneQueryV3::name      name          user_name   STRING
UserPhoneQueryV3::family    family        user_family STRING
UserPhoneQueryV3::age       age           user_age    INT
UserPhoneQueryV3::phone     phone         user_phone  STRING
~~~

Qualifier `details` به‌صورت صریح در `from` و `cteOutput` آمده است. مثال‌های A2 تا A7 نیز `whereIn`، `whereLike`، شرط null، شرط مقداری، Query صفحه‌بندی‌شده و خروجی `fullName` را نشان می‌دهند.

---

[بازگشت به فهرست این نسخه](README.md)
