# Rate Calculator (modern rebuild)

## Get the APK (no PC / Android Studio needed)
1. Create a free GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the repo -> **Actions** tab -> **Build APK** -> wait ~5 minutes.
3. Open the finished run -> **Artifacts** -> download `RateCalculator-APK` -> unzip -> install `app-release.apk`.

(Or in Android Studio: open this folder and run Build > Build APK.)

## Notes
- Installs side by side with your old app (different applicationId ending in `.v2`). Data is not carried over; use Export CSV in the old app if it has it, then Import CSV here.
- Calculation rules are all in `Calc.kt`. Adjust there if your original formulas differ.
- APK is signed with the debug key. Use your own keystore for Play Store release.
