# Queryهای V3

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
import gog.my_project.data_base_query.api.interfaces.relations.Cte
import gog.my_project.data_base_query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base_query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base_query.example.v3.queries.models.UserModelV3

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

---

[بازگشت به فهرست این نسخه](README.md)
