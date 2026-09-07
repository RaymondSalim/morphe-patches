package app.hevy.patches.hermespaywall.hermes

import org.junit.Assume
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HermesBundleTest {

    private fun originalBundle(): ByteArray? =
        System.getenv("HEVY_TEST_ORIGINAL_BUNDLE")?.let { File(it).readBytes() }

    @Test
    fun parsesHermesV96Bundle() {
        val data = originalBundle() ?: return Assume.assumeTrue("HEVY_TEST_ORIGINAL_BUNDLE not set", false)
        val bundle = HermesBundle.parse(data)
        assertEquals(96, bundle.version)
        assertEquals(63609, bundle.functions.size)
        assertEquals("isProStatusOverrideEnabled", bundle.string(62946))
        assertEquals(62946, bundle.stringId("isProStatusOverrideEnabled"))
    }

    @Test
    fun locatesPaywallFunctionsByStringSignatures() {
        val data = originalBundle() ?: return Assume.assumeTrue("HEVY_TEST_ORIGINAL_BUNDLE not set", false)
        val bundle = HermesBundle.parse(data)

        val proStatusGetter = bundle.findFunctions(setOf("getProStatusOverride", "force-pro", "force-free"))
        assertEquals(1, proStatusGetter.size)
        assertEquals(0x1063393, proStatusGetter[0].offset)
        assertEquals(127, proStatusGetter[0].size)

        val graceGetter = bundle.findFunctions(setOf("lastSuccessfulFetch", "lastFetchAt", "expiresAt"))
        assertEquals(1, graceGetter.size)
        assertEquals(0x106357B, graceGetter[0].offset)
        assertEquals(150, graceGetter[0].size)

        val resetCandidates = bundle.findFunctions(
            setOf("HEVY_PRO_DISK_STORAGE_KEY", "HEVY_PRO_LAST_SUCCESSFUL_FETCH")
        )
        assertTrue(resetCandidates.isNotEmpty())
    }
}
