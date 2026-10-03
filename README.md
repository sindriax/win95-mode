# Win95 Mode

An Android icon pack that makes your phone look like Windows 95. Every app gets
its own little 1995 object instead of a logo: a CRT TV for Netflix, a cardboard
box for Amazon, a rubber duck for DuckDuckGo.

[**Download the latest APK**](../../releases/latest)

<p align="center">
  <img src="screenshots/setup.png" width="200" alt="Setup wizard" />
  <img src="screenshots/home.png" width="200" alt="Home" />
  <img src="screenshots/icons.png" width="200" alt="Icons" />
  <img src="screenshots/wallpapers.png" width="200" alt="Wallpapers" />
</p>

## What's in it

- 305 icons for 356 apps, including the system apps on Samsung, Xiaomi and OnePlus phones
- Apps without an icon yet show up inside a tiny Win95 window
- A calendar icon that shows today's date
- Wallpapers, plus the Starfield screensaver as a live wallpaper
- The app itself is a little Win95 desktop, in English and Spanish

Missing an app? Open Win95 Mode, tap **Request Icons**, and send the list.

## Installing

Install the APK and open Win95 Mode. It checks your launcher and helps you
apply the icons.

You'll need a launcher that supports icon packs, like Nova, Lawnchair, Smart
Launcher, Action or Apex. The stock Pixel and Samsung launchers don't, but the
app will point you to a free one.

**Coming from v1 or v2?** Uninstall the old version first. Older builds were
signed with a temporary key, so Android won't install v3 over them. Only this
once.

To check your download is genuine, its signing certificate's SHA-256 is:
`9B:DE:C3:F5:B9:25:1B:9B:BE:F2:E3:A4:43:5A:28:E9:F2:C5:96:B4:02:A1:29:EF:57:8A:A4:8D:A4:CF:46:9E`

## Making icons

Icon art is converted to the pack's pixel style with `scripts/win95ify.py`
(Python 3 and Pillow):

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python scripts/win95ify.py win95ify <source-images> -o app/src/main/res/drawable-nodpi --strip-bg
```

It turns any image into 48x48 pixel art on a 192px canvas. Run it with `--help`
for the options. See [CONTRIBUTING.md](CONTRIBUTING.md) if you'd like to add an
app.

Built with Kotlin; works on Android 7 and up.

## License

Code is [MIT](LICENSE); the artwork is [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/), so share
and remix it with credit, but not commercially.

## Credits

- MS Sans Serif pixel font: [FontStruct recreation by "lou"](https://fontstruct.com/fontstructions/show/1384746) (CC BY-SA 3.0), via [98.css](https://github.com/jdan/98.css)
- Classic system icons based on the Windows 95/98 originals
