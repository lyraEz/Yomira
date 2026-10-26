package yomira.anime4k

/**
 * JNI bridge to src/main/cpp/anime4k: the engine that runs the official Anime4K shaders the way mpv
 * does. [create] and [destroy] can run anywhere; the other calls need the GL context current.
 */
internal object Anime4KNative {

    val available: Boolean by lazy {
        runCatching { System.loadLibrary("yomira_anime4k") }.isSuccess
    }

    /** Parses the shaders. Throws with the parser message when one is not supported. */
    fun create(sources: List<String>): Long {
        check(available) { "Anime4K engine not available on this device" }
        val error = arrayOfNulls<String>(1)
        val handle = nativeCreate(sources.toTypedArray(), error)
        if (handle == 0L) throw IllegalArgumentException(error[0] ?: "invalid shader")
        return handle
    }

    /** Null when ready, otherwise why it failed (a shader did not compile, no half-float targets). */
    fun configure(handle: Long, width: Int, height: Int, outWidth: Int, outHeight: Int): String? =
        nativeConfigure(handle, width, height, outWidth, outHeight)

    fun flopsPerFrame(handle: Long): Long = nativeMacsPerFrame(handle) * 2

    fun passCount(handle: Long): Int = nativePassCount(handle)

    /** Draws into [targetFramebuffer], or the bound one when it is -1. */
    fun process(handle: Long, inputTexture: Int, targetFramebuffer: Int, finishEachPass: Boolean): String? =
        nativeProcess(handle, inputTexture, targetFramebuffer, finishEachPass)

    fun releaseGl(handle: Long) = nativeReleaseGl(handle)

    fun destroy(handle: Long) = nativeDestroy(handle)

    private external fun nativeCreate(sources: Array<String>, error: Array<String?>): Long
    private external fun nativeConfigure(handle: Long, width: Int, height: Int, outWidth: Int, outHeight: Int): String?
    private external fun nativeMacsPerFrame(handle: Long): Long
    private external fun nativePassCount(handle: Long): Int
    private external fun nativeProcess(handle: Long, inputTexture: Int, targetFramebuffer: Int, finishEachPass: Boolean): String?
    private external fun nativeReleaseGl(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
