# Android Package Name Configuration

The OCTO Android client ships with **`com.example.octo`** as a placeholder
`applicationId`. This is intentional — the upstream OSS fork is not bound
to any specific Play Store listing, and you cannot build a signed /
distributable APK or AAB as-is.

Before building your fork, replace the placeholder everywhere below with
your own reverse-DNS identifier (e.g. `com.yourcompany.octo`).

## Files to change

| File | What to change |
|------|----------------|
| `app/build.gradle` (or `.gradle.kts`) | `defaultConfig { applicationId "com.example.octo" }` |
| `app/src/main/AndroidManifest.xml` | `package="com.example.octo"` attribute (if present) and any fully-qualified component names |
| `app/src/*/AndroidManifest.xml` (debug / release / flavors) | Same as above |
| `google-services.json` | Regenerate from your Firebase console (see `firebase-template.md`) |
| Source file package declarations | `package com.example.octo...` at the top of every `.kt` / `.java` under `app/src/main/java/` |
| `app/src/main/res/xml/*.xml` (shortcuts, file providers, network security) | Any `authorities="com.example.octo.fileprovider"` etc. |
| `app/src/main/res/values/strings.xml` | Any string resource that embeds the package name for deep links |

## One-liner

```bash
# Preview hits first:
grep -rl 'com.example.octo' app/ | xargs grep -nH 'com.example.octo'

# Then rewrite (GNU sed on Linux; on macOS drop the empty -i)
grep -rl 'com.example.octo' app/ \
    | xargs sed -i 's|com\.example\.octo|com.yourcompany.octo|g'

# Rename the source tree directories so `package` declarations line up:
mv app/src/main/java/com/example/octo app/src/main/java/com/yourcompany/octo
# repeat for src/debug, src/release, src/test, src/androidTest if present
```

## After renaming

1. `./gradlew clean assembleDebug` — confirms compilation still works
   against the new package.
2. Open Android Studio → Build Variants → confirm every variant now reads
   `applicationId com.yourcompany.octo`.
3. Firebase console → Add app with the new package → download the new
   `google-services.json` → drop into `app/` (see `firebase-template.md`).
4. If you ship deep-link intent filters (e.g. `intent-filter` with
   `<data android:scheme="https" android:host="..."/>`), update the host
   **and** host an App Links verification JSON under `.well-known` on
   that domain.

## Tooling gotchas

- **R class references** (`import com.example.octo.R`) are auto-generated,
  but stale IDE caches sometimes hold onto the old package. Invalidate
  caches (File → Invalidate Caches and Restart).
- **ProGuard / R8** — if your `proguard-rules.pro` keeps specific classes,
  the `-keep class com.example.octo.**` lines need to be updated.
- **Gradle Plugin versions** sometimes strip `applicationId` from
  androidManifest in favour of the `defaultConfig` value. If you find a
  build that embeds the placeholder anyway, inspect the merged manifest
  under `app/build/intermediates/merged_manifests/`.

## Questions

Every upstream pull may re-introduce the placeholder in new files. Pair
this doc with a CI check that fails the build if `com.example.octo`
appears in any tracked file outside `overlay/` / docs.
