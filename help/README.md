# راهنمای Help محلی پروژه

Help یک برنامهٔ محلی مبتنی بر Node.js است. این برنامه از امکانات داخلی Node.js، فایل Bootstrap RTL نسخهٔ 5.3.8 و Gradle Wrapper همین مخزن استفاده می‌کند؛ نیازی به نصب جداگانهٔ Gradle یا اتصال CDN در زمان اجرا نیست. مجوز Bootstrap در `help/public/vendor/BOOTSTRAP-LICENSE.txt` قرار دارد.

## پیش‌نیازها

* Node.js
* JDK نسخهٔ ۲۴ یا جدیدتر. `buildSrc` این مخزن برای اجرای Gradle به Java 24 نیاز دارد.

متغیر `JAVA_HOME` باید به پوشهٔ نصب JDK اشاره کند؛ همان پوشه‌ای که زیرپوشهٔ `bin` را دارد. مسیرهای رایج نصب در ویندوز:

* `C:\Program Files\Java\jdk-24`
* `C:\Program Files\Eclipse Adoptium\jdk-24...`
* `C:\Program Files\Microsoft\jdk-24...`

مسیر دقیق به نصب‌کننده‌ای که استفاده کرده‌اید بستگی دارد. برای جست‌وجوی مسیرهای رایج در PowerShell این دستور را اجرا کنید:

```powershell
Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft' -ErrorAction SilentlyContinue
```

اگر JDK نسخهٔ ۲۴ یا جدیدتر در فهرست نبود، ابتدا آن را نصب کنید. سپس PowerShell را باز کنید و `JAVA_HOME` را روی مسیر واقعی JDK تنظیم کنید. در مثال زیر، مسیر نمونه را با مسیری که در سیستم خود پیدا کرده‌اید جایگزین کنید:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

بررسی کنید که خروجی `java -version` نسخهٔ ۲۴ یا جدیدتر را نشان دهد. این تنظیمات فقط در همان پنجرهٔ PowerShell فعال‌اند؛ Help را نیز از همان پنجره اجرا کنید.

## اجرای Help

از ریشهٔ مخزن:

```powershell
node help/src/server.mjs
```

یا از پوشهٔ `help`:

```powershell
node src/server.mjs
```

سپس در مرورگر به نشانی `http://127.0.0.1:4173` بروید. برای انتخاب درگاه دیگری، پیش از اجرا متغیر `HELP_PORT` را تنظیم کنید. هنگام شروع، Help نمودار کامل Projectهای Gradle را یک‌بار کشف می‌کند؛ در انتخاب آبشاری همهٔ آن‌ها نمایش داده می‌شوند. فقط Projectهایی که `help.json` دارند می‌توانند Test اجرا کنند. اگر کشف Projectها یا خواندن یکی از فایل‌های موجود `help.json` ناموفق باشد، Help با خطا متوقف می‌شود.

انتخاب ماژول، اکشن و تست در query نشانی مرورگر نگه داشته می‌شود؛ برای نمونه `?module=%3Adata_base%3Amigration&action=module-test&test=migration-system-integration`. با بازکردن یا تازه‌سازی این نشانی همان انتخاب‌ها بازیابی می‌شوند. فقط شناسهٔ ماژول، اکشن و تست وارد URL می‌شوند؛ مقادیر فرم و رمز عبور وارد آن نمی‌شوند.

اولین Definition واقعی در `data_base/migration/help.json` قرار دارد. فرم تست Migration با پیش‌فرض‌های مثال همین مخزن پر می‌شود: `jdbc:mysql://127.0.0.1`، درگاه `3306`، پایگاه دادهٔ `kotlin_db` و کاربر `root`. اگر تنظیمات MySQL شما متفاوت است، مقدارها را تغییر دهید. رمز عبور به‌صورت پیش‌فرض خالی است. مرورگر آخرین مقدار فیلدهای غیرمحرمانه را برای همان Test در فضای محلی خود به خاطر می‌سپارد؛ رمز عبور ذخیره نمی‌شود و پس از اجرا از فرم پاک می‌شود. در صورت تمایل می‌توانید از Password Manager مرورگر استفاده کنید.
