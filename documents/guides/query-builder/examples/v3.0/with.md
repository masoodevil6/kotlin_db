# افزودن CTE به `WITH`

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

---

[بازگشت به فهرست این نسخه](README.md)
