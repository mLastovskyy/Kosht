package by.mlastovsky.kosht

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import by.mlastovsky.kosht.data.lock.LockState
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.KoshtRoot
import by.mlastovsky.kosht.ui.lock.LockScreen
import by.mlastovsky.kosht.ui.theme.KoshtAppTheme
import by.mlastovsky.kosht.ui.theme.isAppInDarkTheme
import by.mlastovsky.kosht.util.LocaleHelper

/**
 * A [FragmentActivity] rather than a plain one: the system biometric prompt is
 * a fragment, and borrowing the phone's own fingerprint dialog is worth the
 * base class.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels { AppViewModelProvider.Factory }

    private val appLock by lazy { (application as KoshtApp).container.appLock }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash also waits for the lock: Home must not flash up for a
        // moment before the code screen replaces it.
        splashScreen.setKeepOnScreenCondition {
            viewModel.settings.value == null || appLock.state.value == LockState.Unknown
        }
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val lockState by appLock.state.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val darkTheme = isAppInDarkTheme(current.themeMode)

            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkTheme }
                )
            }

            // With a lock on, the task switcher should not hold a readable
            // picture of the balance — the point of the lock is that the
            // figures need the code, and a thumbnail does not ask for one.
            LaunchedEffect(lockState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(lockState == LockState.Off)
                }
            }

            KoshtAppTheme(
                themeMode = current.themeMode,
                dynamicColors = current.dynamicColors
            ) {
                Surface {
                    when (lockState) {
                        // Standing in place of the app, not over it: nothing
                        // can be left showing behind the code screen that way.
                        LockState.Locked -> LockScreen()
                        LockState.Unknown -> Unit
                        else -> KoshtRoot()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        appLock.onBackground()
    }

    /**
     * Every camera, gallery and settings screen the app opens comes through
     * here, whichever launcher asked for it. Telling the lock about it is what
     * keeps photographing a receipt from looking like leaving the app.
     */
    @Deprecated("Kept to notice the app's own trips out; launchers still go through it")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        appLock.expectExternalResult()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Kept to notice the app's own trips out; launchers still go through it")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        appLock.expectExternalResult()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode)
    }
}
