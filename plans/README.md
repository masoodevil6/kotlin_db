# Migration Plans

این پوشه برنامه‌های نسخه‌بندی‌شده‌ی توسعه‌ی زیرسیستم `data_base:migration` را نگهداری می‌کند.

## اصل اصلی: Contract-first

هیچ operation جدیدی نباید قبل از تعریف Contract کامل آن وارد مرحله‌ی طراحی interface یا implementation شود.

ترتیب طراحی هر operation:

```text
Contract
    ↓
AST
    ↓
Validation
    ↓
Builder/API
    ↓
Dialect/Renderer
    ↓
Executor
    ↓
Tests
```

Contract هر operation باید حداقل این بخش‌ها را مشخص کند:

```text
Purpose
Input
Options
AST
Invariants
Validation
Rendering
Execution
Result
Errors
Tests
```

نام interfaceها و ساختار packageها بعد از تثبیت Contract طراحی می‌شوند. interfaceها باید Contract را پیاده‌سازی کنند و نباید جایگزین آن شوند.

## نسخه‌ها

| نسخه | موضوع | وضعیت |
|---|---|---|
| `v1.0` | Contract و baseline مربوط به `CREATE TABLE` | planned |
| `v1.1` | Contract و implementation plan مربوط به `DROP TABLE` | planned |
| `v1.2` | Contract مشترک operationهای migration | planned |

## وضعیت plan

مقدار وضعیت فقط یکی از موارد زیر است:

- `planned`: Contract و محدوده مشخص شده، implementation شروع نشده است.
- `in-progress`: implementation یا تست‌های مربوط به plan در حال انجام است.
- `implemented`: معیارهای پذیرش کامل شده و تغییرات با تست معتبر شده‌اند.
- `blocked`: اجرای plan به تصمیم، dependency یا زیرساختی خارج از محدوده وابسته است.

هر plan باید علاوه بر Contract، این اطلاعات اجرایی را داشته باشد:

```text
Dependencies
Implementation phases
Expected file/module changes
Tests
Risks and decisions
Definition of Done
```

## ترتیب پیشنهادی اجرا

```text
v1.0  تثبیت CREATE TABLE
  ↓
v1.1  پیاده‌سازی DROP TABLE
  ↓
v1.2  abstraction مشترک operationهای migration
  ↓
v1.3  ALTER TABLE
  ↓
v1.4  index و foreign key
  ↓
v2.0  migration runner و history
```

هر plan باید قبل از implementation به‌روزرسانی شود و معیار پذیرش قابل بررسی داشته باشد.

## قواعد اجرای plan

1. ابتدا Contract همان operation نهایی می‌شود.
2. تغییرات فقط در module مسئول همان لایه انجام می‌شود؛ module والد محل implementation جایگزین نیست.
3. AST هر operation مستقل می‌ماند؛ abstraction مشترک فقط بعد از مشاهده‌ی duplication واقعی ساخته می‌شود.
4. render باید بدون دیتابیس تست شود.
5. execution باید dependencyهای اتصال و خطای واقعی JDBC را شفاف نگه دارد.
6. integration test با MySQL باید opt-in و دارای configuration مشخص باشد.
7. پس از هر phase، compile/test همان module و در پایان test زنجیره‌ی migration اجرا می‌شود.

## ساختار استاندارد هر plan

هر plan باید با این ترتیب نوشته شود:

```text
1. Contract
2. Scope / Out of scope
3. Dependencies
4. Implementation phases
5. Module and file impact
6. Tests
7. Risks and decisions
8. Definition of Done
```
