# وضعیت اجرای Migration

پیاده‌سازی قدیمی اجرا و ذخیره‌سازی history برای بازنویسی از صفر حذف شده است. در وضعیت فعلی، `DatabaseHandle` و DSL مربوط به DatabaseManager قابلیت اجرای Migration ارائه نمی‌کنند.

قراردادهای تعریف و ثبت Migration و DSL ساخت عملیات Schema فعلاً باقی مانده‌اند، اما اجرای آن‌ها و persistence تاریخچه در این مرحله فراهم نیست. زیرساخت عمومی Query، Schema، Operation، Connection و Transaction همچنان مستقل و در دسترس است.
