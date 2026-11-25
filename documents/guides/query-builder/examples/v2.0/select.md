# SELECT

~~~kotlin
val query = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::id)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::name)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::family)
            }
        }
        addColumn {
            column {
                tableSelector(UserModelV2::class)
                columnSelector(UserModelV2::age)
            }
        }
        addColumn {
            column {
                tableSelector(UserPhoneModelV2::class)
                columnSelector(UserPhoneModelV2::phone)
            }
        }
    }
    .from { model(UserModelV2::class) }
    .joins {
        addJoin {
            innerJoin()
            table { table(UserPhoneModelV2::class) }
            condition {
                logicalOn()
                addCondition {
                    sideSelector {
                        tableSelector(UserModelV2::class)
                        columnSelector(UserModelV2::id)
                    }
                    operationEqual()
                    sideValueColumn {
                        tableSelector(UserPhoneModelV2::class)
                        columnSelector(UserPhoneModelV2::userId)
                    }
                }
            }
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
                    paramValue(1)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .details { metadata -> println("meta_data: $metadata") }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("id=" + row.getValue(UserModelV2::id))
            println("name=" + row.getValue(UserModelV2::name))
            println("phone=" + row.getValue(UserPhoneModelV2::phone))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
SELECT uu.id AS user_id, uu.name AS user_name,
       uu.family AS user_family, uu.age AS user_age, up.phone AS user_phone
FROM user_users AS uu
INNER JOIN user_phones AS up ON uu.id = up.user_id
WHERE (uu.id = :id1)
~~~

پارامتر: id1 = 1.

خروجی SELECT:

~~~text
property                 output alias  DataType
UserModelV2::id          user_id       INT
UserModelV2::name        user_name     STRING
UserModelV2::family      user_family   STRING
UserModelV2::age         user_age      INT
UserPhoneModelV2::phone  user_phone    STRING
~~~

نتیجه را با row.getValue(UserModelV2::id) و propertyهای متناظر می‌خوانیم. برای expression یا ستون خام که property metadata ندارد، alias و execute(DataType.X) باید صریحاً تعریف شوند؛ type از schema یا نام ستون حدس زده نمی‌شود.

---

[بازگشت به فهرست این نسخه](README.md)
