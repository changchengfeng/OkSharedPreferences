package online.greatfeng.oksharedpreferences

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Coordinates exclusive access to a .lock file across threads and instances.
 *
 * Java [FileChannel.lock] is not re-entrant: the same thread must not call [lock]
 * twice on overlapping channels for the same file (throws [java.nio.channels.OverlappingFileLockException]).
 * Writer/reader instances in the same process can share one lock path, so nested or
 * back-to-back acquisitions on one thread must reuse the held [FileLock].
 */
internal object OkSpFileLocks {

    private data class Holder(
        val processLock: ReentrantLock = ReentrantLock(),
        val threadDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 },
        val threadRaf: ThreadLocal<RandomAccessFile?> = ThreadLocal.withInitial { null },
        val threadFileLock: ThreadLocal<FileLock?> = ThreadLocal.withInitial { null }
    )

    private val holders = ConcurrentHashMap<String, Holder>()

    fun <T> withExclusiveLock(lockPath: String, block: () -> T): T {
        val holder = holders.getOrPut(lockPath) { Holder() }
        holder.processLock.lock()
        try {
            val depth = holder.threadDepth.get() ?: 0
            if (depth > 0) {
                holder.threadDepth.set(depth + 1)
                try {
                    return block()
                } finally {
                    holder.threadDepth.set(depth)
                }
            }

            val lockFile = File(lockPath)
            if (!lockFile.exists()) {
                lockFile.parentFile?.mkdirs()
                lockFile.createNewFile()
            }
            val raf = RandomAccessFile(lockFile, "rw")
            val fileLock = raf.channel.lock()
            holder.threadRaf.set(raf)
            holder.threadFileLock.set(fileLock)
            holder.threadDepth.set(1)
            try {
                return block()
            } finally {
                holder.threadDepth.set(0)
                holder.threadFileLock.set(null)
                holder.threadRaf.set(null)
                try {
                    fileLock.release()
                } finally {
                    raf.close()
                }
            }
        } finally {
            holder.processLock.unlock()
        }
    }
}
