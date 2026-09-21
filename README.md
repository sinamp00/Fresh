# 🌟 Fresh Super-App v2.0 (تلگرام رسمی + اینستاگرام بومی + ضدسانسور)

<div dir="rtl">

[![Release](https://img.shields.io/github/v/release/sinamp00/Fresh?color=00F5A0&label=Latest%20Version&style=for-the-badge)](https://github.com/sinamp00/Fresh/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-00F5FF?style=for-the-badge)](https://github.com/sinamp00/Fresh)
[![License](https://img.shields.io/badge/License-GPLv2-B388FF?style=for-the-badge)](https://github.com/sinamp00/Fresh)
[![Website](https://img.shields.io/badge/Website-Live%20Page-FFB300?style=for-the-badge)](https://sinamp00.github.io/Fresh/)

**Fresh** یک سوپراپلیکیشن بومی نسل نوین برای اندروید است که کلاینت رسمی تلگرام (v12.10.3) را با موتور مهندسی‌معکوس‌شدهٔ پروتکل اینستاگرام و شبکه ضدسانسور تلفیق کرده است. این اپلیکیشن بدون نیاز به فیلترشکن جانبی یا وب‌ویوهای مسدودشده، دسترسی پایدار به امکانات تلگرام و اینستاگرام را فراهم می‌سازد.

---

## 📥 لینک‌های دانلود مستقیم نسخه رسمی (v2.0.0)

| نسخه فایل | نوع معماری پردازنده | حجم فایل | لینک دانلود مستقیم |
| :--- | :--- | :--- | :--- |
| **Fresh-SuperApp.apk** | تمامی پردازنده‌ها (arm64-v8a + armeabi-v7a) | ۸۱.۰۵ مگابایت | [📥 دانلود مستقیم Fresh-SuperApp.apk](https://github.com/sinamp00/Fresh/releases/download/v2.0.0/Fresh-SuperApp.apk) |
| **Fresh.apk** | لینک کمکی (Universal) | ۸۱.۰۵ مگابایت | [📥 دانلود مستقیم Fresh.apk](https://github.com/sinamp00/Fresh/releases/download/v2.0.0/Fresh.apk) |

> 🌐 **وب‌سایت رسمی سوپراپلیکیشن**: [https://sinamp00.github.io/Fresh/](https://sinamp00.github.io/Fresh/)  
> 📦 **مشاهده Release در گیت‌هاب**: [https://github.com/sinamp00/Fresh/releases/tag/v2.0.0](https://github.com/sinamp00/Fresh/releases/tag/v2.0.0)

---

## ✨ ویژگی‌های برجسته نسخه ۲.۰

### ۱. هسته رسمی تلگرام (بدون دستکاری رنگ‌ها و تم‌ها)
- مبتنی بر آخرین سورس کد رسمی تلگرام اندروید (v12.10.3).
- حفظ ۱۰۰٪ تم‌های کارخانه‌ای (**Classic Blue**, **Day**, **Dark Blue**, **Night**).
- بدون قطعی یا سیاهی صفحه هنگام تعویض به حالت شب (Dark Mode).
- ورود فوری به فهرست چت‌ها (`DialogsActivity`) بدون هیچ صفحه خالی یا لودینگ مسدود.

### ۲. موتور بومی پروتکل اینستاگرام (`:instagram-api`)
- پیاده‌سازی مستقل کلاینت پروتکل معکوس اینستاگرام به زبان Kotlin بدون هیچ‌گونه WebView.
- رمزنگاری اختصاصی RSA و AES-GCM کلمه عبور و داده‌های حساس (`#PWD_INSTAGRAM:4:...`).
- امضای دیجیتال درخواست‌ها با HMAC-SHA256 و شبیه‌سازی دقیق هویت دستگاه.
- دریافت مستقیم فید، تایم‌لاین و ریلزها با سرعت بالا.

### ۳. ناوبری و تب‌های بومی (Native SuperApp Tabs)
- **تب ۱ (گفتگوها)**: پیام‌رسان سریع تلگرام.
- **تب ۲ (فید اینستاگرام)**: نمایش روان پست‌ها، دابل‌تپ لایک، فوروارد به چت‌های تلگرام (`ShareAlert`).
- **تب ۳ (ریلز)**: پخش ویدیو تمام‌صفحه با اسکرول عمودی با بهره‌گیری از ویدیوپلیر سخت‌افزاری Media3.
- **تب ۴ (تنظیمات)**: فرم لاگین امن RSA، ورود دومرحله‌ای (2FA) و کنترل لایه ضدسانسور.

### ۴. شبکه ضدسانسور و دور زدن اختلالات (Resilient Networking)
- **تکه‌تکه‌سازی سلام TLS (ClientHello Fragmentation)**: بای‌پس فایروال‌های بازرسی عمیق پکت (DPI).
- **Clean Anycast DNS**: جلوگیری از مسمومیت DNS و هدایت مستقیم به آی‌پی‌های پایدار متا.
- **Telegram Proxy Bridge**: اتصال هوشمند به پروکسی‌های فعال تلگرام (MTProto / SOCKS5).

---

## 🔒 اعتبارسنجی سلامت فایل (SHA-256)

```text
CCAAF5555E7D00C550CDBD4BA9383779F95EC9F98B6B2877EE732B97831130FF  Fresh-SuperApp.apk
CCAAF5555E7D00C550CDBD4BA9383779F95EC9F98B6B2877EE732B97831130FF  Fresh.apk
```

</div>
