# Build the watch app, put it on a device, and start it.
#
# Usage:
#   .\run.ps1                 build, install, launch
#   .\run.ps1 -Emulator       also start the emulator first if nothing is connected
#   .\run.ps1 -Screenshot     grab the screen afterwards and save it next to this script
#   .\run.ps1 -Log            skip the build; just tail this app's device log
#
# The device can be the emulator or a real watch paired over Wi-Fi; the script does
# not care which, it just uses whatever adb reports as connected.

param(
    [switch] $Emulator,
    [switch] $Screenshot,
    [switch] $Log
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
& $adb install -r $apk

Write-Host "Launching..."
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
