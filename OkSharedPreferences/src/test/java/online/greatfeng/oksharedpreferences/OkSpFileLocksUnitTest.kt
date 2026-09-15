package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files

class OkSpFileLocksUnitTest {

    @Test
    fun nestedLockOnSameThread_doesNotThrowOverlapping() {
        val dir = Files.createTempDirectory("oksp-file-lock").toFile()
        val lockPath = File(dir, ".prefs.lock").absolutePath
        var nestedRan = false
        OkSpFileLocks.withExclusiveLock(lockPath) {
            OkSpFileLocks.withExclusiveLock(lockPath) {
                nestedRan = true
            }
        }
        assertTrue(nestedRan)
        dir.deleteRecursively()
    }

    @Test
    fun sequentialLockOnSameThread_reacquiresAfterRelease() {
        val dir = Files.createTempDirectory("oksp-file-lock").toFile()
        val lockPath = File(dir, ".prefs.lock").absolutePath
        var counter = 0
        OkSpFileLocks.withExclusiveLock(lockPath) { counter++ }
        OkSpFileLocks.withExclusiveLock(lockPath) { counter++ }
        assertEquals(2, counter)
        dir.deleteRecursively()
    }

    @Test(expected = OverlappingFileLockException::class)
    fun rawChannelLock_isNotReentrant() {
        val dir = Files.createTempDirectory("oksp-file-lock").toFile()
        val lockFile = File(dir, ".prefs.lock")
        lockFile.parentFile?.mkdirs()
        lockFile.createNewFile()
        java.io.RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                raf.channel.lock()
            }
        }
    }
}
