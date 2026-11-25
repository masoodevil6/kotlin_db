# افزودن عنصر

`add(element)` یک عنصر را به انتهای Collection اضافه می‌کند. این متد مقدار بازگشتی ندارد و Collection موجود را تغییر می‌دهد.

```kotlin
import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection

data class User(val id: Int, val name: String)

val users: ICollection<User> = Collection()
users.add(User(1, "Ada"))
users.add(User(2, "Grace"))

println(users.size) // 2
```

ترتیب اضافه‌شدن حفظ می‌شود و اگر مقدار یکسانی چند بار اضافه شود، همهٔ رخدادها باقی می‌مانند. `add()` شیء را clone نمی‌کند؛ برای مالکیت و تغییرپذیری objectهای ذخیره‌شده، [قرارداد Collection](collection-contract.md) را ببینید.

**پیاده‌سازی:** `ICollection.add` و `Collection.add` در ماژول `collection/core`.
