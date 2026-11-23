# Aladdin — haydovchilar uchun o'zbekcha ovozli yordamchi

## Imkoniyatlar
- Katta **GAPIRISH** tugmasi, ekran o'chmaydi, fonda ishlaydi (bildirishnomada ham GAPIRISH tugmasi bor).
- «Aladdin» deb ovoz bilan chaqirish (Sozlamalarda yoqiladi).
- «Uy» tugmasini uzoq bosib chaqirish (standart yordamchi qilib qo'yilsa).
- Kontaktga qo'ng'iroq: «Onamga qo'ng'iroq qil», «Akmalga tel qil».
- SMS, Telegram, WhatsApp xabarlarini ovoz chiqarib o'qish (kelganda avtomatik ham o'qiydi).
- SMS yuborish (tasdiq so'raydi).
- Musiqa: to'xtat / davom ettir / keyingi / oldingi. Ovoz balandligi.
- Navigator: «Chilonzorga yo'l ko'rsat».
- Erkin suhbat: OpenAI orqali har qanday savolga o'zbekcha javob.
- O'zbekcha ovoz: OpenAI TTS (gpt-4o-mini-tts), internet bo'lmasa telefon TTS'i.

## Birinchi ishga tushirish
1. APK'ni o'rnating (noma'lum manbalardan o'rnatishga ruxsat bering).
2. Barcha ruxsatlarni bering (mikrofon, kontaktlar, qo'ng'iroq, SMS, bildirishnoma).
3. **Sozlamalar** → OpenAI API kalitini kiriting → SAQLASH.
4. Ekrandagi sariq yozuvni bosib, Telegram/WhatsApp xabarlari uchun bildirishnomalarga ruxsat bering.
5. Nutqni tanish uchun telefonda **Google** ilovasi bo'lishi kerak (o'zbek tili qo'llab-quvvatlanadi).

## APK qanday yig'iladi
GitHub'ga yuklangan har bir o'zgarishdan keyin **Actions** avtomatik APK yig'adi.
Tayyor fayl: repozitoriyning **Releases** bo'limida `Aladdin.apk`.
