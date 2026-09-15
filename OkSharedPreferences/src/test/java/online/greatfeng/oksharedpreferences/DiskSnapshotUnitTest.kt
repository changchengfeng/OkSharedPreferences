package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiskSnapshotUnitTest {

    @Test
    fun missing_matchesAbsentFile() {
        val dir = Files.createTempDirectory("oksp-snapshot").toFile()
        val file = File(dir, "prefs.oksp")
        assertTrue(DiskSnapshot.missing().matches(file))
        dir.deleteRecursively()
    }

    @Test
    fun capture_matchesSameFileIdentity() {
        val dir = Files.createTempDirectory("oksp-snapshot").toFile()
        val file = File(dir, "prefs.oksp")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val snapshot = DiskSnapshot.capture(file)
        assertTrue(snapshot.matches(file))
        dir.deleteRecursively()
    }

    @Test
    fun capture_doesNotMatchAfterRewrite() {
        val dir = Files.createTempDirectory("oksp-snapshot").toFile()
        val file = File(dir, "prefs.oksp")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val snapshot = DiskSnapshot.capture(file)
        file.writeBytes(byteArrayOf(4, 5))
        assertFalse(snapshot.matches(file))
        dir.deleteRecursively()
    }

    @Test
    fun capture_doesNotMatchSameLengthRewriteWithSameLastModified() {
        val dir = Files.createTempDirectory("oksp-snapshot").toFile()
        val file = File(dir, "prefs.oksp")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val snapshot = DiskSnapshot.capture(file)
        file.writeBytes(byteArrayOf(9, 2, 3, 4))
        file.setLastModified(snapshot.lastModified)
        assertFalse(snapshot.matches(file))
        dir.deleteRecursively()
    }
}
