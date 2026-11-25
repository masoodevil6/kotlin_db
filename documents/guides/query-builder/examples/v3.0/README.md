<a id="query-builder-example-3-0"></a>
# 3.0 — CTE-backed SELECT و Model-backed Mutation

در V3، SELECTها از declarationهای Query استفاده می‌کنند که به CTE و sourceهای ترکیبی تبدیل می‌شوند. نام پیش‌فرض CTEهای declaration-based از `QBQuery.name` می‌آید؛ `QBQuery.alias` alias پیش‌فرض occurrence است. در API فعلی، declaration با `cte(declaration)` داخل بلوک `from { ... }` انتخاب می‌شود و `alias("...")` همان source occurrence را نام‌گذاری می‌کند. `cte(name)` نیز برای انتخاب CTE بر اساس نام باقی می‌ماند. هر خروجی با `QBQuerySelect` نام منطقی و SQL alias خودش را مشخص می‌کند. INSERT، UPDATE و DELETE همچنان با `UserModelV3` ساخته می‌شوند.

## مثال‌های این نسخه

- [انتخاب FROM و Source](sources.md)
- [تعریف Queryها و خروجی‌های typed](query-declarations.md)
- [قرارداد Query و پارامترها](query-contract.md)
- [SELECT و CTE](select.md)
- [افزودن CTE به WITH](with.md)
- [INSERT](insert.md)
- [UPDATE](update.md)
- [DELETE](delete.md)

[بازگشت به فهرست همهٔ نسخه‌ها](../README.md)
