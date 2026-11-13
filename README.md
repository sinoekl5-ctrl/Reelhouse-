# Reelhouse

تطبيق أندرويد لتنظيم مكتبة الأفلام الشخصية: بوسترات، مشغل فيديو، ترجمة عربية، Chromecast، وقفل بالبصمة.
واجهة التطبيق بالإنجليزية فقط، كما في المواصفات.

- Kotlin + Jetpack Compose + Material 3
- Room (قاعدة البيانات) • Media3 ExoPlayer (المشغل) • Google Cast • AndroidX Biometric
- `minSdk 26` (أندرويد 8.0) • `targetSdk 34`

## كيف تحصل على ملف APK

### الطريقة 1: GitHub Actions (بدون تثبيت أي شيء)
1. أنشئ مستودعاً جديداً على GitHub وارفع كل محتويات هذا المجلد.
2. افتح تبويب **Actions** ← سيبدأ بناء **Build APK** تلقائياً (أو اضغط *Run workflow*).
3. بعد دقائق، افتح عملية البناء وحمّل **Reelhouse-release-apk** (أو debug) من قسم Artifacts.
4. فك الضغط وثبّت الـ APK على هاتفك (فعّل "التثبيت من مصادر غير معروفة").

### الطريقة 2: Android Studio
افتح المجلد في Android Studio (Koala أو أحدث) ثم **Build ▸ Build APK(s)**، أو من الطرفية:

```
./gradlew assembleRelease
```
الملف يظهر في `app/build/outputs/apk/release/`.
نسخة الـ release موقّعة بمفتاح الـ debug حتى يمكن تثبيتها مباشرة. إذا أردت النشر على Google Play، أنشئ مفتاح توقيع خاصاً بك.

## المفاتيح المطلوبة (من داخل التطبيق: Settings ▸ Accounts / Keys)

| الخدمة | لماذا | كيف تحصل عليها |
|---|---|---|
| **TMDB API key** | البوسترات، الملخص، مدة الفيلم، السنة | حساب مجاني على themoviedb.org ← Settings ← API. يعمل الـ API Key أو الـ Read Access Token |
| **OpenSubtitles API key** | البحث عن الترجمة العربية | حساب مجاني على opensubtitles.com ← Profile ← API consumers |
| **OpenSubtitles username/password** | تنزيل الترجمة (مطلوب لمعظم الحسابات) | نفس حسابك على opensubtitles.com |

المفاتيح تُحفظ على الهاتف فقط ولا تدخل في النسخة الاحتياطية. الخطة المجانية لـ OpenSubtitles لها حد يومي للتنزيل، لذلك التطبيق ينزّل ترجمة فيلم واحد في كل مرة (عند فتحه أو عند الضغط على Download) وليس المكتبة كلها.

## أين كل ميزة

| الميزة | المكان |
|---|---|
| إضافة مجلدات الأفلام | زر **+** في Library، أو Settings ▸ Folders |
| Rescan / Remove | Settings ▸ Folders |
| تصحيح الأفلام المتعرَّف عليها خطأً | Settings ▸ Review matches، أو زر Edit في صفحة الفيلم |
| تغيير البوستر (TMDB / المعرض / رابط + قص 2:3) | ضغطة مطولة على البوستر ▸ Change Poster، أو Edit ▸ Change poster |
| Favorites / Watched / تقييم / ملاحظات | صفحة التفاصيل |
| Playlists | تبويب Playlists، وAdd to playlist من التفاصيل أو القائمة السريعة |
| الترجمة (Download / Choose file / ضبط التوقيت) | قسم Subtitles في التفاصيل، وزر ⚙ داخل المشغل |
| Chromecast | زر Cast في التفاصيل والمشغل |
| قفل البصمة | Settings ▸ Security ▸ App Lock |
| Backup / Restore / Clear cache | Settings ▸ Data |

## ملاحظات مهمة

- **لا توجد تواريخ مشاهدة في أي مكان.** يُحفظ فقط: علامة ✓ Watched وموضع الاستكمال. (يوجد عدّاد داخلي لترتيب "Continue Watching" وهو رقم تسلسلي وليس تاريخاً ولا يظهر.)
- **Chromecast:** يجب أن يكون الهاتف والتلفزيون على نفس شبكة Wi-Fi، ويبقى التطبيق مفتوحاً أثناء الإرسال، ويُوقف الإرسال عند إغلاق المشغل. صيغ مثل HEVC/MKV/AC3 قد لا تعمل على أجهزة Chromecast القديمة، وسيعرض التطبيق تنبيهاً. هذه الميزة تحتاج تجربة على جهاز حقيقي.
- **حذف فيلم من المكتبة (Remove from Library)** لا يحذف ملف الفيديو من الهاتف أبداً.
- ترميز ملفات الترجمة (Windows-1256 وغيره) يُكتشف ويُحوَّل تلقائياً إلى UTF-8.

## هيكل المشروع

```
app/src/main/java/com/reelhouse/app/
  data/     قاعدة البيانات، TMDB، OpenSubtitles، فحص المجلدات، تحليل أسماء الملفات، النسخ الاحتياطي
  cast/     خادم HTTP محلي + خدمة الإرسال + أدوات Cast
  player/   PlayerActivity (ExoPlayer)
  ui/       MainActivity، التنقل، قفل البصمة، الثيم
  ui/screens/  Library، Details، Poster، Folders، Playlists، Settings، Lock
```
