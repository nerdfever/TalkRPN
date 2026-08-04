# Build the watch app, put it on a device, and start it.
#
# Usage:
#   .\run.ps1                 build, install, launch
#   .\run.ps1 -Wait           wait for the watch to appear first, then do all that
#   .\run.ps1 -Emulator       also start the emulator first if nothing is connected
#   .\run.ps1 -Screenshot     grab the screen afterwards and save it next to this script
#   .\run.ps1 -Log            skip the build; just tail this app's device log
#
# The device can be the emulator or a real watch paired over Wi-Fi; the script does
# not care which, it just uses whatever adb reports as connected.
#
# -Wait is for the real watch, whose adbd comes up only briefly when wireless
# debugging is switched on and then dies again. Start this first, then press the
# button - it connects the moment the watch answers, so there is no window to hit
# by hand. Discovery itself lives in watch.ps1; this only calls it.

param(
    [switch] $Emulator,
    [switch] $Screenshot,
    [switch] $Log,
    [switch] $Wait
)

# Stop at the first failure rather than blundering on to the next step.
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Work out where everything is.
# ---------------------------------------------------------------------------

# This script lives in the project root, so the project root is wherever it is.
$projectRoot = $PSScriptRoot

# The SDK location and the redirected build directory are both recorded in
# local.properties, so read them from there rather than hardcoding them twice.
$localProperties = @{}

Get-Content "$projectRoot\local.properties" | ForEach-Object {

    # Skip blank lines and comments.
    if ($_ -match '^\s*(#|$)') { return }

    # Split "key=value" on the first '=' only, and undo the escaping that
    # Java properties files apply to backslashes and colons.
    $key, $value = $_ -split '=', 2
    $localProperties[$key.Trim()] = $value.Trim() -replace '\\:', ':' -replace '\\\\', '\'
}

$sdk = $localProperties['sdk.dir']
$adb = "$sdk\platform-tools\adb.exe"
$emulatorExe = "$sdk\emulator\emulator.exe"

# Build output normally sits under the project, unless it has been redirected.
if ($localProperties.ContainsKey('buildDir.root')) {
    $apk = "$($localProperties['buildDir.root'])\app\outputs\apk\debug\app-debug.apk"
} else {
    $apk = "$projectRoot\app\build\outputs\apk\debug\app-debug.apk"
}

# What we are installing and starting.
$appId = "com.nerdfever.talkrpn"
$activity = "$appId/.MainActivity"

# Where the APK is staged on the device before being installed from there.
$remoteApk = "/data/local/tmp/$appId.apk"

# How many times to retry the transfer when the Wi-Fi link drops part-way.
$PUSH_ATTEMPTS = 4

# ---------------------------------------------------------------------------
# Log mode: tail this app's output and nothing else, then stop.
# ---------------------------------------------------------------------------

if ($Log) {

    # logcat can filter by process id, which is far cleaner than grepping tags -
    # but it needs the app to actually be running to have one.
    $processId = (& $adb shell pidof $appId).Trim()

    if (-not $processId) {
        throw "$appId is not running. Launch it first, then re-run with -Log."
    }

    Write-Host "Tailing log for $appId (pid $processId). Ctrl+C to stop."
    & $adb logcat --pid=$processId

    # Nothing below applies to log mode.
    return
}

# ---------------------------------------------------------------------------
# Make sure something is listening before we spend time on a build.
# ---------------------------------------------------------------------------

if ($Wait) {

    # Blocks until the watch answers, or throws if it never does.
    & "$projectRoot\watch.ps1" -Wait
}

$connected = & $adb devices | Select-String -Pattern "\sdevice$"

if (-not $connected) {

    if (-not $Emulator) {
        throw "No device connected. Pair a watch, or re-run with -Emulator to start the virtual one."
    }

    Write-Host "Starting the emulator..."

    # Detached, because the emulator runs until it is closed and we want the
    # script to carry on.
    Start-Process -FilePath $emulatorExe -ArgumentList "-avd", "Wear5_Round", "-no-boot-anim"

    # Wait for adb to see it, then for Android itself to finish booting - those
    # are two different things, and installing between them fails.
    & $adb wait-for-device
    & $adb shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'

    Write-Host "Emulator ready."
}

# ---------------------------------------------------------------------------
# Build, install, launch.
# ---------------------------------------------------------------------------

Write-Host "Building..."
& "$projectRoot\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed." }

Write-Host "Installing..."

# Push first, then install from the device.
#
# A streamed "adb install" that dies part-way leaves nothing behind to resume, and
# on the real watch it has died part-way - the link drops, the install reports
# "device offline", and adbd then comes back on a different port. A push can simply
# be retried whole, and the install that follows is local to the device.
#
# Waking the screen first keeps the Wi-Fi radio up for the duration of the transfer.
& $adb shell input keyevent KEYCODE_WAKEUP

$pushed = $false

foreach ($attempt in 1..$PUSH_ATTEMPTS) {

    # Not $ErrorActionPreference-safe as a plain call: adb reports transfer progress
    # on stderr, which "Stop" would treat as a failure. Look at what it says instead.
    $output = & $adb push $apk $remoteApk 2>&1 | ForEach-Object { "$_" }

    if ($output -match 'file pushed|bytes in') { $pushed = $true; break }

    Write-Host "  transfer dropped, attempt $attempt of $PUSH_ATTEMPTS - reconnecting"

    & "$projectRoot\watch.ps1" -Wait
}

if (-not $pushed) { throw "Could not get the APK onto the device after $PUSH_ATTEMPTS attempts." }

& $adb shell pm install -r -t $remoteApk

Write-Host "Launching..."
& $adb shell am force-stop $appId
& $adb shell am start -n $activity

# ---------------------------------------------------------------------------
# Optionally capture what it looks like.
# ---------------------------------------------------------------------------

if ($Screenshot) {

    # Give the app a moment to actually draw its first frame.
    Start-Sleep -Seconds 2

    # Capture on the device and pull the file across. Screenshotting straight
    # down a pipe would work on a POSIX shell, but PowerShell's redirection
    # mangles binary data, so the file has to make the trip intact.
    & $adb shell screencap -p /sdcard/screenshot.png
    & $adb pull /sdcard/screenshot.png "$projectRoot\screenshot.png"

    Write-Host "Saved $projectRoot\screenshot.png"
}

Write-Host "Done."
