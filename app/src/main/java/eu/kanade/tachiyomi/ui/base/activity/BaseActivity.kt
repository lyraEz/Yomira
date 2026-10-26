package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.util.system.enableHighRefreshRate
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    // the player picks its own refresh rate to match each video
    protected open val pinHighRefreshRate: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
        if (pinHighRefreshRate) enableHighRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        if (pinHighRefreshRate) enableHighRefreshRate()
    }
}
