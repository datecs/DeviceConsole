# DeviceService API Reference

Client library for Datecs Android devices. Provides system-level control over device hardware, UI, app management, OTA updates, and kiosk mode.

This repository contains:
- `app/libs/com.android.device.jar` - the library to compile your app against
- **DeviceConsole** (`app/`) - a demo app with a control for every API described here
- this API reference

Build the demo with `./gradlew assembleDebug` and install `app/build/outputs/apk/debug/app-debug.apk`. It works only on Datecs Android devices, because the library is part of their firmware. DeviceConsole also declares itself as a home app, so that it can be used to try [Home Launcher](#home-launcher); after installing it, Android may ask which home app to use.

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

The methods of the listener and callback interfaces (`IInstallerListener`, `IOtaUpdateListener`, `IInstallAppCallback`, `IRemoveAppCallback`, `IInstallOtaCallback`) are called on a background (binder) thread. Switch to the main thread before touching the UI, for example with `runOnUiThread { }` or `Handler(Looper.getMainLooper()).post { }`.

### API level and older firmware

The library classes are loaded from the device, not from your APK. A method that the firmware does not have throws `NoSuchMethodError` when called. Existing methods are never removed or changed, so an app built against an older jar keeps working on newer firmware.

`getApiVersion()` tells you what the firmware supports. It exists from level 1, so on older firmware the call itself throws:

```kotlin
val api = try { dm.apiVersion } catch (e: NoSuchMethodError) { 0 }

if (api >= 1) {
    dm.setKioskFeatures(DeviceManager.KIOSK_FEATURE_SYSTEM_INFO)
}
```

| Level | Adds |
|-------|------|
| 0 | Everything not listed below (firmware without `getApiVersion()`) |
| 1 | `getApiVersion()`, `setKioskFeatures()` / `getKioskFeatures()`, hardware button state getters. Kiosk mode behaves as described under [Kiosk Mode](#kiosk-mode) |
| 2 | `setAllowedCallers()` / `getAllowedCallers()`, see [Allowed Callers](#allowed-callers). Every state-changing call is written to the log with the calling package |

USB file transfer and screen sleep / wake were added shortly before level 1 and are present on some level 0 firmware; guard them with `NoSuchMethodError` if the app must run on any firmware.

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

Read the current state (API level 1):

| Method | Returns |
|--------|---------|
| `getPowerButtonEnabled()` | `true` if the power button works |
| `getHomeButtonEnabled()` | `true` if the home button works |
| `getVolumeUpButtonEnabled()` / `getVolumeDownButtonEnabled()` | `true` if the volume button works |
| `getLeftScanButtonEnabled()` / `getRightScanButtonEnabled()` | `true` if the scan button works |

```kotlin
val powerWorks = dm.powerButtonEnabled
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

### Screen Sleep / Wake

Turn the screen off and on from code, like a power button press. Use it when the power button cannot be reached (device mounted in a machine) or is disabled.

| Method | Returns | Description |
|--------|---------|-------------|
| `goToSleep()` | `boolean` | Screen off, device goes to sleep. `true` if the device is asleep when the call returns |
| `wakeUp()` | `boolean` | Screen on. `true` if the device is awake when the call returns |

```kotlin
// Requires <uses-permission android:name="android.permission.WAKE_LOCK" />
val wakeLock = context.getSystemService(PowerManager::class.java)
    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp:screenCycle")

wakeLock.acquire(10_000L)            // keep the CPU running while the screen is off
if (dm.goToSleep()) {
    Handler(Looper.getMainLooper()).postDelayed({
        dm.wakeUp()
        if (wakeLock.isHeld) wakeLock.release()
    }, 1_000L)
} else {
    wakeLock.release()
}
```

- **Hold a partial wake lock from before `goToSleep()` until after `wakeUp()`.** Once asleep the CPU may suspend, and a delayed `wakeUp()` (Handler, coroutine `delay`, Timer) then does not run until something else wakes the device.
- Both work in kiosk mode and while the power button is disabled. With the power button disabled the user cannot wake the device, so the wake lock above is not optional.
- `goToSleep()` turns the screen off even if an activity keeps it on (`FLAG_KEEP_SCREEN_ON`, screen wake lock).
- If a screen lock is configured, `wakeUp()` shows the lock screen.
- Available only on firmware that includes these methods. On older firmware the call throws `NoSuchMethodError`; catch it if the app must also run there.

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
| -3 | The calling app is not an allowed caller (API level 2) |
| Other | Android `PackageInstaller` status code (`PackageInstaller.STATUS_FAILURE` ...) |

Installing an older version than the one already installed is reported as success (`0`); the installed version is kept.

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

`installApp()` returns `false`, without calling the callback, if the file does not exist, the signature check fails or the caller is not allowed. `onError()` receives `2` when an older version than the installed one was offered (the installed version is kept) and `3` for any other failure.

### Signature Verification

In PCI-compliant mode (`ro.vendor.datecs.pinpad.enabled=1`), APK installs are verified against:
1. Embedded Datecs application certificate
2. Platform (system) signing key

Rejected with code `-2` if neither matches. Skipped in test mode (`pinpad_test_mode=1`).

### Device Owner Registration

An app installed through this API (extended or legacy) is registered as the Android device owner right after the install, if it declares a device admin receiver:

```xml
<receiver android:name=".AdminReceiver"
          android:permission="android.permission.BIND_DEVICE_ADMIN"
          android:exported="true">
    <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

- Nothing is shown to the user and the install result does not report it. Check with `DevicePolicyManager.isDeviceOwnerApp(packageName)`.
- A device has one owner. If one is already set, the new app is installed but not registered.
- Updating the owner app keeps it owner.
- A device owner cannot be uninstalled. To give the role up, the app calls `DevicePolicyManager.clearDeviceOwnerApp(packageName)`; after that it can be removed.
- An app without a device admin receiver is installed normally.

---

## OTA Update

### Extended API (recommended)

```kotlin
// Register a listener for real-time updates
val listener = object : IOtaUpdateListener.Stub() {
    override fun onStatusChanged(status: Int, progress: Int) {
        when {
            status == 0x7FFFFFFE -> { /* copying the file: progress% */ }
            status == 3 -> { /* downloading: progress% */ }
            status == 4 -> { /* verifying: progress% */ }
            status == 5 -> { /* finalizing: progress% */ }
            status == 6 -> { /* installed, waiting for a reboot */ }
            status == 7 || status < 0 -> { /* failed */ }
        }
    }
}
dm.addOtaListener(listener)

// Start the update (no auto-reboot)
val started = dm.installOtaUpdate("/sdcard/Download/update.zip", null)

// Poll the status (alternative to a listener)
val status = dm.otaStatus
val progress = dm.otaProgress

// Reboot into the new firmware (returns false unless the status is 6)
dm.rebootAfterOta()

// Unregister
dm.removeOtaListener(listener)
```

- `addOtaListener()` reports the current status to the new listener at once. If an update is already waiting for a reboot, the listener receives status 6 immediately, so do not call `rebootAfterOta()` from the listener unless a reboot at that moment is acceptable.
- `installOtaUpdate()` returns `false` if the file does not exist or an update is already running. A listener passed to it is registered like one passed to `addOtaListener()`.
- The update is installed to the inactive system slot while the device keeps working. The device runs the old firmware until it reboots.

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
    override fun onComplete(errorCode: Int) { /* called only on failure, with a negative code */ }
})
```

`installOta()` returns `false` if the file does not exist. On success the device reboots by itself and `onComplete()` is not called.

---

## Kiosk Mode

Lock the device to a single application. Persists across reboots.

```kotlin
// Enter kiosk
dm.enterKiosk(ComponentName("com.example.app", "com.example.app.MainActivity"))

// Exit kiosk
dm.exitKiosk()
```

`enterKiosk()` returns `false` and changes nothing if the activity is not installed or is disabled. The app is started the way a launcher starts it (`ACTION_MAIN` + `CATEGORY_LAUNCHER`). If it is already running but not locked, it is restarted; if it is already locked, it is left as it is. `exitKiosk()` closes the kiosk app.

In kiosk mode:
- The user cannot leave the app: Home and Recents are hidden, notifications and quick settings are off
- Safe boot, factory reset and adding users are blocked
- Network settings (Wi-Fi, Bluetooth, mobile network) are not blocked
- App install and uninstall through this API keep working
- The power button still turns the screen off and on, unless `disablePowerButton()` was called

Updating the kiosk app while in kiosk mode releases the lock: Android closes the app to replace it, and the new version starts unlocked. The lock returns at the next `enterKiosk()` or reboot. A kiosk app that updates itself should call `enterKiosk()` when it starts.

### Kiosk features (API level 1)

Choose what stays available to the user in kiosk mode. The value is kept across reboots and `exitKiosk()`. It applies at once if kiosk mode is on, otherwise at the next `enterKiosk()`.

| Flag | Value | Meaning |
|------|-------|---------|
| `KIOSK_FEATURE_NONE` | 0 | Nothing beyond the app |
| `KIOSK_FEATURE_SYSTEM_INFO` | 1 | Clock, battery and connectivity in the status bar |
| `KIOSK_FEATURE_NOTIFICATIONS` | 2 | Notifications (needs `KIOSK_FEATURE_HOME`) |
| `KIOSK_FEATURE_HOME` | 4 | Home button |
| `KIOSK_FEATURE_RECENTS` | 8 | Recents button (needs `KIOSK_FEATURE_HOME`) |
| `KIOSK_FEATURE_POWER_MENU` | 16 | Power menu on a long press of the power button |
| `KIOSK_FEATURES_DEFAULT` | 17 | System info and power menu |

```kotlin
// Unattended kiosk: status bar info only, no power menu
dm.setKioskFeatures(DeviceManager.KIOSK_FEATURE_SYSTEM_INFO)

// Read
val features = dm.kioskFeatures
```

`setKioskFeatures()` returns `false` and changes nothing for an unknown flag, or for notifications or recents without home.

With the power menu off the user cannot switch the device off; offer `shutdown()` / `reboot()` in the app if operators need it.

On firmware older than API level 1 the kiosk features cannot be changed and depend on the model: the power menu is blocked on DAKT-50, BlueCash-05 and BlueCash-5000 and available on the other models; DAKT-50, BlueCash-05 and BlueCash-5000 also block network settings in kiosk mode.

---

## Allowed Callers

From API level 2. By default every app on the device may call every method. An app can restrict the methods that change something (install, uninstall, OTA, reboot, scheduled reboot, shutdown, kiosk, home launcher, buttons, bars, USB file transfer, sleep / wake) to a list of packages. Methods that only read stay open to every app.

```kotlin
// Only this app and the management agent may control the device
dm.setAllowedCallers(arrayOf(packageName, "com.example.agent"))

// Read; an empty array means everyone
val callers = dm.allowedCallers

// Remove the restriction
dm.setAllowedCallers(null)
```

Rules:
- While the list is empty, any app may set it. Afterwards only a listed package can change or clear it.
- The calling app's own package must be in the list it sets, so an app cannot lock itself out.
- A listed package does not have to be installed yet.
- The list is kept across reboots and firmware updates. A factory reset clears it.
- Apps that are part of the firmware are always allowed.
- `adb shell` counts as the package `com.android.shell`; add it to the list to keep using `service call` on a restricted test device.

`setAllowedCallers()` returns `false` and changes nothing if the caller may not change the list, is not in the new list, or an entry is not a package name.

A call from an app that is not listed changes nothing and does not throw: methods with a result return `false`, methods without a result do nothing, and the install / uninstall listener receives result code `-3`. The refused call is written to the device log (tag `DeviceService`) with the calling package.

The first app to set the list controls it until a factory reset. Set it from the app that owns the device, as part of provisioning.

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
dm.resetDefaultHome()  // back to a pre-installed launcher
```

Both return `true` on success. `resetDefaultHome()` returns `false` if no other launcher is installed.
