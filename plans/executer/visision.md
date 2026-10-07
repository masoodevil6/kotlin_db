``` kotlin
lass UserDisplayRelation :
QueryRelationDeclaration<UserDisplayRelation.Filters>() {

    data class Filters(
        val userId: Int? = null,
    )

    override val relationName = "user_display"

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
QueryRelationDeclaration<Filters>() {

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

