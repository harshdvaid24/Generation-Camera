# Releasing Generation Camera

Generation Camera (`com.generationcamera`) ships to Google Play from GitHub Actions: Gradle builds and signs the app bundle, and fastlane hands it to the Play Developer API. There is no backend, server or API to deploy; the app is offline and holds no INTERNET permission.

| Workflow | File | Runs on | What it does |
|----------|------|---------|--------------|
| Android CI | `.github/workflows/android.yml` | Every branch push, pull requests, manual run | Unit tests, debug APK artifact |
| Play Store release | `.github/workflows/release.yml` | Tag push `v*`, manual run | Unit tests, signed AAB, upload to a Play track |
| Play Store listing | `.github/workflows/play-listing.yml` | Manual run | Pushes `fastlane/metadata/android/` to the store listing |

The two Play workflows share a concurrency group, so only one of them talks to Play at a time. **What is possible on day one:** internal testing needs no review and reaches up to 100 testers within minutes through an opt-in link. Production is not available that fast on a new personal account: personal developer accounts created after 2023-11-13 must first run a closed test with at least 12 opted-in testers for 14 continuous days, then apply for production access.

## One-time setup

You need a Play Console developer account, the `gh` CLI signed in to this repository, JDK 17, and the upload keystore folder `~/keystores/generation-camera/` (`upload.jks`, alias `upload`, and `keystore.env`, which exports `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`).

> **Back up the keystore folder and never commit it.** `.gitignore` already excludes `*.jks`, `keystore.env` and `play-service-account*.json`. With Play App Signing, Google holds the real app-signing key; this keystore is only the upload key, so if it is lost it can be reset through Play Console support.

### 1. Create the app and upload the first bundle by hand

The Play Developer API cannot create an app, and it cannot accept an app's very first binary. Do both by hand. First create the app in Play Console (name "Generation Camera", an app rather than a game), then build a signed bundle on your machine:

```bash
source ~/keystores/generation-camera/keystore.env
./gradlew bundleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab   # must print "jar verified."
```

In Play Console, open Internal testing, create a new release, upload `app/build/outputs/bundle/release/app-release.aab`, keep the default Play App Signing setup (Google generates the app-signing key) and roll the release out. Then add your testers' Google accounts to the tester list and send them the opt-in link.

This first upload fixes two things. **The package name** `com.generationcamera` becomes permanent: this is the last chance to change `applicationId` in `app/build.gradle.kts`. **The upload key** is registered: Play remembers the certificate that signed this bundle and rejects later uploads signed with any other key.

### 2. Publish the privacy policy

The policy lives in `/docs` and is served by GitHub Pages at https://harshdvaid24.github.io/Generation-Camera/privacy.html. Enable Pages once in the repository settings (deploy from branch `claude/end-to-end-research-dev-x2ck2x`, folder `/docs`), then confirm the page is live before you paste the URL into Play Console:

```bash
curl -sI https://harshdvaid24.github.io/Generation-Camera/privacy.html | head -1   # expect: HTTP/2 200
```

### 3. Create the service account

The workflows call the Play Developer API as a Google Cloud service account.

1. In Google Cloud Console, create or pick a project and enable the **Google Play Android Developer API**.
2. Create a service account in that project, then create a JSON key for it. Save the downloaded file as `~/keystores/generation-camera/play-service-account.json`.
3. In Play Console, open Users and permissions, invite the service account's email address, and grant it permissions for this app: release to testing tracks, release to production, and manage store presence.
4. Store the JSON as a repository secret:

```bash
gh secret set PLAY_SERVICE_ACCOUNT_JSON < ~/keystores/generation-camera/play-service-account.json
```

Permission changes can take a while to reach the API. If the first run is refused, wait and retry before you change anything.

### 4. Check the GitHub secrets

| Name | Kind | Value | Who sets it |
|------|------|-------|-------------|
| `KEYSTORE_BASE64` | Secret | base64 of `upload.jks` | Set once from the owner's Mac (commands below) |
| `KEYSTORE_PASSWORD` | Secret | Keystore password | Same |
| `KEY_ALIAS` | Secret | `upload` | Same |
| `KEY_PASSWORD` | Secret | Key password | Same |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | Raw JSON of the service-account key | Owner, step 3 |
| `PLAY_RELEASE_STATUS` | Variable, optional | `draft` or `completed`, used by tag-triggered runs (default `draft`) | Owner, after the first publish |

```bash
gh secret list        # names only; all five secrets should be listed
# Create or rotate the keystore secrets:
source ~/keystores/generation-camera/keystore.env
base64 < ~/keystores/generation-camera/upload.jks | gh secret set KEYSTORE_BASE64
printf %s "$KEYSTORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD
printf %s "$KEY_ALIAS" | gh secret set KEY_ALIAS
printf %s "$KEY_PASSWORD" | gh secret set KEY_PASSWORD
```

## Shipping a release

Before you ship: Android CI is green on the default branch, the release notes in `fastlane/metadata/android/en-US/changelogs/default.txt` are current (Play allows 500 characters), and the release build passed the device smoke test below.

### Tag flow

```bash
git tag v1.3.0
git push origin v1.3.0
gh run watch          # pick the "Play Store release" run
```

A `v*` tag always builds and uploads to the **internal** track. `versionName` is the tag without the `v` (`1.3.0`). The release status is the `PLAY_RELEASE_STATUS` variable, or `draft` if it is not set.

### Manual run

```bash
gh workflow run release.yml -f track=internal -f status=draft                 # from the default branch
gh workflow run release.yml --ref v1.3.0 -f track=alpha -f status=completed   # a tag, to closed testing
gh workflow run release.yml -f upload=false                                   # build only, no upload
```

| Input | Values | Default | Meaning |
|-------|--------|---------|---------|
| `track` | `internal`, `alpha`, `beta`, `production` | `internal` | Internal testing, closed testing, open testing, production |
| `status` | `draft`, `completed` | `draft` | See "Draft or completed" |
| `upload` | `true`, `false` | `true` | `false` stops after the build |

Every run attaches the signed AAB and the R8 mapping file for 14 days as the artifact `generation-camera-release` (`gh run download <run-id> -n generation-camera-release`). A run started from a branch keeps the default `versionName`; pass `--ref <tag>` to take it from a tag. To move a build you have already tested to another track, promote that release in Play Console: a new workflow run would build a new bundle with a new `versionCode`.

### Draft or completed

- **`draft`** uploads the bundle and creates the release on the track without rolling it out. Open the release in Play Console, review it and roll it out. Until the app has been published once, the API accepts only `draft`; anything else fails with `Only releases with status draft may be created on draft app`.
- **`completed`** rolls the release out to the whole track as part of the upload, with no Console step. Switch to it once Play accepts it, and make tag builds follow:

```bash
gh variable set PLAY_RELEASE_STATUS --body completed
```

## Updating the store listing

The listing is kept in `fastlane/metadata/android/en-US/`. The repository is the source of truth: a run overwrites listing text and graphics that were edited by hand in Play Console.

| File | Listing field | Requirement |
|------|---------------|-------------|
| `title.txt` | App name | Up to 30 characters |
| `short_description.txt` | Short description | Up to 80 characters |
| `full_description.txt` | Full description | Up to 4000 characters |
| `images/icon.png` | App icon | 512x512 PNG |
| `images/featureGraphic.png` | Feature graphic | 1024x500 |
| `images/phoneScreenshots/*.jpg` | Phone screenshots, in file-name order | At least 2; ours are 1080x1920 |
| `changelogs/default.txt` | Release notes | Sent by the release workflow, not this one |

```bash
gh workflow run play-listing.yml
```

The editable sources for the icon, feature graphic and screenshots are on a Claude Design canvas: https://claude.ai/artifact/Uef26JzFxXAuJe1Dk2AtQp (private to the owner unless shared).

The `listing` lane attaches to the newest build on the **internal** track (fastlane resolves one release on a track even when it only pushes the listing), so at least one build must be uploaded there first; otherwise it stops with `No release on the 'internal' track yet`. It changes no release. To run it from your machine, or against another track (needs Ruby and `bundle install`):

```bash
SUPPLY_JSON_KEY=~/keystores/generation-camera/play-service-account.json \
  bundle exec fastlane android listing                    # or: ... listing track:production
```

The screenshots are rendered mock-ups: the phone UI is redrawn from the Compose source, and every photo in them is real output of the app's own shaders and LUTs (run through WebGL on three sample photos, credited in `ATTRIBUTIONS.md`). Replace them with device captures when convenient.

## Play Console checklist

Internal testing can start before these declarations are complete. Closed testing, open testing and production need them, so fill them in early. The answers for this app follow; for anything else the Console asks, answer from the same facts: an offline camera utility with no accounts, no ads, and no data leaving the device.

| Declaration | Answer |
|-------------|--------|
| Privacy policy | https://harshdvaid24.github.io/Generation-Camera/privacy.html |
| App access | All functionality is available without special access (no login) |
| Ads | No ads |
| Content rating | Utility / photography app, not a game or social app. No violence, sexual content, profanity, drugs or gambling. Users cannot share content with each other inside the app; Share hands a photo to another app through the Android share sheet |
| Target audience | 13 and over; not designed for children |
| Data safety | No data collected, no data shared. The app has no INTERNET permission and photos stay on the device |
| Advertising ID | Not used (no ads or analytics SDKs, no `AD_ID` permission) |
| Government app | No |
| Financial features | None |
| Health | None |
| Category | Photography |
| Contact email | Owner's choice; it is shown publicly on the listing |

## Versioning

- **`versionName`** is the tag without the `v`. Runs from a branch and local builds use the default in `app/build.gradle.kts` (`1.2.1` at the time of writing).
- **`versionCode`** is the release workflow's GitHub run number plus 100. Local builds use the default in `app/build.gradle.kts` (`8`), which is why the hand-uploaded first bundle sits below every CI build.
- Override either one locally with Gradle properties: `./gradlew bundleRelease -PversionCode=250 -PversionName=1.3.0`.
- Play accepts each `versionCode` once, and devices only update to a higher one. After the first upload, ship CI-built bundles only, so every code comes from the same counter.
- Do not rename `release.yml`. The run number belongs to the workflow file and would start again at 1; if that ever happens, raise the `+ 100` offset in the "Resolve version" step above the highest code Play has seen.

## Local signed build and device smoke test

The release build is R8-minified, so it can fail where the debug build works. As of 2026-10-06 it had not been run on a device (none was available when the pipeline was set up). Do this before every rollout:

```bash
source ~/keystores/generation-camera/keystore.env
./gradlew assembleRelease
adb uninstall com.generationcamera        # only if a debug or Play-installed build is on the device
adb install app/build/outputs/apk/release/app-release.apk
```

Debug builds, local release builds and Play-delivered builds are signed with three different keys, and Android refuses to install one over another, hence the uninstall. Without `keystore.env` loaded the release build is unsigned and cannot be installed. After the rollout, repeat the check on a build installed from the internal testing opt-in link, which is the bundle exactly as Play serves it.

1. First launch asks for camera access, then shows the live preview.
2. Turn the dial through several stops (for example 1900s, 1960s, 1990s, 2026): the look changes and the era's controls appear and disappear.
3. Capture in a few eras: the shutter sound plays and a "Saved to Pictures/GenerationCamera" toast appears.
4. Turn Frame on and capture: the print ejects and develops, accepts a note, and saves. Retake returns to the camera.
5. Open the gallery: the photos are listed, open full screen, swipe, and share.
6. The system back button steps from a photo to the gallery grid to the camera, and closes the print review.

## Rollback

Play has no undo. A device that has installed a build only ever moves to a higher `versionCode`, and Play accepts each `versionCode` once.

1. **Stop the damage.** In Play Console, open the release on its track and halt the rollout. People who have not updated yet stay on the previous release; people who already updated keep the bad build until a newer one arrives.
2. **Roll forward.** Ship the last good code under a new, higher `versionCode` by starting a new run on the last good tag (here `v1.3.0`):

```bash
gh workflow run release.yml --ref v1.3.0 -f track=internal -f status=completed
```

A new run gets a new run number, so Play accepts the build. The tag must already contain `release.yml`; for anything older, `git revert` the bad change on the default branch and tag a new patch release.

What does not work: re-running the old workflow run, or uploading its AAB again. A re-run keeps its run number, so both carry the old `versionCode`. Play rejects it as already used, and neither Play nor Android will put an older build on a device that already has a newer one.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Secret KEYSTORE_BASE64 is not set (see RELEASING.md)` | Keystore secrets are missing | Setup step 4, then start a new run |
| `Secret PLAY_SERVICE_ACCOUNT_JSON is not set (see RELEASING.md)` | Service-account secret is missing (the release workflow still attaches the signed AAB to the run) | Setup step 3, then start a new run |
| Gradle fails while signing the bundle (keystore, password or alias error) | `KEYSTORE_PASSWORD`, `KEY_ALIAS` or `KEY_PASSWORD` does not match the keystore | Set the secrets again from `keystore.env` (step 4) |
| `Package not found: com.generationcamera` | The app does not exist in Play Console, has no first upload yet, or the service account cannot see it | Setup steps 1 and 3 |
| `Only releases with status draft may be created on draft app` | The app has never been published | Run with `status=draft` and roll out in Play Console; leave `PLAY_RELEASE_STATUS` unset until `completed` is accepted |
| `The caller does not have permission` | The service account is not invited, lacks a permission for this app, or the change has not propagated | Check step 3.3, wait, retry |
| `Google Play Android Developer API has not been used in project ... or it is disabled` | The API is not enabled in the Cloud project | Step 3.1, then retry after a few minutes |
| `Could not parse service account json` | The secret is not the raw JSON key file | Run the `gh secret set` command from step 3 again |
| `Version code N has already been used` | A finished run was re-run, or that code was uploaded by hand | Start a new run; it gets a new run number |
| Play says the bundle is signed with the wrong key | CI signs with a different keystore than the first upload | Compare `keytool -printcert -jarfile app-release.aab` with the upload certificate shown in Play Console, then fix `KEYSTORE_BASE64` |
| `No release on the 'internal' track yet` | The listing workflow ran before any build was uploaded to internal testing | Upload a build (setup step 1), then run it again |
