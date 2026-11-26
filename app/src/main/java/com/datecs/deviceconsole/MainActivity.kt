package com.datecs.deviceconsole

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.device.DeviceManager
import com.android.device.IInstallerListener
import com.android.device.IOtaUpdateListener
import com.datecs.deviceconsole.ui.theme.DeviceConsoleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceConsoleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (hasDeviceLibrary()) {
                        MainScreen(Modifier.padding(padding))
                    } else {
                        Text(
                            "This app needs the com.android.device library, which is part of " +
                                "the firmware of Datecs Android devices. It is not available " +
                                "on this device.",
                            modifier = Modifier.padding(padding).padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// The library is loaded from the device (uses-library in the manifest), not from the APK.
private fun hasDeviceLibrary(): Boolean = try {
    Class.forName("com.android.device.DeviceManager")
    true
} catch (e: ClassNotFoundException) {
    false
}

@Composable
private fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dm = remember { DeviceManager.getInstance(context) }

    // API level of the firmware. Firmware older than level 1 does not have getApiVersion(), so
    // the call itself throws NoSuchMethodError there. Methods added in a later level must only
    // be called after this check.
    val apiVersion = remember { try { dm.apiVersion } catch (e: NoSuchMethodError) { 0 } }

    val serial = remember { dm.serialNumber ?: "" }
    val version = remember { dm.productVersion ?: "" }
    val imei = remember { dm.imei ?: "" }
    val imsi = remember { dm.imsi ?: "" }
    val iccid = remember { dm.iccid ?: "" }
    val phone = remember { dm.phoneNumber ?: "" }
    val operator = remember { dm.networkOperatorName ?: "" }

    // Install, uninstall and OTA results arrive on a binder thread, where a toast cannot be
    // shown. Post every toast to the main thread.
    val toast: (String) -> Unit = remember {
        val main = Handler(Looper.getMainLooper())
        val appContext = context.applicationContext
        { msg: String -> main.post { Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show() }; Unit }
    }

    var statusBarEnabled by remember { mutableStateOf(dm.statusBarEnabled) }
    var expansionEnabled by remember { mutableStateOf(dm.statusBarExpansionEnabled) }
    var navBarEnabled by remember { mutableStateOf(dm.navigationBarEnabled) }
    var usbFileTransferEnabled by remember { mutableStateOf(dm.usbFileTransferEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("DeviceService API Demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))

        DeviceInfoSection(serial, version, apiVersion, imei, imsi, iccid, phone, operator)

        UIControlsSection(statusBarEnabled, expansionEnabled, navBarEnabled,
            onStatusBar = { statusBarEnabled = it; dm.enableStatusBar(it) },
            onExpansion = { expansionEnabled = it; dm.enableStatusBarExpansion(it) },
            onNavBar = { navBarEnabled = it; dm.enableNavigationBar(it) }
        )

        HardwareButtonsSection(dm, apiVersion)
        UsbSection(usbFileTransferEnabled) { usbFileTransferEnabled = it; dm.enableUsbFileTransfer(it) }
        PowerSection(dm, toast)
        AppInstallSection(dm, toast)
        OtaSection(dm, toast)
        KioskSection(dm, apiVersion, toast)
        if (apiVersion >= 2) {
            AllowedCallersSection(dm, toast)
        }
    }
}

@Composable
private fun DeviceInfoSection(
    serial: String, version: String, apiVersion: Int, imei: String, imsi: String,
    iccid: String, phone: String, operator: String
) {
    Section("Device Info") {
        InfoRow("Serial", serial)
        InfoRow("Version", version)
        InfoRow("API level", apiVersion.toString())
        InfoRow("IMEI", imei)
        InfoRow("IMSI", imsi)
        InfoRow("ICCID", iccid)
        InfoRow("Phone", phone)
        InfoRow("Operator", operator)
    }
}

@Composable
private fun UIControlsSection(
    statusBar: Boolean, expansion: Boolean, navBar: Boolean,
    onStatusBar: (Boolean) -> Unit,
    onExpansion: (Boolean) -> Unit,
    onNavBar: (Boolean) -> Unit
) {
    Section("UI Controls", expanded = false) {
        ToggleRow("Enable Status Bar", statusBar, onStatusBar)
        ToggleRow("Enable Expansion", expansion, onExpansion)
        ToggleRow("Enable Navigation Bar", navBar, onNavBar)
    }
}

@Composable
private fun HardwareButtonsSection(dm: DeviceManager, apiVersion: Int) {
    Section("Hardware Buttons", expanded = false) {
        // The state getters exist from API level 1. Older firmware cannot report the state,
        // so the switches start as "on" there.
        val canRead = apiVersion >= 1
        ToggleButtonRow("Enable Power Button", !canRead || dm.powerButtonEnabled,
            dm::enablePowerButton, dm::disablePowerButton)
        ToggleButtonRow("Enable Home Button", !canRead || dm.homeButtonEnabled,
            dm::enableHomeButton, dm::disableHomeButton)
        ToggleButtonRow("Enable Volume Up", !canRead || dm.volumeUpButtonEnabled,
            dm::enableVolumeUpButton, dm::disableVolumeUpButton)
        ToggleButtonRow("Enable Volume Down", !canRead || dm.volumeDownButtonEnabled,
            dm::enableVolumeDownButton, dm::disableVolumeDownButton)
        ToggleButtonRow("Enable Left Scan", !canRead || dm.leftScanButtonEnabled,
            dm::enableLeftScanButton, dm::disableLeftScanButton)
        ToggleButtonRow("Enable Right Scan", !canRead || dm.rightScanButtonEnabled,
            dm::enableRightScanButton, dm::disableRightScanButton)
    }
}

@Composable
private fun UsbSection(fileTransfer: Boolean, onFileTransfer: (Boolean) -> Unit) {
    Section("USB", expanded = false) {
        ToggleRow("Enable File Transfer", fileTransfer, onFileTransfer)
    }
}

@Composable
private fun PowerSection(dm: DeviceManager, toast: (String) -> Unit) {
    Section("Power Management", expanded = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerButton("Reboot Now") { dm.reboot() }
            DangerButton("Shutdown") { dm.shutdown() }
        }

        Spacer(Modifier.height(8.dp))
        Text("Schedule Reboot", style = MaterialTheme.typography.titleSmall)

        var mins by remember { mutableStateOf("5") }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = mins,
                onValueChange = { mins = it.filter(Char::isDigit) },
                label = { Text("Minutes") },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
            Button(onClick = {
                val m = mins.toIntOrNull() ?: 0
                if (m > 0) {
                    dm.scheduleReboot(System.currentTimeMillis() + m * 60_000L)
                    toast("Reboot in $m min")
                }
            }) { Text("Schedule") }
            Button(onClick = { dm.cancelReboot(); toast("Cancelled") }) { Text("Cancel") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Screen", style = MaterialTheme.typography.titleSmall)

        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { safe(toast) { if (!dm.goToSleep()) toast("Sleep failed") } }) {
                Text("Sleep")
            }
            Button(onClick = { safe(toast) { sleepThenWake(context, dm, 5_000L, toast) } }) {
                Text("Sleep 5 s + Wake")
            }
        }
    }
}

// Turns the screen off, then back on after delayMs. The partial wake lock keeps the CPU running
// while the device is asleep; without it the device may suspend and wakeUp() would never run.
private fun sleepThenWake(context: Context, dm: DeviceManager, delayMs: Long, toast: (String) -> Unit) {
    val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DeviceConsole:sleepThenWake")
    wakeLock.acquire(delayMs + 5_000L)
    if (!dm.goToSleep()) {
        wakeLock.release()
        toast("Sleep failed")
        return
    }
    Handler(Looper.getMainLooper()).postDelayed({
        val awake = try { dm.wakeUp() } catch (e: Exception) { false }
        if (wakeLock.isHeld) wakeLock.release()
        toast(if (awake) "Woke up" else "Wake failed")
    }, delayMs)
}

@Composable
private fun AppInstallSection(dm: DeviceManager, toast: (String) -> Unit) {
    Section("App Install / Uninstall", expanded = false) {
        var apkPath by remember { mutableStateOf("${Environment.getExternalStorageDirectory()}/Download/test.apk") }
        var pkg by remember { mutableStateOf("com.datecs.deviceconsole") }

        val installCallback = remember {
            object : IInstallerListener.Stub() {
                override fun onInstallComplete(p: String?, c: Int, m: String?) {
                    toast(if (c == 0) "Installed: $p" else "Install failed: ${m ?: "code $c"}")
                }
                override fun onUninstallComplete(p: String?, c: Int, m: String?) {}
            }
        }

        OutlinedTextField(apkPath, { apkPath = it }, Modifier.fillMaxWidth(),
            label = { Text("APK Path") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                safe(toast) { toast("Installing..."); dm.installAppSilent(apkPath, installCallback) }
            }) { Text("Install") }
            Button(onClick = {
                safe(toast) { toast("Installing..."); dm.installApplication(apkPath, installCallback, true) }
            }) { Text("Install+Launch") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pkg, { pkg = it }, Modifier.fillMaxWidth(),
            label = { Text("Package Name") }, singleLine = true)
        Button(onClick = {
            if (pkg.isNotBlank()) safe(toast) {
                toast("Uninstalling...")
                dm.uninstallAppSilent(pkg, object : IInstallerListener.Stub() {
                    override fun onInstallComplete(p: String?, c: Int, m: String?) {}
                    override fun onUninstallComplete(p: String?, c: Int, m: String?) {
                        toast(if (c == 0) "Uninstalled: $p" else "Failed: ${m ?: "code $c"}")
                    }
                })
            }
        }) { Text("Uninstall") }
    }
}

@Composable
private fun OtaSection(dm: DeviceManager, toast: (String) -> Unit) {
    Section("OTA Update", expanded = false) {
        var otaPath by remember { mutableStateOf("${Environment.getExternalStorageDirectory()}/Download/update.zip") }
        var otaStatus by remember { mutableStateOf("") }
        var otaProgress by remember { mutableFloatStateOf(0f) }
        var otaRunning by remember { mutableStateOf(false) }
        // Status 6: the update is installed and the device must reboot to finish it.
        var otaReady by remember { mutableStateOf(false) }
        // The listener is told the current status as soon as it is registered. Reboot on our own
        // only for an update started with the button below, not for one that was already waiting.
        var startedHere by remember { mutableStateOf(false) }

        DisposableEffect(dm) {
            val listener = object : IOtaUpdateListener.Stub() {
                override fun onStatusChanged(status: Int, progress: Int) {
                    otaProgress = progress.toFloat()
                    otaStatus = formatOtaStatus(status, progress)
                    otaRunning = status in 1..5 || status == 0x7FFFFFFE
                    otaReady = status == 6
                    if (status == 6 && startedHere) dm.rebootAfterOta()
                }
            }
            dm.addOtaListener(listener)
            onDispose { dm.removeOtaListener(listener) }
        }

        OutlinedTextField(otaPath, { otaPath = it }, Modifier.fillMaxWidth(),
            label = { Text("OTA Zip Path") }, singleLine = true, enabled = !otaRunning)
        if (otaStatus.isNotEmpty()) {
            Text(otaStatus, style = MaterialTheme.typography.bodyMedium)
        }
        if (otaRunning) {
            LinearProgressIndicator(progress = { otaProgress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = {
                safe(toast) {
                    if (dm.installOtaUpdate(otaPath, null)) {
                        startedHere = true; otaRunning = true; otaStatus = "Starting..."
                    } else toast("OTA failed to start")
                }
            },
            enabled = !otaRunning && !otaReady
        ) { Text("Install OTA") }
        if (otaReady) {
            DangerButton("Reboot to Finish Update") {
                safe(toast) { if (!dm.rebootAfterOta()) toast("Reboot refused") }
            }
        }
    }
}

@Composable
private fun KioskSection(dm: DeviceManager, apiVersion: Int, toast: (String) -> Unit) {
    Section("Kiosk & Home Launcher", expanded = false) {
        Text("Kiosk Mode", style = MaterialTheme.typography.titleSmall)
        var kPkg by remember { mutableStateOf("com.datecs.deviceconsole") }
        var kCls by remember { mutableStateOf("com.datecs.deviceconsole.MainActivity") }
        OutlinedTextField(kPkg, { kPkg = it }, Modifier.fillMaxWidth(),
            label = { Text("Package") }, singleLine = true)
        OutlinedTextField(kCls, { kCls = it }, Modifier.fillMaxWidth(),
            label = { Text("Activity") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { safe(toast) { dm.enterKiosk(ComponentName(kPkg, kCls)) } }) {
                Text("Enter Kiosk")
            }
            Button(onClick = { safe(toast) { dm.exitKiosk() } }) { Text("Exit Kiosk") }
        }

        // What stays available in kiosk mode. From API level 1.
        if (apiVersion >= 1) {
            Spacer(Modifier.height(4.dp))
            Text("Available in Kiosk", style = MaterialTheme.typography.titleSmall)
            var features by remember { mutableIntStateOf(dm.kioskFeatures) }
            val setFeature = { flag: Int, on: Boolean ->
                val wanted = if (on) features or flag else features and flag.inv()
                if (dm.setKioskFeatures(wanted)) {
                    features = wanted
                } else {
                    toast("Refused: notifications and recents need the home button")
                }
            }
            ToggleRow("System Info", (features and DeviceManager.KIOSK_FEATURE_SYSTEM_INFO) != 0) {
                setFeature(DeviceManager.KIOSK_FEATURE_SYSTEM_INFO, it)
            }
            ToggleRow("Power Menu", (features and DeviceManager.KIOSK_FEATURE_POWER_MENU) != 0) {
                setFeature(DeviceManager.KIOSK_FEATURE_POWER_MENU, it)
            }
            ToggleRow("Home Button", (features and DeviceManager.KIOSK_FEATURE_HOME) != 0) {
                setFeature(DeviceManager.KIOSK_FEATURE_HOME, it)
            }
            ToggleRow("Recents Button", (features and DeviceManager.KIOSK_FEATURE_RECENTS) != 0) {
                setFeature(DeviceManager.KIOSK_FEATURE_RECENTS, it)
            }
            ToggleRow("Notifications", (features and DeviceManager.KIOSK_FEATURE_NOTIFICATIONS) != 0) {
                setFeature(DeviceManager.KIOSK_FEATURE_NOTIFICATIONS, it)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Home Launcher", style = MaterialTheme.typography.titleSmall)
        var hPkg by remember { mutableStateOf("com.datecs.deviceconsole") }
        var hCls by remember { mutableStateOf("com.datecs.deviceconsole.MainActivity") }
        OutlinedTextField(hPkg, { hPkg = it }, Modifier.fillMaxWidth(),
            label = { Text("Package") }, singleLine = true)
        OutlinedTextField(hCls, { hCls = it }, Modifier.fillMaxWidth(),
            label = { Text("Activity") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { safe(toast) { dm.setHomeActivity(ComponentName(hPkg, hCls)) } }) {
                Text("Set Home")
            }
            Button(onClick = { safe(toast) { dm.resetDefaultHome() } }) { Text("Reset Home") }
        }
    }
}

// Which packages may make state-changing calls. From API level 2.
@Composable
private fun AllowedCallersSection(dm: DeviceManager, toast: (String) -> Unit) {
    Section("Allowed Callers", expanded = false) {
        val context = LocalContext.current
        var current by remember { mutableStateOf(dm.allowedCallers.toList()) }
        var input by remember { mutableStateOf(context.packageName) }
        InfoRow("Current", if (current.isEmpty()) "everyone" else current.joinToString("\n"))
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(),
            label = { Text("Packages, comma separated") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val list = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (!dm.setAllowedCallers(list.toTypedArray())) {
                    toast("Refused: this app must be in the list and allowed to change it")
                }
                current = dm.allowedCallers.toList()
            }) { Text("Set") }
            Button(onClick = {
                if (!dm.setAllowedCallers(null)) toast("Refused: this app is not an allowed caller")
                current = dm.allowedCallers.toList()
            }) { Text("Allow Everyone") }
        }
    }
}

@Composable
private fun Section(
    title: String,
    expanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(expanded) }
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (isExpanded) "▲" else "▼")
    }
    if (isExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifEmpty { "-" }, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun ToggleButtonRow(label: String, initial: Boolean, enable: () -> Unit, disable: () -> Unit) {
    var enabled by remember { mutableStateOf(initial) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = enabled, onCheckedChange = { enabled = it; if (it) enable() else disable() })
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) { Text(text) }
}

private inline fun safe(toast: (String) -> Unit, block: () -> Unit) {
    try { block() } catch (e: Exception) { toast("Error: ${e.message}") }
}

private fun formatOtaStatus(status: Int, progress: Int): String = when {
    status < 0 -> "Error: $status"
    status == 0 -> ""
    status == 1 -> "Checking for update..."
    status == 2 -> "Update available"
    status == 3 -> "Downloading... $progress%"
    status == 4 -> "Verifying... $progress%"
    status == 5 -> "Finalizing... $progress%"
    status == 6 -> "Update installed, reboot to finish"
    status == 7 -> "Error"
    status == 8 -> "Rolling back..."
    status == 0x7FFFFFFE -> "Copying... $progress%"
    else -> "status=$status $progress%"
}
