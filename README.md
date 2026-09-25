# Win95 Mode

A nostalgic Windows 95-style icon pack and wallpaper manager for Android. Transform your phone into a retro desktop experience.

## Download

[**Download latest APK**](../../releases/latest)

## Screenshots

<p align="center">
  <img src="screenshots/home_view.png" width="260" alt="Home Screen" />
  <img src="screenshots/app_view.png" width="260" alt="App View" />
  <img src="screenshots/wallpaper_dialog.png" width="260" alt="Wallpaper Dialog" />
</p>

## What's New in v3

- Samsung, Xiaomi and OnePlus/Oppo support: system apps (dialer, camera, messages, gallery, clock, files and more) are finally themed on non-Pixel phones — icon mappings grew from 71 to 181
- New calculator, Internet and security artwork covers calculator apps, Samsung Internet and Xiaomi Security
- Starfield Screensaver live wallpaper: the classic flying-through-space simulation, forever
- Dynamic calendar icon that shows today's actual date (Nova, Lawnchair and friends)
- Apply button: pick your launcher in a Display Properties dialog instead of digging through settings
- Request Icons: an Add/Remove Programs screen lists your unthemed apps and sends the request with one tap
- Icon search over the preview grid
- Wallpapers regenerated at real phone resolution — no more blurry pixel art
- Releases are now signed with a permanent key, so future updates install in place (this first one needs a one-time uninstall/reinstall — see the release notes)

## Features

- 180+ themed app mappings styled like classic Windows 95
- Apps without a themed icon get a beveled Win95 plaque, so the whole home screen stays coherent
- Authentic Win95 UI elements (beveled borders, 3D buttons, MS Sans Serif pixel font)
- Classic wallpapers (teal, clouds, setup, stars, matrix) for home and lock screen
- Works with all major Android launchers

## Supported Launchers

- Nova Launcher
- Lawnchair
- Action Launcher
- Apex Launcher
- Smart Launcher
- And more...

## Installation

1. Download and install the APK from [Releases](../../releases/latest)
2. Open your launcher settings
3. Navigate to Icon Pack or Theme
4. Select "Win95 Mode"
5. Enjoy the nostalgia!

## Tech Stack

- **Language:** Kotlin
- **Platform:** Android (API 24 - Android 7.0+)
- **UI:** XML Layouts with custom drawable resources
- **Build System:** Gradle with Kotlin DSL
- **Architecture:** Single-activity app with intent filters for launcher integration
- **Icon Pack Protocol:** Supports ADW, Nova, Apex, Action, Lawnchair, and Smart Launcher icon pack formats via `appfilter.xml`
- **Min SDK:** 24 | **Target SDK:** 36

## Icon Tooling

Icon artwork is normalized with `scripts/win95ify.py` (requires Python 3 with Pillow):

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python scripts/win95ify.py win95ify <source-images> -o app/src/main/res/drawable --strip-bg
```

It converts any source image to a true pixel grid with a reduced retro palette
on a 192x192 canvas. Run it with `--help` for all options.

## Credits

- MS Sans Serif pixel font: [FontStruct recreation by "lou"](https://fontstruct.com/fontstructions/show/1384746) (CC BY-SA 3.0), via [98.css](https://github.com/jdan/98.css)
- Classic system icons based on the Windows 95/98 originals
