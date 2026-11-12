# Lax Wi-Fi

An Android app that runs actions when your phone joins or leaves a saved Wi-Fi network, without ever using Location (GPS).

## What it does

- **Save networks.** Connect to a Wi-Fi network, tap **Add current network** and give it a name.
- **Choose actions per network.** Each network has an **On connect** and an **On disconnect** list. Available actions:
  - Set the ringer to Normal, Vibrate or Silent
  - Show a notification with your own text (`{network}` is replaced by the network's name)
- **Test actions.** The **Test** button on each list runs it immediately.

## How it works without Location

Android only reveals a Wi-Fi network's name (SSID) to apps that hold the location permission while Location is switched on. Lax Wi-Fi declares no location permission at all. It recognises a network by a fingerprint read from the connection itself:

- Router (gateway) address, IPv4 and IPv6
- Subnet
- DNS servers

Consequences:

- **You type the network name yourself.** The app cannot read it.
- **Matches are "strong" or "weak".** A router with its own long IPv6 link-local address is close to unique, which gives a strong match. Without one, the match relies on the IPv4 router address, subnet and DNS, so another network with the same setup (for example two `192.168.1.1` home routers) triggers the same actions. The edit screen shows which kind each network has.
- **Connect actions run a few seconds after joining.** The app waits about 2.5 seconds for the connection details to settle before matching.

## Permissions

| Permission | Why |
|---|---|
| Notifications | Notification actions and the ongoing monitoring notification |
| Do Not Disturb access | Switching to Silent and back |
| Unrestricted battery | Keeps monitoring alive in the background |
| Run at startup | Restarts monitoring after a reboot |

The home screen shows a **Finish setup** card for any of the first three that are still missing.

A foreground service keeps the network callback registered, so a low-priority notification is always visible while the app is monitoring. After a reboot Android can take a minute or two to restart it.

The app has two notification categories in Android's settings:

- **Wi-Fi monitoring** is the ongoing notification. Its text never changes, and turning the category off hides it without stopping monitoring.
- **Custom notifications** covers the notifications you add as actions. Set their sound here.

## Building

Requirements:

- Android SDK with platform 37 (`compileSdk 37`, `targetSdk 36`, `minSdk 26`)
- A JDK; the one bundled with Android Studio works

Open the folder in Android Studio, or build from a terminal with `JAVA_HOME` pointing at a JDK:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug and release builds are signed with different keys, so one cannot be installed over the other; Android reports "App not installed". To switch between them, uninstall the app first, which erases its saved networks and settings.

## Project layout

All source is under `app/src/main/java/com/laxwifi/app/`.

| File | Contents |
|---|---|
| `Model.kt` | Networks, actions, fingerprints, matching rules, JSON storage format |
| `Repo.kt` | Saved networks in SharedPreferences, live status for the UI |
| `WifiInfoReader.kt` | Builds a fingerprint from the current connection |
| `MonitorService.kt` | Foreground service that watches Wi-Fi changes; boot receiver |
| `ActionRunner.kt` | Carries out ringer and notification actions |
| `ui/` | Jetpack Compose screens and the black and magenta theme |

## Status

Tested on an Android 17 emulator: adding a network, connect and disconnect actions, and restart after reboot. Also tested on a physical phone. Vendor battery savers on other phones may still interfere with background monitoring.
