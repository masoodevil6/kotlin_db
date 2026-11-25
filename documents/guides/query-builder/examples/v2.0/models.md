# Modelهای V2

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

---

[بازگشت به فهرست این نسخه](README.md)
