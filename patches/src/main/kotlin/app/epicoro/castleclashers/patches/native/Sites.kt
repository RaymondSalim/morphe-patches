package app.epicoro.castleclashers.patches.native

// Reverse-engineered patch sites for Castle Clashers 1.17.2 (libil2cpp.so arm64).
// Each site description states: target function, why this site, and the
// behavioral contract after patching (what the caller does with the result).
// Rows were verified against the 1.17.2 binary; the unit tests re-verify
// signature uniqueness and expected bytes on every run.
//
// Patches are placed at wrapper level (Voodoo.Sauce.Internal.Ads.Interstitial,
// ...Ads.Banner and ...Ads.AppOpen), which covers both real mediation adapters
// compiled into the binary (AdnAdsAdapter, MaxMediationAdapter) and
// FakeMediationAdapter.
// Rewarded formats are untouched: RewardedVideo.Show dispatches to
// RewardedInterstitialVideo.Show, a separate wrapper chain that does not
// pass through any of the patched gates; the interstitial/banner/app-open
// gates never intercept the rewarded path, so it stays byte-identical by
// design.
//
// CodeHash determination (ACTk Genuine, CodeStage.AntiCheat.Genuine.CodeHash):
// present but never started, therefore not enforced. Evidence:
// - CodeHashGenerator.AddToSceneOrGetExisting (VA 0x40AA7BC) has zero direct
//   callers in the whole binary (exhaustive B/BL scan, apk/re/scripts/scan_bl.py).
// - Static entry points Generate (VA 0x40AA7FC) and GenerateAsync
//   (VA 0x40AA9EC) are called only by their own ICodeHashGenerator interface
//   stubs (VA 0x40AAB74 / 0x40AAB7C), and no class outside CodeHashGenerator
//   references the ICodeHashGenerator type (dump.cs type scan), so the
//   interface slots are unreachable.
// - add_HashGenerated (VA 0x40AA634, the result-delivery event) has zero
//   subscribers, so even a generated hash would have no consumer.
// - IsTargetPlatformCompatible (VA 0x40AA7B4) has zero callers.
// - No VoodooTune/config key drives it: metadata strings contain no
//   codehash/integrity activation keys for ACTk. The strings castle_integrity,
//   current_integrity, integrity_loss and com.castleclashers.integrity.
//   PlayIntegrityBridge belong to the game's separate Google Play Integrity
//   flow (Voodoo.Nakama.IntegrityTokenProvider), which is server-side and not
//   part of ACTk Genuine CodeHash.
// => codeHashSites stays empty; no detection compare/report path is reachable.

val adsSites = listOf(
    NativeSite(
        name = "ads.interstitial.getShowStatus",
        description = "Voodoo.Sauce.Internal.Ads.Interstitial.GetShowStatus(bool): " +
            "the decision gate every interstitial show path consults. Patched to always " +
            "return Interstitial.InternalAdState.CAN_NOT_SHOW (0). Contract: Interstitial.Show " +
            "(VA 0x419AE8C) calls GetShowStatus first (bl at VA 0x419AF6C) and, for any status " +
            "other than CAN_SHOW, invokes the onComplete(false) callback (delegate call at " +
            "VA 0x429AFDC..0x429AFF0) and returns, so AdsManager.ShowInterstitial " +
            "(VA 0x4185DB4, tail-calls Show for both Interstitial and the inheriting " +
            "SecondInterstitial) completes with shown=false and game flow advances. " +
            "AdsManager.InterstitialCanBeShown (VA 0x4186124) also reports false through " +
            "ReadyToShow -> GetShowStatus. Caller evidence (exhaustive direct-call scan): " +
            "ShouldShowSecondInterstitial (VA 0x4185EFC), Interstitial.ReadyToShow (VA 0x419AD3C), " +
            "Interstitial.Show (VA 0x419AF6C) only. The rewarded path never reaches it: " +
            "RewardedVideo.Show (VA 0x41A13B8) and the rewarded-replacement wrapper " +
            "RewardedInterstitialVideo.Show (VA 0x419F4C8) are separate functions that do not " +
            "call this gate, so rewarded flows stay byte-identical.",
        signature = hex(
            "FE 57 BE A9 F4 4F 01 A9 35 BB 02 D0 F4 03 01 2A " +
                "F3 03 00 AA A8 AE 69 39 48 02 00 37 80 8B 02 D0 00 B0 44 F9",
        ),
        patchOffset = 0,
        expectedBytes = hex("FE 57 BE A9 F4 4F 01 A9"),
        replacementBytes = hex("00 00 80 52 C0 03 5F D6"),
    ),
    NativeSite(
        name = "ads.interstitial.isLoaded",
        description = "Voodoo.Sauce.Internal.Ads.Interstitial.IsLoaded(bool): availability " +
            "report comparing BaseAd.State to AdLoadingState.Loaded (cmp #3 at VA 0x429AD28). " +
            "Patched to always return false, so interstitial availability polls report " +
            "not-loaded. Contract: callers AdsManager.IsInterstitialLoaded (VA 0x418606C, " +
            "public availability API forwarded to game code) and AdsManager." +
            "ShouldShowSecondInterstitial (VA 0x4185F2C) take their natural no-ad paths; " +
            "direct-call scan shows no other callers and none in the rewarded flow, so " +
            "RewardedVideo availability is unaffected.",
        signature = hex(
            "08 20 40 B9 1F 0D 00 71 E0 17 9F 1A C0 03 5F D6 " +
                "FE 0F 1F F8 E1 03 1F 2A 05 00 00 94 1F 04 00 71 E0 17 9F 1A",
        ),
        patchOffset = 0,
        expectedBytes = hex("08 20 40 B9 1F 0D 00 71"),
        replacementBytes = hex("00 00 80 52 C0 03 5F D6"),
    ),
    NativeSite(
        name = "ads.banner.canShow",
        description = "Voodoo.Sauce.Internal.Ads.Banner.CanShow(): banner display gate. " +
            "Patched to always return Banner.InternalAdState.CAN_NOT_SHOW (1), the same value " +
            "the unpatched function returns when paid hide-ads is active. Contract: " +
            "Banner.Show (VA 0x419951C) calls CanShow before touching the adapter (bl at " +
            "VA 0x42995DC) and, on CAN_NOT_SHOW, returns immediately without invoking the " +
            "onBannerDisplayed callback (early epilogue at VA 0x42995EC..0x4299600), so " +
            "VoodooSauce.ShowBanner (VA 0x40F2F28, tail-calls Show) becomes a no-op with no " +
            "hang risk. Direct-call scan: CanShow is called only from Banner.Show, which has " +
            "no rewarded-flow callers (RewardedVideo/RewardedInterstitialVideo have their own " +
            "wrappers), so banner gating cannot affect rewarded paths.",
        signature = hex(
            "FE 0F 1E F8 F4 4F 01 A9 34 BB 02 F0 F3 03 00 AA " +
                "88 5E 69 39 A8 02 00 37 80 8B 02 D0 00 94 47 F9",
        ),
        patchOffset = 0,
        expectedBytes = hex("FE 0F 1E F8 F4 4F 01 A9"),
        replacementBytes = hex("20 00 80 52 C0 03 5F D6"),
    ),
    NativeSite(
        name = "ads.banner.isAvailable",
        description = "Voodoo.Sauce.Internal.Ads.Banner.IsAvailable(): banner availability " +
            "report comparing BaseAd.State to AdLoadingState.Disabled (cmp #5 at VA 0x42991B4). " +
            "Patched to always return false so game-side banner availability polls report " +
            "unavailable. Contract: its only caller, VoodooSauce.IsBannerAvailable " +
            "(VA 0x40F33D0), tail-branches straight into this function and forwards the result " +
            "to game code; with false the game treats the banner surface as unavailable. " +
            "No rewarded-flow caller exists (direct-call scan).",
        signature = hex(
            "08 20 40 B9 1F 15 00 71 E0 07 9F 1A C0 03 5F D6 " +
                "FE 0F 1E F8 F4 4F 01 A9 34 BB 02 F0 F3 03 00 AA 88 56 69 39 C8 00 00 37",
        ),
        patchOffset = 0,
        expectedBytes = hex("08 20 40 B9 1F 15 00 71"),
        replacementBytes = hex("00 00 80 52 C0 03 5F D6"),
    ),
    NativeSite(
        name = "ads.appOpen.canShow",
        description = "Voodoo.Sauce.Internal.Ads.AppOpen.CanShow(bool): app-open display " +
            "gate. Patched to always return AppOpen.InternalAdState.CAN_NOT_SHOW (0), the same " +
            "value the unpatched function returns on its paid hide-ads and " +
            "conditions-not-met paths. Contract: AppOpen.Show (VA 0x4197D14) calls CanShow " +
            "first (bl at VA 0x4297DD0) and, for any status other than CAN_SHOW(1), skips the " +
            "adapter show path, invokes the onComplete Action when one was supplied (call at " +
            "VA 0x42981E4, skipped for null at VA 0x42981B8) and returns, so the resume-time " +
            "call from AdsManager.OnApplicationPause (VA 0x4185C74, tail-call " +
            "Show(appOpen, onComplete=null, ignoreConditions=false) at VA 0x4185DAC) becomes a " +
            "no-op in a fire-and-forget lifecycle callback, and the Voodoo debug screen's " +
            "ShowAppOpenAdInfo lambdas (VA 0x425B504) complete normally. Direct-call scan: " +
            "CanShow is called only from AppOpen.Show; no rewarded-flow caller exists " +
            "(RewardedVideo.Show has its own gate, VA 0x41A1120).",
        signature = hex(
            "FE 57 BE A9 F4 4F 01 A9 55 BB 02 B0 F4 03 01 2A " +
                "F3 03 00 AA A8 22 69 39 A8 02 00 37 A0 8B 02 B0 00 10 44 F9",
        ),
        patchOffset = 0,
        expectedBytes = hex("FE 57 BE A9 F4 4F 01 A9"),
        replacementBytes = hex("00 00 80 52 C0 03 5F D6"),
    ),
)

val codeHashSites = listOf<NativeSite>() // ACTk Genuine CodeHash never starts; evidence in the header comment.

val aimGuideSites = listOf(
    NativeSite(
        name = "aim.updateTrajectory.arcCap",
        description = "ProjectileController.UpdateTrajectory(float) (VA 0x44F765C): the aim guide " +
            "draw loop clamps and terminates once the accumulated polyline arc exceeds the baked-in " +
            "constant 4.5f world units (fmov s1,#4.5 at VA 0x44F7B88, fcmp at VA 0x44F7B90, b.gt to " +
            "the clamp/exit block at VA 0x44F7F68). NOPing the branch removes the cap for every " +
            "power level; the loop then runs to its only remaining termination, the marker-count " +
            "bound (i < points.Length at VA 0x44F7B10), drawing the full simulated projectile " +
            "path through all markers. Contract: the player aim guide (markers at points[], " +
            "up/down edge SpriteShapeControllers at 0x1B8/0x1C0 and the background band at 0x1C8) " +
            "always renders to the end of the simulated trajectory; obstacles cannot shorten it " +
            "because the guide contains no collision logic. Direct-call scan: UpdateTrajectory is " +
            "called only from ProjectileController.Update (bl at VA 0x44F7088) and " +
            "ProjectileController.SetAimPreview (bl at VA 0x44FA608), both player aiming UI; the " +
            "shared trajectory math (GetProjectileForce/GetTrajectorySegments/SimulatePath, used by " +
            "PopulateEnemyPredictionTrajectories) is untouched.",
        signature = hex(
            "47 08 21 1E 01 50 22 1E 08 29 20 1E 00 21 21 1E " +
                "AC 1E 00 54 68 2A 40 F9",
        ),
        patchOffset = 16,
        expectedBytes = hex("AC 1E 00 54"),
        replacementBytes = hex("1F 20 03 D5"),
    ),
)
