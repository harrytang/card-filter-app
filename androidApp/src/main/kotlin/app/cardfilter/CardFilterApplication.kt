package app.cardfilter

import android.app.Application
import app.cardfilter.data.db.AppDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class CardFilterApplication : Application() {
    // One driver (and so one database connection) for the whole process; activities come and go.
    val container: AppContainer by lazy {
        AppContainer(AndroidSqliteDriver(AppDatabase.Schema, applicationContext, "cardfilter.db"))
    }
}
