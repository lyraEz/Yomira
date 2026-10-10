package yomira.tv

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.util.system.DeviceTierManager

/**
 * Coarse device facts used to pick player defaults that a Fire TV Stick can sustain.
 *
 * Set once from App.onCreate. Defaults only: every setting can still be changed by hand.
 */
object DeviceProfile {

    @Volatile
    var isLowEndTv: Boolean = false
        private set

    @Volatile
    var totalRamGb: Double = 0.0
        private set

    fun init(context: Context, isTv: Boolean) {
        totalRamGb = DeviceTierManager.getTotalRamGb(context)
        val only32Bit = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        isLowEndTv = isTv && (only32Bit || totalRamGb < LOW_RAM_GB)
    }

    private const val LOW_RAM_GB = 2.5
}
