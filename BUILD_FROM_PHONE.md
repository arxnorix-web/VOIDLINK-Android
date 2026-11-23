# Build VOIDLINK APK from your phone (no PC required)

This project includes `.github/workflows/android.yml` for a cloud build using GitHub Actions.

1. Sign in to GitHub in your phone browser and create a **new public or private repository** named `VOIDLINK`.
2. Upload the contents of this project ZIP to the repository (keep the `.github/workflows/android.yml` path intact).
3. Open the repository's **Actions** tab. If prompted, enable workflows.
4. Select **Build VOIDLINK APK** → **Run workflow**.
5. Wait for the run to finish successfully. Open that run and find **Artifacts** → **VOIDLINK-debug-apk**.
6. Download the artifact ZIP, extract it on your phone, and tap `app-debug.apk` to install. If Android asks, allow installation from that browser/file manager.

The first build may take several minutes. GitHub may require account verification and Actions availability. This workflow builds a debug APK; it is suitable for testing and is not a Play Store release signing setup.
