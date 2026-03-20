package com.datecs.deviceconsole

import android.content.ComponentName
import android.os.Bundle
import android.os.Environment
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
                    MainScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dm = remember { DeviceManager.getInstance(context) }

    val serial = remember { dm.serialNumber ?: "" }
    val version = remember { dm.productVersion ?: "" }
    val imei = remember { dm.imei ?: "" }
    val imsi = remember { dm.imsi ?: "" }
    val iccid = remember { dm.iccid ?: "" }
    val phone = remember { dm.phoneNumber ?: "" }
    val operator = remember { dm.networkOperatorName ?: "" }

    var statusBarEnabled by remember { mutableStateOf(dm.statusBarEnabled) }
    var expansionEnabled by remember { mutableStateOf(dm.statusBarExpansionEnabled) }
    var navBarEnabled by remember { mutableStateOf(dm.navigationBarEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("DeviceService API Demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))

        DeviceInfoSection(serial, version, imei, imsi, iccid, phone, operator)

        UIControlsSection(statusBarEnabled, expansionEnabled, navBarEnabled,
            onStatusBar = { statusBarEnabled = it; dm.enableStatusBar(it) },
            onExpansion = { expansionEnabled = it; dm.enableStatusBarExpansion(it) },
            onNavBar = { navBarEnabled = it; dm.enableNavigationBar(it) }
        )

        HardwareButtonsSection(dm)
        PowerSection(dm) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        AppInstallSection(dm) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        OtaSection(dm) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        KioskSection(dm) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun DeviceInfoSection(
    serial: String, version: String, imei: String, imsi: String,
    iccid: String, phone: String, operator: String
) {
    Section("Device Info") {
        InfoRow("Serial", serial)
        InfoRow("Version", version)
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
private fun HardwareButtonsSection(dm: DeviceManager) {
    Section("Hardware Buttons", expanded = false) {
        ToggleButtonRow("Enable Power Button", dm::enablePowerButton, dm::disablePowerButton)
        ToggleButtonRow("Enable Home Button", dm::enableHomeButton, dm::disableHomeButton)
        ToggleButtonRow("Enable Volume Up", dm::enableVolumeUpButton, dm::disableVolumeUpButton)
        ToggleButtonRow("Enable Volume Down", dm::enableVolumeDownButton, dm::disableVolumeDownButton)
        ToggleButtonRow("Enable Left Scan", dm::enableLeftScanButton, dm::disableLeftScanButton)
        ToggleButtonRow("Enable Right Scan", dm::enableRightScanButton, dm::disableRightScanButton)
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
    }
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
                safe(toast) { dm.installAppSilent(apkPath, installCallback); toast("Installing...") }
            }) { Text("Install") }
            Button(onClick = {
                safe(toast) { dm.installApplication(apkPath, installCallback, true); toast("Installing...") }
            }) { Text("Install+Launch") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pkg, { pkg = it }, Modifier.fillMaxWidth(),
            label = { Text("Package Name") }, singleLine = true)
        Button(onClick = {
            if (pkg.isNotBlank()) safe(toast) {
                dm.uninstallAppSilent(pkg, object : IInstallerListener.Stub() {
                    override fun onInstallComplete(p: String?, c: Int, m: String?) {}
                    override fun onUninstallComplete(p: String?, c: Int, m: String?) {
                        toast(if (c == 0) "Uninstalled: $p" else "Failed: ${m ?: "code $c"}")
                    }
                })
                toast("Uninstalling...")
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

        DisposableEffect(dm) {
            val listener = object : IOtaUpdateListener.Stub() {
                override fun onStatusChanged(status: Int, progress: Int) {
                    otaProgress = progress.toFloat()
                    otaStatus = formatOtaStatus(status, progress)
                    otaRunning = status in 1..5 || status == 0x7FFFFFFE
                    if (status == 6) { otaRunning = false; dm.rebootAfterOta() }
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
                        otaRunning = true; otaStatus = "Starting..."
                    } else toast("OTA failed to start")
                }
            },
            enabled = !otaRunning
        ) { Text("Install OTA") }
    }
}

@Composable
private fun KioskSection(dm: DeviceManager, toast: (String) -> Unit) {
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
private fun ToggleButtonRow(label: String, enable: () -> Unit, disable: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
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
    status == 6 -> "Update complete, rebooting..."
    status == 7 -> "Error"
    status == 8 -> "Rolling back..."
    status == 0x7FFFFFFE -> "Copying... $progress%"
    else -> "status=$status $progress%"
}
