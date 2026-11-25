# قرارداد مقصد قطعی V3.0 برای Query و پارامترها

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

---

[بازگشت به فهرست این نسخه](README.md)
