# Contributing

The easiest way to help is an [icon request](../../issues/new?template=icon-request.yml) —
but if you want to get your hands dirty, contributions are very welcome and
unusually easy here.

## Adding an icon mapping (a one-line PR)

If an app already has fitting artwork in the pack but isn't themed on your
phone, the fix is a single line in
`app/src/main/res/xml/appfilter.xml`:

```xml
<item component="ComponentInfo{com.example.app/com.example.app.MainActivity}" drawable="ic_phone" />
```

To find an app's component name:

```bash
adb shell cmd package query-activities --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
```

That prints the `package/activity` pair for every launchable app on your
device. The same app can have several aliases (different OEMs, different OS
versions) — adding another alias for an already-mapped app is fine and useful.

The test suite validates your change automatically: it checks that every
referenced drawable exists and that no component is mapped twice. Run it with:

```bash
./gradlew test
```

If the tests pass, your PR is almost certainly good. CI runs the same checks.

## Adding new artwork

Icons are real Win95-era objects (a CRT television, a cardboard box), not
pixelated modern logos — pitch your idea in an issue first so no one draws the
same thing twice. Artwork is normalized to a 192x192 retro-palette pixel grid
with `scripts/win95ify.py` (see the README's Icon Tooling section).

New drawables also need an entry in `app/src/main/res/xml/drawable.xml`, and
either a mapping in `appfilter.xml` or — for artwork that intentionally has no
app yet — a line in the `decorative` set in `IconPackResourcesTest.kt`.

## Anything else

Open an issue, or email hello@sindriax.dev if you don't have a GitHub account.
