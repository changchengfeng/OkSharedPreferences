package online.greatfeng.library.fileobserver


import android.util.Log
import android.util.SparseArray
import androidx.annotation.IntDef
import java.io.File
import java.lang.ref.WeakReference
import java.util.Arrays


abstract class OkFileObserver(val mFiles: List<File>, val mMask: Int) {

    @IntDef(
        flag = true,
        value = [ACCESS, MODIFY, ATTRIB, CLOSE_WRITE, CLOSE_NOWRITE, OPEN, MOVED_FROM, MOVED_TO, CREATE, DELETE, DELETE_SELF, MOVE_SELF]
    )
    @Retention(
        AnnotationRetention.SOURCE
    )
    annotation class NotifyEventType


    private var mDescriptors: IntArray? = null


    protected fun finalize() {
        stopWatching()
    }


    fun startWatching() {
        if (mDescriptors == null) {
            mDescriptors =
                s_observerThread.startWatching(mFiles, mMask, this)
        }
    }


    fun stopWatching() {
        if (mDescriptors != null) {
            s_observerThread.stopWatching(mDescriptors)
            mDescriptors = null
        }
    }


    abstract fun onEvent(event: Int, path: String?)

    companion object {
        /** Event type: Data was read from a file  */
        const val ACCESS = 0x00000001

        /** Event type: Data was written to a file  */
        const val MODIFY = 0x00000002

        /** Event type: Metadata (permissions, owner, timestamp) was changed explicitly  */
        const val ATTRIB = 0x00000004

        /** Event type: Someone had a file or directory open for writing, and closed it  */
        const val CLOSE_WRITE = 0x00000008

        /** Event type: Someone had a file or directory open read-only, and closed it  */
        const val CLOSE_NOWRITE = 0x00000010

        /** Event type: A file or directory was opened  */
        const val OPEN = 0x00000020

        /** Event type: A file or subdirectory was moved from the monitored directory  */
        const val MOVED_FROM = 0x00000040

        /** Event type: A file or subdirectory was moved to the monitored directory  */
        const val MOVED_TO = 0x00000080

        /** Event type: A new file or subdirectory was created under the monitored directory  */
        const val CREATE = 0x00000100

        /** Event type: A file was deleted from the monitored directory  */
        const val DELETE = 0x00000200

        /** Event type: The monitored file or directory was deleted; monitoring effectively stops  */
        const val DELETE_SELF = 0x00000400

        /** Event type: The monitored file or directory was moved; monitoring continues  */
        const val MOVE_SELF = 0x00000800


        private const val LOG_TAG = "FileObserver"

        private val s_observerThread = ObserverThread()

        init {
            s_observerThread.start()
        }
    }


    private class ObserverThread : Thread("FileObserver") {
        private val m_observers = HashMap<Int, WeakReference<*>>()
        private val mRealObservers = SparseArray<WeakReference<*>>()
        private val m_fd: Int

        init {
            m_fd = init()
        }

        override fun run() {
            observe(m_fd)
        }

        fun startWatching(
            files: List<File>,
            @NotifyEventType mask: Int, observer: OkFileObserver
        ): IntArray {
            val count = files.size
            val paths = arrayOfNulls<String>(count)
            for (i in 0 until count) {
                paths[i] = files[i].absolutePath
            }
            val wfds = IntArray(count)
            Arrays.fill(wfds, -1)
            startWatching(m_fd, paths, mask, wfds)
            val fileObserverWeakReference = WeakReference(observer)
            synchronized(mRealObservers) {
                for (wfd in wfds) {
                    if (wfd >= 0) {
                        mRealObservers.put(wfd, fileObserverWeakReference)
                    }
                }
            }
            return wfds
        }

        fun stopWatching(descriptors: IntArray?) {
            stopWatching(m_fd, descriptors)
        }

        fun onEvent(wfd: Int, @NotifyEventType mask: Int, path: String?) {
            // look up our observer, fixing up the map if necessary...
            var observer: OkFileObserver? = null
            synchronized(mRealObservers) {
                val weak = mRealObservers[wfd]
                if (weak != null) {  // can happen with lots of events from a dead wfd
                    observer = weak.get() as OkFileObserver?
                    if (observer == null) {
                        mRealObservers.remove(wfd)
                    }
                }
            }

            // ...then call out to the observer without the sync lock held
            if (observer != null) {
                try {
                    observer?.onEvent(mask, path)
                } catch (throwable: Throwable) {
                    Log.wtf(
                        LOG_TAG,
                        "Unhandled exception in FileObserver $observer", throwable
                    )
                }
            }
        }

        private external fun init(): Int
        private external fun observe(fd: Int)
        private external fun startWatching(
            fd: Int, paths: Array<String?>,
            @NotifyEventType mask: Int, wfds: IntArray
        )

        private external fun stopWatching(fd: Int, wfds: IntArray?)
    }
}
