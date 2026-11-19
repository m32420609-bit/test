# NovaXR — Java/Maven starter

NovaXR is a Windows-first Java desktop companion prototype. It scans Steam library folders, lists installed games, launches games through Steam, and hosts a local Android companion page.

## Requirements
- Windows 10/11 (other desktop OSes may work, but discovery is Windows-focused)
- JDK 17 or newer
- Maven 3.9+
- Android phone and PC on the same Wi-Fi/LAN

## Run
```bat
mvn clean javafx:run
```
Build:
```bat
mvn clean package
```
The JavaFX app is easiest to run with `mvn javafx:run`; the shaded JAR may need JavaFX runtime modules on the target machine.

## Features in this prototype
- Detects common Steam install locations and Steam library folders from `libraryfolders.vdf`.
- Reads installed game manifests (`appmanifest_*.acf`).
- Launches a selected game with `steam://run/APPID`.
- Hosts a phone companion at `http://PC-LAN-IP:8765/`.
- Shows the desktop as a live MJPEG stream in the phone browser.
- Receives Android device-orientation readings over WebSocket.
- Offers a simple side-by-side phone viewing mode and touch/gamepad input demo.

## Important limitations
This is a working foundation, not a replacement for the SteamVR runtime:
- A phone browser cannot become a fully compliant SteamVR/OpenXR headset by itself.
- Real head tracking in PC games requires an OpenXR/SteamVR driver or a dedicated bridge that exposes pose data to the runtime. NovaXR currently receives orientation telemetry but does not inject it into games.
- Side-by-side mode duplicates the desktop image; it does not synthesize two 3D eye views.
- A normal non-VR game does not become stereoscopic VR automatically. Use a compatible game/mod/driver for that.
- The prototype does not emulate tracked VR controllers yet. It receives basic phone orientation and touch events; gamepad support is planned.
- Windows Firewall may ask permission for local network access. Allow Java on private networks only.
- Keep the phone and PC on a trusted private network. Do not expose the companion server to the public internet.

## Phone connection
1. Start NovaXR on the PC.
2. Open the shown LAN address on Android Chrome.
3. Tap **Enable head tracking** and allow motion/orientation access if prompted.
4. Put the phone in a suitable VR viewer for the split-screen view.

## Project layout
- `src/main/java/com/novaxr/` — Java desktop app and local services
- `src/main/resources/phone/` — Android browser companion
