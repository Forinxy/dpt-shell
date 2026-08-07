package com.luoye.dpt.app

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.luoye.dpt.builder.AndroidPackage
import com.luoye.dpt.builder.Apk
import com.luoye.dpt.config.Const
import com.luoye.dpt.util.FileUtils
import com.luoye.dpt.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private val logs = mutableStateListOf<String>()
    private var protecting by mutableStateOf(false)
    private var selectedApkPath by mutableStateOf<String?>(null)
    private var outputApkPath by mutableStateOf<String?>(null)
    private val initialized = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureExternalStoragePermission()

        setContent {
            MaterialTheme {
                val pickApkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        onApkPicked(uri)
                    }
                }

                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        CenterAlignedTopAppBar(title = { Text("dpt-shell 加固工具") })
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        Button(
                            onClick = {
                                if (selectedApkPath != null) {
                                    // Reselecting: clear logs and start a new round.
                                    logs.clear()
                                    outputApkPath = null
                                    selectedApkPath = null
                                }
                                pickApkLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                            },
                            enabled = !protecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedApkPath == null) "选择 APK 文件" else "重新选择 APK")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedApkPath != null) {
                            Text(
                                text = "已选择: $selectedApkPath",
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { startProtect() },
                            enabled = selectedApkPath != null && !protecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (protecting) "加固中..." else "开始加固")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (outputApkPath != null) {
                            Button(
                                onClick = { shareOutputApk() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("分享加固后的 APK")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("日志", style = MaterialTheme.typography.titleSmall)
                            Row {
                                TextButton(onClick = { copyLogs() }) {
                                    Text("复制", fontSize = 12.sp)
                                }
                                TextButton(onClick = { logs.clear() }) {
                                    Text("清空", fontSize = 12.sp)
                                }
                            }
                        }
                        HorizontalDivider()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                logs.forEach { msg ->
                                    Text(
                                        text = msg,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Initialize shell-files & debug keystore once on the main thread context.
        ensureInitialized()
    }

    private fun ensureExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                appendLog("[WARN] 请授予\"所有文件访问\"权限，以便把加固产物保存到原 APK 所在目录")
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }

    private fun ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) {
            return
        }
        // 1. Init shell-files from assets
        val shellFilesDir = File(filesDir, "shell-files")
        extractAsset("shell-files", shellFilesDir)

        // 2. Inject paths
        FileUtils.setExecutablePath(filesDir.absolutePath)
        FileUtils.setUserDir(filesDir.absolutePath)
        Const.setRootOfOutDir(cacheDir.absolutePath)

        // 3. Debug keystore (PKCS12, generated at runtime)
        val keyStoreFile = DebugKeyStoreGenerator.ensure(File(filesDir, "debug.p12"))
        AndroidPackage.setKeyStoreType("PKCS12")
        AndroidPackage.setDebugKeyStorePath(keyStoreFile.absolutePath)

        // 4. Wire logger
        LogUtils.setLogListener { level, _, msg ->
            appendLog("[$level] $msg")
        }
    }

    private fun onApkPicked(uri: Uri) {
        // New round: clear the log panel and the previous output.
        logs.clear()
        outputApkPath = null
        val realPath = resolveRealPath(uri)
        if (realPath != null) {
            selectedApkPath = realPath
            appendLog("[INFO] Selected APK: $realPath")
        } else {
            copyToCache(uri)
        }
    }

    /**
     * Resolve a content:// Uri to a real file path so the reinforced APK can be
     * written next to the source file. Requires MANAGE_EXTERNAL_STORAGE on
     * Android 11+.
     */
    private fun resolveRealPath(uri: Uri): String? {
        try {
            if ("com.android.externalstorage.documents" == uri.authority) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val path = split[1]
                    val root = if ("primary".equals(type, ignoreCase = true)) {
                        Environment.getExternalStorageDirectory().absolutePath
                    } else {
                        "/storage/$type"
                    }
                    return File(root, path).absolutePath
                }
            }
        } catch (e: Exception) {
            appendLog("[WARN] Resolve real path failed: " + e.message)
        }
        return null
    }

    private fun copyToCache(uri: Uri) {
        val dest = File(cacheDir, "input_" + System.currentTimeMillis() + ".apk")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            selectedApkPath = dest.absolutePath
            appendLog("[INFO] Selected APK: " + dest.absolutePath)
        } catch (e: Exception) {
            appendLog("[ERROR] Failed to read selected file: " + e.message)
        }
    }

    private fun startProtect() {
        val apkPath = selectedApkPath ?: return
        protecting = true
        outputApkPath = null
        logs.clear()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    protectApk(apkPath)
                } catch (t: Throwable) {
                    appendLog("[ERROR] Protect failed: " + t.message)
                    null
                }
            }
            protecting = false
            if (result != null) {
                outputApkPath = result
                appendLog("[INFO] Done. Protected APK: " + result)
            } else {
                appendLog("[ERROR] Protection finished with errors, no output APK produced.")
            }
        }
    }

    private fun protectApk(apkPath: String): String? {
        val inputFile = File(apkPath)
        val originName = inputFile.name.removeSuffix(".apk")
        // Write the reinforced APK next to the source file, named "<origin>_加固.apk".
        val outputDir = inputFile.parentFile ?: cacheDir
        val outputFile = File(outputDir, "${originName}_加固.apk")
        outputDir.mkdirs()

        LogUtils.info("Building APK: %s", apkPath)
        val apk = Apk.Builder()
            .filePath(apkPath)
            .outputPath(outputFile.absolutePath)
            .sign(true)
            .build()
        apk.protect()

        return if (outputFile.exists()) outputFile.absolutePath else null
    }

    private fun extractAsset(assetDirName: String, destDir: File) {
        val assetManager = assets
        val list = assetManager.list(assetDirName) ?: return
        for (name in list) {
            val srcPath = "$assetDirName/$name"
            val children = assetManager.list(srcPath)
            val dest = File(destDir, name)
            if (children != null && children.isNotEmpty()) {
                dest.mkdirs()
                extractAsset(srcPath, dest)
            } else {
                dest.parentFile?.mkdirs()
                assetManager.open(srcPath).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun shareOutputApk() {
        val path = outputApkPath ?: return
        val file = File(path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享加固后的 APK"))
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            logs.add(line)
        }
    }

    private fun copyLogs() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("dpt-shell logs", logs.joinToString("\n")))
    }
}
