# قرارداد فعلی SELECT FROM

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

---

[بازگشت به فهرست این نسخه](README.md)
