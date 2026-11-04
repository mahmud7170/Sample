# Rate Calculator (modern rebuild)

## Get the APK (no PC needed)
1. Upload everything in this folder to a GitHub repo (keep the `.github` folder).
2. Actions tab -> Build APK -> wait ~5 min -> Artifacts -> download `RateCalculator-APK` -> install `app-release.apk`.

## Notes
- Installs side by side with the old app (applicationId ends in `.v2`).
- Formulas are in `Calc.kt`: Total = rate x qty; CC = qty / tablets-per-CC.
- Tap a product card to set its quantity (or edit the product).
