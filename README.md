# 🧩 Morphe Patches

Morphe compatible patches for Hevy - Gym Log Workout Tracker (com.hevy) and Castle Busters (com.epicoro.castleclashers).

## ❓ About

Morphe Patches is a Morphe compatible patch bundle for the Hevy workout tracking app (Pro feature unlock) and the Castle Busters game (ads block, aim guide). Patched apps are renamed so they can be installed alongside the originals.

### How to use these patches

1. Install [Morphe Manager](https://morphe.software/).
2. Click [this link](https://morphe.software/add-source?github=RaymondSalim/morphe-patches) to add these patches to Morphe Manager.
3. Get Hevy 3.0.11 as an APK or APKM from [APKMirror](https://www.apkmirror.com/apk/hevy/hevy-gym-log-workout-tracker/).
4. In Morphe Manager, select the patches and patch the app.
5. Install the patched app alongside the original.

### How to patch Castle Busters

1. Install [Morphe Manager](https://morphe.software/).
2. Click [this link](https://morphe.software/add-source?github=RaymondSalim/morphe-patches) to add these patches to Morphe Manager.
3. Get Castle Busters **1.17.2** as an APK or APKM from [APKMirror](https://www.apkmirror.com/apk/voodoo/castle-clashers/castle-busters-1-17-2-release/). APKMirror also hosts newer versions of the game.
4. In Morphe Manager, select the Castle Busters app, keep all three patches selected, and patch the app.
5. Install the patched game alongside the original. With default options it installs as **Castle Bustërs**.

All three patches are selected by default, and the ads block and aim guide patches can be toggled independently in Morphe Manager:

- **Block ads (banner, interstitial & app open)** stops banner, interstitial and app-open ads from loading and displaying. Rewarded ads still play and still grant their in-game rewards, by design.
- **Extend aim guide** shows the full projectile trajectory instead of the short preview; obstacles no longer cut it short.

The patches are version-locked to Castle Busters 1.17.2 and fail loudly on any other version, so a future game update needs new patch sites before it can be patched again.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.3.1-dev.3](https://github.com/RaymondSalim/morphe-patches/releases/tag/v1.3.1-dev.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;5 patches total
<details open>
<summary>📦 Castle Busters&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 1.17.2 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block ads (banner, interstitial & app open)](#block-ads-banner-interstitial-app-open) | Prevents banner, interstitial and app-open ads from loading and displaying. Rewarded ads still work and still grant rewards. |  |
| [Extend aim guide](#extend-aim-guide) | Shows the full projectile trajectory while aiming instead of the short preview. Obstacles no longer cut the guide short. |  |
| [Rename package & app name (Castle Busters)](#rename-package-app-name-castle-busters) | Renames the app package so the patched game can be installed alongside the original Castle Busters, and lets you change the app's launcher name. | • Package name<br>• App name |

</details>

<details open>
<summary>📦 Hevy - Gym Log Workout Tracker&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 3.1.12 | 3.0.11 |
| :---: | :---: |

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

Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE)
