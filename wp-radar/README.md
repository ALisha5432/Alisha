# WP Radar

WhatsApp qruplarında **açar söz** olan mesaj gələndə ayrıca, **güclü səsli və vibrasiyalı** bildiriş göndərən Android tətbiqi.
WhatsApp və WhatsApp Business dəstəklənir.

## Yükləmə (telefondan)

Ən son versiya həmişə bu linkdədir:

**https://github.com/ALisha5432/Alisha/releases/latest/download/WP-Radar.apk**

Bütün versiyalar: https://github.com/ALisha5432/Alisha/releases

Hər push-dan sonra GitHub Actions APK-nı yenidən yığır (~5 dəq.) və yeni Release yaradır.
Bütün versiyalar eyni imza ilə çıxır, ona görə yenisini köhnəni silmədən üstündən qurmaq olar.

## Quraşdırma

1. APK-nı açın → «Bu mənbədən quraşdırmağa icazə ver» → **Quraşdır**.
2. WP Radar → **Əsas** ekran → «1. Bildiriş girişi» → **İcazə ver** → siyahıda WP Radar-ı aktiv edin.
   - Android 13+ «Məhdudlaşdırılmış ayar» desə: **Tətbiq məlumatı** → sağ yuxarıda **⋮** →
     **«Məhdud ayarlara icazə verin»** → yenidən cəhd edin.
3. «2. Bildiriş icazəsi» → **İcazə ver**.
4. «3. Batareya optimizasiyası» → **Məhdudiyyəti götür** (Xiaomi/Huawei-də «Avtostart»-a da icazə verin).
5. **Ayarlar** → açar sözləri əlavə edin (vergüllə bir neçəsini birdən yazmaq olar).
   Qrup siyahısı boş qalsa, bütün qruplarda axtarılır.
6. **Əsas** → **Test bildirişi** ilə səsi yoxlayın.

## Necə işləyir

- `NotificationListenerService` yalnız `com.whatsapp` və `com.whatsapp.w4b` bildirişlərini oxuyur.
- Qrup, göndərən və mesaj `MessagingStyle` mesajlarından, olmasa `EXTRA_CONVERSATION_TITLE`,
  `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES` sahələrindən çıxarılır.
- Axtarışda böyük/kiçik hərf fərq etmir və Azərbaycan qaydası işləyir: `İ→i`, `I→ı`, `Ə→ə`.
  «Hərf fərqlərinə baxma» açıqdırsa (standart), `ə=e, ı=i, ş=s, ç=c, ğ=g, ö=o, ü=u` sayılır.
- WhatsApp qrup bildirişini hər yeni mesajda köhnə mesajlarla birlikdə yenidən göndərir.
  Hər mesajın açarı (qrup + göndərən + vaxt + mətn) yadda saxlanır, ona görə eyni mesaja ikinci dəfə xəbərdarlıq getmir.
  Telefon yenidən başlasa da bu yaddaş qalır.
- Xəbərdarlığa toxunanda birbaşa həmin WhatsApp söhbəti açılır (alınmasa — WhatsApp özü).
- Dinləyici qopanda `requestRebind` çağırılır. Telefon açılanda və tətbiq yenilənəndə də çağırılır.

## Vacib

- WhatsApp-da qrupu **«Səssiz» (mute) etməyin**. Səssiz qrup üçün WhatsApp bildiriş yaratmır, radar da onu görmür.
  Qrupun səsini söndürmək istəyirsinizsə: telefon Ayarları → Tətbiqlər → WhatsApp → Bildirişlər →
  «Qrup bildirişləri» kanalını səssiz edin.
- WhatsApp-da bildiriş önizləməsi (preview) açıq olmalıdır.

## İmza açarı

`signing/wp-radar.jks` repo-da açıq saxlanır (parol `app/build.gradle.kts`-dədir).
Belə olanda hər build eyni imza ilə çıxır. Repo public olduğu üçün bu açarla başqası da APK imzalaya bilər.
Ona görə WP Radar-ı yalnız bu repo-nun Releases səhifəsindən yükləyin.

## Kod

```
app/src/main/java/com/wpradar/
  core/      Android-dan asılı olmayan məntiq (unit testlərlə): normallaşdırma, parser, uyğunluq, təkrar yoxlaması
  service/   NotificationListenerService, bildirişdən məlumat çıxarma, rebind
  notify/    Xəbərdarlıq kanalları və bildiriş
  data/      DataStore: ayarlar, tarixçə; görülən mesajlar faylı
  ui/        Compose ekranları: Əsas, Ayarlar, Tarixçə
```
