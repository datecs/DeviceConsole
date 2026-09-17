# DeviceService API Reference

Client library for Datecs Android devices. Provides system-level control over device hardware, UI, app management, OTA updates, and kiosk mode.

## Setup

Add `com.android.device.jar` as a compile-only dependency:

```kotlin
dependencies {
    compileOnly(files("libs/com.android.device.jar"))
}
```

Declare the library in your manifest:

```xml
<application>
    <uses-library android:name="com.android.device" android:required="false" />
</application>
```

Get an instance:

```kotlin
val dm = DeviceManager.getInstance(context)
```

---

## Device Info

All methods return `null` when the value is unavailable.

| Method | Returns | Description |
|--------|---------|-------------|
| `getSerialNumber()` | `String` | Device serial number |
| `getProductVersion()` | `String` | Firmware version (`ro.vendor.datecs.version.release`) |
| `getImei()` | `String` | IMEI |
| `getImsi()` | `String` | IMSI |
| `getIccid()` | `String` | SIM ICCID |
| `getPhoneNumber()` | `String` | Line1 phone number |
| `getNetworkOperatorName()` | `String` | Mobile carrier name |

```kotlin
val serial = dm.serialNumber       // "P6A2202408063019"
val version = dm.productVersion    // "2.0.0.1"
val imei = dm.imei                 // "359706073134263"
val operator = dm.networkOperatorName  // "A1 BG"
```

---

## UI Controls

| Method | Description |
|--------|-------------|
| `enableStatusBar(boolean)` | Show or hide the status bar |
| `getStatusBarEnabled()` | Returns current visibility |
| `enableStatusBarExpansion(boolean)` | Enable or disable pull-down |
| `getStatusBarExpansionEnabled()` | Returns current expansion state |
| `enableNavigationBar(boolean)` | Show or hide navigation bar (reclaims screen space) |
| `getNavigationBarEnabled()` | Returns current visibility |
| `showStatusBar(boolean)` | Legacy alias for `enableStatusBar()` |
| `showNavigationBar(boolean)` | Legacy alias for `enableNavigationBar()` |

```kotlin
// Hide navigation bar (full screen)
dm.enableNavigationBar(false)

// Disable status bar pull-down
dm.enableStatusBarExpansion(false)

// Read state
val visible = dm.statusBarEnabled
```

---

## Hardware Buttons

Disable buttons to make them do nothing when pressed.

| Method | Description |
|--------|-------------|
| `enablePowerButton()` / `disablePowerButton()` | Power button |
| `enableHomeButton()` / `disableHomeButton()` | Home button |
| `enableVolumeUpButton()` / `disableVolumeUpButton()` | Volume up |
| `enableVolumeDownButton()` / `disableVolumeDownButton()` | Volume down |
| `enableLeftScanButton()` / `disableLeftScanButton()` | Left scan (F12) |
| `enableRightScanButton()` / `disableRightScanButton()` | Right scan (F11) |

```kotlin
dm.disablePowerButton()   // ignore power button presses
dm.enablePowerButton()    // restore
```

---

## USB File Transfer

Block file access from a connected PC. Persists across reboots.

| Method | Description |
|--------|-------------|
| `enableUsbFileTransfer(boolean)` | Allow or block MTP/PTP file transfer over USB |
| `getUsbFileTransferEnabled()` | Returns current state |

```kotlin
// PC sees the device but no storage; apps keep full access to internal storage and SD card
dm.enableUsbFileTransfer(false)

// Read state
val allowed = dm.usbFileTransferEnabled
```

When disabled:
- An active File transfer / PTP session is closed
- "File transfer" and "PTP" are hidden in Settings → USB preferences
- ADB and charging are not affected

---

## Power Management

| Method | Description |
|--------|-------------|
| `reboot()` | Reboot immediately |
| `shutdown()` | Shutdown immediately |
| `scheduleReboot(long timeMs)` | Schedule reboot at absolute time (epoch ms) |
| `cancelReboot()` | Cancel a scheduled reboot |

```kotlin
// Reboot in 5 minutes
dm.scheduleReboot(System.currentTimeMillis() + 5 * 60_000L)

// Cancel
dm.cancelReboot()
```

---

## App Install / Uninstall

### Extended API

```kotlin
val callback = object : IInstallerListener.Stub() {
    override fun onInstallComplete(packageName: String?, resultCode: Int, message: String?) {
        if (resultCode == 0) { /* success */ }
        else { /* failed: message */ }
    }
    override fun onUninstallComplete(packageName: String?, resultCode: Int, message: String?) {}
}

// Silent install
dm.installAppSilent("/sdcard/Download/app.apk", callback)

// Install and launch
dm.installApplication("/sdcard/Download/app.apk", callback, true)

// Uninstall
dm.uninstallAppSilent("com.example.app", object : IInstallerListener.Stub() {
    override fun onInstallComplete(p: String?, c: Int, m: String?) {}
    override fun onUninstallComplete(packageName: String?, resultCode: Int, message: String?) {
        if (resultCode == 0) { /* success */ }
    }
})
```

### Install Result Codes

| Code | Description |
|------|-------------|
| 0 | Success |
| -1 | File not found / IO error |
| -2 | Signature verification failed |
| Other | Android PackageInstaller error code |

### Legacy API

```kotlin
dm.installApp("/sdcard/Download/app.apk", object : IInstallAppCallback.Stub() {
    override fun onSuccess() { }
    override fun onError(errorCode: Int) { }
})

dm.removeApp("com.example.app", object : IRemoveAppCallback.Stub() {
    override fun onSuccess(packageName: String?) { }
    override fun onError() { }
})
```

### Signature Verification

In PCI-compliant mode (`ro.vendor.datecs.pinpad.enabled=1`), APK installs are verified against:
1. Embedded Datecs application certificate
2. Platform (system) signing key

Rejected with code `-2` if neither matches. Skipped in test mode (`pinpad_test_mode=1`).

---

## OTA Update

### Extended API (recommended)

```kotlin
// Register listener for real-time updates
dm.addOtaListener(object : IOtaUpdateListener.Stub() {
    override fun onStatusChanged(status: Int, progress: Int) {
        when (status) {
            0 -> { /* idle */ }
            1 -> { /* checking */ }
            3 -> { /* downloading: progress% */ }
            4 -> { /* verifying: progress% */ }
            5 -> { /* finalizing: progress% */ }
            6 -> { /* complete — reboot when ready */ dm.rebootAfterOta() }
            7 -> { /* error */ }
            0x7FFFFFFE -> { /* copying file: progress% */ }
        }
    }
})

// Start update (no auto-reboot)
dm.installOtaUpdate("/sdcard/Download/update.zip", null)

// Poll status (alternative to listener)
val status = dm.otaStatus
val progress = dm.otaProgress

// Reboot (only works when status == 6)
dm.rebootAfterOta()

// Unregister
dm.removeOtaListener(listener)
```

### Status Codes

| Code | Description |
|------|-------------|
| 0 | Idle |
| 1 | Checking for update |
| 2 | Update available |
| 3 | Downloading |
| 4 | Verifying |
| 5 | Finalizing |
| 6 | Ready to reboot |
| 7 | Error |
| 8 | Rolling back |
| 0x7FFFFFFE | Copying zip |
| < 0 | Error code |

### Legacy API (auto-reboots on success)

```kotlin
dm.installOta("/sdcard/Download/update.zip", object : IInstallOtaCallback.Stub() {
    override fun onStatusUpdate(status: Int, percent: Int) { }
    override fun onComplete(errorCode: Int) { /* negative = error */ }
})
```

---

## Kiosk Mode

Lock the device to a single application. Persists across reboots.

```kotlin
// Enter kiosk
dm.enterKiosk(ComponentName("com.example.app", "com.example.app.MainActivity"))

// Exit kiosk
dm.exitKiosk()
```

In kiosk mode:
- Home, Recent, and Back buttons are hidden
- Status bar is controllable via `enableStatusBar()`
- Power menu (long-press power) is available

---

## Home Launcher

Set any app as the default home launcher. The activity must declare:

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

```kotlin
dm.setHomeActivity(ComponentName("com.example.launcher", "com.example.launcher.MainActivity"))
dm.resetDefaultHome()  // clear preference
```
