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

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels { AppViewModelProvider.Factory }

    private val appLock by lazy { (application as KoshtApp).container.appLock }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

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
