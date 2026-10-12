package app.socmed.manager

import android.app.Application
import app.socmed.manager.data.AppDatabase
import app.socmed.manager.data.Backup
import app.socmed.manager.data.MediaFiles
import app.socmed.manager.data.Repository
import app.socmed.manager.notify.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SocMedApp : Application() {
    lateinit var repo: Repository
    lateinit var backup: Backup
    lateinit var scope: CoroutineScope

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val db = AppDatabase.build(this)
        repo = Repository(this, db, MediaFiles(this), scope)
        backup = Backup(this, repo)
        Reminders.ensureChannel(this)
        scope.launch(Dispatchers.IO) { repo.ensureDefaults() }
        repo.data.debounce(1500).onEach { if (it.loaded) Reminders.sync(this, it) }.launchIn(scope)
    }
}
