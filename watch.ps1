# Pair with, or connect to, the watch over Wi-Fi.
#
# Usage:
#   .\watch.ps1 -Code 275014     pair with the watch (once, ever)
#   .\watch.ps1                  connect to it (after every watch reboot)
#   .\watch.ps1 -Wait            keep trying until it appears, then connect
#
# Both ports the watch listens on rotate - the pairing port changes every time the
# "Pair new device" screen is opened, and the connection port changes on reboot.
# Reading them off a tiny screen and typing them in is where this goes wrong, so
# this script asks adb to discover them over mDNS instead. The only thing you ever
# have to read off the watch is the six-digit pairing code, once.
#
# -Wait exists because adbd on this watch does not stay up. It comes back when
# wireless debugging is switched on - by hand, or with the button in the test app -
# and then dies again within a minute or two, sometimes mid-transfer. Waiting for
# it beats trying to press the button and start an install at the same moment.

param(
    [string] $Code,
    [switch] $Wait,
    [int] $WaitMinutes = 0
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Tweakables.
#
# All durations are in the unit named in the variable. Ports are TCP.
# ---------------------------------------------------------------------------

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# The watch's Wi-Fi MAC, used to find its current IP when mDNS says nothing.
# Read it from Settings > About watch > Status if the watch is ever replaced.
$WATCH_MAC = '3e-ce-ba-05-db-28'

# The /24 this PC and the watch share. Only used for the ping sweep that
# populates the ARP table before we look the MAC up in it.
$WATCH_SUBNET = '192.168.6'

# How long a single mDNS discovery attempt keeps asking before giving up.
$MDNS_DISCOVERY_TIMEOUT_SECONDS = 20

# Gap between mDNS queries within one discovery attempt. Discovery is
# asynchronous, and a single query often comes back empty even when the watch
# is sitting right there.
$MDNS_RETRY_INTERVAL_MS = 750

# adbd lands somewhere in Android's ephemeral range. Sweeping it is the fallback
# for when the watch is listening but not advertising, which happens often enough
# to be worth the twenty seconds it costs.
$PORT_SWEEP_LOW = 30000
$PORT_SWEEP_HIGH = 61000

# Sockets opened at once during a sweep. Much above this and Windows starts
# refusing them with "insufficient buffer space", killing the sweep.
$PORT_SWEEP_BATCH = 1200

# How long to let a batch of connection attempts settle before reading results.
$PORT_SWEEP_SETTLE_MS = 700

# After "adb connect", how long to let the TLS handshake finish before deciding
# whether the device is really usable. Too short and a good device reads as dead.
$CONNECT_HANDSHAKE_MS = 1500

# -Wait pacing: the cheap mDNS check runs often, the expensive sweep rarely.
$WAIT_POLL_INTERVAL_SECONDS = 3
$WAIT_SWEEP_INTERVAL_SECONDS = 25
$WAIT_DEFAULT_MINUTES = 25

# The parameter default has to be a literal, so the real default lives here.
if ($WaitMinutes -le 0) { $WaitMinutes = $WAIT_DEFAULT_MINUTES }

# How long a ping may take before we call that address empty, during the sweep
# that populates the ARP table.
$PING_TIMEOUT_MS = 900

# ---------------------------------------------------------------------------
# Run adb without its stderr becoming a fatal error.
#
# adb writes routine chatter ("daemon not running", "device offline") to stderr.
# In PowerShell 5.1 a native command's stderr becomes an ErrorRecord, which
# $ErrorActionPreference = "Stop" then treats as fatal - so probing for a device
# that is not there would abort the script instead of returning "not there".
# ---------------------------------------------------------------------------

function Invoke-Adb {
    param(
        [string[]] $Arguments
    )

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'

    try {
        & $adb @Arguments 2>&1 | ForEach-Object { "$_" }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

# ---------------------------------------------------------------------------
# Find a service the watch is advertising over mDNS.
# ---------------------------------------------------------------------------

function Find-WatchService {
    param(
        [string] $ServiceType,
        [int] $TimeoutSeconds = $MDNS_DISCOVERY_TIMEOUT_SECONDS,
        [switch] $NoServerReset
    )

    # The adb server caches mDNS records and will happily hand back a port the watch
    # stopped listening on - which looks like a network fault rather than a stale
    # cache. Restarting the server is the only way to clear it, and it costs a second.
    #
    # -NoServerReset is for the -Wait loop, which polls far too often to restart the
    # server every time.
    if (-not $NoServerReset) {
        Invoke-Adb @('kill-server') | Out-Null
        Start-Sleep -Milliseconds 800
        Invoke-Adb @('start-server') | Out-Null
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {

        # Lines look like:  adb-RFAY807ERSM-P9IZ0Z  _adb-tls-pairing._tcp  192.168.6.169:41417
        $line = Invoke-Adb @('mdns', 'services') |
                Select-String -Pattern ([regex]::Escape($ServiceType)) |
                Select-Object -First 1

        if ($line -and $line.Line -match '(\d+\.\d+\.\d+\.\d+:\d+)\s*$') {
            return $Matches[1]
        }

        Start-Sleep -Milliseconds $MDNS_RETRY_INTERVAL_MS
    }

    return $null
}

# ---------------------------------------------------------------------------
# Find the watch's current IP by MAC, for when mDNS is silent.
# ---------------------------------------------------------------------------

function Get-WatchIp {

    # The ARP entry may already be there from recent traffic.
    $entry = arp -a | Select-String -Pattern $WATCH_MAC

    if (-not $entry) {

        # Nothing cached, so make every host on the subnet answer. Fire all the
        # pings at once and wait for the batch; sequentially this takes minutes.
        $pings = 1..254 | ForEach-Object {
            (New-Object System.Net.NetworkInformation.Ping).SendPingAsync("$WATCH_SUBNET.$_", $PING_TIMEOUT_MS)
        }

        [System.Threading.Tasks.Task]::WaitAll($pings, 7000) | Out-Null

        $entry = arp -a | Select-String -Pattern $WATCH_MAC
    }

    if (-not $entry) { return $null }

    return ($entry.Line.Trim() -split '\s+')[0]
}

# ---------------------------------------------------------------------------
# Find open ports on the watch, for when it is listening but not advertising.
# ---------------------------------------------------------------------------

function Find-WatchOpenPorts {
    param(
        [string] $Ip
    )

    $open = @()
    $low = $PORT_SWEEP_LOW

    while ($low -lt $PORT_SWEEP_HIGH) {

        $high = [math]::Min($low + $PORT_SWEEP_BATCH - 1, $PORT_SWEEP_HIGH)

        # Start every connection in the batch, then come back and read them.
        # A batch can still hit the socket ceiling under load, so skip the ones
        # that will not open rather than letting the whole sweep die.
        $attempts = @()

        foreach ($port in $low..$high) {
            try {
                $client = New-Object System.Net.Sockets.TcpClient
                $attempts += [PSCustomObject]@{
                    Port   = $port
                    Client = $client
                    Task   = $client.ConnectAsync($Ip, $port)
                }
            }
            catch {
                Start-Sleep -Milliseconds 50
            }
        }

        Start-Sleep -Milliseconds $PORT_SWEEP_SETTLE_MS

        foreach ($attempt in $attempts) {

            if ($attempt.Task.IsCompleted -and -not $attempt.Task.IsFaulted -and $attempt.Client.Connected) {
                $open += "$Ip`:$($attempt.Port)"
            }

            $attempt.Client.Close()
        }

        $low = $high + 1
    }

    return $open
}

# ---------------------------------------------------------------------------
# Is this endpoint a device we can actually drive?
#
# "adb connect" reporting success is not enough - it happily returns "connected"
# for a device that then answers every command with "device offline".
# ---------------------------------------------------------------------------

function Test-WatchEndpoint {
    param(
        [string] $Endpoint
    )

    Invoke-Adb @('connect', $Endpoint) | Out-Null
    Start-Sleep -Milliseconds $CONNECT_HANDSHAKE_MS

    $reply = Invoke-Adb @('-s', $Endpoint, 'shell', 'echo', 'ok')

    return [bool] ($reply | Where-Object { $_ -match '^ok\s*$' })
}

# ---------------------------------------------------------------------------
# One full attempt: advertised first, swept second.
# ---------------------------------------------------------------------------

function Connect-Watch {
    param(
        [switch] $Quick
    )

    # mDNS is cheap and usually right.
    $timeout = if ($Quick) { 1 } else { $MDNS_DISCOVERY_TIMEOUT_SECONDS }

    $endpoint = Find-WatchService -ServiceType "_adb-tls-connect._tcp" `
                                  -TimeoutSeconds $timeout `
                                  -NoServerReset:$Quick

    if ($endpoint -and (Test-WatchEndpoint $endpoint)) { return $endpoint }

    # -Quick is the frequent poll in the wait loop; sweeping there is the
    # caller's decision, not this function's.
    if ($Quick) { return $null }

    $ip = Get-WatchIp
    if (-not $ip) { return $null }

    foreach ($candidate in (Find-WatchOpenPorts -Ip $ip)) {
        if (Test-WatchEndpoint $candidate) { return $candidate }
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
# Wait mode: keep looking until adbd shows up.
# ---------------------------------------------------------------------------

if ($Wait) {

    Write-Host "Waiting for the watch (up to $WaitMinutes min). Turn wireless debugging on when ready."

    # Clear any stale mDNS cache once, up front. The loop below cannot afford to
    # restart the adb server on every poll.
    Invoke-Adb @('kill-server') | Out-Null
    Start-Sleep -Milliseconds 800
    Invoke-Adb @('start-server') | Out-Null

    $deadline = (Get-Date).AddMinutes($WaitMinutes)

    # Backdated so the first sweep happens immediately rather than after a wait.
    $lastSweep = (Get-Date).AddSeconds(-$WAIT_SWEEP_INTERVAL_SECONDS)

    $endpoint = $null

    while ((Get-Date) -lt $deadline -and -not $endpoint) {

        # Cheap check, run often.
        $endpoint = Connect-Watch -Quick

        # Expensive check, run rarely.
        if (-not $endpoint -and ((Get-Date) - $lastSweep).TotalSeconds -ge $WAIT_SWEEP_INTERVAL_SECONDS) {

            $lastSweep = Get-Date

            $ip = Get-WatchIp

            if ($ip) {
                foreach ($candidate in (Find-WatchOpenPorts -Ip $ip)) {
                    if (Test-WatchEndpoint $candidate) { $endpoint = $candidate; break }
                }
            }
        }

        if (-not $endpoint) { Start-Sleep -Seconds $WAIT_POLL_INTERVAL_SECONDS }
    }

    if (-not $endpoint) {
        throw "Gave up after $WaitMinutes minutes. Wireless debugging never came up - check it is on, and that the watch's Wi-Fi is on at all."
    }

    Write-Host "Connected to $endpoint"
    Write-Host ""

    & $adb devices -l
    return
}

# ---------------------------------------------------------------------------
# Connect mode: the everyday path, after the watch has rebooted.
# ---------------------------------------------------------------------------

Write-Host "Looking for the watch's connection service..."
$endpoint = Connect-Watch

if (-not $endpoint) {
    throw "No connection service found. Check Wireless debugging is on, and that the watch is on the same subnet as this PC. If you have never paired, run .\watch.ps1 -Code <code from the watch> first. To sit and wait for it instead, use .\watch.ps1 -Wait."
}

Write-Host "Connected to $endpoint"

Write-Host ""
& $adb devices -l
