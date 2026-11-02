# Muse Kitchen Frame

**A kitchen-tablet hub for Muse.** Muse Kitchen Frame turns an ordinary Android
tablet into a Muse gadget: it sits in a picture frame on the kitchen counter,
always on, showing the time and the day's status — and it takes announcements,
answers questions out loud, holds live voice calls, and prints to a thermal
label printer. It is a personal project, built with Muse, and it has long
since outgrown the app it was forked from.

The frame is a Samsung Galaxy Tab A9+ (SM-X210, Android 16). Nothing about the
design is Portal-specific any more: pairing uses the standard Bluetooth GATT
flow, so it works on modern Android.

## What it does

**The hub (home screen)**
- Dark bronze-gold home screen: live clock and date, Muse's framed portrait,
  and a status panel that Muse updates through the day — upcoming events,
  lunch and dinner plans, notes, a dad joke when there's nothing better.
- While a question, a spoken answer, or a call is live, the panel shows the
  turn's captions instead: pages that hold until the voice reaches the last
  word, with the spoken word lit up.
- Announcements still take the whole screen when they arrive, then the hub
  comes back.

**Voice**
- "Hey Muse" wake word (offline, Vosk) for hands-free questions.
- Voice notes up to 18 seconds, with a live countdown — the cap is the
  service's real ceiling, found by experiment.
- Realtime voice calls from the green phone button, with a call microphone
  tuned for talking across a kitchen.
- Ask by tapping the portrait; answers are captioned and spoken.

**Announcements**
- Full-screen cards (rendered at the panel's native 1920×1200) with spoken
  audio, a replay history for the last five, and a persistent mute.

**Printing**
- A Nelko PM230 pocket thermal printer, paired to the tablet, prints on
  command: urgent-reminder slips, recipes, labels — zero touch. The printer
  speaks Bluetooth Classic SPP and TSPL; the implementation is byte-exact
  against captured jobs from the official app, verified by golden tests.
  (Long prints need patience with Bluetooth's drain timing — the app holds
  the link until the job has truly left the tablet. That lesson was paid for
  in wasted paper, like all the best ones.)

## Gadget commands

The frame registers with Muse as a gadget and answers these commands:

| Command | What it does |
| --- | --- |
| `device.health` | Connection, pairing, battery, uptime, printer state |
| `display.draw_url` | Show an image full-screen until dismissed |
| `display.clear` | Dismiss the pushed image |
| `display.set_status` | Set the hub status panel's header and body (light layout markup) |
| `display.set_recipe` | Show a recipe card on the panel: title, two-column ingredients, numbered steps |
| `audio.play_url` / `audio.stop` | Play / stop audio through the speakers |
| `speaker.speak` | Speak text with the tablet's TTS voice |
| `voice.ask` | Ask Muse a question; the answer shows and is spoken |
| `printer.print` | Print a text slip on the PM230 (title, alignment, density, copies) |
| `printer.probe` | Check the printer is reachable, printing nothing |

## Install

Every build is published as a
[GitHub Release](https://github.com/realtechnick/MuseKitchenFrame/releases):
download `portal-muse-gadget-vX.Y.Z.apk` and install it on the tablet.
All builds are signed with the same key, so updates install in place —
pairing and settings survive. Then open the app, paste an SDK token from
gadgets.muse.ai, name the frame, and pair it from the Muse app.

## Building

GitHub Actions builds on every push to `main`: unit tests, then the app and
probe APKs, signed with the persistent keystore, and a Release is published
with the version's change notes. Local Android builds are not part of the
workflow — CI is the build machine.

## Lineage

Forked from [wobsoriano/portal-muse](https://github.com/wobsoriano/portal-muse),
which brought Muse to the Meta Portal; the gadget protocol core descends
from Meta's gadget SDK by way of hey-muse's port. Everything above that
layer — pairing on modern Android, the microphone and caption work, calls,
the hub, the printer — is this project's own. The PM230 protocol was
reverse-engineered by
[an21p/nelko-pm230-driver](https://github.com/an21p/nelko-pm230-driver)
(MIT); this app's TSPL implementation is a port of that work, fixtures and
all. Standing on these shoulders is the whole point.
