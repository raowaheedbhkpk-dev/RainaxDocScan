# Rainax DocScan – document scanner for Android (Kotlin)

Features
- Dark home screen with tool grid, Recents, bottom tabs (Home / All Documents / Tools / Me)
- Import photos to PDF, import PDF files, extract text from any image, save PDF to Downloads
- Camera scan with auto edge detection, crop, rotate, clean/filters (Google ML Kit Document Scanner)
- Import photos from the gallery, multi-page documents
- ID card mode (2 pages)
- Library with search, rename, delete, multi-select
- Merge several documents into one PDF
- Filters on saved docs: Original / Grayscale / Black & white
- Sign (draw signature, stamped on any page)
- OCR text extraction (on device, Latin scripts) with copy / share
- Share as PDF, as JPG images, or as a password-protected PDF
- Fully offline processing, no watermark, no account
- AdMob: banner on every screen, interstitial after a scan (max 1/min), rewarded ad before OCR

## Build
1. Install Android Studio (latest). Open the `RainaxDocScan` folder. Let Gradle sync.
2. Run on a phone/emulator with Google Play services.
3. `applicationId` is `com.rainax.docscan` (set in `app/build.gradle.kts`). It must be unique on Google Play.

## Before publishing
1. Create an AdMob account, add the app, then replace:
   - app id in `app/build.gradle.kts` (`admobAppId`)
   - ad unit ids in `Ads.kt` (currently Google TEST ids)
   Never click your own live ads.
2. Rename the app and make your own icon (512x512 PNG for the Play listing). Do not use the CamScanner name or logo (Rainax DocScan is an independent app).
3. Build a signed bundle: Build > Generate Signed Bundle (AAB). Google Play needs AAB, not APK.
4. Play Console: create app, upload AAB, fill Data safety (ads = advertising ID collected), add a privacy policy URL, content rating, and the "Contains ads" declaration.
5. New personal developer accounts must run a closed test with 12+ testers for 14 days before production access.

## Notes
- Not compiled or run in the build environment; open it in Android Studio and fix any version warnings on first sync.
- OCR supports Latin scripts only (English, Urdu not included). Add other ML Kit recognizers for more scripts.
