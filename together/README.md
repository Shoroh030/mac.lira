# Вместе Android releases

The app checks the public GitHub repository's latest stable release and offers to download its APK. Android still requires the user to allow installs from this app and confirm the system installer; updates are never installed silently.

## Pairing and sharing

On a fresh install, a skippable animated welcome screen appears before the sign-in/registration dialog. Existing signed-in users and incoming share links bypass it. After signing in, create a couple to display its invite QR code or scan the other phone's code from **Пригласить**; entering the eight-character code manually remains available. Stories can contain text or a photo and expire after 24 hours. Android's share sheet can send text links or photos to the app; they appear as a chat draft for review before sending, and web links in chat open in the in-app viewer.

## Profile photos

Selected profile photos are auto-oriented from EXIF metadata, center-cropped to a square, and resized to a 256 px JPEG before local storage and private couple sync. The app renders that thumbnail as a circular avatar, so portrait and landscape images fit consistently without uploading the full-size source.

## Gift links

Gift Radar opens public HTTP(S) links in an in-app WebView instead of launching another app. Local/private hosts and non-web schemes are blocked, as are file/content access and JavaScript; no JavaScript bridge is registered. Some shops require scripts, sign-in, or protected playback and may show a limited or unavailable page; the app does not bypass those restrictions.

## Watch together

From the home screen or **Просмотр**, **Создать комнату** makes the current phone the host; if active rooms already exist, the app asks whether to select one or explicitly replace them. The host is then shown a QR code, invitation code and Android share action. The partner can open the shared `vmeste://room/<UUID>` link, scan the QR, enter the code or use **Подключиться к комнате партнёра** and choose the active room. Joining never creates another room automatically.

If a room signal is rejected with HTTP 403/SQLSTATE `42501`, the client explains the RLS requirements: the sender must be an authenticated member of the same couple, `sender_id` must match the current account, and the referenced room must still be active with matching room and couple IDs. If these conditions already match, the project administrator must verify the deployed policy; do not close rooms or apply remote migrations as an automatic recovery.

The room synchronizes YouTube videos and public HTTPS MP4, HLS (`.m3u8`), and WebM links. The host can select a link in the room; the partner receives it and playback/pause/seek state is synchronized through couple-scoped Supabase room signals. Other sites may open in the in-app WebView, but their player controls are not synchronized. Protected-content and embed restrictions are not bypassed. Text chat and playback work without camera or microphone permissions. Both are off when the room opens; each is requested only after its own button is tapped. Camera and microphone tracks are sent to the partner over WebRTC and are not uploaded to Supabase. Screen capture is not supported. Apply `supabase/migrations/20261025000100_watch_player_signals.sql` before using synchronized playback with the remote Supabase project.

## 16 KB memory pages

The Android module packages only `arm64-v8a` and `x86_64`. Check every native library's ELF `PT_LOAD` alignment and the APK's 16 KB ZIP alignment after building:

```powershell
$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
  Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
powershell -ExecutionPolicy Bypass -File .\together\scripts\check-16kb-compatibility.ps1 `
  -ApkPath .\together\build\outputs\apk\debug\together-debug.apk `
  -ZipAlignPath "$($buildTools.FullName)\zipalign.exe"
```

Passing this static check verifies the packaged ELF load segments and APK ZIP alignment; it does not replace runtime testing on a 16 KB Android emulator or device. Devices limited to `armeabi-v7a` are not supported by this module's ABI filters.

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

After the secrets are configured, push a version tag such as `v1.3.7`. The `Android release` workflow builds a signed APK and publishes it as `together-1.3.7.apk`. The in-app updater accepts only that version-matched asset, requires GitHub's SHA-256 asset digest, verifies the downloaded file, package name, version code and signing certificate, then hands it to Android's package installer. The version code is generated monotonically from the GitHub Actions run number.

The in-app updater uses the latest stable release endpoint for `Shoroh030/mac.lira`; the repository must remain public for unauthenticated clients to find releases. No GitHub token is embedded in the app.
