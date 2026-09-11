# Castle Clashers Patches — Design

Date: 2026-09-12
Repo: `RaymondSalim/morphe-patches` (bundle currently branded "Hevy Pro Patches", `dev` branch)
New target app: Castle Busters (EpiCoro/Voodoo), package `com.epicoro.castleclashers`, version **1.17.2** (version code 696)

## Goal

Add Castle Clashers as a second app in the existing Morphe patch bundle, with three selectable patches:

1. **Package rename** — installs the patched game alongside the original (same mechanism as the Hevy patch).
2. **Ads block** — banners and interstitials never display; **rewarded ads keep working** and still grant their in-game rewards.
3. **Aim guide** — the trajectory preview shows the full projectile path; obstacles (enemy bases, walls, units) do not terminate the drawn path; **ground contact is the only terminating condition**; player side only (enemy prediction code untouched).

The aim-guide and ads patches are the repo's first native-code patches: game logic lives in `libil2cpp.so` (Unity IL2CPP, arm64), not in dex or Hermes bytecode.

## Ground truth (verified on 1.17.2 / 696)

App facts (from the staged split APKs in `apk/com.epicoro.castleclasher/`, byte-identical to a universal APK of the same build):

- Split AAB: `base.apk` (170 MB) + `split_config.arm64_v8a.apk` (native libs) + `split_UnityDataAssetPack.apk` (284 MB Unity data). arm64-v8a is the only native ABI staged; the patch treats arm64-only as an invariant (see Ads patch).
- Unity IL2CPP: `libil2cpp.so` 160 MB, `global-metadata.dat` 26 MB in base `assets/bin/Data/Managed/Metadata/`, **unencrypted** (magic `0xFAB11BAF`, metadata version 31).
- Launcher activity `com.google.firebase.MessagingUnityPlayerActivity`, label `@string/app_name` = "Castle Busters", minSdk 26, targetSdk 35.
- No Voodoo/EpiCoro Java ad bridge exists in dex (`com/epicoro/*` are generated R classes only), so there is no single Java choke point for ads; 7+ ad networks (Voodoo.Sauce, AppLovin MAX, AdMob, Unity Ads, Audience Network, Pangle, Moloco, Yandex) are wired from C#.
- Ads and aim logic are IL2CPP-side. Metadata strings confirm: `Voodoo.Sauce.Internal.Ads`, `VoodooPremium` (paid no-ads product, must stay untouched), `EpiCoro.Internal.IAP` with `RemoveAdsIAPUIItem`/`NoAdsPurchaseDelegate`; aim code in `Assets\Scripts\Controllers\TrajectoryController.cs` (`TrajectoryController`, `_backgroundTrajectoryRenderer`) and `ShotController.PopulatePlayersPredictionTrajectories` (distinct from `PopulateEnemyPredictionTrajectories`). An `AimConfig` exists in `CastleClashers.VoodooTune` (remote config), so the patch must target consumers, not config values.
- IAP flow: Unity IAP on Play Billing 8.0.0, validated via `Voodoo.Sauce.IAP|PurchaseValidator` and uploaded to Nakama (`CCFoundationPurchasePayload`). Entitlements are server-backed; no patch touches them.
- Anti-cheat: full CodeStage Anti-Cheat Toolkit (ACTk) is compiled in. Irrelevant layers: Obscured types + ObscuredCheatingDetector (value tamper), ObscuredPrefs/ObscuredFilePrefs (encrypted saves), InjectionDetector, SpeedHackDetector, TimeCheatingDetector, WallHackDetector. **Relevant layer: `CodeStage.AntiCheat.Genuine.CodeHash`** (`CodeHashGenerator`, `AndroidWorker`, `BuildHashes`) — runtime hashing of code files (on Android, native libs including `libil2cpp.so`) compared against build-time-embedded expected hashes. A patched `.so` is exactly what it detects. Whether it is enabled/started/enforced is an RE-phase determination; if active, a conditional patch site makes the integrity check report the expected hash unconditionally. `AppInstallationSourceValidator` sees the sideload; assumed analytics-only unless RE shows enforcement.

Patcher API (morphe-patcher 1.8.0, verified via jar inspection):

- `resourcePatch` with `document(...)` — manifest/XML edits (Hevy precedent).
- `rawResourcePatch` with `get(path)` returning a `File` — raw byte patches (Hevy precedent on `assets/index.android.bundle`). **`lib/arm64-v8a/libil2cpp.so` addressability is assumed but unproven; first task is a spike.**
- `bytecodePatch` via dexlib2 mutable classes; `preserveAppCode` keeps dex passthrough. No bytecode changes are planned. All three new patches `dependsOn(preserveAppCode)`: the comment in `PreserveAppCode.kt` documents that the patcher compiles dex output only when a bytecode patch is in the selected patch set, so each selectable combination must pull it in.

## Architecture

### Bundle restructure (two apps, one bundle)

```
patches/src/main/kotlin/app/hevy/...                    (unchanged)
patches/src/main/kotlin/app/epicoro/castleclashers/
    patches/Patches.kt                                   allPatches for this app
    patches/shared/Constants.kt                          Compatibility entries for this app
    patches/packagerename/PackageRenamePatch.kt          port of Hevy's rename patch
    patches/adblock/AdsBlockPatch.kt                     native ads patch
    patches/aimguide/AimGuidePatch.kt                    native aim guide patch
    patches/native/Arm64Patcher.kt                       shared .so signature/patch helpers
```

- Top-level patch list merges both apps' patches; `util/PatchListGenerator.kt` needs no changes (it maps patches to apps via each patch's `compatibleWith` Compatibility entries).
- Bundle `about` name (build.gradle.kts `patches { about { ... } }`), `settings.gradle.kts` `rootProject.name`, and README title rebranded from "Hevy Pro Patches" to "Morphe Patches". Hevy patch behavior untouched. Artifact-name impact of the rename (`.releaserc`/`release.yml` reference `patches/build/libs/patches-*.mpp`, pattern-based) is confirmed green via the local build before pushing.
- Compatibility for the new app: two entries (APKM preferred, APK), name "Castle Busters", package `com.epicoro.castleclashers`, target version `1.17.2` only, `appIconColor` extracted from the launcher icon during implementation.
- Input format: Morphe expects APK or APKM. The user provides a clean single APK/APKM for 1.17.2; for local headless verification the same input is used. The staged splits remain in `apk/` (gitignored) as RE input.
- `.gitignore`: `apk/` (staging + RE artifacts) is ignored; 160 MB binaries never enter git.

### Shared native plumbing (`native/Arm64Patcher.kt`)

- `rawResourcePatch` reaches `get("lib/arm64-v8a/libil2cpp.so")`.
- Each patch site is located by a **unique byte-pattern signature** scanned across the whole `.so`, never a bare file offset. Exactly-one-match assertion; expected original bytes asserted before writing; fail-loud on 0 or >1 matches (Hevy Hermes precedent). Per-site log line describing what was patched.
- `execute()` throws if any `lib/*/libil2cpp.so` other than `arm64-v8a` is present in the input (no half-patched multi-ABI output).
- Spike (must pass before any RE continues): throwaway raw patch doing `get("lib/arm64-v8a/libil2cpp.so")`, writing 4 bytes, running the patcher headlessly, unzipping the output and confirming the modified `.so` is in the APK. If the resource layer cannot address `lib/` paths, read the morhe-patcher source (open source, version-pinned 1.8.0) and adapt the approach before proceeding.

### Patch: PackageRename (`PackageRenamePatch.kt`)

Direct port of `app.hevy.patches.packagerename.PackageRenamePatch` with `ORIGINAL_PACKAGE = "com.epicoro.castleclashers"`:

- Manifest `package` attribute → option `package-name` (default `com.epicoro.castleclashers.mod`, required, same regex validator, must differ from original).
- Provider `android:authorities` values starting with the original prefix are re-prefixed (startup-provider, adjoe, appmetrica, fileprovider, billing-related, all follow the prefix).
- Custom permission `com.epicoro.castleclashers.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` renamed in both its `<permission>` declaration and `<uses-permission>` reference; package-prefixed `uses-permission` entries re-prefixed the same way.
- Launcher label: `res/values/strings.xml` `app_name` string (exactly one expected, assert) → option `app-name` (required, non-blank; default `Castle Bustërs`, accent-char style matching the Hevy patch's default).
- Third-party permissions, class names, `extractNativeLibs`, Play Asset Pack metadata untouched. Resource-side remapping is the patcher's built-in `PackageRenamingProcessor`.
- `dependsOn(preserveAppCode)` like Hevy's.

### Patch: AdsBlock (`AdsBlockPatch.kt`)

- RE target: interstitial and banner entry points in `Voodoo.Sauce.Internal.Ads` (the show entry and the load-ready gate Voodoo.Sauce polls before choosing a format). Dump via Il2CppDumper, disassemble around RVAs in Ghidra.
- Patch semantics per site: early return with the value the surrounding code treats as "no ad available, continue flow" — plain `RET` for void methods, `MOV W0,#0` (or `#1`, per site) + `RET` for bool gates. Between-level flow must advance normally (device-test assertion).
- **Non-targets**: every `Rewarded*` path stays byte-identical; paid `VoodooPremium` flows untouched; dex-side ad SDK init untouched. A too-wide patch that breaks rewarded earnings is the primary failure mode; the device test asserts rewards still grant.
- No options; independently selectable in Morphe Manager, `default = true`.

### Patch: AimGuide (`AimGuidePatch.kt`)

- RE target: `TrajectoryController` and `ShotController.PopulatePlayersPredictionTrajectories`. Expected shape: a step-simulated trajectory loop terminating on max-steps, max-distance, or first collision with any layer. Exact ARM64 edits are an RE deliverable; candidates are (a) relaxing the termination predicate to ground-only (skip castle/unit/obstacle layer masks), (b) NOPping the step/distance cap when it binds first.
- Requirement recap: the drawn path continues past enemy bases and walls; only ground contact ends it; player-side path only (`PopulateEnemyPredictionTrajectories` untouched).
- Consumer-side patch: VoodooTune remote config (`AimConfig`) cannot shrink the guide back; config may still change colors.
- No options; independently selectable, `default = true`.

### Conditional patch: CodeHash enforcement (only if RE confirms active)

- If ACTk Genuine CodeHash validation runs at startup and enforcement affects the user (report-to-server/ban/lockout), add a site that makes the integrity check report the embedded expected hash unconditionally (patch the compare/report path, not the hashing loop). If it does not run, nothing to do. Device test watches startup for tamper states either way.

### RE workflow and tooling (approved installs)

- `brew install dotnet` + Il2CppDumper GitHub release → `dump.cs` / `script.json` from `global-metadata.dat` + `libil2cpp.so` (arm64).
- `brew install --cask ghidra` + `openjdk@21` (jenv) for ARM64 disassembly.
- RE deliverable: a **site table** — for each site: target function, semantics, unique context signature (byte pattern), expected original bytes, replacement bytes, and the behavioral contract (what the patched code does). This table feeds both the patch code and the unit tests. RE is gated: no patch code is written for a site until its row exists with byte-exact expectations.

## Verification

1. **Spike** (gate for everything else): `get("lib/arm64-v8a/libil2cpp.so")` round-trips a 4-byte write through the patcher into the output APK. Not passed → stop and re-approach via patcher source.
2. **Unit (committed, Hevy pattern)**: per-site tests reading the original `.so` path from env var (e.g. `CC_TEST_IL2CPP`), skipping when unset: assert signature matches exactly once, original bytes match, patched bytes equal the site-table expectation.
3. **Headless E2E (local)**: Morphe patcher run with all three patches against the clean APK; unzip output and diff: `.so` sites byte-equal to expectations, manifest rename correct (package/authorities/permission/label), dex passthrough intact (compare classes*.dex checksums), two same-file patches each see the other's edits (sequential patch interplay verified here; fallback if broken: single combined native patch).
4. **Device test (user)**: install patched APK: boots to gameplay, no crash; banners/interstitials gone; a rewarded ad still grants its reward; aim guide runs to ground contact; premium/IAP UI loads; several sessions for stability; startup shows no tamper/lockout state.
5. **Repo checks**: `./gradlew buildAndroid` (or the repo's build task) green; `generatePatchesList` output contains both apps with the right patches.

## Non-goals

- No premium/purchase unlock, no currency changes (server-validated; out of scope).
- No fake rewarded rewards, no rewarded changes of any kind.
- No enemy-prediction changes.
- No support for versions other than 1.17.2 (patches fail loudly on other builds).
- No 32-bit ABI support; arm64 only.
- No Hevy patch behavior changes.

## Risks

- **ACTk Genuine CodeHash** may flag the patched `.so` (detection event → server). Mitigation: RE determination + conditional neutralization site; device test watches startup. Residual risk if enforcement is server-side per-event: watch for account flags over test sessions.
- **Site signatures are version-specific**: 1.17.2 only; other versions fail loudly at patch time (correct).
- **`get("lib/...")` unproven**: spike gates everything; fallback is patcher-source adaptation.
- **Same-file patch interplay** (AdsBlock + AimGuide both edit `libil2cpp.so`): verified in headless E2E; fallback combined patch.
- **Rewarded distinction**: site selection must be exact; device test asserts reward grant.
- **VoodooTune server config** may alter other aim/ads behavior at runtime; we patch consumers, so the two patched behaviors stay, but unrelated config changes can shift surrounding behavior between game updates.
- **160 MB `.so` patching cost**: patcher memory/time — Hevy's APK was comparable scale; measured in the spike.
