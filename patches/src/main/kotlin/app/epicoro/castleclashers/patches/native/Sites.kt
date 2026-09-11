package app.epicoro.castleclashers.patches.native

// Reverse-engineered patch sites for Castle Clashers 1.17.2 (libil2cpp.so arm64).
// Each site description states: target function, why this site, and the
// behavioral contract after patching (what the caller does with the result).
// Rows were verified against the 1.17.2 binary; the unit tests re-verify
// signature uniqueness and expected bytes on every run.
//
// Patches are placed at wrapper level (Voodoo.Sauce.Internal.Ads.Interstitial
// and ...Ads.Banner), which covers both real mediation adapters compiled into
// the binary (AdnAdsAdapter, MaxMediationAdapter) and FakeMediationAdapter.
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
)

val codeHashSites = listOf<NativeSite>() // ACTk Genuine CodeHash never starts; evidence in the header comment.
