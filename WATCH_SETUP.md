# Connecting the Galaxy Watch7 to this machine for ADB

One-time setup, then a two-command reconnect each time the watch reboots.

Verified against Google's and Samsung's current documentation on 2026-07-30 —
see [SOURCES.md](SOURCES.md) for the pages and their last-updated dates. The
menu paths have moved between One UI Watch versions, so these are transcribed
from the docs rather than from memory.

---

## Wi-Fi is the only route

Two things rule out the alternatives:

- **Bluetooth debugging was removed in Wear OS 3.** It is not coming back.
- **The Watch7 has no USB data connection.** It charges on pogo pins; there is
  no data path over the charging puck.

So wireless debugging is not a convenience here, it is the only option. The
watch and this PC must be on the **same Wi-Fi network** for any of it to work.

---

## Step 1 — Enable Developer Mode on the watch

1. On the watch: **Settings → About watch → Software information**
2. Tap **Software version** **five times**
3. A confirmation appears, and a **Developer options** entry is added to Settings

---

## Step 2 — Turn on the three developer settings

Go to **Settings → Developer options** and enable:

| Setting | Why |
|---|---|
| **ADB debugging** | The master switch — nothing works without it |
| **Wireless debugging** | Opens the network debugging port |
| **Turn off automatic Wi-Fi** | Samsung-specific; see below |

**"Turn off automatic Wi-Fi" matters more than it sounds.** By default the watch
drops Wi-Fi whenever it has a Bluetooth link to a phone, to save power. That
kills the ADB connection mid-session, seemingly at random. Disabling it keeps
Wi-Fi up.

When you enable Wireless debugging, a dialog asks you to confirm. Choose
**Always allow on this network** so you are not re-prompted every time.

---

## Step 3 — Pair the PC to the watch (once only)

Pairing and connecting are **two different operations on two different ports**.
This is the step people get wrong, so read the port numbers carefully.

On the watch: **Settings → Developer options → Wireless debugging → Pair new device**

The watch now displays three things — leave this screen up, it times out:

- a six-digit **Wi-Fi pairing code**
- an **IP address**
- a **pairing port**

In a PowerShell window on this PC:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" pair 192.168.1.xxx:PAIRING_PORT
```

It prompts for the pairing code. Type the six digits from the watch.

Success looks like:

```
Successfully paired to 192.168.1.xxx:PAIRING_PORT
```

---

## Step 4 — Connect (repeat after every watch restart)

Back out one screen, to the main **Wireless debugging** page. Under the heading
itself — *not* under "Pair new device" — the watch shows an **IP address** and a
**connection port**.

**The connection port is a different number from the pairing port.** Using the
pairing port here is the single most common failure.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" connect 192.168.1.xxx:CONNECTION_PORT
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

`adb devices` should list the watch as `device`. If it says `unauthorized`,
check the watch for a confirmation dialog waiting to be tapped.

---

## Step 5 — Tell me the address

Once `adb devices` shows the watch, give me the `ip:port` and I will take it
from there — the probe app installs and runs over the same connection.

The connection port **changes when the watch reboots**, and often when it
rejoins the network. Pairing survives; the connection does not. So step 4 is the
routine one, and step 3 should never need repeating.

---

## What does and does not break the connection

Measured on 2026-07-31, not assumed:

**The watch dozing does NOT break it.** Polled every 10 seconds for 15 minutes with the
screen black and `mWakefulness=Dozing` throughout: not one dropped call. Shell, install,
logcat and launching the app all work normally with the display off. The single
exception is `screencap`, which returns an empty or black frame because the display
really is off - so take screenshots only after `input keyevent KEYCODE_WAKEUP`.

**What actually breaks it, in order of likelihood:**

1. **Wireless debugging switching itself off.** Android disables it whenever Wi-Fi drops
   or the network changes - and toggling airplane mode does exactly that. It does not
   come back by itself. This accounted for every disconnection in the first session.
2. **The connection port rotating.** It changes on reboot, and whenever wireless
   debugging is re-enabled. Never hardcode it; `watch.ps1` discovers it.
3. **A stale mDNS cache.** The adb server caches discovery records and will cheerfully
   hand back a port nothing is listening on, which presents as "actively refused" and
   looks like a network fault. `watch.ps1` restarts the server first to avoid this.

Distinguishing them: if `ping` succeeds but the port is refused, wireless debugging is
off or the port moved. If `ping` fails, the watch is off Wi-Fi.

## Troubleshooting

**`adb devices` shows nothing.** The SSID does not matter — the *subnet* does. This
PC is wired, on `192.168.6.189/24`, so the watch needs an address on
`192.168.6.x` for the two to reach each other. Compare the IP the watch displays
against that.

If the third octet differs, that SSID is on its own subnet; try the other one. If
the addresses match but nothing connects, the router most likely has **AP / client
isolation** turned on for that SSID — standard on guest networks, and it blocks
wireless clients from talking to anything on the LAN.

**The connection drops after a minute or two.** "Turn off automatic Wi-Fi" is
probably still off. See step 2.

**It worked yesterday and does not today.** The connection port changed. Re-read
it from the watch and run step 4 again.

**`adb pair` says "failed to authenticate".** The pairing screen timed out. Tap
**Pair new device** again for fresh numbers and retry — the code is short-lived.
