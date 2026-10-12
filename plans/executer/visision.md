``` kotlin
@QBQuery(name = "user_display")
class UserDisplayRelation :
QueryDeclaration<UserDisplayRelation.Filters>() {

    data class Filters(
        val userId: Int? = null,
    )

    val id: Int
        get() = error("Declaration only")

    val name: String
        get() = error("Declaration only")

    val age: Int?
        get() = error("Declaration only")

    override fun queryRelation(
        params: Filters,
    ): QueryRelation = buildQueryRelation {

        select {
            addColumn {
                column(UserModel::id)
                    .alias(UserDisplayRelation::id)
            }

            addColumn {
                column(UserModel::name)
                    .alias(UserDisplayRelation::name)
            }

            addColumn {
                column(UserModel::age)
                    .alias(UserDisplayRelation::age)
            }
        }

        table {
            table(UserModel::class)
        }
    }
}
```
----





``` kotlin
val relation = UserDisplayRelation()

val query = select {
from(relation)

    select {
        addColumn(UserDisplayRelation::id)
        addColumn(UserDisplayRelation::name)
        addColumn(UserDisplayRelation::age)
    }
}
```
----




``` kotlin
val result = query.execute()
```
----






``` kotlin
for (row in result) {
    val id: Int =
        row[UserDisplayRelation::id]

    val name: String =
        row[UserDisplayRelation::name]

    val age: Int? =
        row[UserDisplayRelation::age]
}
```
----




``` kotlin
row.getInt("id")
row.getString("name")
row.getNullableInt("age")
```














الان Relationها یک مسئله‌ی نسبتاً پیشرفته‌تر هستند. اگر مستقیم Result Mapping را روی Relation سوار کنیم، دو مسئله را همزمان حل می‌کنیم:

هوشمند کردن SELECT → execute → ResultSet
هوشمند کردن Relation → typed output → execute

و بعد معلوم نیست کدام بخش خراب شده. بشر برای ساختن دیباگ‌های چندلایه ظاهراً به اندازه کافی انگیزه دارد.

چیزی که الان واقعاً داریم

در ساده‌ترین Query:

Query Builder
↓
IQueryRenderSelectApi
↓
SQL
↓
Executor
↓
ResultSet
↓
دستی:
rs.getInt("user_id")
rs.getString("user_name")
...

مشکل اصلی همین قسمت آخر است.

خود Query Builder می‌داند چه چیزهایی انتخاب شده‌اند:

alias("user_id")
alias("user_name")
alias("user_family")
alias("user_age")
alias("user_phone")

اما Executor دوباره مجبور است این اطلاعات را دستی تکرار کند:

rs.getInt("user_id")
rs.getString("user_name")
rs.getString("user_family")
rs.getInt("user_age")
rs.getString("user_phone")

این duplication دقیقاً جایی است که باید هدف بگیریم.

Vision مرحله اول

من فعلاً Relation را کاملاً کنار می‌گذارم.

هدف:

یک SELECT معمولی بتواند ResultSet خودش را به‌شکل typed و قابل استفاده expose کند، بدون اینکه کاربر نام SQL column و getter JDBC را دوباره بنویسد.

مثلاً همین Query:

.select {
addColumn {
column { tableColumn("uu", "id") }
alias("user_id")
}

    addColumn {
        column { tableColumn("uu", "name") }
        alias("user_name")
    }

    addColumn {
        column { tableColumn("uu", "family") }
        alias("user_family")
    }

    addColumn {
        column { tableColumn("uu", "age") }
        alias("user_age")
    }

    addColumn {
        column { tableColumn("up", "phone") }
        alias("user_phone")
    }
}

باید خودش تبدیل شود به یک Result Contract.

مثلاً مفهومی:

SELECT OUTPUT
├── user_id
├── user_name
├── user_family
├── user_age
└── user_phone

و executor بتواند این Contract را مصرف کند.

اما من از DTO شروع نمی‌کنم

مثلاً فعلاً نمی‌خواهم بگوییم:

data class UserResult(
val id: Int,
val name: String,
val family: String,
val age: Int,
val phone: String,
)

و بعد:

execute<UserResult>()

چون این یک Result Mapping System جدید است که باید جداگانه طراحی شود.

در حالی که مسئله‌ی فعلی خیلی کوچک‌تر است.

قدم اول بهتر

اول باید ببینیم آیا خود select می‌تواند خروجی typed تعریف کند.

مثلاً vision می‌تواند به چیزی شبیه این برسد:

val query = QueryRenderSelectBuilder()
.select {
addColumn {
column { tableColumn("uu", "id") }
.alias("user_id")
}

        addColumn {
            column { tableColumn("uu", "name") }
                .alias("user_name")
        }
    }

و بعد:

query.execute(...)

نتیجه چیزی مثل:

result.forEach { row ->
println(row["user_id"])
println(row["user_name"])
}

این اولین مرحله است.

اما حتی این هم هنوز به اندازه کافی typed نیست.

مرحله دوم

بعد باید ببینیم آیا می‌توانیم type را از declaration انتخاب استخراج کنیم.

مثلاً اگر API فعلی امکان چیزی شبیه این را داشته باشد:

addColumn {
column(UserModel::id)
.alias("user_id")
}

یا:

addColumn(UserModel::id)
.alias("user_id")

آن وقت می‌توانیم به این برسیم:

SQL Output
│
├── name = user_id
└── type = Int

و Executor می‌فهمد:

user_id → getInt()

در نتیجه:

row["user_id"]

می‌تواند Int باشد.

مرحله سوم: Typed Selector

بعد می‌توانیم selector را از String جدا کنیم.

مثلاً مفهومی:

val id = result.column("user_id")

بعد:

val id: Int = result.column("user_id")

اما این هنوز compile-time safe نیست.

نسخه‌ی بهتر:

val id = result[UserModel::id]

یا یک Output Reference مستقل:

val id = result[UserModel::id]

اینجا تازه داریم به vision Relation نزدیک می‌شویم.

و بعد Relation خیلی طبیعی می‌شود

بعد از اینکه SELECT معمولی این pipeline را داشته باشد:

SELECT
↓
Output Definition
↓
Query
↓
Executor
↓
Typed Result

Relation می‌تواند فقط یک extension روی همین سیستم باشد:

Relation
↓
SELECT Definition
↓
Typed Outputs
↓
Query
↓
Executor
↓
Typed Result

یعنی Relation دیگر نباید یک Result Mapping مستقل داشته باشد.

مثال نهایی Relation

در vision نهایی:

class UserDisplayRelation :
QueryDeclaration<Filters>() {

    val id: Int
        get() = error("declaration only")

    val fullName: String?
        get() = error("declaration only")

    val age: Int?
        get() = error("declaration only")

    override fun queryRelation(
        params: Filters,
    ): QueryRelation = buildQueryRelation {
        select {
            addColumn {
                column(UserModel::id)
                    .alias(UserDisplayRelation::id)
            }

            addColumn {
                column(...)
                    .alias(UserDisplayRelation::fullName)
            }

            addColumn {
                column(UserModel::age)
                    .alias(UserDisplayRelation::age)
            }
        }
    }
}

ولی Executor از همان مکانیزمی استفاده می‌کند که برای SELECT عادی ساخته‌ایم.

پس:

                    ┌──────────────────┐
                    │   SELECT عادی    │
                    └────────┬─────────┘
                             │
                             ▼
                    Typed Output Model
                             │
                             ▼
                         Executor
                             │
                             ▼
                        Typed Row
                             ▲
                             │
                    ┌────────┴─────────┐
                    │     Relation     │
                    └──────────────────┘

این معماری خیلی سالم‌تر است.

یک نکته خیلی مهم درباره همین کد فعلی

من حتی پیشنهاد می‌کنم قبل از طراحی API جدید، همین A1ExampleSelectV1 را به‌عنوان baseline نگه داریم.

چون این مثال دقیقاً همه چیز لازم را دارد:

5 selected columns
5 aliases
2 SQL tables
JOIN
WHERE
parameters
ResultSet

پس می‌تواند test case طلایی ما باشد.

الان:

val id = rs.getInt("user_id")
val name = rs.getString("user_name")
val family = rs.getString("user_family")
val age = rs.getInt("user_age")
val phone = rs.getString("user_phone")

هدف این نیست که فوراً این را به یک API عجیب تبدیل کنیم.

هدف اول فقط این است که همان اطلاعاتی که Query Builder از قبل دارد، به Execution layer منتقل شود.

مسیر پیشنهادی من

به ترتیب:

Phase 1: Traceability

بررسی کنیم:

QueryRenderSelectAst
QuerySelectAst
SelectItem AST
Column AST
Alias
IQueryRenderSelectApi
QueryRenderSelectBuilder
IQueryBuilderExecutor
ExecuteResult
ResultSet handling

و مشخص کنیم الان اطلاعات SELECT دقیقاً کجا وجود دارد و کجا از بین می‌رود.

Phase 2: Contract

مشخص کنیم کوچک‌ترین abstraction لازم برای:

SELECT Output → ResultSet

چیست.

بدون Relation.

بدون DTO.

بدون Generic Result Framework.

بدون Reflection جدید مگر اینکه repository evidence آن را توجیه کند.

Phase 3: Implementation

همان A1ExampleSelectV1 را با API جدید اجرا کنیم.

Phase 4: Verification

باید ثابت شود:

SQL همان است
parameters همان‌اند
column ordering همان است
aliases همان‌اند
JDBC types درست خوانده می‌شوند
NULL درست مدیریت می‌شود
executor failure همان semantics را حفظ می‌کند
Phase 5: Relation

بعد از اینکه SELECT عادی حل شد، برمی‌گردیم سراغ:

UserDisplayRelation::age

و می‌گوییم:

حالا این property چطور می‌تواند Typed Output همان سیستم SELECT باشد؟

در آن نقطه احتمالاً جواب خیلی روشن‌تر خواهد بود، چون دیگر لازم نیست همزمان Executor را هم طراحی کنیم.

پس بله، من این تغییر مسیر را تأیید می‌کنم: اول Smart Execute برای SELECT عادی، بعد Typed Relation Result. این ترتیب از نظر معماری هم کم‌ریسک‌تر است و هم اجازه می‌دهد Relation واقعاً روی یک قابلیت پایه‌ی تثبیت‌شده سوار شود، نه اینکه خودش تبدیل به آزمایشگاه Result Mapping شود.




-----------------------------------
Vision تمیزتر این است:

QueryRenderSelectBuilder
│
├── sql()
│
├── first()
│
└── get()

یعنی وقتی Query ساخته شد، خودش نقطه ورود تمام عملیات اصلی است.

مثال کامل
val query = QueryRenderSelectBuilder()
.select {
addColumn {
column {
tableColumn("uu", "id")
}
alias("id")
execute(DataType.INT)
}

        addColumn {
            column {
                tableColumn("uu", "name")
            }
            alias("name")
            execute(DataType.STRING)
        }

        addColumn {
            column {
                tableColumn("uu", "family")
            }
            alias("family")
            execute(DataType.STRING)
        }
    }
    .table {
        table("user_users", "uu")
    }

حالا برنامه‌نویس دیگر با execution internals کاری ندارد.

SQL
val sql = query.sql()

مثلاً:

SELECT
uu.id AS id,
uu.name AS name,
uu.family AS family
FROM user_users AS uu
یک Row
val result = query.first()

نتیجه:

Row?

و:

val userId = result["id"]

یا اگر typed property API داشته باشیم:

val userId = result.id

با توجه به:

execute(DataType.INT)

نوع userId می‌تواند Int باشد.

چند Row
val result = query.get()

نتیجه:

Result
├── row(0)
├── row(1)
├── row(2)
└── ...

مصرف:

val userId = result.row(0)["id"]

یا:

val userId = result.row(0).id

و:

for (row in result) {
println(row.name)
}

اینجا دیگر:

while (rs.next())

کاملاً از application code حذف شده است.

نقش دقیق execute(DataType.INT)

اینجا نکته‌ای که گفتی خیلی مهم است.

execute(...) جزء تعریف عمومی Column نیست.

این:

addColumn {
column {
tableColumn("uu", "id")
}
alias("id")
execute(DataType.INT)
}

در واقع می‌گوید:

SELECT output
│
├── SQL identity → "id"
│
└── Result extraction → INT

و فقط وقتی Result به آن output اشاره می‌کند، مورد استفاده قرار می‌گیرد:

result["id"]

یا:

result.id
یک نکته ظریف درباره alias

به نظرم اینجا باید یک قرارداد مهم داشته باشیم.

اگر بنویسیم:

column {
tableColumn("uu", "id")
}
alias("user_id")
execute(DataType.INT)

کلید Result باید:

result["user_id"]

باشد، نه:

result["id"]

چون آخرین SELECT alias منبع حقیقت output identity است.

بنابراین:

column source
↓
uu.id

alias
↓
user_id

Result key
↓
"user_id"

این برای Relationها هم حیاتی است.

بنابراین Vision نهایی
Query
│
┌──────────┼──────────┐
▼          ▼          ▼
sql()      first()     get()
│          │
▼          ▼
Row?       Result
│          │
┌─────┴─────┐    ├── row(0)
│           │    ├── row(1)
▼           ▼    └── ...
["id"]       .id
│           │
└─────┬─────┘
▼
DataType.INT
│
▼
Int

و این API خیلی به چیزی که از Laravel می‌خواهی نزدیک است:

val query = QueryRenderSelectBuilder()
.select { ... }
.table { ... }

val sql = query.sql()

val user = query.first()

val users = query.get()

بعد:

val id = user["id"]

یا:

val id = user.id

و برای collection:

val id = users.row(0)["id"]

یا:

val id = users.row(0).id
فقط یک نکته مهم

result.id یک API خیلی بالاتر و جذاب‌تر است، ولی نباید برای رسیدن به آن از الان Reflection/DTO/Code Generation یا سیستم پیچیده‌ای بسازیم.

Primitive پایه باید اول این باشد:

result["id"]

و اگر repository شواهد کافی برای typed property access داشته باشد، بعداً:

result.id

می‌تواند sugar روی همان contract باشد.

پس هسته Vision این است:

Query خودش API مصرف دارد. sql(), first(), get() عملیات سطح Query هستند. first() یک Row می‌دهد، get() مجموعه Row می‌دهد، و هر Row خروجی‌ها را با alias نهایی SELECT مصرف می‌کند. execute(DataType) قرارداد extraction همان output است. ResultSet و while(next()) جزئیات داخلی Executor هستند و نباید وارد application code شوند.

این دیگر یک Vision مشخص است، نه مجموعه‌ای از تکه‌های API که هر کدام از یک گوشه افتاده‌اند.







```kotlin
package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.relations.QueryDeclaration
import gog.my_project.data_base.query.example.v3.queries.models.UserModelV3
import gog.my_project.data_base.core.annotations.queries.QBQuery
import gog.my_project.data_base.core.annotations.queries.QBQuerySelect

@QBQuery(
    name = "v3_user_display",
    alias = "uud"
)
class UserDisplayQueryV3 :
    QueryDeclaration<UserDisplayQueryV3.RelationFilters>() {

    data class RelationFilters(
        val userId: Int? = null,
        val minAge: Int? = null,
    )

    @QBQuerySelect(
        name = "id",
        alias = "id"
    )
    val id: Int
        get() = error("Typed output property must not be read")

    @QBQuerySelect(
        name = "full_name",
        alias = "full_name"
    )
    val fullName: String?
        get() = error("Typed output property must not be read")

    @QBQuerySelect(
        name = "age",
        alias = "age"
    )
    val age: Int?
        get() = error("Typed output property must not be read")

    override fun queryRelation(
        params: RelationFilters
    ): QueryRelation = buildQueryRelation {
        select {
            addColumn {
                column(UserModelV3::class, UserModelV3::id)
                    .alias(UserDisplayQueryV3::id)
            }

            addColumn {
                column {
                    tableAttribute("concat( uu.name , '-' , uu.family )")
                }.alias(UserDisplayQueryV3::fullName)
            }

            addColumn {
                column(UserModelV3::class, UserModelV3::age)
                    .alias(UserDisplayQueryV3::age)
            }
        }

        table {
            table(UserModelV3::class)
        }

        if (params.userId != null || params.minAge != null) {
            where {
                conditions {
                    params.userId?._let_ { userId ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableColumn(
                                    UserModelV3::class,
                                    UserModelV3::id,
                                    "uu"
                                )
                            }
                            operationEqual()
                            sideValue("relation_user_id", userId)
                        }
                    }

                    params.minAge?._let_ { minAge ->
                        addCondition {
                            logicalAnd()
                            sideSelector {
                                tableColumn(
                                    UserModelV3::class,
                                    UserModelV3::age,
                                    "uu"
                                )
                            }
                            operationGreaterThanOrEqual()
                            sideValue("relation_min_age", minAge)
                        }
                    }
                }
            }
        }
    }
}
```


```kotlin

package gog.my_project.data_base.query.example.v3.queries.select

import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.UserDisplayQueryV3
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class A7ExampleSelectV3 {

    fun query() = QueryRenderSelectBuilder()
        .from(
            UserDisplayQueryV3().queryRelation(
                UserDisplayQueryV3.RelationFilters(
                    userId = 10,
                    minAge = 18
                )
            )
        )
        .select {
            addColumn {
                relationColumn(UserDisplayQueryV3::id)
            }

            addColumn {
                relationColumn(UserDisplayQueryV3::fullName)
            }

            addColumn {
                relationColumn(UserDisplayQueryV3::age)
            }
        }

    fun execute(queryManager: IQueryBuilderExecutor) {
        val query = query()

        queryManager
            .queryBuilder(query)
            .sql { (queryText, params) ->
                println("query: $queryText")
                println("params: $params")
            }
            .execute()
            .success { rows ->
                rows.forEach { row ->
                    println(
                        "id=${row.getValue(UserDisplayQueryV3::id)}, " +
                        "fullName=${row.getValue(UserDisplayQueryV3::fullName)}, " +
                        "age=${row.getValue(UserDisplayQueryV3::age)}"
                    )
                }
                rows
            }
            .error { failure ->
                println("error: $failure")
            }
    }
}

```

```kotlin
addColumn {
    column {
        tableColumn("details", UserPhoneQueryV3::id)
    }
    alias("user_id")
    execute(DataType.LONG)
}
```

```
tableAlias = "details"
columnName = "id"
columnAlias = "id"
columnDataType = DataType.INT
```

```
tableAlias = "details"
columnName = "id"
columnAlias = "user_id"
columnDataType = DataType.LONG
```








## توضیح قواعد اولویت‌بندی نام‌گذاری در Model و Query

هدف این است که نام کلاس، نام Property، نام منطقی و alias در SQL به‌عنوان مفاهیم مستقل در نظر گرفته شوند و ترتیب اولویت آن‌ها هنگام ساخت Metadata مشخص باشد.

این قواعد باید در چهار بخش بررسی و یکپارچه شوند:

### ۱. Metadata اولیه کلاس Model

در سطح کلاس Model، باید مشخص شود نام جدول و alias آن از کجا به دست می‌آیند.

برای مثال:

```kotlin
@QBTable(
    name = "user_users",
    alias = "uu"
)
class UserModelV3
```

در این مثال، نام کلاس Kotlin برابر `UserModelV3`، نام جدول برابر `user_users` و alias جدول برابر `uu` است.

نام کلاس، نام جدول و alias جدول نباید یک مقدار واحد تلقی شوند. اگر نام یا alias به‌صورت صریح در Annotation تعریف شده باشد، باید طبق قرارداد metadata اولویت‌بندی شود. در صورت تنظیم‌نشدن مقدار، fallback باید مطابق قواعد فعلی `QBTable` تعیین شود.

اگر DSL امکان override کردن نام یا alias جدول را فراهم می‌کند، باید مشخص شود این override در چه سطحی اعمال می‌شود و آیا فقط همان ارجاع در Query را تغییر می‌دهد یا تعریف اصلی Model را.

### ۲. Metadata اولیه Propertyهای Model

در سطح Propertyهای Model نیز باید نام Property در Kotlin از نام ستون و alias آن جدا باشد.

برای مثال:

```kotlin
@QBColumn(
    name = "user_id",
    alias = "id",
    primaryKey = true
)
val id: Int
```

در این مثال:

```text
propertyName = id
columnName   = user_id
columnAlias  = id
primaryKey   = true
```

نام Property در Kotlin هویت فنی آن است. نام ستون و alias از Metadata مربوط به Property به دست می‌آیند و ممکن است با نام Property یکسان نباشند.

اگر DSL مقدار alias را تغییر دهد، باید فقط alias مؤثر تغییر کند؛ نام ستون منبع و سایر ویژگی‌های Property نباید به‌صورت ضمنی تغییر کنند.

ترتیب اولویت مقادیر صریح Annotation، مقادیر پیش‌فرض و overrideهای DSL باید از پیاده‌سازی فعلی `QBColumn` استخراج شود و مبنای قواعد مشترک قرار گیرد.

### ۳. Metadata اولیه کلاس Query

در سطح کلاس Query، همین جداسازی باید برای هویت Query، نام منطقی آن و alias آن رعایت شود.

برای مثال:

```kotlin
@QBQuery(
    name = "v3_user_display",
    alias = "uud"
)
class UserDisplayQueryV3
```

در این مثال:

```text
className = UserDisplayQueryV3
queryName = v3_user_display
queryAlias = uud
```

نام کلاس Kotlin هویت فنی کلاس است؛ `QBQuery.name` نام منطقی Query را مشخص می‌کند و `QBQuery.alias` alias پیش‌فرض آن را تعیین می‌کند.

هنگام استفاده از Query به‌عنوان منبع یک Query دیگر، اگر DSL امکان تعیین alias جدید را داشته باشد، باید alias صریح همان استفاده بر alias پیش‌فرض Query اولویت داشته باشد؛ بدون آنکه نام منطقی Query تغییر کند.

قواعد پیش‌فرض و fallback در `QBQuery` باید با قواعد `QBTable` هماهنگ باشند، مگر اینکه تفاوت مشخصی در قرارداد وجود داشته باشد.

### ۴. Metadata اولیه Propertyهای Query

در Propertyهای Query باید سه مفهوم مستقل وجود داشته باشد:

- نام Property در Kotlin
- نام منطقی خروجی
- alias منتشرشده در SQL

برای مثال:

```kotlin
@QBQuerySelect(
    name = "full_name",
    alias = "full_name"
)
val fullName: String?
    get() = error("Typed output property must not be read")
```

در این حالت:

```text
propertyName = fullName
logicalName  = full_name
columnAlias  = full_name
```

اگر در DSL از این عبارت استفاده شود:

```kotlin
.alias("display_name")
```

نتیجه باید چنین باشد:

```text
propertyName = fullName
logicalName  = full_name
columnAlias  = display_name
```

در این مثال، override فقط alias خروجی SQL را تغییر می‌دهد. نام منطقی و هویت Property ثابت می‌مانند.

برای `QBQuerySelect.name` و `QBQuerySelect.alias` نیز باید قواعد fallback مشخص باشند. اگر نام منطقی صریح تنظیم نشده باشد، رفتار جایگزین باید از قرارداد نهایی metadata پیروی کند. اگر قرارداد استفاده از نام Property به‌عنوان پیش‌فرض را تعیین می‌کند، این رفتار باید صریح و قابل آزمون باشد.

همچنین `QueryRow.getValue(KProperty)` باید همچنان با استفاده از هویت Property و نگاشت موجود به SQL alias کار کند. برای استخراج Metadata نباید Getter مربوط به Property فراخوانی شود.

### قواعد مشترک مورد انتظار

در هر چهار بخش، موارد زیر باید رعایت شوند:

1. نام کلاس یا Property در Kotlin با نام منطقی یا alias یکی فرض نشود.
2. اولویت مقادیر صریح Annotation، مقادیر پیش‌فرض و overrideهای DSL مشخص و مستند باشد.
3. override هر مقدار فقط همان مقدار را تغییر دهد، مگر آنکه قرارداد موجود خلاف آن را صریحاً تعریف کرده باشد.
4. قواعد fallback در `QBQuery` و `QBQuerySelect` تا حد امکان با قواعد موجود `QBTable` و `QBColumn` یکسان باشد.
5. مقدار تنظیم‌نشده از مقدار صریح خالی قابل تشخیص باشد، در صورتی که قرارداد metadata این تمایز را پشتیبانی می‌کند.
6. نام ستون منبع، alias خروجی و نوع داده مستقل از یکدیگر resolve شوند.
7. اعتبارسنجی alias و یکتایی نام منطقی خروجی‌های Query در محدوده صحیح حفظ شود.
8. تغییرات به حداقل لازم محدود بمانند و تا زمانی که نیاز مشخصی اثبات نشده، ساختار AST، Executor، `QueryRow`، Materializer و امضاهای API عمومی تغییر نکنند.

**نکته مهم:** ترتیب دقیق اولویت‌ها و fallbackها نباید بر اساس حدس تعیین شود. ابتدا باید قرارداد و پیاده‌سازی فعلی `QBTable` و `QBColumn` بررسی شود؛ سپس قواعد متناظر برای `QBQuery` و `QBQuerySelect` تعریف و تفاوت‌های ضروری مستند شوند.

فعلاً هدف، تکمیل Plan و مشخص‌کردن قواعد است، نه اجرای تغییرات کد.





















فایل فعلی	نام پیشنهادی	محل پیشنهادی	دلیل
api/interfaces/relations/IQueryRelation.kt	ICte.kt	query:api	قرارداد عمومی یک CTE
api/interfaces/relations/QueryRelation.kt	Cte.kt	query:api	مدل CTE شامل نام، تعریف و وابستگی‌ها
api/interfaces/relations/QueryDefinition.kt	فعلاً QueryDefinition.kt	query:api	تعریف SELECT و پارامترها؛ نام آن لزوماً مختص CTE نیست
builder/relations/QueryDeclaration.kt	CteDeclaration.kt	query:builder	تعریف قابل‌استفاده برای اعلام یک CTE
builder/relations/QueryRelationBuilder.kt	CteBuilder.kt یا CteDefinitionBuilder.kt	query:builder	ساخت و اعتبارسنجی تعریف CTE
builder/.../QueryRenderSelectBuilder.kt	بدون تغییر نام فایل	query:builder	سازنده عمومی SELECT است؛ فقط بخش‌های مربوط به Relation باید به CTE تغییر نام دهند
builder/.../QuerySourceContext.kt	بدون تغییر نام فایل	query:builder	مدیریت context منابع و aliasها؛ نباید بدون دلیل CTE-specific شود
builder/.../QueryTableBuilder.kt	بدون تغییر نام فایل	query:builder	API عمومی افزودن منبع به SELECT؛ نام متدها باید جداگانه بررسی شود
builder/.../QueryColumnsBuilder.kt	بدون تغییر نام فایل	query:builder	مسئولیت عمومی ستون‌ها
builder/.../QuerySelectBuilder.kt	بدون تغییر نام فایل	query:builder	مسئولیت عمومی ساخت SELECT
builder/.../QueryBuilderMetadata.kt	بدون تغییر نام فایل	query:builder	متادیتای عمومی Builder، مگر اینکه بررسی کد نشان دهد بخشی مختص Relation است
مثال‌های v3/queries/relations/	v3/queries/ctes/	query:example	همسان‌سازی نام‌گذاری مثال‌ها با مفهوم جدید