package app.epicoro.castleclashers.patches.native

import java.io.File

object Arm64Patcher {

    /**
     * Applies every site to [file] in one read-modify-write pass. Each site
     * must match its signature exactly once, and the expected original
     * bytes must be present at the patch offset, or the whole call throws
     * and the file is left unpatched (throwing happens before any write).
     */
    fun applySites(file: File, sites: List<NativeSite>): List<String> {
        val data = file.readBytes()
        val logs = sites.map { applySite(data, it) }
        file.writeBytes(data)
        return logs
    }

    private fun applySite(data: ByteArray, site: NativeSite): String {
        check(site.expectedBytes.size == site.replacementBytes.size) {
            "Site ${site.name}: expected/replacement length mismatch"
        }
        val matches = findMatches(data, site.signature)
        check(matches.size == 1) {
            "Site ${site.name}: expected exactly 1 signature match, found ${matches.size}"
        }
        val start = matches[0] + site.patchOffset
        val end = start + site.expectedBytes.size
        check(end <= data.size) {
            "Site ${site.name}: patch window $start..$end exceeds file size ${data.size}"
        }
        val actual = data.copyOfRange(start, end)
        check(actual.contentEquals(site.expectedBytes)) {
            "Site ${site.name}: expected ${site.expectedBytes.toHex()} at $start, found ${actual.toHex()}"
        }
        site.replacementBytes.copyInto(data, start)
        return "${site.name}: patched ${site.expectedBytes.size} bytes at file offset $start"
    }

    fun findMatches(data: ByteArray, needle: ByteArray): List<Int> {
        val result = ArrayList<Int>()
        var i = 0
        while (i <= data.size - needle.size) {
            if (matchesAt(data, i, needle)) result.add(i)
            i += 1
        }
        return result
    }

    private fun matchesAt(data: ByteArray, offset: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) {
            if (data[offset + j] != needle[j]) return false
        }
        return true
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
}
