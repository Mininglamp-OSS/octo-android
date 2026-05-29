# Android Signing Configuration

This fork intentionally ships **no signing material**. Every `*.jks`,
`*.keystore`, `*.p12`, and `*.cer` has been stripped before publish by the
`octo-release` pipeline. The pipeline also deny-paths
`**/keystore.properties` and `**/signing.properties` so no release-key
alias or password bleeds through.

You cannot build a distributable APK / AAB without providing your own
signing configuration.

## What you need

| Asset | How to generate | Fork location |
|-------|-----------------|---------------|
| Upload key (Play App Signing) | `keytool -genkey -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000` | **Do NOT commit** — keep in CI secret store |
| Play App Signing certificate | Google Play Console → App Signing | Google holds the master, you only need the upload cert |
| Debug keystore | Android Studio auto-creates `~/.android/debug.keystore` | Leave it in `~/.android/` — never commit |

## Gradle configuration

Create an UNTRACKED `keystore.properties` file next to `app/build.gradle`:

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=<secret>
keyAlias=upload
keyPassword=<secret>
```

Make sure `.gitignore` covers it (the upstream `.gitignore` in this fork
already excludes `**/keystore.properties`, but re-check when you pull).

Wire it into `app/build.gradle`:

```groovy
def keystorePropsFile = rootProject.file("app/keystore.properties")
def keystoreProps = new Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.withInputStream { keystoreProps.load(it) }
}

android {
    signingConfigs {
        release {
            if (keystorePropsFile.exists()) {
                storeFile     file(keystoreProps['storeFile'])
                storePassword keystoreProps['storePassword']
                keyAlias      keystoreProps['keyAlias']
                keyPassword   keystoreProps['keyPassword']
            }
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

## CI signing (GitHub Actions)

Base64-encode the keystore and the properties file into repo secrets:

```bash
base64 -i upload.jks | pbcopy       # paste into KEYSTORE_JKS_B64 secret
base64 -i app/keystore.properties | pbcopy  # paste into KEYSTORE_PROPS_B64 secret
```

Then at workflow time:

```yaml
- name: Restore signing material
  run: |
    echo "${{ secrets.KEYSTORE_JKS_B64 }}"   | base64 -d > app/upload.jks
    echo "${{ secrets.KEYSTORE_PROPS_B64 }}" | base64 -d > app/keystore.properties
- name: Build signed release bundle
  run: ./gradlew bundleRelease
```

## Play App Signing (recommended)

Enrol in Play App Signing so Google holds the signing certificate and you
only manage the upload key. If your upload key ever leaks, you can rotate
it without losing update continuity on the Play Store.
<https://support.google.com/googleplay/android-developer/answer/9842756>

## Reminder

The `octo-release` scrub list is not a substitute for repo-level
`.gitignore`. If your fork accidentally tracks a signing file, rotating
the key is the only recovery — once it is in git history it may survive
a force-push depending on GitHub's caching.
