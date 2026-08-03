# Pair with, or connect to, the watch over Wi-Fi.
#
# Usage:
#   .\watch.ps1 -Code 275014     pair with the watch (once, ever)
#   .\watch.ps1                  connect to it (after every watch reboot)
#
# Both ports the watch listens on rotate - the pairing port changes every time the
# "Pair new device" screen is opened, and the connection port changes on reboot.
# Reading them off a tiny screen and typing them in is where this goes wrong, so
# this script asks adb to discover them over mDNS instead. The only thing you ever
# have to read off the watch is the six-digit pairing code, once.

param(
    [string] $Code
)

$ErrorActionPreference = "Stop"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# ---------------------------------------------------------------------------
# Find a service the watch is advertising.
# ---------------------------------------------------------------------------

function Find-WatchService {
    param(
        [string] $ServiceType,
        [int] $TimeoutSeconds = 20
    )

    # The adb server caches mDNS records and will happily hand back a port the watch
    # stopped listening on - which looks like a network fault rather than a stale
    # cache. Restarting the server is the only way to clear it, and it costs a second.
    #
    # No 2>&1 on these: adb writes "daemon not running" to stderr as normal chatter,
    # and redirecting a native command's stderr in PowerShell 5.1 turns each line into
    # an ErrorRecord, which $ErrorActionPreference = "Stop" then treats as fatal.
    & $adb kill-server | Out-Null
    Start-Sleep -Milliseconds 800
    & $adb start-server | Out-Null

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    # Discovery is asynchronous - the browser needs a moment to hear the watch, and
    # a single query often comes back empty even when the watch is right there.
    while ((Get-Date) -lt $deadline) {

        $line = & $adb mdns services |
                Select-String -Pattern ([regex]::Escape($ServiceType)) |
                Select-Object -First 1

        if ($line) {

            # Lines look like:  adb-RFAY807ERSM-P9IZ0Z  _adb-tls-pairing._tcp  192.168.6.169:41417
            if ($line.Line -match '(\d+\.\d+\.\d+\.\d+:\d+)\s*$') {
                return $Matches[1]
            }
        }

        Start-Sleep -Milliseconds 750
    }

    return $null
}

# ---------------------------------------------------------------------------
# Pairing mode: needs the code currently on the watch's screen.
# ---------------------------------------------------------------------------

if ($Code) {

    Write-Host "Looking for the watch's pairing service..."
    $endpoint = Find-WatchService -ServiceType "_adb-tls-pairing._tcp"

    if (-not $endpoint) {
        throw "No pairing service found. Open Settings > Developer options > Wireless debugging > Pair new device on the watch, and leave that screen up."
    }

    Write-Host "Pairing with $endpoint"

    # Passing the code as an argument rather than answering the interactive prompt:
    # the pairing session is short-lived, and typing time has been known to outlast it.
    & $adb pair $endpoint $Code

    Write-Host ""
    Write-Host "Now run .\watch.ps1 with no arguments to connect."
    return
}

# ---------------------------------------------------------------------------
# Connect mode: the everyday path, after the watch has rebooted.
# ---------------------------------------------------------------------------

Write-Host "Looking for the watch's connection service..."
$endpoint = Find-WatchService -ServiceType "_adb-tls-connect._tcp"

if (-not $endpoint) {
    throw "No connection service found. Check Wireless debugging is on, and that the watch is on the same subnet as this PC. If you have never paired, run .\watch.ps1 -Code <code from the watch> first."
}

Write-Host "Connecting to $endpoint"
& $adb connect $endpoint

Write-Host ""
& $adb devices -l
