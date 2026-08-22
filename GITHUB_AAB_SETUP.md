# Image Studio — GitHub AAB Build Setup

This repository package was prepared from the latest uploaded Image Studio project.

## Verified app version

- `versionName`: **1.2**
- `versionCode`: **3**
- `applicationId`: `com.aistudio.imagecompressor.rtpkms`
- `targetSdk`: 36
- `compileSdk`: 36.1
- AGP: 9.1.1
- Kotlin: 2.2.10

## Build an AAB on GitHub

1. Create a new GitHub repository.
2. Upload the contents of this package to the repository root.
3. Push to `main` (or `master`) or use **Actions → Build Android AAB → Run workflow**.
4. Open the completed workflow run.
5. Download the artifact named `image-studio-release-aab`.

## Play Store signing

For a Play Store upload, the release AAB should be signed with the correct upload keystore.

Create these GitHub Actions secrets:

- `KEYSTORE_BASE64` — Base64-encoded `.jks` upload keystore
- `STORE_PASSWORD` — keystore password
- `KEY_PASSWORD` — key password
- `KEY_ALIAS` — key alias (the current Gradle configuration defaults to `upload` when this secret is omitted)

### Create a Base64 keystore secret on Windows PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))
```

Copy the output into the GitHub secret `KEYSTORE_BASE64`.

### Important

Do not commit the `.jks` keystore, passwords, API keys, `.env`, or Google service credentials to GitHub.

The workflow can still build an unsigned AAB if the signing secret is not supplied. **An unsigned AAB should not be uploaded to Google Play.** Add the correct signing secrets before the production release.

## Versioning

The current project is set to:

```text
versionName = "1.2"
versionCode = 3
```

For the next Play Store update, increase `versionCode` to a higher integer (for example `4`) and update `versionName` as appropriate.
