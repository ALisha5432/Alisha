# Növbə Radarı

EvoApp-da **Available Shifts** siyahısına baxan və sənin saatına uyğun növbə (standart: **16:00–00:00**)
ortaya atılan kimi bildiriş göndərən şəxsi Android tətbiqi.

> 🔔 16 Okt (Cümə) · E2 Big Wheel TUR · 16:00–00:00 · ortadadır (KA)

**Yalnız oxuyur.** RESPOND və EvoApp-da başqa heç bir düyməyə özü basmır. Naviqasiya üçün yalnız
"N SHIFTS AVAILABLE" girişinə, ay oxlarına (‹ ›) və sistemin Geri/Ana ekran düymələrinə toxunur.
Hər toxunuşdan əvvəl həmin elementin içində "respond / accept / take / confirm…" kimi söz olmadığı yoxlanılır.

## Yükləmə

Ən son APK: **[Releases](../../releases/latest)** → `NovbeRadari-1.0.N.apk`.

Hər yeni versiya eyni açarla imzalanır, ona görə köhnəni silmədən üstündən quraşdırmaq olar — ayarlar və siyahı qalır.

## Telefonda quraşdırma

1. Releases səhifəsində `NovbeRadari-…apk` faylına bas və yüklə.
2. Yüklənən faylı aç. Telefon **"Naməlum mənbələr"** barədə soruşacaq: **Ayarlar** → brauzer (Chrome) və ya
   "Fayllar" üçün **"Bu mənbədən icazə ver"**-i yandır → geri qayıt → **Quraşdır**.
3. **Play Protect** xəbərdarlığı çıxsa ("Yoxlanmamış tətbiq" / "Unsafe app blocked"):
   **Ətraflı** (More details) → **Hər halda quraşdır** (Install anyway).
   Skan üçün göndərməyi təklif etsə, **Göndərmədən quraşdır** seçə bilərsən.
   Bu xəbərdarlıq tətbiq Google Play-də olmadığı üçün çıxır.
4. Tətbiqi aç — **Quraşdırma** ekranı addım-addım hər şeyi göstərir.

### Accessibility və "Məhdudlaşdırılmış ayar" (Android 13+)

Radar EvoApp-ın ekranını Accessibility xidməti ilə oxuyur.

1. Tətbiqdə **"Accessibility-ni aç"** → **Yüklənmiş tətbiqlər** → **Növbə Radarı** → yandır.
2. **"Məhdudlaşdırılmış ayar"** (Restricted setting) pəncərəsi çıxsa:
   tətbiqdə **"Tətbiq məlumatı"** düyməsi → sağ yuxarıda **⋮** → **"Məhdudlaşdırılmış ayarlara icazə ver"**
   (Allow restricted settings) → PIN ilə təsdiqlə.
3. Yenidən Accessibility-yə qayıt və Növbə Radarını yandır.

### Xiaomi / Redmi / POCO

- **Avtomatik başlama** (Autostart): Növbə Radarı → yandır.
- **Tətbiq icazələri → Digər icazələr**: "Fonda işləyərkən yeni pəncərələr aç" və "Kilid ekranında göstər" → İcazə ver.
- **Batareya qənaəti** → "Məhdudiyyət yoxdur".

### Ekran kilidi

- Kilid **"Yoxdur"** və ya **"Sürüşdür"** olanda radar hər N dəqiqədən bir (standart 5) ekranı oyadır, EvoApp-ı yoxlayır,
  sonra ekranı yenidən kilidləyir. Bu vaxt ekranın üstünə qara pərdə çəkilir (gecə işıq saçmasın); pərdəyə toxunsan yoxlama dayanır.
- **PIN/naxış** varsa, radar kilidli telefonu özü aça bilmir. Onda yoxlama yalnız telefon açıq olanda olur:
  EvoApp-a özün baxanda (görünən növbələr avtomatik oxunur), **"İndi yoxla"** düyməsi ilə (bildirişdən də) və
  ekran söndükdən dərhal sonra, telefon hələ kilidlənməmişkən.

Radar telefonu işlədəndə yoxlamır (ekran sönən kimi yoxlayır), zəng gedəndə və batareya 15%-dən az olanda dayanır.

## Öyrənmə rejimi

EvoApp-ın quruluşu telefondan telefona fərqli ola bilər. Oxuma səhv olsa:

1. Tətbiqdə **Öyrən** bölməsi → **Öyrənmə rejimi**-ni aç.
2. EvoApp-ı aç: **My Schedule**-a bax, sonra **Available Shifts**-ə keç, siyahını yavaş-yavaş sona qədər aşağı,
   sonra yuxarı sürüşdür, növbəti aya keç və geri qayıt.
3. Növbə Radarına qayıt, rejimi söndür, **Paylaş (mətn kimi)** → Claude-a göndər.

Bu rejimdə radar heç nəyə toxunmur, yalnız ekrandakı mətnləri, id-ləri və yerləşməni yazır.
Yazılarda iş yoldaşlarının inisialları və masa adları olur — onları ictimai yerdə paylaşma və repoya əlavə etmə.

## Tətbiqin bölmələri

- **Radar** — fırlanan radar, vəziyyət ("Radar aktivdir ✓" / "İcazə çatmır ⚠️"), son yoxlama, növbəti yoxlamaya qalan vaxt,
  **İndi yoxla** və **Test** (saxta növbə ilə bütün zənciri yoxlayır və bildiriş göndərir).
- **Tapılanlar** — uyğun növbələr, hər birində **EvoApp-ı aç**. Götürülən növbə "götürülüb" olur; yenidən atılsa, yenə bildiriş gəlir.
- **Jurnal** — hər yoxlamanın nəticəsi və xətalar.
- **Öyrən** — öyrənmə rejimi.
- **Ayarlar** — yoxlama aralığı (2–30 dəq), növbə saatları, "yalnız boş günlərim", səs/vibrasiya/alarm rejimi, səssiz saatlar.

## Texniki

- Kotlin, Jetpack Compose (Material 3), minSdk 28, targetSdk 35.
- `core/` — Android-dan asılı olmayan oxuma məntiqi (ekran təhlili, scroll səhifələrinin birləşdirilməsi, tarix,
  filtr, təkrar bildirişin qarşısı) və unit testlər.
- `app/` — Accessibility xidməti, foreground xidmət (`specialUse`), `AlarmManager.setAlarmClock`, ML Kit OCR (ehtiyat yol).
- GitHub Actions hər push-da testləri işlədir, APK yığır və (sabit açar varsa) Release yaradır.
