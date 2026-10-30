package yomira.anime4k

/** What the GPU queue does after each shader pass (src/main/cpp/anime4k/hook_pipeline.h). */
enum class PassSync(val code: Int) {
    NONE(0),

    /** Each pass is sent on its own (no long single GPU job), without waiting for it. */
    FLUSH(1),

    /** Waits for every pass. Only for measurements. */
    FINISH(2),
}

/**
 * JNI bridge to src/main/cpp/anime4k: the engine that runs mpv user shaders (Anime4K, light AI) the
 * way mpv does. [create], [estimateFlops] and [destroy] can run anywhere; the other calls need the
 * GL context current.
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

    /** Work per frame of the passes that run at this size, without a GL context; null if it cannot run. */
    fun estimateFlops(handle: Long, width: Int, height: Int, outWidth: Int, outHeight: Int): Long? =
        nativePlanMacs(handle, width, height, outWidth, outHeight).takeIf { it >= 0 }?.times(2)

    fun flopsPerFrame(handle: Long): Long = nativeMacsPerFrame(handle) * 2

    fun passCount(handle: Long): Int = nativePassCount(handle)

    /** Draws into [targetFramebuffer], or the bound one when it is -1. */
    fun process(handle: Long, inputTexture: Int, targetFramebuffer: Int, sync: PassSync): String? =
        nativeProcess(handle, inputTexture, targetFramebuffer, sync.code)

    fun releaseGl(handle: Long) = nativeReleaseGl(handle)

    fun destroy(handle: Long) = nativeDestroy(handle)

    private external fun nativeCreate(sources: Array<String>, error: Array<String?>): Long
    private external fun nativeConfigure(handle: Long, width: Int, height: Int, outWidth: Int, outHeight: Int): String?
    private external fun nativePlanMacs(handle: Long, width: Int, height: Int, outWidth: Int, outHeight: Int): Long
    private external fun nativeMacsPerFrame(handle: Long): Long
    private external fun nativePassCount(handle: Long): Int
    private external fun nativeProcess(handle: Long, inputTexture: Int, targetFramebuffer: Int, sync: Int): String?
    private external fun nativeReleaseGl(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
