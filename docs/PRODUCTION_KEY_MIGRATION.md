# Production signing-key migration plan

## Status and safety boundary

This is a plan only. No signing keys, GitHub Actions secrets or variables, installed apps, or device data were changed.

The currently connected Honor and Pixel installations were read-only checked at `com.lira.vmest`, version `1.3.9` / versionCode `22`. Both are signed with `CN=Android Debug`, SHA-256 `9489b3c2541bd20259026ec6d6c185b84edaf39245bb2a442beb26b3b4534808`. The current debug build is `1.3.10` / versionCode `23`; it is not a production release. The repository release workflow requires `VMEST_PRODUCTION_CERT_SHA256`, which is not configured. The app manifest has `android:allowBackup="false"` and stores app state in private preferences/files, so uninstalling can erase local-only content.

**Do not uninstall, clear app data, install a different-signer APK, create a release tag, or change signing configuration until one of the migration paths below has passed its go/no-go checks and the user explicitly authorizes the device operation.**

## Preferred path: preserve installation through a verified signing lineage

1. **Identify and approve the production signer.** The owner must provide or select the intended production keystore through a secure, private channel. Never create or disclose its password, private key, or base64 keystore in the repository, chat, logs, or issue comments. Inspect only the public certificate fingerprint and confirm it is the intended long-term signer.
2. **Check whether the installed signer can authorize a key rotation.** On a secured local machine, confirm the private key corresponding to the installed debug certificate is available and controlled by the owner. Do not export it to CI or send it elsewhere. If it is unavailable or not trusted for this purpose, stop; do not claim that a normal APK upgrade can change signers.
3. **Run an isolated compatibility proof.** Using Android SDK signing tools, create a signing lineage from the current certificate to the approved production certificate. Build a disposable APK for the same `applicationId` and test the lineage update on disposable devices/emulators covering Android 10 (Honor-like) and a current Android release (Pixel-like). Confirm that Android accepts an in-place update, app-private preferences/files remain intact, authentication can be restored by signing in if needed, and the final APK reports the production signer. Do not use either personal phone for this proof.
4. **Validate the release candidate.** Set an explicitly approved, monotonic versionCode; run unit tests, assemble the signed release, lint, `apksigner verify`, the 16-KB ELF/ZIP audit, and the disposable-device update test. Verify the release signer fingerprint matches the approved public fingerprint and ensure the workflow rejects missing or mismatched fingerprints and any Android Debug signer.
5. **Configure GitHub only after approval.** The repository owner, not this plan, should add the public `VMEST_PRODUCTION_CERT_SHA256` variable and any required signing secrets through GitHub’s protected settings. Do not print secret values. Keep the signing lineage file non-secret and review whether it belongs in the repository before adding it.
6. **Authorize rollout separately.** After all checks pass, ask for explicit permission before publishing the version and before updating each personal phone. Install with a normal package update only if Android confirms the signing lineage; preserve app data and verify the installed version, signer, launch, local records, and pair session afterward.

The lineage route is a hypothesis to validate, not a guarantee: acceptance depends on the exact certificate, APK signing schemes, OS version, and install path. A failure on either disposable OS is a no-go for personal devices.

## Fallback path: explicit export and restore

Use this only if signing-lineage continuity is impossible and the owner explicitly accepts a reinstall:

1. Implement a user-visible, authenticated local export/import for app-private content. Inventory `vmeste_data` preferences, local-only notes/timers/settings, profile avatar files, and `chat_archive`; separate server-synced data from local-only data.
2. Exclude access/refresh tokens, credentials, Supabase keys, temporary recordings/downloads, and cached release APKs. Require a fresh sign-in after restore. Encrypt the export with a user-controlled passphrase; do not upload it automatically.
3. Add tests for complete round-trip restore, malformed or tampered archives, wrong passphrase, interrupted export/import, duplicate IDs, and preservation of existing destination data. Test only with disposable profiles first.
4. Verify an export and restore on disposable devices/emulators, including comparison of record counts and representative content without logging content or secrets.
5. Obtain explicit consent for each personal device. Keep the verified export in a user-chosen safe location before uninstalling. Install the production-signed APK, restore, sign in, reconnect the existing pair, and verify records before declaring success. Never run uninstall or clear-data commands as part of this plan.

If export/restore cannot be demonstrated without loss, do not migrate the personal phones. Continue local debug testing only; do not publish a debug-signed APK as production.

## Go/no-go checklist

- [ ] Production keystore ownership, custody, recovery, and public certificate fingerprint confirmed.
- [ ] Signing-lineage proof passes on disposable Android 10 and current-Android devices, **or** export/restore fallback passes its tests.
- [ ] VersionCode is greater than every already distributed version.
- [ ] Unit tests, build, lint, signer verification, and 16-KB audit pass on the exact release artifact.
- [ ] GitHub signing configuration is added by the owner after approval; secrets remain private.
- [ ] Release asset and stable download URL resolve to the verified production artifact.
- [ ] User explicitly authorizes release publication and each personal-device update.
- [ ] Post-update app data, account reauthentication, pairing, and key workflows are checked without clearing data.

Until every applicable check is complete, the production release and device migration remain blocked.
