package online.greatfeng.oksharedpreferences

import java.io.File
import java.util.zip.CRC32

/**
 * Tracks on-disk .oksp identity via existence, length, lastModified, and CRC32.
 * Length + mtime alone can collide under rapid cross-process writes; CRC prevents
 * skipping a reload when content changed but metadata stayed the same.
 */
internal data class DiskSnapshot(
    val lastModified: Long,
    val length: Long,
    val exists: Boolean,
    val crc32: Long = 0L
) {
    fun matches(file: File): Boolean {
        if (!exists) {
            return !file.exists()
        }
        if (!file.exists() || file.length() != length || file.lastModified() != lastModified) {
            return false
        }
        return crc32Of(file) == crc32
    }

    companion object {
        fun missing(): DiskSnapshot = DiskSnapshot(0L, 0L, false, 0L)

        fun capture(file: File): DiskSnapshot {
            if (!file.exists()) {
                return missing()
            }
            return DiskSnapshot(file.lastModified(), file.length(), true, crc32Of(file))
        }

        private fun crc32Of(file: File): Long {
            val crc = CRC32()
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    crc.update(buffer, 0, read)
                }
            }
            return crc.value
        }
    }
}
