package com.example.rhythme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from GitHub releases.
 * Every push to main builds a signed APK and publishes it as release "build-N" (N = versionCode).
 * The app compares N with its own versionCode, downloads the APK and hands it to the installer.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val build: Int, val apkUrl: String) : UpdateState
    data class Downloading(val build: Int, val progress: Float) : UpdateState // progress < 0 = unknown size
    data class Ready(val build: Int, val file: File) : UpdateState
    data object Failed : UpdateState
}

class UpdateController(context: Context) {
    private val appContext = context.applicationContext

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** The pop-up offered at startup when a newer build exists. */
    var promptVisible by mutableStateOf(false)

    val currentBuild: Int = BuildConfig.VERSION_CODE

    /** [silent]: the startup check — no error or "up to date" message, only a prompt if newer. */
    suspend fun check(silent: Boolean = false) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        val latest = withContext(Dispatchers.IO) { runCatching { fetchLatest() }.getOrNull() }
        state = when {
            latest == null -> if (silent) UpdateState.Idle else UpdateState.Failed
            latest.first > currentBuild -> UpdateState.Available(latest.first, latest.second)
            else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
        }
        if (silent && state is UpdateState.Available) promptVisible = true
    }

    /** Download (if needed) and open the system installer. */
    suspend fun downloadAndInstall() {
        when (val s = state) {
            is UpdateState.Ready -> install(s.file)
            is UpdateState.Available -> {
                state = UpdateState.Downloading(s.build, -1f)
                val file = withContext(Dispatchers.IO) {
                    runCatching { download(s.apkUrl) { p -> state = UpdateState.Downloading(s.build, p) } }.getOrNull()
                }
                if (file == null) {
                    state = UpdateState.Failed
                } else {
                    state = UpdateState.Ready(s.build, file)
                    install(file)
                }
            }
            else -> Unit
        }
    }

    private fun fetchLatest(): Pair<Int, String>? {
        val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Rhythme-app")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val build = json.optString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    return build to asset.getString("browser_download_url")
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, onProgress: (Float) -> Unit): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the newest download
        val target = File(dir, "rhythme-update.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Rhythme-app")
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastReported = 0f
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - lastReported >= 0.02f) { lastReported = p; onProgress(p) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    private fun install(file: File) {
        // Android 8+: the user must allow this app to install apps (once)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return // state stays Ready: tapping "Install" again continues
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/yanivmalka/Rhythme/releases/latest"
    }
}

/** Status line + buttons for the About screen. */
@Composable
fun UpdateSection(updates: UpdateController) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_title), fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        val status = when (val s = updates.state) {
            UpdateState.Idle -> null
            UpdateState.Checking -> stringResource(R.string.update_checking)
            UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
            is UpdateState.Available -> stringResource(R.string.update_available, s.build)
            is UpdateState.Downloading -> stringResource(R.string.update_downloading)
            is UpdateState.Ready -> stringResource(R.string.update_ready, s.build)
            UpdateState.Failed -> stringResource(R.string.update_failed)
        }
        if (status != null) {
            Text(status, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        (updates.state as? UpdateState.Downloading)?.let { DownloadProgress(it.progress) }
        when (updates.state) {
            is UpdateState.Available, is UpdateState.Ready ->
                Button(onClick = { scope.launch { updates.downloadAndInstall() } }) {
                    Text(stringResource(R.string.update_install))
                }
            UpdateState.Checking, is UpdateState.Downloading -> Unit
            else ->
                OutlinedButton(onClick = { scope.launch { updates.check() } }) {
                    Text(stringResource(R.string.update_check))
                }
        }
    }
}

/** Pop-up shown at startup when a newer build is on GitHub. */
@Composable
fun UpdatePrompt(updates: UpdateController) {
    if (!updates.promptVisible) return
    val scope = rememberCoroutineScope()
    val state = updates.state
    val build = when (state) {
        is UpdateState.Available -> state.build
        is UpdateState.Downloading -> state.build
        is UpdateState.Ready -> state.build
        else -> null
    }
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) updates.promptVisible = false },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(
                    when (state) {
                        is UpdateState.Downloading -> stringResource(R.string.update_downloading)
                        UpdateState.Failed -> stringResource(R.string.update_failed)
                        else -> stringResource(R.string.update_available, build ?: 0)
                    }
                )
                if (state is UpdateState.Downloading) {
                    Spacer(Modifier.height(12.dp))
                    DownloadProgress(state.progress)
                }
            }
        },
        confirmButton = {
            if (state is UpdateState.Available || state is UpdateState.Ready) {
                TextButton(onClick = { scope.launch { updates.downloadAndInstall() } }) {
                    Text(stringResource(R.string.update_install))
                }
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = { updates.promptVisible = false }) { Text(stringResource(R.string.update_later)) }
            }
        }
    )
}

@Composable
private fun DownloadProgress(progress: Float) {
    if (progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}
