# مستندات ماژول‌های پایگاه داده

این راهنما محتوای پروژه را در سه حوزهٔ اصلی سازمان‌دهی می‌کند: اتصال به پایگاه داده، Migration و Query Builder. در حال حاضر، بخش Query Builder شامل مستندات کامل مثال‌های V1، V2 و V3 است.

## فهرست

- [1. Connection](#1-connection)
- [2. Migration](#2-migration)
- [3. Query Builder](#3-query-builder)
  - [V1 — نام خام جدول و ستون](#v1--نام-خام-جدول-و-ستون)
  - [V2 — Model-backed Query](#v2--model-backed-query)
    - [V3 — CTE-backed SELECT و Model-backed Mutation](#v3--cte-backed-select-و-model-backed-mutation)
  - [مقایسهٔ نسخه‌ها](#خلاصه-تفاوت-نسخهها)
  - [Rules](#rules)
    - [Model](#rules-model)
    - [Query](#rules-query)

## 1. Connection

## 2. Migration

## 3. Query Builder

این بخش نمونه‌های واقعی Query Builder را در سه نسخه شرح می‌دهد. هر نسخه، SELECT و عملیات CRUD خود را با کد نمونه، SQL مورد انتظار و پارامترها نشان می‌دهد. قواعد Metadata نیز در [Rules](#rules) آمده‌اند و به دو بخش مستقل **Model** و **Query** تقسیم شده‌اند. INSERT، UPDATE و DELETE داده را تغییر می‌دهند؛ آن‌ها را فقط روی دیتابیس توسعه یا آزمایشی اجرا کنید.

---

### V1 — نام خام جدول و ستون

در V1 نام جدول و ستون به‌صورت String داده می‌شود. SELECT outputها alias و DataType صریح دارند.

#### SELECT

~~~kotlin
val query = QueryRenderSelectBuilder()
    .select {
        addColumn {
            column { tableColumn("uu", "id") }
            alias("user_id")
            execute(DataType.LONG)
        }
        addColumn {
            column { tableColumn("uu", "name") }
            alias("user_name")
            execute(DataType.STRING)
        }
        addColumn {
            column { tableColumn("uu", "family") }
            alias("user_family")
            execute(DataType.STRING)
        }
        addColumn {
            column { tableColumn("up", "phone") }
            alias("user_phone")
            execute(DataType.STRING)
        }
    }
    .from { table("user_users").alias("uu") }
    .joins {
        addJoin {
            innerJoin()
            table { table("user_phones").alias("up") }
            condition {
                logicalOn()
                addCondition {
                    sideSelector { tableColumn("uu", "id") }
                    operationEqual()
                    sideValueColumn { tableColumn("up", "user_id") }
                }
            }
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
                    paramValue(1)
                }
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery ->
        println(builtQuery.query)
        println(builtQuery.params)
    }
    .details { metadata ->
        println("meta_data: $metadata")
    }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("user_id=" + row.getValue("user_id"))
            println("user_name=" + row.getValue("user_name"))
            println("user_family=" + row.getValue("user_family"))
            println("user_phone=" + row.getValue("user_phone"))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL تولیدشده از نظر ساختار:

~~~sql
SELECT uu.id AS user_id, uu.name AS user_name,
       uu.family AS user_family, up.phone AS user_phone
FROM user_users AS uu
INNER JOIN user_phones AS up ON uu.id = up.user_id
WHERE (uu.id = :id1)
~~~

پارامتر: id1 = 1

خروجی SELECT:

~~~text
alias       DataType
user_id     LONG
user_name   STRING
user_family STRING
user_phone  STRING
~~~

استخراج بر اساس ترتیب SELECT و index انجام می‌شود؛ alias کلید QueryRow است:

~~~text
SELECT item #0 + user_id + LONG
    → ResultSet.getLong(1)
    → QueryRow["user_id"] = Long
~~~

Executor نتیجه را پیش از بسته‌شدن `ResultSet` به‌صورت eager می‌سازد. تعداد ستون‌ها و label هر ستون باید با خروجی SELECT و `ColumnAlias` متناظر برابر باشد؛ `ExecutionType` نوع خواندن JDBC را تعیین می‌کند. `QueryRow` با SQL aliasها کلیدگذاری می‌شود و دسترسی typed با `getValue(property)` از `PropertyReference` و alias همان خروجی استفاده می‌کند.

#### INSERT

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

#### UPDATE

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

#### DELETE

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

### V2 — Model-backed Query

در V2 نام جدول و ستون از Model و propertyها می‌آید. SELECTهای property-backed از metadata موجود Model برای نام خروجی و نوع استخراج استفاده می‌کنند.

#### Modelهای V2

دو Model مورد استفاده در مثال‌ها:

~~~kotlin
@QBTable(name = "user_users", alias = "uu")
class UserModelV2 : IModelBase {
    @QBColumn(name = "id", alias = "user_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "name", alias = "user_name")
    val name: String? = null

    @QBColumn(name = "family", alias = "user_family")
    val family: String? = null

    @QBColumn(name = "age", alias = "user_age")
    val age: Int? = null
}

@QBTable(name = "user_phones", alias = "up")
class UserPhoneModelV2 : IModelBase {
    @QBColumn(name = "id", alias = "user_phone_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "phone", alias = "user_phone")
    val phone: String? = null

    @QBColumn(name = "user_id", alias = "phone_user_id")
    val userId: Int = 0
}
~~~

معنای metadata این Modelها:

~~~text
UserModelV2      → table user_users، source alias پیش‌فرض uu
UserPhoneModelV2 → table user_phones، source alias پیش‌فرض up
UserModelV2::id  → column id، SELECT output alias user_id، Kotlin type Int
UserModelV2::name → column name، SELECT output alias user_name، Kotlin type String?
UserModelV2::family → column family، SELECT output alias user_family، Kotlin type String?
UserModelV2::age → column age، SELECT output alias user_age، Kotlin type Int?
UserPhoneModelV2::phone → column phone، SELECT output alias user_phone، Kotlin type String?
~~~

در declaration، `model(UserModelV2::class)` منبع را انتخاب می‌کند و `column { tableSelector(...); columnSelector(...) }` ستون property-backed را برمی‌گزیند. Annotation، alias خروجی SELECT و نوع استخراج پیش‌فرض را فراهم می‌کند؛ alias و نوع صریح در خود SELECT می‌توانند آن خروجی‌ها را override کنند. overload قدیمی `column(KClass, property)` همچنان برای سازگاری وجود دارد، اما deprecated است.

#### SELECT

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

#### INSERT

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

#### UPDATE

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

#### DELETE

~~~kotlin
val query = QueryRenderDeleteBuilder()
    .addTarget("uu")
    .table { table(UserModelV2::class) }
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
    .success { count -> println("deleted rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: DELETE uu FROM user_users AS uu WHERE (uu.id = ...). پارامتر: id1 = 5؛ نتیجه ExecuteResult<Int> است.

---

### V3 — CTE-backed SELECT و Model-backed Mutation

در V3، SELECTها از declarationهای Query استفاده می‌کنند که به CTE و sourceهای ترکیبی تبدیل می‌شوند. نام پیش‌فرض CTEهای declaration-based از `QBQuery.name` می‌آید؛ `QBQuery.alias` alias پیش‌فرض occurrence است. در API فعلی، declaration با `cte(declaration)` داخل بلوک `from { ... }` انتخاب می‌شود و `alias("...")` همان source occurrence را نام‌گذاری می‌کند. `cte(name)` نیز برای انتخاب CTE بر اساس نام باقی می‌ماند. هر خروجی با `QBQuerySelect` نام منطقی و SQL alias خودش را مشخص می‌کند. INSERT، UPDATE و DELETE همچنان با `UserModelV3` ساخته می‌شوند.

#### قرارداد فعلی SELECT FROM

ورودی عمومی منبع SELECT یکپارچه است: `.from { ... }`. داخل این بلوک، source از طریق `IQueryTableApi` انتخاب می‌شود:

~~~kotlin
// جدول با نام خام
from {
    table("user_users")
    alias("uu")
}

// منبع Model
from {
    model(UserModelV3::class)
}

// Query declaration با پارامترهای متصل‌شده به نمونه
from {
    cte(UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters(userId = 5)))
    alias("details")
}
~~~

`cte(String)` و `alias(String)` نیز روی همان receiver در دسترس‌اند. اگر برای Query alias صریح ندهیم، alias metadata تعریف Query به‌عنوان alias پیش‌فرض occurrence استفاده می‌شود؛ alias داخل بلوک فقط همان occurrence را override می‌کند.

برای SELECT تو‌در‌تو، `table` یک lambda با receiver از نوع `IQueryRenderSelectApi` می‌گیرد و یک derived table می‌سازد؛ آن را به CTE تبدیل نمی‌کند:

~~~kotlin
from {
    table {
        select {
            addColumn {
                column { tableColumn("uu", "id") }
                alias("id")
                execute(DataType.INT)
            }
        }
        from {
            table("user_users")
            alias("uu")
        }
    }
    alias("q")
}
~~~

این مثال sourceای به شکل `(SELECT uu.id AS id FROM user_users AS uu) AS q` می‌سازد. پارامترهای SELECT داخلی به ترتیب placeholderهای SQL نهایی حفظ می‌شوند. `table(KClass)` همچنان برای مسیرهای مشترک CRUD/JOIN باقی است؛ در sourceهای Model در SELECT از `model(KClass)` استفاده می‌شود. CTE declaration در `from { ... }` با `cte(declaration)` انتخاب می‌شود و Builder آن را به مسیر composition موجود می‌سپارد.

#### Queryهای V3

V3 چهار Query declaration نمونه دارد. هر declaration نام runtime، alias پیش‌فرض source، فیلترهای ورودی و propertyهای خروجی typed را مشخص می‌کند. Propertyهای خروجی فقط metadata و identity هستند؛ getter آن‌ها عمداً خطا می‌دهد و مقدار واقعی از SELECT materialize می‌شود.

| Query | `QBQuery.name` / نام CTE | `QBQuery.alias` پیش‌فرض | ورودی‌ها | خروجی‌های typed |
|---|---|---|---|---|
| `UserStatsQueryV3` | `user_stats_query_v3` | `v3_user_stats` | `userId` و `minAge` اختیاری | `id`, `name`, `family`, `age` |
| `UserPhoneQueryV3` | `user_phone_query_v3` | `v3_user_phone_details` | `userId` اختیاری | `id`, `name`, `family`, `age`, `phone` |
| `UserPageQueryV3` | `user_page_query_v3` | `v3_user_page` | `limit` پیش‌فرض 2 و `offset` پیش‌فرض 0 | `id`, `name`, `family`, `age`; از UserStats عبور داده می‌شوند |
| `UserDisplayQueryV3` | `user_display_query_v3` | `v3_user_display` | `userId` و `minAge` اختیاری | `id`, `fullName`, `age` |

برای هر property خروجی، `QBQuerySelect.name` نام منطقی و `QBQuerySelect.alias` نام SQL منتشرشده است:

| Query property | نام منطقی | SQL alias |
|---|---|---|
| `UserStatsQueryV3::id/name/family/age` | همان نام property | همان نام property |
| `UserPhoneQueryV3::id` | `id` | `user_id` |
| `UserPhoneQueryV3::name` | `name` | `user_name` |
| `UserPhoneQueryV3::family` | `family` | `user_family` |
| `UserPhoneQueryV3::age` | `age` | `user_age` |
| `UserPhoneQueryV3::phone` | `phone` | `user_phone` |
| `UserPageQueryV3::id/name/family/age` | همان نام property | همان نام property |
| `UserDisplayQueryV3::id` | `id` | `id` |
| `UserDisplayQueryV3::fullName` | `full_name` | `fullName` |
| `UserDisplayQueryV3::age` | `age` | `age` |

همهٔ propertyهای خروجی چهار Query در source با `QBQuerySelect` مشخص شده‌اند. نمونهٔ `UserStatsQueryV3` بالا declaration کامل را نشان می‌دهد؛ این قطعه، metadata کامل سه Query دیگر را خلاصه می‌کند:

~~~kotlin
@QBQuery(name = "user_phone_query_v3", alias = "v3_user_phone_details")
class UserPhoneQueryV3 {
    @QBQuerySelect(name = "id", alias = "user_id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "user_name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "user_family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "user_age")
    val age: Int? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "phone", alias = "user_phone")
    val phone: String? get() = error("Typed output property must not be read")
}

@QBQuery(name = "user_page_query_v3", alias = "v3_user_page")
class UserPageQueryV3 {
    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")
}

@QBQuery(name = "user_display_query_v3", alias = "v3_user_display")
class UserDisplayQueryV3 {
    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "full_name", alias = "fullName")
    val fullName: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")
}
~~~

نمونهٔ declaration در UserStatsQueryV3:

~~~kotlin
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3

@QBQuery(name = "user_stats_query_v3", alias = "v3_user_stats")
class UserStatsQueryV3(
    override val params: RelationFilters,
) : ICteDeclaration<UserStatsQueryV3.RelationFilters> {

    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")

    override fun createCte(params: RelationFilters): Cte =
        buildCte {
            select {
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::id)
                    }
                    alias(UserStatsQueryV3::id)
                }
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::name)
                    }
                    alias(UserStatsQueryV3::name)
                }
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::family)
                    }
                    alias(UserStatsQueryV3::family)
                }
                addColumn {
                    column {
                        tableSelector(UserModelV3::class)
                        columnSelector(UserModelV3::age)
                    }
                    alias(UserStatsQueryV3::age)
                }
            }
            from { model(UserModelV3::class) }
        }
}
~~~

در سورس فعلی V3، سه declaration مستقیماً `ICteDeclaration<...>` را پیاده می‌کنند و `UserDisplayQueryV3` از کلاس پایهٔ اختیاری `CteDeclaration<...>` استفاده می‌کند. این توضیح، وضعیت فعلی سورس را ثبت می‌کند. `alias(Query::property)` خروجی SELECT را به property typed متصل می‌کند، `cteOutput(Query::property, qualifier)` آن خروجی را از CTE فعال می‌خواند و `row.getValue(Query::property)` مقدار materialize‌شده را برمی‌گرداند؛ resolver getter را برای خواندن metadata اجرا نمی‌کند.

##### قرارداد مقصد قطعی V3.0 برای Query و پارامترها

در معماری مقصد، خود Query با constructor بدون آرگومان ساخته می‌شود و پارامترهای اجرایی روی آن ذخیره نمی‌شوند. `UserStatsQueryV3` که اجرای مستقل نیاز دارد از `CteDeclaration<RelationFilters>()` ارث می‌برد و `createCte(params)` ورودی را صریح می‌گیرد. برای هر row، mapper نمونهٔ تازهٔ همین کلاس را می‌سازد و فقط propertyهای خروجی دارای getter/setter واقعی را مقدار می‌دهد.

نمونهٔ مرجع برای declaration و composition در `A2ExampleSelectV3` پس از migration:

~~~kotlin
val cte = UserStatsQueryV3()
val cteParams = UserStatsQueryV3.RelationFilters(userId = 5)
val withName = "active_user_stats_v3"

val withQuery = QueryRenderSelectBuilder().withs {
    addWith { with(withName, cte(cteParams)) }
}

val directResult = cte.execute(queryManager, cteParams)
~~~

`cte(cteParams)` از operator factory استفاده می‌کند و یک `Cte` می‌سازد؛ overload موجود `with(String, Cte)` composition را انجام می‌دهد. برای FROM، معادل آن `from { cte(declaration(params)) }` است که از overload موجود `cte(Cte)` استفاده می‌کند. هر دو مسیر، params را فقط در همان ساخت/اجرا مصرف می‌کنند؛ `cte` هیچ property پارامتری ندارد. `directResult` همچنان قرارداد legacy یعنی `ExecuteResult<List<QueryRow>>` را دارد.

`QBQuerySelect.name` نام منطقی خروجی است و `QBQuerySelect.alias` نام منتشرشدهٔ SQL است. برای نمونه، در `UserPhoneQueryV3`، `id` نام منطقی دارد ولی با alias `user_id` منتشر می‌شود؛ reference typed هم از metadata همین alias استفاده می‌کند.

جزئیات خروجی‌ها:

~~~text
UserStatsQueryV3
    table source: UserModelV3
    outputs: id, name, family, age (logical name = SQL alias)

UserPhoneQueryV3
    table source: UserModelV3
    INNER JOIN: UserPhoneModelV3
    outputs: id→user_id, name→user_name, family→user_family,
             age→user_age, phone→user_phone (logical name → SQL alias)

UserPageQueryV3
    source: UserStatsQueryV3
    outputs: id, name, family, age (logical name = SQL alias)
    applies limit and offset from Query filters

UserDisplayQueryV3
    table source: UserModelV3
    outputs: id, fullName, age
    fullName: logical name full_name, published SQL alias fullName
    expression: concat(name, '-', family)
~~~

هر چهار declaration با `QBQuery.name` نام پیش‌فرض CTE خود را می‌گیرند. برای استفاده در SELECT، declaration به `cte(declaration)` داخل `from { ... }` داده می‌شود؛ Builder از پارامترهای متصل‌شده استفاده می‌کند، `createCte(params)` را برای ساخت تعریف CTE فراخوانی می‌کند و نتیجه را به مسیر composition داخلی می‌سپارد. کلاس پایهٔ `CteDeclaration<P>` اختیاری است؛ پیاده‌سازی مستقیم `ICteDeclaration<P>` نیز پشتیبانی می‌شود.

~~~kotlin
queryManager
    .queryBuilder { operations ->
        operations.querySelect { query ->
            query
                .from {
                    cte(UserStatsQueryV3(UserStatsQueryV3.RelationFilters(userId = 5)))
                    alias("stats")
                }
                .select {
                    addColumn { cteOutput(UserStatsQueryV3::id, "stats") }
                    addColumn { cteOutput(UserStatsQueryV3::name, "stats") }
                }
        }
    }
    .sql { (queryText, params) -> println(queryText + "\n" + params) }
    .details { metadata -> println("meta_data: $metadata") }
    .execute()
~~~

در این نمونه alias صریح `stats` در بلوک `from` برای source occurrence تنظیم شده و همان qualifier به `cteOutput` داده شده است. در نمونهٔ بعدی، `details` همین نقش را برای UserPhone دارد. نام CTE از `QBQuery.name` می‌آید و با alias occurrence یکی نیست.

وابستگی Query چندلایه در UserPageQueryV3 به این شکل است:

~~~text
UserStatsQueryV3
    → UserPageQueryV3
        → SELECT consumer
~~~

UserPage خروجی‌های Stats را با همان propertyهای typed عبور می‌دهد و `limit`/`offset` را روی query داخلی اعمال می‌کند. UserPhone با join بین `UserModelV3` و `UserPhoneModelV3` خروجی `phone` را اضافه می‌کند. UserDisplay خروجی `fullName` را از expression محاسبه‌شدهٔ concat نام و نام خانوادگی می‌سازد.

#### SELECT

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

#### افزودن CTE به `WITH`

`IQueryWithsItemApi` دو overload برای افزودن CTE جدید دارد. `with(cte)` از نام مؤثر خود شیء `Cte` استفاده می‌کند؛ `with(withName, cte)` نام صریح caller را برای عبارت SQL `WITH` انتخاب می‌کند:

~~~kotlin
val statsDeclaration = UserStatsQueryV3(UserStatsQueryV3.RelationFilters())
val statsCte = statsDeclaration.createCte(statsDeclaration.params)

val withResolvedName = QueryRenderSelectBuilder().withs {
    addWith { with(statsCte) }
}

val withExplicitName = QueryRenderSelectBuilder().withs {
    addWith { with("published_stats", statsCte) }
}
~~~

در overload نام‌دار، `published_stats` بر `statsCte.name` اولویت دارد، اما مقدار `statsCte.name` را تغییر نمی‌دهد. Builder نام انتخاب‌شده را در تعریف WITH و ارجاع‌های وابستگی به‌صورت هماهنگ به کار می‌برد. نام CTE از alias پیش‌فرض occurrence یعنی `QBQuery.alias` مستقل است؛ alias منبع همچنان از metadata یا `alias("...")` در همان occurrence می‌آید. نام صریح خالی و نام مؤثر تکراری رد می‌شود. APIهای قدیمی `with(name, ICte)` و `with(name, block)` نیز حفظ شده‌اند.

#### INSERT

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

#### UPDATE

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

#### DELETE

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

### خلاصه تفاوت نسخه‌ها

| نسخه | SELECT output و دسترسی | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| V1 | alias و DataType صریح؛ خواندن با alias | generated key: Long | affected rows: Int | affected rows: Int |
| V2 | Model property؛ خواندن با Model::property | Model | Model | Model |
| V3 | Query property و SQL alias اعلام‌شده؛ خواندن با `Query::property` | Model V3 | Model V3 | Model V3 |

`QueryConsumer.execute()` همهٔ ردیف‌ها را به‌صورت eager و با نوع `ExecuteResult<List<QueryRow>>` برمی‌گرداند؛ `first()` فقط ردیف اول را می‌خواند. متد `execute()` در Consumerهای INSERT، UPDATE و DELETE نیز همان لحظه عملیات را اجرا می‌کند و به‌ترتیب `ExecuteResult<Long>` یا `ExecuteResult<Int>` می‌دهد. متد `sql()` فقط SQL و bindingهای آن را می‌سازد و Query را اجرا نمی‌کند.

پارامترهای جدید به‌صورت AST تعریف می‌شوند: شرط‌ها از `sideValueParam { paramName(...); paramValue(...) }`، مقدارهای INSERT/UPDATE از `setValue { ... }` و مجموعه‌ها از `sideValueCollection { addParam { ... } }` استفاده می‌کنند. اگر `paramName` داده نشود، Builder نام تعیین می‌کند؛ نوع SQL از مقدار پارامتر resolve می‌شود. تعریف‌های هم‌نام فقط وقتی مقدار و SQL type یکسان داشته باشند در scope مشترک reuse می‌شوند؛ تفاوت مقدار یا نوع به binding مستقل می‌انجامد. Renderer پارامترمحور، SQL نمایشی با نام‌هایی مانند `:id1` و SQL JDBC با `?` تولید می‌کند و binding هر placeholder را در ترتیب ظهور SQL نگه می‌دارد. Executor این دنباله را از طریق `BoundQuery` به‌صورت positional bind می‌کند؛ بنابراین ترتیب نهایی SQL، از جمله برای SELECT تو‌در‌تو و CTE، مرجع ترتیب binding است. مسیر قدیمی `BuiltQuery` با placeholder نام‌دار نیز برای ورودی‌های legacy حفظ شده است.

پیش از اجرای SELECT، alias و `ExecutionType` تمام خروجی‌ها اعتبارسنجی می‌شوند. هنگام materialization نیز تعداد خروجی‌های ResultSet و label هر ستون با SELECT تطبیق داده می‌شود؛ مقدار هر خانه با `ExecutionType` متناظر خوانده می‌شود و `QueryRow` برای دسترسی رشته‌ای از SQL alias و برای دسترسی typed از `PropertyReference` همان خروجی استفاده می‌کند.

#### مشاهدهٔ Metadata مؤثر SELECT با `details`

`QueryConsumer.details()`، Metadata مؤثر SELECT را از Provider همان Query دریافت می‌کند. نسخهٔ callback یعنی `details { metadata -> ... }` Metadata را به callback می‌دهد و همان Consumer را برمی‌گرداند؛ بنابراین می‌توان آن را میان `.sql { ... }` و `.execute()` زنجیره کرد:

~~~kotlin
import gog.my_project.data_base.query.example.metadata.formatEffectiveQueryMetadata

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .details { metadata -> println(formatEffectiveQueryMetadata(metadata)) }
    .execute()
~~~

تابع formatEffectiveQueryMetadata در ماژول Example اطلاعات را در دو جدول کنسولی، یکی برای Relationها و دیگری برای خروجی‌ها، نمایش می‌دهد. مقدار null با `<null>`، رشتهٔ خالی با `<empty>` و فهرست بدون عضو با `<none>` نمایش داده می‌شود. Metadata فعلی CTE و Query را گزارش می‌کند؛ sourceهای عادی Model یا جدول در این فهرست Relationها قرار نمی‌گیرند، بنابراین SELECT بدون CTE در هر دو بخش `<none>` نشان می‌دهد.

مقدار `metadata` از نوع `EffectiveQueryMetadata` است و فهرست Relationها و خروجی‌های مؤثر را نگه می‌دارد؛ از جمله نام منطقی Query، نام runtime مربوط به CTE، نام SQL در `WITH`، alias مربوط به occurrence، نام منطقی خروجی، alias منتشرشده در SQL و `executionType`. فیلدهای nullable در صورت نبود مقدار `null` باقی می‌مانند و خروجی‌ها و Relationها ترتیب Query را حفظ می‌کنند.

فراخوانی `details` به‌تنهایی SQL را render نمی‌کند و Query را اجرا نمی‌کند. اگر SELECT سفارشی Provider مربوط به `EffectiveQueryMetadata` را نداشته باشد، `details()` با `IllegalStateException` روشن خطا می‌دهد؛ این محدودیت بر `.sql()` و `.execute()` اثری ندارد. این API متعلق به SELECT است و برای Consumerهای INSERT، UPDATE و DELETE ارائه نشده است.

### Rules

#### ساختار Metadata در AST فعلی

Metadata منبع، selector ستون پایه و خروجی SELECT در ساختارهای جدا نگه‌داری می‌شوند:

| ساختار | مقادیر | ترتیب Resolve یا کاربرد |
|---|---|---|
| `QueryTableAstMetadata` | `nameFromModel`، `nameFromAnnotation`، `nameFromAst`؛ و فیلدهای مشابه `aliasFrom...` | `resolveName()` و `resolveAlias()` هر دو AST ← Annotation ← Model |
| `QueryColumnsBaseAstMetadata` | `tableAliasFrom...`، `columnNameFrom...`، `columnAliasFromModel` و `columnAliasFromAnnotation` | alias منبع و نام ستون: AST ← Annotation ← Model؛ alias پیش‌فرض ستون: Annotation ← Model |
| `QueryColumnsAstMetadata` | `ColumnAlias`، `ExecutionType`، `IsExecutionTypeExplicit`، `PropertyReference` و `ColumnMethod` | alias/نوع/هویت خروجی SELECT؛ مستقل از نام و alias منبع |

در منبع derived SELECT، AST نگه‌دارندهٔ `derivedQuery` است و فیلدهای نام منبع خالی می‌مانند؛ alias occurrence جداگانه Resolve می‌شود. `cte(String)` نام CTE را در Metadata منبع می‌گذارد، درحالی‌که `cte(declaration)` هویت relation را در context composition نگه می‌دارد. در API فعلی `execute(DataType)` روی خروجی SELECT (`IQueryColumnsApi`) اعمال می‌شود، نه روی selector ستون پایه؛ مقدار صریح آن فقط `ExecutionType` خروجی را override می‌کند و نوع Property یا Metadata منبع را تغییر نمی‌دهد.

<a id="rules-model"></a>
#### Model

##### ۱.۱. Metadata سطح کلاس

تعریف Model:

```kotlin
@QBTable(name = "user_users", alias = "uu")
class UserModel : IModelBase
```

###### مرحله اول: مقادیر پیش‌فرض

Engine مقادیر اولیه را از نام کلاس استخراج می‌کند:

```text
tableName  = user
tableAlias = user
```

در این مثال، پسوند انتهایی `Model` از نام کلاس حذف و نام باقی‌مانده به `snake_case` تبدیل می‌شود.

###### مرحله دوم: اعمال `QBTable`

مقادیر Annotation جایگزین مقادیر پیش‌فرض متناظر می‌شوند:

```text
tableName  = user_users
tableAlias = uu
```

- `QBTable.name` نام جدول منبع را مشخص می‌کند.
- `QBTable.alias`، alias پیش‌فرض جدول را مشخص می‌کند.

###### مرحله سوم: اعمال تنظیمات DSL

```kotlin
table {
    table(UserModel::class)
    alias("uu1")
}
```

مقادیر نهایی:

```text
tableName  = user_users
tableAlias = uu1
```

alias صریح DSL بر alias تعریف‌شده در Annotation اولویت دارد، اما نام جدول را تغییر نمی‌دهد.

###### ترتیب اولویت در سطح کلاس

| فیلد | ترتیب Resolve |
|---|---|
| `tableName` | نام پیش‌فرض حاصل از کلاس ← `QBTable.name` |
| `tableAlias` | alias پیش‌فرض ← `QBTable.alias` ← alias صریح DSL |

در این مثال، چون override مستقیمی برای `tableName` در DSL وجود ندارد، مقدار Annotation حفظ می‌شود.

##### ۱.۲. Metadata سطح Property

تعریف Property:

```kotlin
@QBColumn(
    name = "user_id",
    alias = "user_id",
    primaryKey = true
)
val id: Int = 0
```

فرض می‌کنیم Metadata جدول قبلاً Resolve شده است.

###### مرحله اول: مقادیر پیش‌فرض

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = id
columnAlias    = id
columnDataType = INT
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- `columnName` از نام Property گرفته می‌شود.
- `columnAlias` به‌صورت پیش‌فرض برابر نام Property است.
- `columnDataType` از نوع تعریف‌شده Property، یعنی `Int`، استنباط می‌شود.

###### مرحله دوم: اعمال `QBColumn`

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = user_id
columnAlias    = user_id
columnDataType = INT
```

در این مرحله:

- `QBColumn.name` نام ستون منبع را override می‌کند.
- `QBColumn.alias`، alias پیش‌فرض ستون را override می‌کند.
- نوع داده همچنان `INT` باقی می‌ماند، زیرا Annotation در این مثال نوع داده را تغییر نمی‌دهد.

همچنین Metadata مربوط به `primaryKey = true` باید مستقل از نام و alias ستون حفظ شود.

###### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    column {
        tableSelector(UserModel::class)
        columnSelector(UserModel::id)
    }
    alias("user_id_out")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
tableName      = user_users
tableAlias     = uu1
-------------------------
columnName     = user_id
columnAlias    = user_id_out
columnDataType = LONG
```

نتیجه:

- `columnName` برابر `user_id` باقی می‌ماند.
- `columnAlias` به `user_id_out` تغییر می‌کند.
- `columnDataType` به دلیل فراخوانی `execute(DataType.LONG)` برابر `LONG` می‌شود.

تغییر نوع خروجی Query نباید نوع تعریف‌شده Property یا Metadata اصلی Model را تغییر دهد.

###### ترتیب اولویت در سطح Property

| فیلد | ترتیب Resolve |
|---|---|
| `columnName` | نام پیش‌فرض Property ← `QBColumn.name` |
| `columnAlias` | alias پیش‌فرض Property ← `QBColumn.alias` ← alias صریح DSL |
| `columnDataType` | نوع استنباط‌شده از Property ← نوع صریح DSL از طریق `execute(DataType)` |

---

<a id="rules-query"></a>
#### Query

در Query، چند مفهوم مستقل وجود دارند که نباید با یکدیگر ادغام شوند:

- نام منطقی تعریف Query
- نام CTE مورد استفاده در SQL
- alias پیش‌فرض Query
- alias یک occurrence مشخص از CTE در Query
- نام منطقی خروجی Property
- SQL alias خروجی
- نوع داده خروجی

این مقادیر حتی اگر در یک مثال برابر باشند، الزاماً معنای یکسانی ندارند.

##### ۲.۱. Metadata سطح کلاس

تعریف Query:

```kotlin
@QBQuery(
    name = "user_display_query_v3",
    alias = "v3_user_display"
)
class UserDisplayQueryV3(
    params: UserDisplayQueryV3.RelationFilters,
) : CteDeclaration<UserDisplayQueryV3.RelationFilters>(params) {
    data class RelationFilters(val userId: Int? = null)
}
```

###### مرحله اول: مقادیر پیش‌فرض

Engine نام‌های اولیه را از قرارداد نام‌گذاری کلاس استخراج می‌کند:

```text
cteName             = user_display_query_v3
defaultOccurrenceAlias = user_display_query_v3
```

در این نام کلاس، نام به `snake_case` تبدیل می‌شود؛ قرارداد حذف پسوند `Query` فقط هنگامی اعمال می‌شود که نام کلاس واقعاً به آن پسوند ختم شود.

###### مرحله دوم: اعمال `QBQuery`

```text
cteName               = user_display_query_v3
defaultOccurrenceAlias = v3_user_display
```

در این مرحله:

- `QBQuery.name` نام پیش‌فرض runtime برای CTE را تعیین می‌کند.
- `QBQuery.alias`، alias پیش‌فرض occurrence منبع را تعیین می‌کند.

این مقادیر مستقل‌اند: نام CTE هویت SQL در `WITH` را مشخص می‌کند و alias فقط source occurrence را نام‌گذاری می‌کند.

###### مرحله سوم: ساخت CTE و اعمال DSL

```kotlin
from {
    cte(UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters()))
    alias("cte_user_display")
}
```

مقادیر نهایی CTE و occurrence در این مثال:

```text
runtime CTE name       = user_display_query_v3
source occurrence alias = cte_user_display
```

توضیح:

- نام runtime CTE از `QBQuery.name` resolve می‌شود؛ در این مسیر declaration نام صریح مستقلی ندارد.
- alias صریح `cte_user_display`، alias همان source occurrence را override می‌کند.
- مقدار `v3_user_display` همچنان alias پیش‌فرض occurrence است، اما در این نمونه به دلیل وجود override صریح DSL استفاده نمی‌شود.

**نکتهٔ نام‌گذاری:** helper ساخت CTE از Declaration، `QBQuery.name` و سپس convention کلاس را به‌کار می‌گیرد. Factoryای که نام runtime را صریحاً دریافت می‌کند، آن نام را بر metadata مقدم می‌کند. `QBQuery.alias` فقط alias پیش‌فرض occurrence است و `alias("...")` در بلوک `from { cte(declaration); ... }` فقط همان occurrence را override می‌کند. `with(cte)` از `Cte.name` استفاده می‌کند؛ `with(withName, cte)` نام صریح caller را برای SQL `WITH` و ارجاع‌های وابستگی مقدم می‌کند، بدون تغییر `Cte.name` یا alias occurrence.

###### ترتیب اولویت در سطح کلاس

| فیلد یا مفهوم | ترتیب Resolve |
|---|---|
| نام پیش‌فرض CTE از Declaration | convention کلاس ← `QBQuery.name` |
| نام CTE در factory دارای نام صریح | convention کلاس ← `QBQuery.name` ← `name` صریح factory |
| نام CTE در `WITH` | `Cte.name` برای `with(cte)` ← `withName` صریح برای `with(withName, cte)` |
| alias پیش‌فرض occurrence | convention کلاس ← `QBQuery.alias` |
| alias occurrence در DSL | alias صریح `from { ... }` ← alias حاصل از metadata |

نام `Cte.name` از نام انتخاب‌شده هنگام ساخت CTE پیروی می‌کند. `with(withName, cte)` نام شیء CTE را تغییر نمی‌دهد؛ فقط نام SQL همان مورد WITH و ارجاع‌های وابسته را با نام صریح caller هماهنگ می‌کند.

###### افزودن CTE به `WITH`

`IQueryWithsItemApi` دو ورودی برای CTE جدید دارد. Declaration ابتدا به یک شیء `Cte` تبدیل می‌شود؛ سپس یکی از دو overload زیر آن را به `WITH` اضافه می‌کند:

~~~kotlin
val declaration = UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters())
val cte = declaration.createCte(declaration.params)

val useCteName = QueryRenderSelectBuilder().withs {
    addWith { with(cte) }
}

val useExplicitName = QueryRenderSelectBuilder().withs {
    addWith { with("published_user_display", cte) }
}
~~~

`with(cte)` از `Cte.name` استفاده می‌کند. `with(withName, cte)` نام صریح را برای تعریف SQL `WITH` به کار می‌برد و آن را در ارجاع‌های dependency نیز هماهنگ می‌کند؛ خود `Cte.name` تغییر نمی‌کند. این نام‌گذاری از `QBQuery.alias` و alias occurrence مستقل است. نام خالی و نام مؤثر تکراری در composition پذیرفته نمی‌شوند. overloadهای legacy `with(name, ICte)` و `with(name, block)` حفظ شده‌اند.

##### ۲.۲. Metadata سطح Property

تعریف Property خروجی:

```kotlin
@QBQuerySelect(
    name = "full_name",
    alias = "fullName"
)
val fullName: String?
    get() = error("Typed output property must not be read")
```

Getter عمداً خطا ایجاد می‌کند تا مشخص شود Engine باید Metadata را از Declaration، Reflection و Annotation استخراج کند، نه از خواندن مقدار Property.

###### مرحله اول: مقادیر پیش‌فرض

با فرض اینکه CTE و source occurrence قبلاً Resolve شده باشند:

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = full_name
columnDataType = STRING
```

مقادیر اولیه به این صورت تعیین می‌شوند:

- نام منطقی و SQL alias پیش‌فرض از Property یعنی `fullName` به `snake_case` یعنی `full_name` تبدیل می‌شوند.
- `columnDataType` از نوع تعریف‌شده Property یعنی `String?` استنباط می‌شود و به `STRING` نگاشت می‌شود.

###### مرحله دوم: اعمال `QBQuerySelect`

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = fullName
columnDataType = STRING
```

در این مرحله:

- `QBQuerySelect.name` نام منطقی خروجی Property را تعیین می‌کند.
- `QBQuerySelect.alias`، SQL alias منتشرشدهٔ خروجی را تعیین می‌کند.
- نوع داده همچنان `STRING` باقی می‌ماند.

نام منطقی خروجی و SQL alias دو فیلد مستقل هستند؛ در این مثال `full_name` و `fullName` مقادیر متفاوتی دارند.

###### مرحله سوم: اعمال تنظیمات DSL

```kotlin
addColumn {
    cteOutput(UserDisplayQueryV3::fullName)
    alias("name")
    execute(DataType.LONG)
}
```

مقادیر نهایی:

```text
cteName             = user_display_query_v3
occurrenceAlias     = cte_user_display
-------------------------
logicalOutputName   = full_name
publishedSqlAlias   = name
columnDataType = LONG
```

نتیجه:

- نام منطقی خروجی `full_name` باقی می‌ماند؛ زیرا alias صریح خروجی آن را تغییر نمی‌دهد.
- SQL alias خروجی به `name` تغییر می‌کند؛ زیرا alias صریح DSL بر `QBQuerySelect.alias` اولویت دارد.
- `columnDataType` به `LONG` تغییر می‌کند؛ زیرا `execute(DataType.LONG)` نوع خروجی را صریحاً تعیین می‌کند.

این تغییر نوع فقط به خروجی Query مربوط است و نباید نوع تعریف‌شده Property یا Metadata اصلی Query را تغییر دهد.

**توجه:** این مثال صرفاً برای توضیح اولویت‌بندی Metadata است. با توجه به اینکه Property از نوع `String?` است، تعیین `DataType.LONG` لزوماً با نوع واقعی مقدار خروجی سازگار نیست. بررسی سازگاری نوع داده موضوعی مستقل از اولویت‌بندی Metadata است.

Engine همچنین نباید Getter مربوط به `fullName` را برای Resolve کردن Metadata فراخوانی کند.

###### ترتیب اولویت در سطح Property

| فیلد | ترتیب Resolve |
|---|---|
| نام منطقی خروجی | نام پیش‌فرض Property ← `QBQuerySelect.name` |
| SQL alias خروجی | alias پیش‌فرض Property ← `QBQuerySelect.alias` ← alias صریح DSL |
| نوع داده خروجی | نوع استنباط‌شده از Property ← نوع صریح DSL از طریق `execute(DataType)` |

تغییر SQL alias نباید نام منطقی خروجی را تغییر دهد. به همین ترتیب، تغییر نوع خروجی نباید نوع تعریف‌شده Property را بازنویسی کند.

---

#### ۳. تفاوت مقدار تعریف‌نشده و رشته خالی

مقدار تعریف‌نشده و رشته خالی الزاماً معنای یکسانی ندارند.

برای فیلدهای جدید Annotationهای Query، در صورت نیاز به تشخیص این دو حالت، می‌توان از یک مقدار قراردادی مانند `QB_METADATA_UNSET` استفاده کرد.

- **تعریف‌نشده:** مقداری برای فیلد ارائه نشده است؛ بنابراین مقدار پیش‌فرض یا fallback متناظر قابل استفاده است.
- **رشته خالی صریح:** مقدار فیلد عمداً خالی تعیین شده است؛ اگر آن فیلد الزاماً باید مقدار غیرخالی داشته باشد، باید اعتبارسنجی آن را رد کند.

رفتار فعلی Annotationهای Model باید حفظ شود. به‌ویژه، مقادیر پیش‌فرض رشته خالی در `QBTable` و `QBColumn` نباید صرفاً برای یکسان‌سازی با Query تغییر کنند.

استفاده از `QB_METADATA_UNSET` نباید بدون بررسی سازگاری به فیلدهای موجود Model تعمیم داده شود.

---

#### ۴. قواعد عمومی Resolve کردن Metadata

قواعد زیر برای فرآیند Resolve کردن Metadata اعمال می‌شوند:

1. مقادیر پیش‌فرض پیش از Annotationها تعیین شوند.
2. هر Annotation فقط فیلدهای متناظر خود را تغییر دهد.
3. تنظیمات صریح DSL در مرحله آخر اعمال شوند و فقط فیلدهایی را تغییر دهند که آن عملیات برای override کردنشان تعریف شده است.
4. نام منطقی، نام منبع، نام CTE، SQL alias و نوع داده مستقل باقی بمانند.
5. یکسان بودن مقدار دو فیلد به معنای یکسان بودن مفهوم آن‌ها نیست.
6. Getter مربوط به Propertyهای خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
7. `relationName` بخشی از قرارداد Query نیست؛ نام runtime پیش‌فرض CTE از `QBQuery.name` و سپس convention مجاز resolve می‌شود، درحالی‌که نام صریح factory بر آن مقدم است.
8. رفتار موجود Model در مواردی که با قواعد جدید Query متفاوت است، حفظ شود.
9. override نوع خروجی از نوع تعریف‌شده Property مستقل باقی بماند.
10. اعتبارسنجی Metadata پس از تکمیل Declaration مربوط به SELECT و پیش از ساخت `BuiltCteComposition` انجام شود.

##### الزامات اعتبارسنجی

- اعتبارسنجی یکتایی SQL aliasها حفظ شود.
- یکتایی نام‌های منطقی خروجی در میان خروجی‌های Typed متعلق به قرارداد فعلی Query بررسی شود.
- تعلق یک خروجی به Query بر اساس رابطه موجود `declarationOwner` تعیین شود، نه صرفاً بر اساس کلاسی که Property در آن تعریف شده است.
- Property معتبر به‌ارث‌رسیده نباید صرفاً به دلیل تعریف‌شدن در کلاس والد حذف شود.
- Propertyهای منبع Model نباید به‌عنوان خروجی‌های Query شناسایی شوند.
- برای اعتبارسنجی، مقدار Property نباید خوانده شود.

---

#### ۵. سازگاری و محدوده تغییرات

این سند قرارداد Resolve کردن و اولویت‌بندی Metadata را مشخص می‌کند. محدودیت‌های این بخش فقط دامنهٔ تغییرات Metadata را مشخص می‌کنند؛ مسیر AST-based پارامتر و اجرای positional که جداگانه در بالای سند آمده، بخشی مستقل از این قرارداد است.

مگر اینکه یک مانع فنی مشخص، تغییر دیگری را ضروری کند:

- برای تغییرات محدود به Metadata، AST، Executor، `QueryRow`، Materializer و امضاهای API عمومیِ نامرتبط تغییر نکنند.
- در تغییرات محدود به Metadata، قرارداد `Cte` و semantics composition آن تغییر نکند. مهاجرت مستقل و تأییدشدهٔ V3.0 عمداً `ICteDeclaration.params`، constructor پارامترپذیر `CteDeclaration`، `execute(queryManager)` و overload `cte(declaration)` را به ورودی صریح منتقل می‌کند؛ overloadهای `with(Cte)` و `with(withName, Cte)` و معنای نام CTE/alias occurrence حفظ می‌شوند.
- صرفاً برای یکسان‌سازی ظاهری Metadataهای Model و Query، abstraction عمومی جدیدی اضافه نشود.
- رفتار فعلی رشته خالی در Annotationهای Model تغییر نکند.
- وجود `QBQuery.name` نباید نام صریحی را که caller به factory ساخت CTE داده است بازنویسی کند.
- Resolve کردن Metadata از اجرای Query و استخراج مقدار Property مستقل باقی بماند.

هر تغییر در این محدوده‌ها باید جداگانه توجیه و تأیید شود، نه اینکه به‌صورت ضمنی وارد بازنویسی Metadata شود.

---

#### ۶. معیارهای پذیرش

پیاده‌سازی زمانی با این قرارداد سازگار است که:

1. مثال کلاس Model به `tableName = user_users` و `tableAlias = uu1` برسد.
2. مثال Property در Model به `columnName = user_id`، `columnAlias = user_id_out` و `columnDataType = LONG` برسد.
3. نام منطقی و alias تعریف‌شده در Annotation مربوط به Query حفظ شوند.
4. مثال Query به `cteName = user_display_query_v3` و `occurrenceAlias = cte_user_display` برسد: نام runtime از `QBQuery.name` و alias از override همان occurrence می‌آید.
5. مثال خروجی Query به نام منطقی `full_name`، SQL alias برابر `name` و نوع خروجی `LONG` برسد.
6. هر override فقط فیلد متناظر خود را تغییر دهد.
7. Getter مربوط به Property خروجی Typed Query برای Resolve کردن Metadata فراخوانی نشود.
8. نام منطقی Query، نام runtime CTE و alias occurrence مستقل باقی بمانند؛ نام `withName` صریح فقط در SQL `WITH` override شود.
9. رفتار موجود Model و APIهای عمومی Query و CTE حفظ شوند.
10. اعتبارسنجی Metadata پیش از ساخت `BuiltCteComposition` انجام شود.
