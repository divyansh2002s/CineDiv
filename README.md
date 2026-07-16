# CineDiv Android 1.2

CineDiv is a private Android APK that reads the full Movies and Shows catalogs from the connected Google Sheet.

## Main features

- Movies and Series modes
- Cinema Vault with full Google Sheet catalog
- Search by title, words or year
- One-tap search clear button
- Decade and custom year filters
- A-Z, newest and oldest sorting
- Local Favorites
- Local Watchlist with watched status
- Add to Favorites or Watchlist from Cinema Vault
- Add manual local titles to Favorites or Watchlist
- Offline catalog cache
- Black, gold and white cinema theme

## Google Sheet behavior

The app is read-only. It reads:

- Movies tab, columns A and B
- Shows tab, columns A and B

It does not add, edit or delete Google Sheet rows.

## Build with GitHub Actions

1. Upload everything inside this folder to the repository root.
2. Ensure `.github/workflows/build-apk.yml` exists.
3. Open Actions in GitHub.
4. Open `Build CineDiv APK` and click `Run workflow`.
5. Download the `CineDiv-APK` artifact.
6. Extract it and install `app-debug.apk`.

## Version

- Version code: 3
- Version name: 1.2.0
- Minimum Android: Android 7.0 / API 24
