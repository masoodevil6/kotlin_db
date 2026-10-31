# Collection

ماژول Collection یک مجموعهٔ generic با قرارداد `ICollection<T>` و پیاده‌سازی `Collection<T>` ارائه می‌کند. این نوع، API کوچک و مستقل خودش را دارد؛ برای نمونه، `ICollection<T>` با `List<T>` یکی نیست و فقط هنگام نیاز می‌توان آن را با `toList()` به فهرست استاندارد Kotlin تبدیل کرد.

کلاس نمونهٔ زیر در مثال‌ها استفاده می‌شود. فیلد `email` nullable است تا دسترسی به propertyهای معمولی و nullable در پیمایش هم دیده شود:

~~~kotlin
class UserModel(
    var id: Int,
    var name: String,
    var email: String?,
)
~~~

## قرارداد `ICollection<T>`

| API | رفتار |
|---|---|
| `add(element)` | عنصر را به همان مجموعه اضافه می‌کند. |
| `forEach(action)` | action را برای هر عنصر اجرا می‌کند. |
| `filter(predicate)` | مجموعه‌ای تازه از عناصر منطبق، با همان نوع `T`، برمی‌گرداند. |
| `map(transform)` | مجموعه‌ای تازه از مقادیر تبدیل‌شده با نوع `R` برمی‌گرداند: `ICollection<R>`. |
| `firstOrNull(predicate)` | نخستین عنصر منطبق را برمی‌گرداند؛ اگر موردی پیدا نشود، `null` می‌دهد. |
| `toList()` | عناصر را به `List<T>` تبدیل می‌کند. |
| `get(index)` یا `collection[index]` | عنصر جایگاه مشخص‌شده را می‌دهد؛ index نامعتبر مطابق پیاده‌سازی فعلی `IndexOutOfBoundsException` ایجاد می‌کند. |
| `size` | تعداد فعلی عناصر را می‌دهد. |

پیاده‌سازی فعلی `Collection<T>` ترتیب درج را حفظ می‌کند. `filter()` و `map()` نتیجه را در یک Collection تازه می‌سازند؛ تغییر آن نتیجه، مجموعهٔ مبدأ را جایگزین نمی‌کند. `toList()` یک کپی سطحی از فهرست عناصر می‌سازد؛ خود اشیای mutable داخل مجموعه clone نمی‌شوند.

## مثال کامل قابل اجرا

نمونهٔ زیر یک Collection می‌سازد و تمام عملیات اصلی را روی همان داده‌ها اجرا می‌کند. می‌توان آن را در یک فایل Kotlin با dependency به `:collection:core` اجرا کرد:

~~~kotlin
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

class UserModel(
    var id: Int,
    var name: String,
    var email: String?,
)

fun main() {
    val users: ICollection<UserModel> = Collection()
    users.add(UserModel(1, "Ada", "ada@example.com"))
    users.add(UserModel(2, "Grace", null))
    users.add(UserModel(3, "Alan", "alan@example.com"))

    println("Added ${users.size} users")

    users.forEach { user ->
        println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
    }

    val firstUserName = users[0].name
    println("First user: $firstUserName")

    val usersWithAName = users.filter { user -> user.name.startsWith("A") }
    println("Users with names starting with A: ${usersWithAName.size}")
    usersWithAName.forEach { user -> println(user.name) }

    val names: ICollection<String> = users.map { user -> user.name }
    names.forEach { name -> println("Mapped name: $name") }

    val firstMatchingUser = users.firstOrNull { user -> user.name.startsWith("A") }
    println("First matching user: ${firstMatchingUser?.name}")

    val userList: List<UserModel> = users.toList()
    userList.forEach { user -> println("List item: ${user.id} - ${user.name}") }
}
~~~

خروجی این داده‌ها به‌ترتیب شامل سه عضو، سه خط پیمایش، نام `Ada` برای index صفر، دو نتیجهٔ فیلتر، سه نام mapشده، نخستین نتیجهٔ `Ada` و سه عضو تبدیل‌شده به `List` است. مقدار `email` برای `Grace` برابر `null` باقی می‌ماند.

## ساخت و افزودن عنصر

`Collection<T>` را می‌توان مستقیماً ساخت و از طریق قرارداد `ICollection<T>` نگه داشت. `add()` مجموعهٔ موجود را تغییر می‌دهد:

~~~kotlin
import examples.UserModel
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

val users: ICollection<UserModel> = Collection()
users.add(UserModel(1, "Ada", "ada@example.com"))
users.add(UserModel(2, "Grace", null))

println("Added ${users.size} users")
~~~

با دو بار افزودن، مقدار `size` برابر ۲ می‌شود؛ در مثال کامل بالا سه عنصر اضافه شده‌اند و مقدار آن ۳ است.

## پیمایش و دسترسی اندیسی

`forEach` عناصر را برای خواندن یا پردازش یکی‌یکی در اختیار می‌گذارد. مقدار nullable مانند `email` همان nullable باقی می‌ماند و مصرف‌کننده باید آن را متناسب با نیاز مدیریت کند:

~~~kotlin
users.forEach { user ->
    println("id: ${user.id}, name: ${user.name}, email: ${user.email}")
}

val firstUserName = users[0].name
~~~

در مثال کامل، پیمایش هر سه کاربر را چاپ می‌کند و `users[0]` کاربر نخست را می‌دهد. index از صفر آغاز می‌شود و باید در بازهٔ `0 until size` باشد.

## فیلتر و شمارش

`filter` شرط را روی هر عنصر اعمال می‌کند و یک `ICollection<T>` تازه از موارد منطبق می‌سازد. `size` تعداد نتیجه را می‌دهد:

~~~kotlin
val usersWithAName = users.filter { user -> user.name.startsWith("A") }
println("Matching users: ${usersWithAName.size}")
~~~

در مثال کامل، شرط `name.startsWith("A")` دو کاربر `Ada` و `Alan` را برمی‌گرداند. `size` روی مجموعهٔ فیلترشده مقدار ۲ و روی مجموعهٔ اصلی مقدار ۳ دارد.

## تبدیل با `map`

`map` نوع عناصر را می‌تواند تغییر دهد. نتیجه همچنان از قرارداد `ICollection` پیروی می‌کند:

~~~kotlin
val names: ICollection<String> = users.map { user -> user.name }
names.forEach { name -> println(name) }
~~~

در مثال کامل، `ICollection<UserModel>` به `ICollection<String>` تبدیل می‌شود و نام هر سه کاربر چاپ می‌شود. این تبدیل مجموعهٔ تازه‌ای می‌سازد و به‌خودی‌خود مجموعهٔ اصلی را تغییر نمی‌دهد.

## جست‌وجوی نخستین مورد

`firstOrNull` برای یافتن نخستین عنصر منطبق مناسب است و در صورت نبودن نتیجه، مقدار nullable برمی‌گرداند:

~~~kotlin
val firstMatchingUser = users.firstOrNull { user -> user.name.startsWith("A") }
println("First matching user: ${firstMatchingUser?.name}")
~~~

در مثال کامل، نخستین نام منطبق `Ada` است. از آنجا که نتیجه ممکن است `null` باشد، دسترسی امن مانند `?.` یا بررسی صریح null لازم است.

## تبدیل به `List`

اگر API دیگری `List<T>` لازم داشته باشد، `toList()` عناصر را به فهرست استاندارد Kotlin تبدیل می‌کند:

~~~kotlin
val userList: List<UserModel> = users.toList()
userList.forEach { user -> println("${user.id}: ${user.name}") }
~~~

در مثال کامل، نوع خروجی صریحاً `List<UserModel>` است. تبدیل، نوع خروجی را از `ICollection<T>` به `List<T>` عوض می‌کند؛ بنابراین فقط زمانی از آن استفاده کنید که قرارداد مصرف‌کننده `List` بخواهد.

## اجرای مثال‌ها

ماژول `collection:examples` به `collection:core` وابسته است. `main.kt` هشت مثال را با ترتیب `add`، `forEach`، `filter`، `size`، `map`، `firstOrNull`، `toList` و `get` اجرا می‌کند. مقدار پیش‌فرض همهٔ گزینه‌ها `true` است؛ هر مثال را می‌توان جداگانه با Gradle project property خاموش یا روشن کرد:
