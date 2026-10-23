# Вместе Android releases

The app checks the public GitHub repository's latest stable release and offers to download its APK. Android still requires the user to allow installs from this app and confirm the system installer; updates are never installed silently.

## Publish an update

One-time setup by a repository administrator: open **Settings → Secrets and variables → Actions** in `Shoroh030/mac.lira` and add these repository secrets:

- `VMEST_KEYSTORE_BASE64`: Base64-encoded Android signing keystore.
- `VMEST_KEYSTORE_PASSWORD`: keystore password.
- `VMEST_KEY_ALIAS`: signing-key alias.
- `VMEST_KEY_PASSWORD`: signing-key password.

On Windows, the Base64 value can be sent to the GitHub secret directly from PowerShell without printing it or pasting it into chat:

```powershell
$keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)) |
  gh secret set VMEST_KEYSTORE_BASE64 --repo Shoroh030/mac.lira
```

Use the exact keystore that signed the installed app. If it is not the default Android debug keystore, change the file path and set the matching passwords and alias in the other three secrets.

The signing certificate must match the one used by installed copies; Android rejects updates signed by another key. This repository currently verifies against the certificate already installed on the two test phones. Do not create a new key, replace the existing keystore, put it in source control, or send it in chat. Only the repository administrator should add it to GitHub Actions secrets. The release workflow fails clearly when any secret is missing, and checks the certificate before publishing.

After the secrets are configured, push a version tag such as `v1.2.2`. The `Android release` workflow builds a signed APK and publishes it as `together-1.2.2.apk`. The in-app updater accepts only that version-matched asset, requires GitHub's SHA-256 asset digest, verifies the downloaded file, package name, version code and signing certificate, then hands it to Android's package installer. The version code is generated monotonically from the GitHub Actions run number.

The in-app updater uses the latest stable release endpoint for `Shoroh030/mac.lira`; the repository must remain public for unauthenticated clients to find releases. No GitHub token is embedded in the app.
