# ప్రజాకీయం (Prajakeeyam)

A public, village-level problem tracker for Andhra Pradesh. People pick their constituency → mandal → village, sign in with Google, and post the problems of their village (roads, water, power…) so the whole village, and its MLA, can see them in one place: with a status, upvotes ("I have this problem too") and comments.

Independent and non-partisan. Not linked to any political party or government body.

```
data/      ap_locations.json(.gz) built by the pipeline; overrides/*.csv are the only hand-edited data
backend/   FastAPI API (Render), SQLAlchemy models, data pipeline + seed scripts, pytest suite
android/   Native Kotlin + Jetpack Compose app (minSdk 24, targetSdk 36, Google Sign-In, Cloudinary photos)
docs/      privacy.html, terms.html, delete-account.html (publish with GitHub Pages; Play Console needs the URLs)
render.yaml  Render Blueprint for the API
```

## 1. Location data (26 districts · 175 constituencies · ~680 mandals · ~16,600 villages)

```powershell
cd backend
python -m venv .venv; .\.venv\Scripts\pip install -r requirements.txt -r requirements-dev.txt
$env:PYTHONUTF8 = "1"
python scripts\build_locations.py          # downloads + scrapes (cached in data/raw), writes data/ap_locations.json(.gz)
```

Sources: constituencies/districts from [satishvmadala/andhrapradesh_opendata_locations](https://github.com/satishvmadala/andhrapradesh_opendata_locations), constituency→mandal from English Wikipedia "… Assembly constituency" pages, mandal→village from the [LGD mirror](https://github.com/planemad/india-local-government-directory), Telugu names from Wikipedia language links. See `DATA-LICENSES.md`.

Check `data/reports/build_report.md` after every run. Fix data **only** via `data/overrides/*.csv`:

| file | purpose |
|---|---|
| `ac_wiki_titles.csv` | Wikipedia title to use for a constituency (ambiguous names such as the two Gannavarams) |
| `mandal_aliases.csv` | map a Wikipedia mandal spelling to an LGD sub-district code (`lgd_code` 0 drops the row; `ac_no` optional) |
| `ac_mandal_overrides.csv` | move/add/remove an LGD mandal to/from a constituency |
| `names_te.csv` | Telugu display names |

After rebuilding: copy `data/ap_locations.json` to `android/app/src/main/assets/` and run `python scripts\seed_db.py` against the database (idempotent upsert; the API serves the bundle from the DB).

## 2. Backend (FastAPI)

```powershell
cd backend
python scripts\seed_db.py                  # SQLite dev.db by default
$env:DEV_FAKE_AUTH = "1"                   # enables POST /auth/dev for the emulator (never in production)
.\.venv\Scripts\uvicorn app.main:app --reload
# http://127.0.0.1:8000/docs
.\.venv\Scripts\python -m pytest -q
```

Environment variables (see `app/config.py`): `DATABASE_URL`, `JWT_SECRET`, `FIREBASE_PROJECT_ID`, `CLOUDINARY_CLOUD_NAME`, `ADMIN_EMAILS` (comma separated: these Google accounts become moderators), `ENV=production`, `DEV_FAKE_AUTH` (dev only).

Key endpoints: `GET /health`, `GET /locations/version|bundle`, `GET /search/villages?q=`, `POST /auth/firebase`, `GET|PATCH|DELETE /me`, `GET|POST /problems`, `GET|PATCH /problems/{id}`, `POST /problems/{id}/upvote|comments|report`, `POST /users/{id}/block`, `/admin/*` (moderation).

## 3. Android app

Open `android/` in Android Studio, or:

```powershell
cd android
.\gradlew.bat assembleDebug                 # app\build\outputs\apk\debug\app-debug.apk (talks to http://10.0.2.2:8000)
.\gradlew.bat bundleRelease                 # AAB for Play (needs android/keystore.properties, see app/build.gradle.kts)
```

Configuration lives in `android/gradle.properties` (`APP_ID`, `API_BASE_URL_RELEASE`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET`, `PRIVACY_URL`) and `android/app/google-services.json` (Firebase + Google Sign-In). While the JSON has no web OAuth client, **debug** builds fall back to the backend's dev login (only honoured when the server runs with `ENV=dev` and `DEV_FAKE_AUTH=1`). Photos are enabled once `CLOUDINARY_CLOUD_NAME` is set.

Design notes: no Firebase/Hilt/Room/Retrofit/Coil (hand-rolled OkHttp client, org.json, SharedPreferences), R8 + resource shrinking, only `en` + `te` resources, emoji category icons, the whole village list ships inside the APK (about 170 KB compressed) and refreshes from the server when the version changes.

## 4. Going live (checklist)

1. **Database: Supabase Postgres** (project in ap-south-1). Use the **Session pooler** string, not the direct `db.<ref>.supabase.co` host: the direct host is IPv6-only and neither this laptop nor Render can reach it. Format: `postgresql://postgres.<project-ref>:<url-encoded-password>@aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require` (URL-encode `%` as `%25` and `@` as `%40` in the password). Keep it in `backend/.env` locally (git-ignored) and in Render's `DATABASE_URL`. Seed from your laptop: `python scripts\seed_db.py`. Note: free Supabase projects pause after a week without traffic; open the dashboard to resume. (Neon is the alternative if that becomes annoying; Render's own free Postgres is deleted after 30 days.)
2. **Firebase (Google Sign-In)**: Firebase project `prajakeeyam-4421a`, Android app with package `com.prajakeeyam`.
   * Authentication → Sign-in method → enable **Google** (pick a support e-mail).
   * Project settings → Your apps → add the SHA-1 fingerprints: debug keystore, the upload keystore (`android/keystore/upload-keystore.jks`, password in the git-ignored `android/keystore.properties`), and later the Play App Signing key (Play Console → App integrity). `.\gradlew.bat signingReport` prints them.
   * Re-download `google-services.json` into `android/app/`. The build reads the project id, app id, API key and the web OAuth client id from it; no Gradle plugin is involved. Users sign in with Google through Firebase Auth and the backend verifies the Firebase ID token (`FIREBASE_PROJECT_ID` env var on Render).
3. **Cloudinary**: Settings → Upload → add an **unsigned** preset named `problems` with incoming transformation `c_limit,w_1280,h_1280,q_auto,f_auto` and folder `problems`. Put the cloud name in `gradle.properties` and in the backend env.
4. **Render**: New → Blueprint → this repo (`render.yaml`). Set `DATABASE_URL`, `GOOGLE_WEB_CLIENT_ID`, `CLOUDINARY_CLOUD_NAME`, `ADMIN_EMAILS`. Verify `https://<service>.onrender.com/health`, then put that URL in `API_BASE_URL_RELEASE`. Free instances sleep after 15 min: add a free cron-job.org job that GETs `/health` every 10 minutes.
5. **GitHub Pages** for `docs/` (Settings → Pages → `/docs`). Replace `CONTACT_EMAIL` in the three pages. Put the privacy URL in `PRIVACY_URL` and in Play Console.
6. **Play Console**: create the app, upload the AAB to Internal testing, fill Data safety (name, e-mail, photos, user content; no ads, no location), content rating, UGC declarations (report/block/delete exist), add the privacy policy and account-deletion URLs, then closed testing → production.
