# UADE licence review

Checked on 2026-11-16 against the UADE source at commit `dc84f94` (2 November 2026),
<https://gitlab.com/uade-music-player/uade>. UADE is not part of AMPlatypus yet; this note records
what was found so the decision can be made on facts. It is not legal advice.

## What UADE consists of

| Part | Licence as stated in the source | Usable in a GPL-3 app? |
|------|---------------------------------|------------------------|
| Emulator core (from UAE), `src/` | "the GNU GPL" (`COPYING`); the bundled text is GPL 2. No file names a version or says "only". | Probably yes: where no version is named, GPL section 9 lets the recipient pick any version. Not confirmed by the authors. |
| libuade and front ends, `src/frontends/` | Per file: GPL, LGPL, public domain, or dual GPL / public domain | Yes |
| Sound core, `amigasrc/score/` | GNU LGPL (`COPYING`) | Yes |
| Player binaries, `players/` (about 176 files) | "various different licenses and quite a many different copyright holders" (`COPYING`) | Depends on the player, see below |

## The players are the problem

Each Amiga format is played by its own small Amiga program. These fall into three groups:

- **Players with a licence tag.** Six binaries carry `$LICENSE: GNU LGPL`: Fred, JochenHippel_UADE,
  ManiacsOfNoise, MusicAssembler, PreTracker, SIDMon1.0. Their source is in `amigasrc/players/uade/`.
  These are fine.
- **Wanted Team players.** 97 source directories, DigitalSoundStudio (DSS) among them.
  `amigasrc/players/wanted_team/COPYING.README` says: "Do not assume these sources are open source
  licensed even though you will find source code from here." There is no licence grant.
- **Everything else.** No licence statement was found in the binaries. Many are the original replay
  routines from games and trackers, wrapped for DeliTracker or EaglePlayer.

So of the two formats named in the runbook as the goal of this phase, PreTracker is clear and DSS is not.

## What this means for distribution

- **F-Droid** accepts only free software. A build that contains the Wanted Team players or the
  unlabelled ones would not be accepted.
- **Google Play, GitHub, IzzyOnDroid** have no free-software rule, but shipping the unlabelled players
  still means distributing other people's code without a stated permission. UADE has done so for about
  twenty years without known objection; that is a practical observation, not a right.
- The players are loaded as data by the emulator and are not linked into the app, so they do not affect
  the app's own GPL-3 licence. The open question is only whether they may be redistributed.

## Options

1. **Free players only.** Ship the UADE core with the LGPL-tagged players. Acceptable everywhere,
   but covers only a handful of formats (PreTracker, Fred, SIDMon 1, Music Assembler, Hippel, MoN).
2. **Two builds.** A free build as in option 1 for F-Droid, and a full build with all players for
   GitHub and IzzyOnDroid. This is what the runbook anticipated.
3. **Ask first.** Write to Wanted Team and to the UADE maintainer for a statement on redistribution,
   then decide between 1 and 2.
4. **Players as a download.** Ship the core only and let the user fetch the player set from the UADE
   project on first use. The app then distributes nothing unclear, at the cost of an extra step and a
   network dependency.

No option has been chosen yet.
