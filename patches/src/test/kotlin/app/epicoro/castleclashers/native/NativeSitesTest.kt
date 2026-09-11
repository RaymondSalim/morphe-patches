package app.epicoro.castleclashers.native

import app.epicoro.castleclashers.patches.native.Arm64Patcher
import app.epicoro.castleclashers.patches.native.adsSites
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume

class NativeSitesTest {

    @Test
    fun everyAdsSiteMatchesExactlyOnceInRealBinary() {
        val soPath = System.getenv("CC_TEST_IL2CPP")
        Assume.assumeTrue("CC_TEST_IL2CPP not set", soPath != null)
        val data = File(soPath).readBytes()
        assertTrue(adsSites.isNotEmpty(), "No ads sites defined yet")
        for (site in adsSites) {
            val matches = Arm64Patcher.findMatches(data, site.signature)
            assertEquals(1, matches.size, "Site ${site.name} signature match count")
            val start = matches[0] + site.patchOffset
            val actual = data.copyOfRange(start, start + site.expectedBytes.size)
            assertTrue(
                actual.contentEquals(site.expectedBytes),
                "Site ${site.name} expected bytes mismatch at $start",
            )
        }
    }
}
