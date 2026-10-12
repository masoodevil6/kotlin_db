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

### V3 — CTE-backed SELECT و Model-backed Mutation

در V3، هر Query خروجی‌دار یک Declaration با قرارداد `ICteDeclaration<P>` دارد و برای استفاده در SELECT به یک `Cte` قابل‌ترکیب تبدیل می‌شود. `@QBQuery.name` نام canonical تعریف CTE را مشخص می‌کند؛ `@QBQuery.alias` alias پیش‌فرض source در محل استفاده است. این دو مقدار نقش جداگانه دارند. INSERT، UPDATE و DELETE همچنان با `UserModelV3` ساخته می‌شوند.

#### CTE Declarationهای V3

چهار Declaration نمونه در V3 عبارت‌اند از:

| Declaration | `QBQuery.name` (نام CTE) | `QBQuery.alias` پیش‌فرض source | ورودی‌ها | خروجی‌ها |
|---|---|---|---|---|
| UserStatsQueryV3 | `user_stats_query_v3` | `v3_user_stats` | `userId` و `minAge` اختیاری | `id`, `name`, `family`, `age` |
| UserPhoneQueryV3 | `user_phone_query_v3` | `v3_user_phone_details` | `userId` اختیاری | `id`, `name`, `family`, `age`, `phone` |
| UserPageQueryV3 | `user_page_query_v3` | `v3_user_page` | `limit` پیش‌فرض 2 و `offset` پیش‌فرض 0 | `id`, `name`, `family`, `age`؛ از Stats عبور داده می‌شوند |
| UserDisplayQueryV3 | `user_display_query_v3` | `v3_user_display` | `userId` و `minAge` اختیاری | `id`, `fullName`, `age` |

قرارداد `ICteDeclaration<P>` پارامترهای متصل به Declaration را از `params` ارائه می‌کند و `createCte(params)` را برای ساخت `Cte` تعریف می‌کند. سه کلاس `UserStatsQueryV3`، `UserPhoneQueryV3` و `UserPageQueryV3` مستقیماً این interface را پیاده می‌کنند؛ `UserDisplayQueryV3` از کلاس پایهٔ اختیاری `CteDeclaration<P>` استفاده می‌کند. `CteDeclaration<P>` پارامترها را نگه می‌دارد و `buildCte { ... }` را برای ساخت تعریف typed فراهم می‌کند.

برای ساخت مستقیم یک تعریف، Builder دو factory به نام `cte(name, block)` و `cte(name, declarationOwner, block)` ارائه می‌کند. Extension به نام `buildCte(block)` روی `ICteDeclaration<*>`، نام و مالک Declaration را برای ساخت CTE typed به کار می‌گیرد.

در `@QBQuerySelect`, مقدار `name` نام منطقی خروجی است و `alias` نام SQL منتشرشده است. Propertyهای typed فقط مرجع metadata هستند؛ Getter آن‌ها نباید برای resolve کردن metadata خوانده شود.

نمونهٔ خلاصه‌شدهٔ قرارداد و ساخت CTE در `UserStatsQueryV3` است؛ بخش اعمال فیلترهای اختیاری برای اختصار حذف شده و در فایل منبع وجود دارد:

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

    override fun createCte(params: RelationFilters): Cte = buildCte {
        select {
            addColumn { column(UserModelV3::class, UserModelV3::id).alias(UserStatsQueryV3::id) }
            addColumn { column(UserModelV3::class, UserModelV3::name).alias(UserStatsQueryV3::name) }
            addColumn { column(UserModelV3::class, UserModelV3::family).alias(UserStatsQueryV3::family) }
            addColumn { column(UserModelV3::class, UserModelV3::age).alias(UserStatsQueryV3::age) }
        }
        from { model(UserModelV3::class) }
        // The source applies its optional userId and minAge filters here.
    }
}
~~~

در فایل منبع، `buildCte` از Declaration و مالک concrete آن برای ساخت `Cte` استفاده می‌کند. `alias(Declaration::property)` در تعریف خروجی، property typed را به خروجی SELECT وصل می‌کند. این property مقدار ستون نیست و Getter آن اجرا نمی‌شود. برای ارجاع به خروجی در SELECT مصرف‌کننده از `cteOutput(Declaration::property)` استفاده می‌شود.

جزئیات خروجی‌ها:

~~~text
UserStatsQueryV3
    table source: UserModelV3
    outputs: id, name, family, age

UserPhoneQueryV3
    table source: UserModelV3
    INNER JOIN: UserPhoneModelV3
    outputs: id, name, family, age, phone

UserPageQueryV3
    source: UserStatsQueryV3
    outputs: id, name, family, age
    applies limit and offset from RelationFilters

UserDisplayQueryV3
    table source: UserModelV3
    outputs: id, fullName, age
    fullName expression: concat(name, '-', family)
~~~

`cte(declaration)` در `IQueryTableApi`، Declaration را به CTE قابل‌ترکیب تبدیل و به منبع SELECT اضافه می‌کند. overload مستقل `cte(name)` همچنان CTE را بر اساس نام string انتخاب می‌کند. نمونهٔ زیر از `cte(declaration)` استفاده می‌کند و alias را فقط برای occurrence فعلی تعیین می‌کند:

~~~kotlin
val statsDeclaration = UserStatsQueryV3(
    UserStatsQueryV3.RelationFilters(userId = 5),
)

val query = QueryRenderSelectBuilder()
    .from {
        cte(statsDeclaration)
        alias("stats")
    }
    .select {
        addColumn { cteOutput(UserStatsQueryV3::id, "stats") }
        addColumn { cteOutput(UserStatsQueryV3::name, "stats") }
    }
~~~

در این مثال، `Cte` نوع تعریف composable است، درحالی‌که `ICteDeclaration<P>` قرارداد ساخت آن از پارامترهای Declaration است. alias صریح `stats` فقط نام occurrence در همین SELECT است؛ نام canonical CTE از `QBQuery.name` می‌آید.

وابستگی CTE چندلایه در UserPageQueryV3 به این شکل است:

~~~text
UserStatsQueryV3
    → UserPageQueryV3
        → SELECT consumer
~~~

`UserPageQueryV3` خروجی‌های `UserStatsQueryV3` را با propertyهای typed ارجاع می‌دهد و `limit`/`offset` را اعمال می‌کند. `UserPhoneQueryV3` با join بین `UserModelV3` و `UserPhoneModelV3` فیلد phone را اضافه می‌کند. `UserDisplayQueryV3` خروجی `fullName` را از expression محاسبه‌شدهٔ concat نام و نام خانوادگی می‌سازد.

#### SELECT

~~~kotlin
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.relations.UserPhoneQueryV3

val query = QueryRenderSelectBuilder()
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

queryManager.queryBuilder(query)
    .sql { builtQuery -> println(builtQuery.query + "\n" + builtQuery.params) }
    .execute()
    .success { rows ->
        rows.forEach { row ->
            println("id=" + row.getValue(UserPhoneQueryV3::id))
            println("name=" + row.getValue(UserPhoneQueryV3::name))
            println("family=" + row.getValue(UserPhoneQueryV3::family))
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

در metadata خروجی، نام منطقی و alias SQL مستقل‌اند؛ برای نمونه، `UserPhoneQueryV3::id` نام منطقی `id` و alias منتشرشدهٔ `user_id` دارد. نوع خروجی نیز از نوع property استنباط می‌شود:

~~~text
property                       logical name  SQL alias    Kotlin type
UserPhoneQueryV3::id        id            user_id      Int
UserPhoneQueryV3::name      name          user_name    String?
UserPhoneQueryV3::family    family        user_family  String?
UserPhoneQueryV3::age       age           user_age     Int?
UserPhoneQueryV3::phone     phone         user_phone   String?
~~~

Qualifier `details` به‌صورت صریح در `from { ... }` و `cteOutput(property, qualifier)` آمده است. برای ارجاع به ستون CTE با نام‌ها و aliasهای SQL خام، `cteColumn(alias, selectName)` API جداگانه‌ای در سطح `IQueryColumnsBaseApi` است؛ این متد جایگزین ارجاع declaration-aware به کمک `cteOutput(...)` نیست. برای افزودن یک CTE ساخته‌شده به `WITH` می‌توان از `with(cte)` یا `with(withName, cte)` استفاده کرد؛ overload اول نام مؤثر خود CTE را می‌گیرد و overload دوم نام صریح caller را بر آن مقدم می‌کند، بدون آنکه نام runtime خود شیء `Cte` تغییر کند. نام occurrence/source از این نام جداست و از alias پیش‌فرض یا alias صریح همان occurrence می‌آید. `ICteDeclaration` ورودی ساخت CTE است. قرارداد legacy `ICte` و overloadهای `with(name, ICte)` و `with(name, block)` همچنان حفظ شده‌اند. همچنین `cte(String)` برای تعیین source با نام و `cteColumn(...)` برای ارجاع SQL-facing باقی مانده‌اند.

مثال‌های A2 تا A7 نیز whereIn، whereLike، شرط null، شرط مقداری، CTE صفحه‌بندی‌شده و خروجی fullName را نشان می‌دهند.

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
| V3 | خروجی Declaration با `cteOutput(...)` و qualifier صریح در صورت نیاز؛ خواندن با `UserStatsQueryV3::property` | Model V3 | Model V3 | Model V3 |

SELECT.execute() همهٔ ردیف‌ها را به‌صورت eager و با نوع ExecuteResult<List<QueryRow>> برمی‌گرداند. Consumer متد first() نیز برای دریافت اولین ردیف دارد. INSERT/UPDATE/DELETE فقط پس از فعال‌کردن گزینهٔ مربوط به اجرا اجرا می‌شوند.
