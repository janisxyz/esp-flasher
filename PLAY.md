# Publish ESP Flasher on Google Play

Privacy policy URL (Play Console):

https://janisxyz.github.io/esp-flasher/

Package: `espflasher.shizoghost.com`  
Version: `1.0.x` (`versionCode` = GitHub Actions Release AAB run number)  
Category: Tools  
Default language: English (United States)

Kotlin/R namespace stays `dev.espflasher.app`. Play only cares about `applicationId`.

## 1. Developer account

- Pay the Play one-time registration fee.
- If this is a personal account created after 13 Nov 2023 you must run a **14-day closed test with at least 12 testers** before production.

## 2. Upload key (shared with PiFlash)

Use **one Leftclick upload keystore for every app**. PiFlash already has it.

Play App Signing keys stay per-app (Google holds those). The **upload** key can be the same JKS / alias / passwords on ESP Flasher, PiFlash, and anything else.

GitHub cannot read PiFlash’s secrets from this repo. Copy the **same values and the same names**:

| Secret | Copy from PiFlash |
|--------|-------------------|
| `PIFLASH_STORE_BASE64` | same |
| `PIFLASH_STORE_PASSWORD` | same |
| `PIFLASH_KEY_ALIAS` | same (`piflash`) |
| `PIFLASH_KEY_PASSWORD` | same |
| `PLAY_SERVICE_ACCOUNT_JSON` | same JSON, then invite that service account on **this** Play app too |

Do **not** generate a second keystore.

Then: Play Console → Create app → package **`espflasher.shizoghost.com`** → enroll Play App Signing → run **Release AAB** (track `alpha`).

In Play Console → Users and permissions, give the existing Play API service account **Release to testing tracks** on ESP Flasher. Wait up to 24 hours if the API 403s.

## 3. Store listing copy

Paste from `fastlane/metadata/android/en-US/` (DE is in `de-DE/`).

Graphics are already in `fastlane/metadata/android/en-US/images/`.

Do **not** round the Play icon. Google applies the mask.

## 4. App content declarations

- Privacy policy: `https://janisxyz.github.io/esp-flasher/`
- Data safety: see `docs/DATA_SAFETY.md`
- Content rating: see `docs/CONTENT_RATING.md`
- Ads: no
- Target audience: 13+ utility; not a kids app
- News / COVID / government: no
- Financial features: no
- Health: no

## 5. Device catalog

`android.hardware.usb.host` is required. Phones without USB host will be excluded.

## 6. Target API

`targetSdk` is **35**. From **31 August 2026** new submissions must target **36**.

## 7. GitHub Pages (privacy policy)

**Live URL:** https://janisxyz.github.io/esp-flasher/

That URL is served from the user site [`janisxyz/janisxyz.github.io`](https://github.com/janisxyz/janisxyz.github.io) (`build_type=legacy`, branch `main`, path `/`, folder `esp-flasher/`). HTML matches `docs/index.html` in this repo.

Project Pages on **this** repo cannot be enabled with current credentials:

- `POST /repos/janisxyz/esp-flasher/pages` → 403 `Resource not accessible by integration` (GitHub App `grok-by-xai` has `administration:write` but **no `pages` permission**)
- `actions/configure-pages` `enablement: true` → same 403 (`GITHUB_TOKEN` has `pages:write` but **no `administration`**)
- PiFlash works because Pages was already enabled there (`build_type=workflow`)

When the policy changes, copy `docs/index.html` to `esp-flasher/index.html` on the user site.

To switch to project Pages later: repo Settings → Pages → Source **GitHub Actions** (needs a PAT/app with `pages:write`). Then restore the PiFlash deploy workflow.

## 8. Hook Play Developer API

Same Google Cloud service account as PiFlash is fine. Invite it on **this** app in Play Console → Users and permissions, with Release to testing tracks.

Closed testers join the opt-in link. API track name for closed testing is **`alpha`**.

## 9. What the Release AAB workflow does

`.github/workflows/release.yml`

- Actions → **Release AAB** → Run workflow (default track: **alpha** / closed testing)
- Or push tag `v1.0.0`
- Builds signed `app-release.aab`
- Creates a GitHub Release
- If `PLAY_SERVICE_ACCOUNT_JSON` is set, uploads to Play with status `completed`
