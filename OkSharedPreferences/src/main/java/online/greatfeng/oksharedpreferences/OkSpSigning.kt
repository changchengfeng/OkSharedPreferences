package online.greatfeng.oksharedpreferences

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Binds on-disk files to the installing APK's signing certificate.
 * Only processes signed with the same certificate share the same storage file names.
 */
internal object OkSpSigning {

    private const val SEPARATOR = "_"

    @Volatile
    private var cachedSigningId: String? = null

    fun signingId(context: Context): String {
        val cached = cachedSigningId
        if (cached != null) {
            return cached
        }
        return synchronized(this) {
            cachedSigningId ?: computeSigningId(context).also { cachedSigningId = it }
        }
    }

    fun storageBaseName(context: Context, logicalName: String): String {
        return storageBaseNameWithSigningId(signingId(context), logicalName)
    }

    fun storageBaseNameWithSigningId(signingId: String, logicalName: String): String {
        return "$signingId$SEPARATOR$logicalName"
    }

    /**
     * Returns the logical preference name when [storageBaseName] belongs to this app signature;
     * otherwise null (foreign or legacy file names are ignored).
     */
    fun logicalNameFromStorageBaseName(context: Context, storageBaseName: String): String? {
        return logicalNameFromStorageBaseName(signingId(context), storageBaseName)
    }

    fun logicalNameFromStorageBaseName(signingId: String, storageBaseName: String): String? {
        val prefix = "$signingId$SEPARATOR"
        if (!storageBaseName.startsWith(prefix)) {
            return null
        }
        val logicalName = storageBaseName.substring(prefix.length)
        return if (logicalName.isEmpty()) {
            null
        } else {
            logicalName
        }
    }

    private fun computeSigningId(context: Context): String {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo
                ?: throw IllegalStateException("Package $packageName has no signingInfo")
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            if (signatures.isEmpty()) {
                throw IllegalStateException("Package $packageName has no signing certificates")
            }
            signatures[0].toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            @Suppress("DEPRECATION")
            val signatures = packageInfo.signatures
            if (signatures.isNullOrEmpty()) {
                throw IllegalStateException("Package $packageName has no signatures")
            }
            signatures[0].toByteArray()
        }
        return sha256Hex(signatureBytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = CharArray(digest.size * 2)
        var index = 0
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            hex[index++] = HEX_DIGITS[value ushr 4]
            hex[index++] = HEX_DIGITS[value and 0x0F]
        }
        return String(hex)
    }

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )
}
