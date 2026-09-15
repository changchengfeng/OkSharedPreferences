package online.greatfeng.oksharedpreferences

import java.io.File

/**
 * Tracks on-disk .oksp identity via existence, length, and lastModified.
 * Used to skip redundant read/parse when the file has not changed since last sync.
 */
internal data class DiskSnapshot(
    val lastModified: Long,
    val length: Long,
    val exists: Boolean
) {
    fun matches(file: File): Boolean {
        if (!exists) {
            return !file.exists()
        }
        return file.exists() && file.length() == length && file.lastModified() == lastModified
    }

    companion object {
        fun missing(): DiskSnapshot = DiskSnapshot(0L, 0L, false)

        fun capture(file: File): DiskSnapshot {
            if (!file.exists()) {
                return missing()
            }
            return DiskSnapshot(file.lastModified(), file.length(), true)
        }
    }
}
