# 🔋 Hevy Pro Patches

Morphe compatible patches for Hevy - Gym Log Workout Tracker (com.hevy).

## ❓ About

Hevy Pro Patches is a Morphe compatible patch bundle that unlocks the Pro features of the Hevy workout tracking app and renames the app so the patched version can be installed alongside the original.

### How to use these patches

1. Install [Morphe Manager](https://morphe.software/).
2. Click [this link](https://morphe.software/add-source?github=RaymondSalim/morphe-patches) to add these patches to Morphe Manager.
3. Get Hevy 3.0.11 as an APK or APKM from [APKMirror](https://www.apkmirror.com/apk/hevy/hevy-gym-log-workout-tracker/).
4. In Morphe Manager, select the patches and patch the app.
5. Install the patched app alongside the original.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0](https://github.com/RaymondSalim/morphe-patches/releases/tag/v1.1.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Hevy - Gym Log Workout Tracker&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 3.0.11 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Rename package & app name](#rename-package-app-name) | Renames the app package so the patched app can be installed alongside the original Hevy app, and lets you change the app's launcher name. | • Package name<br>• App name |
| [Unlock Pro Features](#unlock-pro-features) | Enable pro subscription features |  |

</details>

<!-- PATCHES_END -->

## 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Apply the mpp file using [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) like any other patch bundle.

## 🧑‍💻 Development

- Development happens on the `dev` branch. `feat:` and `fix:` commits on `dev` create prereleases. Merging `dev` into `main` (no squash) creates stable releases. Everything is released automatically by `release.yml`.
- Never manually edit generated files (`patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, the README patch list). They are updated by `release.yml`.

## 📜 License

Hevy Pro Patches are licensed under the [GNU General Public License v3.0](LICENSE)
