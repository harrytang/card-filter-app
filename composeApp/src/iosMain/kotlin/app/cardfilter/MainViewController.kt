package app.cardfilter

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.cardfilter.data.db.AppDatabase
import app.cash.sqldelight.driver.native.NativeSqliteDriver

@Suppress("FunctionName") // Swift calls this as MainViewControllerKt.MainViewController()
fun MainViewController() = ComposeUIViewController {
    val container = remember {
        AppContainer(NativeSqliteDriver(AppDatabase.Schema, "cardfilter.db"))
    }
    App(container)
}
