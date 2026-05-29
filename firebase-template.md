# Android Firebase Configuration Template

This fork ships **no** `google-services.json`. The `octo-release`
pipeline deny-paths `**/google-services.json` before publishing so no
internal Firebase project ID or API key leaks out. You must supply your
own if your fork uses Firebase.

## What you need

1. A Firebase project on [console.firebase.google.com](https://console.firebase.google.com)
2. An Android app registered in that project under your own package name
   (the one you configured per [`README-BUNDLE-ID.md`](README-BUNDLE-ID.md)
   — not the `com.example.octo` placeholder)
3. The `google-services.json` downloaded from Project Settings → General
   → Your apps → Download `google-services.json`

## Where to put it

Drop the file at:

```
app/google-services.json
```

Keep it **untracked**. The upstream `.gitignore` in this fork already
excludes `**/google-services.json`; re-check after every `git pull`.

## Multi-flavour setup

If you use product flavours (e.g. `dev` / `staging` / `prod`), Firebase
expects a separate `google-services.json` per flavour:

```
app/src/dev/google-services.json
app/src/staging/google-services.json
app/src/prod/google-services.json
```

Each JSON file must contain the matching package name and Firebase
project ID for that flavour. The Google Services Gradle plugin picks the
right file at build time based on the active flavour.

## Verifying the config

Build once and look for the generated file under:

```
app/build/generated/res/google-services/<flavour>/<buildType>/values/values.xml
```

It contains the materialised Firebase keys. If your package name in
`build.gradle` does not match the package name inside
`google-services.json`, the Gradle plugin will fail the build with a
clear error.

## Firebase services that require extra setup

- **Crashlytics** — add the Gradle plugin; no extra file, but in CI you
  need to upload symbols with `./gradlew app:uploadCrashlyticsSymbolFileRelease`.
- **Messaging (FCM)** — server-side sends need your FCM Server Key or the
  newer OAuth flow; do NOT commit those into the Android repo. They live
  in your backend.
- **Remote Config** — defaults live in `res/xml/remote_config_defaults.xml`.
  This file does not contain secrets, so feel free to commit it in your
  fork.
- **App Check** — requires per-app Play Integrity / SafetyNet keys; these
  live in the Firebase console, not in `google-services.json`.

## Troubleshooting

- **Build fails with `No matching client found for package name`** → the
  package name in `build.gradle` does not match any `package_name` inside
  `google-services.json`. Re-download the file after you register the
  correct package in Firebase console.
- **FCM tokens are null in release** → check Crashlytics isn't blocking
  the init; verify ProGuard/R8 isn't stripping Firebase classes with
  `-keep class com.google.firebase.**`.
