# مستندات ماژول‌های پایگاه داده

این راهنما محتوای پروژه را در سه حوزهٔ اصلی سازمان‌دهی می‌کند: اتصال به پایگاه داده، Migration و Query Builder. در حال حاضر، بخش Query Builder شامل مستندات کامل مثال‌های V1، V2 و V3 است.

## فهرست

- [1. Connection](#1-connection)
- [2. Migration](#2-migration)
- [3. Query Builder](#3-query-builder)
  - [V1 — نام خام جدول و ستون](#v1--نام-خام-جدول-و-ستون)
  - [V2 — Model-backed Query](#v2--model-backed-query)
  - [V3 — Relation-backed SELECT و Model-backed Mutation](#v3--relation-backed-select-و-model-backed-mutation)
  - [مقایسهٔ نسخه‌ها](#خلاصه-تفاوت-نسخهها)

## 1. Connection

## 2. Migration

## 3. Query Builder

این بخش نمونه‌های واقعی Query Builder را در سه نسخه شرح می‌دهد. هر نسخه، SELECT و عملیات CRUD خود را با کد نمونه، SQL مورد انتظار و پارامترها نشان می‌دهد. INSERT، UPDATE و DELETE داده را تغییر می‌دهند؛ آن‌ها را فقط روی دیتابیس توسعه یا آزمایشی اجرا کنید.

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
    .table { table("user_users").alias("uu") }
    .joins {
        addJoin {
            innerJoin()
            table { table("user_phones").alias("up") }
            condition {
                logicalOn()
                addCondition {
                    sideSelector { tableColumn("uu", "id") }
                    operationEqual()
                    sideValue { tableColumn("up", "user_id") }
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
                sideValue("id1", 1)
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery ->
        println(builtQuery.query)
        println(builtQuery.params)
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

#### INSERT

~~~kotlin
val query = QueryRenderInsertBuilder()
    .table { table("user_users") }
    .addValue { column("name", "Ali") }
    .addValue { column("family", "Sadegi") }
    .addValue { column("age", 50) }

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
    .addValue { column("uu", "name", "--changed--") }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn("uu", "id") }
                operationEqual()
                sideValue("id1", 5)
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
                sideValue("id1", 5)
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

در declaration، table(UserModelV2::class) و column(UserModelV2::class, UserModelV2::id) از همین metadata استفاده می‌کنند. annotation alias، alias خروجی SELECT برای property-backed item فراهم می‌کند؛ alias صریح در خود SELECT می‌تواند آن خروجی را override کند.

#### SELECT

~~~kotlin
val query = QueryRenderSelectBuilder()
    .select {
        addColumn { column(UserModelV2::class, UserModelV2::id) }
        addColumn { column(UserModelV2::class, UserModelV2::name) }
        addColumn { column(UserModelV2::class, UserModelV2::family) }
        addColumn { column(UserModelV2::class, UserModelV2::age) }
        addColumn { column(UserPhoneModelV2::class, UserPhoneModelV2::phone) }
    }
    .table { table(UserModelV2::class) }
    .joins {
        addJoin {
            innerJoin()
            table { table(UserPhoneModelV2::class) }
            condition {
                logicalOn()
                addCondition {
                    sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                    operationEqual()
                    sideValue { tableColumn(UserPhoneModelV2::class, UserPhoneModelV2::userId, "up") }
                }
            }
        }
    }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                operationEqual()
                sideValue("id1", 1)
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
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
    .addValue { column(UserModelV2::class, UserModelV2::name, "Ali") }
    .addValue { column(UserModelV2::class, UserModelV2::family, "Sadegi") }
    .addValue { column(UserModelV2::class, UserModelV2::age, 50) }

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
    .addValue { column(UserModelV2::class, UserModelV2::name, "--changed--") }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                operationEqual()
                sideValue("id1", 5)
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
                sideSelector { tableColumn(UserModelV2::class, UserModelV2::id, "uu") }
                operationEqual()
                sideValue("id1", 5)
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

### V3 — Relation-backed SELECT و Model-backed Mutation

در V3، SELECTها می‌توانند از Concrete Relation و CTE استفاده کنند. Relation propertyها annotation alias ندارند؛ نام property، SQL output name پیش‌فرض است. INSERT، UPDATE و DELETE با UserModelV3 ساخته می‌شوند.

#### Relationهای V3

V3 چهار Relation نمونه دارد. Relation یک query definition با نام CTE، فیلترهای ورودی و propertyهای خروجی typed فراهم می‌کند. این propertyها فقط identity خروجی هستند و getter آن‌ها عمداً خطا می‌دهد؛ مقدار واقعی از SELECT و QueryRow می‌آید.

| Relation | نام Relation / CTE | ورودی‌ها | خروجی‌ها |
|---|---|---|---|
| UserStatsRelationV3 | v3_user_stats | userId و minAge اختیاری | id: Int، name: String?، family: String?، age: Int? |
| UserPhoneRelationV3 | v3_user_phone_details | userId اختیاری | id، name، family، age و phone: String? |
| UserPageRelationV3 | v3_user_page | limit پیش‌فرض 2 و offset پیش‌فرض 0 | id، name، family و age؛ از UserStats عبور داده می‌شوند |
| UserDisplayRelationV3 | v3_user_display | userId و minAge اختیاری | id: Int، fullName: String?، age: Int? |

نمونهٔ declaration در UserStatsRelationV3:

~~~kotlin
class UserStatsRelationV3 :
    IQueryRelation<UserStatsRelationV3.RelationFilters> {

    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    override val relationName = "v3_user_stats"

    val id: Int
        get() = error("Typed output property must not be read")

    val name: String?
        get() = error("Typed output property must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation =
        buildQueryRelation {
            select {
                addColumn {
                    column(UserModelV3::class, UserModelV3::id)
                        .alias(UserStatsRelationV3::id)
                }
                addColumn {
                    column(UserModelV3::class, UserModelV3::name)
                        .alias(UserStatsRelationV3::name)
                }
            }
            table { table(UserModelV3::class) }
        }
}
~~~

در declaration، alias(Relation::property) خروجی SELECT را به property typed وصل می‌کند؛ property خودِ دیتابیس یا مقدار ستون نیست. Consumer از relationColumn(Relation::property) استفاده می‌کند و نتیجه را با row.getValue(Relation::property) می‌خواند. Relationهای V3 فعلی annotation alias ندارند.

از نظر declaration، UserStatsRelationV3، UserPhoneRelationV3 و UserPageRelationV3 مستقیماً IQueryRelation<RelationFilters> را پیاده می‌کنند. UserDisplayRelationV3 از QueryRelationDeclaration<RelationFilters> ارث‌بری می‌کند. این تفاوت فقط روش ساخت declaration را نشان می‌دهد؛ هر چهار مورد در مصرف به QueryRelation تبدیل می‌شوند.

جزئیات خروجی‌ها:

~~~text
UserStatsRelationV3
    table source: UserModelV3
    outputs: id, name, family, age

UserPhoneRelationV3
    table source: UserModelV3
    INNER JOIN: UserPhoneModelV3
    outputs: id, name, family, age, phone

UserPageRelationV3
    source: UserStatsRelationV3
    outputs: id, name, family, age
    applies limit and offset from RelationFilters

UserDisplayRelationV3
    table source: UserModelV3
    outputs: id, fullName, age
    fullName expression: concat(name, '-', family)
~~~

Relationها از طریق QueryRelation ساخته و به source معرفی می‌شوند:

~~~kotlin
val stats = UserStatsRelationV3().queryRelation(
    UserStatsRelationV3.RelationFilters(userId = 5),
)

val query = QueryRenderSelectBuilder()
    .from(stats)
    .select {
        addColumn { relationColumn(UserStatsRelationV3::id) }
        addColumn { relationColumn(UserStatsRelationV3::name) }
    }
~~~

در صورت دادن source alias، qualifier در مصرف خروجی هم صریح است؛ مثل details در نمونهٔ SELECT این بخش. نام Relation/CTE با alias مربوط به occurrence یکی نیست.

وابستگی Relation چندلایه در UserPageRelationV3 به این شکل است:

~~~text
UserStatsRelationV3
    → UserPageRelationV3
        → SELECT consumer
~~~

UserPage خروجی‌های Stats را با همان propertyهای typed عبور می‌دهد و limit/offset را روی query داخلی اعمال می‌کند. UserPhoneRelation نیز با join بین UserModelV3 و UserPhoneModelV3 فیلد phone را به خروجی اضافه می‌کند. UserDisplayRelation خروجی fullName را از expression محاسبه‌شدهٔ concat نام و نام خانوادگی می‌سازد.

#### SELECT

~~~kotlin
val query = QueryRenderSelectBuilder()
    .from(
        UserPhoneRelationV3().queryRelation(UserPhoneRelationV3.RelationFilters()),
        "details",
    )
    .select {
        addColumn { relationColumn(UserPhoneRelationV3::id, "details") }
        addColumn { relationColumn(UserPhoneRelationV3::name, "details") }
        addColumn { relationColumn(UserPhoneRelationV3::family, "details") }
        addColumn { relationColumn(UserPhoneRelationV3::age, "details") }
        addColumn { relationColumn(UserPhoneRelationV3::phone, "details") }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("id=" + row.getValue(UserPhoneRelationV3::id))
            println("name=" + row.getValue(UserPhoneRelationV3::name))
            println("family=" + row.getValue(UserPhoneRelationV3::family))
            println("phone=" + row.getValue(UserPhoneRelationV3::phone))
        }
        rows
    }
    .error { failure -> println(failure) }
~~~

SQL ساختاری:

~~~sql
WITH v3_user_phone_details AS (
    SELECT uu.id AS id, uu.name AS name, uu.family AS family,
           uu.age AS age, up.phone AS phone
    FROM user_users AS uu
    INNER JOIN user_phones AS up ON uu.id = up.user_id
)
SELECT details.id AS id, details.name AS name, details.family AS family,
       details.age AS age, details.phone AS phone
FROM v3_user_phone_details AS details
~~~

خروجی SELECT:

~~~text
property                       output alias  DataType
UserPhoneRelationV3::id        id            INT
UserPhoneRelationV3::name      name          STRING
UserPhoneRelationV3::family    family        STRING
UserPhoneRelationV3::age       age           INT
UserPhoneRelationV3::phone     phone         STRING
~~~

Qualifier details به‌صورت صریح در from و relationColumn آمده است. مثال‌های A2 تا A7 نیز whereIn، whereLike، شرط null، شرط مقداری، Relation صفحه‌بندی‌شده و خروجی fullName را نشان می‌دهند.

#### INSERT

~~~kotlin
val query = QueryRenderInsertBuilder()
    .table { table(UserModelV3::class) }
    .addValue { column(UserModelV3::class, UserModelV3::name, "Ali") }
    .addValue { column(UserModelV3::class, UserModelV3::family, "Sadegi") }
    .addValue { column(UserModelV3::class, UserModelV3::age, 50) }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { key -> println("generated key: " + key); key }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: INSERT INTO user_users (name, family, age) VALUES (...). نتیجه ExecuteResult<Long> است.

#### UPDATE

~~~kotlin
val query = QueryRenderUpdateBuilder()
    .table { table(UserModelV3::class) }
    .addValue { column(UserModelV3::class, UserModelV3::name, "--changed--") }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn(UserModelV3::class, UserModelV3::id, "uu") }
                operationEqual()
                sideValue("id1", 5)
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { count -> println("updated rows: " + count); count }
    .error { failure -> println(failure) }
~~~

SQL ساختاری: UPDATE user_users AS uu SET uu.name = ... WHERE (uu.id = ...). نتیجه ExecuteResult<Int> است.

#### DELETE

~~~kotlin
val query = QueryRenderDeleteBuilder()
    .addTarget("uu")
    .table { table(UserModelV3::class) }
    .where {
        conditions {
            addCondition {
                logicalAnd()
                sideSelector { tableColumn(UserModelV3::class, UserModelV3::id, "uu") }
                operationEqual()
                sideValue("id1", 5)
            }
        }
    }

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
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
| V3 | Relation property و qualifier صریح در صورت alias؛ خواندن با Relation::property | Model V3 | Model V3 | Model V3 |

SELECT.execute() همهٔ ردیف‌ها را به‌صورت eager و با نوع ExecuteResult<List<QueryRow>> برمی‌گرداند. Consumer متد first() نیز برای دریافت اولین ردیف دارد. INSERT/UPDATE/DELETE فقط پس از فعال‌کردن گزینهٔ مربوط به اجرا اجرا می‌شوند.
