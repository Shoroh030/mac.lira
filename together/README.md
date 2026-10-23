# Вместе Android releases

The app checks the public GitHub repository's latest stable release and offers to download its APK. Android still requires the user to allow installs from this app and confirm the system installer; updates are never installed silently.

## Publish an update

Push a version tag such as `v1.2.0`. The `Android release` workflow builds a signed APK and publishes it as `together-1.2.0.apk`. The in-app updater accepts only that version-matched asset, requires GitHub's SHA-256 asset digest, verifies the downloaded file, package name, version code and signing certificate, then hands it to Android's package installer.

The workflow uses `VMEST_KEYSTORE_BASE64`, `VMEST_KEYSTORE_PASSWORD`, `VMEST_KEY_ALIAS`, and `VMEST_KEY_PASSWORD` when they are configured in GitHub Actions. It verifies the resulting APK against the existing app signing-certificate fingerprint before publishing. If signing secrets are absent, the workflow skips release publication; an already-signed APK can be uploaded manually instead. The signing certificate must match installed copies of the app; Android will reject an update signed with a different key. Do not replace the signing key or put it in source control. The current development APK uses the local Android debug signing key, so a production release key cannot update those installations without a separate migration/reinstall plan.

The in-app updater uses the latest stable release endpoint for `Ko0137/mac.lira`; the repository must remain public for unauthenticated clients to find releases. No GitHub token is embedded in the app.
