FastKeyboard_NOVA v1.15

# Fast Keyboard Nova

نسخه تمیز و مستقل پروژه کیبورد، بر پایه آخرین مبنای سبزِ آزمایش‌شده پیش از انتقال به مخزن جدید.

## ساختار مخزن

این مخزن عمداً فقط فایل‌های لازم برای ادامه توسعه و Build را نگه می‌دارد:

- `app/` — کد و منابع برنامه
- `.github/workflows/android.yml` — Build خودکار و دستی APK
- فایل‌های Gradle و تنظیمات پروژه
- `README.md`

سوابق و فایل‌های توضیحی نسخه‌های قدیمی در این مخزن جدید منتقل نشده‌اند تا مخزن از ابتدا تمیز و مستقل باشد.

## Build

پس از Commit و Push، GitHub Actions به‌صورت خودکار Build Debug را اجرا می‌کند. اجرای دستی Workflow نیز از بخش Actions در دسترس است.

APK به‌عنوان Artifact با نام `FastKeyboard-Nova-debug-apk` منتشر می‌شود.

## نکته

چیدمان و قابلیت‌های مبنای قبلی عمداً در این انتقال تغییر داده نشده‌اند. اصلاحات داخلی بعدی باید جداگانه و مرحله‌به‌مرحله اعمال شوند.


## v1.15 — Exact mouse window and working mouse controls

- Quick Settings mouse window now uses the user's supplied reference image `mouse_reference.jpg` as the exact visual base.
- Transparent interactive hit areas are placed over the reference controls, so the image appearance is not redesigned.
- Close, Drag, touch field, left click, both wheel buttons, automatic movement, right click and Select are wired to the corresponding actions.
- Right click first tries Android context-click/long-click and falls back to a long-press gesture.
- The single resize grip remains at the top-left position shown in the supplied image.
- The existing real Send-button behavior from v1.14 is retained.
