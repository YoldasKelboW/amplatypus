// SPDX-License-Identifier: GPL-3.0-or-later
//
// The libopenmpt part of the format list is taken from MPTReplayer.m of 4champ,
// Copyright (c) Aleksi Sitomaniemi, used under the MIT license.
package io.github.yoldaskelbow.amplatypus.audio

import io.github.yoldaskelbow.amplatypus.BuildConfig

object ModuleFormats {
    /** File extensions (lower case) played by libopenmpt or the HivelyTracker replayer. */
    val trackerExtensions: Set<String> = setOf(
        "667", "669", "amf", "ams", "dbm", "digi", "dmf", "dsm", "dsym", "dtm", "far", "fmt", "fst", "gdm",
        "gtk", "gt2", "ice", "it", "imf", "j2b", "m15", "mdl", "med", "mmcmp", "mms", "mo3", "mod", "mptm",
        "mt2", "mtm", "nst", "oct", "okt", "oxm", "plm", "ppm", "psm", "pt36", "ptm", "s3m", "sfx", "sfx2",
        "st26", "stk", "stm", "stp", "stx", "symmod", "ult", "umx", "wow", "xm", "xmf",
        "mmd0", "mmd1", "mmd2", "mmd3",
        // Played by the HivelyTracker replayer.
        "ahx", "hvl",
    )

    /** UADE's replay routines in this build; the "free" and "full" builds ship different sets. */
    private val uadePlayers: Set<String> = BuildConfig.UADE_PLAYERS.split(',').filter { it.isNotEmpty() }.toSet()

    /** Extensions (and Amiga-style prefixes) those routines stand for. */
    val uadeExtensions: Set<String> = BuildConfig.UADE_EXTENSIONS.split(',').filter { it.isNotEmpty() }.toSet()

    /**
     * Modland format folders that UADE plays, with the replay routine that does it.
     *
     * UADE's extensions cannot be used to filter Modland: many of them also belong to formats of other
     * machines there ("mus" is Commodore 64 Sidplayer, "psf" is PlayStation, "ftm" is FamiTracker).
     * The folder says what a file really is. Left out are formats whose songs need more than one companion
     * file, those the tracker replayers already play, and those whose sample files did not play in a test with UADE 3.05.
     */
    private val uadeModlandPlayers: Map<String, String> = mapOf(
        "AM Composer" to "AM-Composer", "AProSys" to "AProSys", "Actionamics" to "ActionAmics",
        "Activision Pro" to "MartinWalker", "Anders Oland" to "Anders_0land", "Arpeggiator" to "MajorTom",
        "Art And Magic" to "ArtAndMagic", "Art Of Noise" to "ArtOfNoise-4V",
        "BP SoundMon 2" to "SoundMon2.0", "BP SoundMon 3" to "SoundMon2.2",
        "Beathoven Synthesizer" to "BeathovenSynthesizer", "Ben Daglish" to "BenDaglish",
        "Ben Daglish SID" to "BenDaglish-SID", "Core Design" to "CoreDesign", "CustomMade" to "CustomMade",
        "Delitracker Custom" to "custom", "IFF-SMUS" to "SonixMusicDriver",
        "Darius Zendeh" to "DariusZendeh", "Dave Lowe" to "DaveLowe", "Dave Lowe New" to "DaveLoweNew",
        "David Hanney" to "DavidHanney", "David Whittaker" to "DavidWhittaker",
        "Delta Music" to "DeltaMusic1.3", "Delta Music 2" to "DeltaMusic2.0", "Delta Packer" to "Tronic",
        "Desire" to "Desire", "Digital Mugician" to "Mugician", "Digital Mugician 2" to "MugicianII",
        "Digital Sonix And Chrome" to "DigitalSonixChrome", "Digital Sound Studio" to "DigitalSoundStudio",
        "EarAche" to "EarAche", "Electronic Music System" to "EMS", "Electronic Music System v6" to "EMS-6",
        "Face The Music" to "FaceTheMusic", "Fashion Tracker" to "FashionTracker",
        "Follin Player II" to "TimFollin",
        "Fred Gray" to "FredGray", "FredMon" to "Fred",
        "Future Composer 1.3" to "FutureComposer1.3", "Future Composer 1.4" to "FutureComposer1.4",
        "Future Composer BSI" to "FutureComposer-BSI", "Future Player" to "FuturePlayer",
        "Game Music Creator" to "GMC", "GlueMon" to "GlueMon",
        "Hippel" to "JochenHippel", "Hippel 7V" to "JochenHippel-7V", "Hippel COSO" to "JochenHippel-CoSo_UADE",
        "Howie Davies" to "HowieDavies", "Images Music System" to "ImagesMusicSystem",
        "InStereo!" to "InStereo", "InStereo! 2.0" to "InStereo2.0",
        "JamCracker" to "JamCracker", "Janko Mrsic-Flogel" to "JankoMrsicFlogel", "Jason Brooke" to "JasonBrooke",
        "Jason Page Old" to "SteveTurner", "Jeroen Tel" to "JeroenTel", "Jesper Olsen" to "JesperOlsen",
        "Leggless Music Editor" to "LegglessMusicEditor",
        "Lionheart" to "SonicArranger", "MCMD" to "JochenHippel", "Maniacs Of Noise" to "ManiacsOfNoise",
        "Mark Cooksey" to "Mark_Cooksey", "Mark II" to "MarkII", "Medley" to "Medley", "Mike Davies" to "MikeDavies",
        "MultiMedia Sound" to "MultiMedia_Sound", "Music Assembler" to "MusicAssembler",
        "Musicline Editor" to "MusiclineEditor", "NovoTrade Packer" to "NovoTradePacker",
        "OctaMED MMDC" to "MMDC", "Paul Shields" to "PaulShields",
        "Powertracker" to "Laxity", "Pretracker" to "PreTracker",
        "Professional Sound Artists" to "ProfessionalSoundArtists", "Pumatracker" to "PumaTracker",
        "Quadra Composer" to "QuadraComposer", "Riff Raff" to "RiffRaff", "Rob Hubbard" to "RobHubbard",
        "Rob Hubbard ST" to "RobHubbard_ST", "Ron Klaren" to "CustomMade", "SCUMM" to "SCUMM",
        "Sean Connolly" to "SeanConnolly", "Sean Conran" to "SeanConran",
        "SidMon 1" to "SIDMon1.0", "SidMon 2" to "SIDMon2.0", "Silmarils" to "Silmarils",
        "Sonic Arranger" to "SonicArranger", "Sound Images" to "SoundImages", "Sound Master" to "SoundMaster",
        "Sound Master II v1" to "SoundMaster", "Sound Master II v3" to "SoundMaster",
        "Sound Programming Language" to "SoundProgrammingLanguage", "SoundControl" to "SoundControl",
        "SoundFactory" to "SoundFactory", "Special FX" to "Special-FX",
        "Special FX ST" to "Special-FX_ST", "Speedy A1 System" to "SpeedyA1System", "Speedy System" to "SpeedySystem",
        "Steve Barrett" to "SteveBarrett", "SunTronic" to "SUN-Tronic", "SynTracker" to "SynTracker",
        "Synthesis" to "Synth", "TCB Tracker" to "TCB_Tracker",
        // Two-file formats; see ModlandCompanions.
        "TFMX" to "TFMX", "TFMX ST" to "TFMX_ST", "Richard Joseph" to "RichardJoseph_Player",
        "Audio Sculpture" to "AudioSculpture",
        "The Musical Enlightenment" to "TheMusicalEnlightenment", "Tomy Tracker" to "TomyTracker",
        "Voodoo Supreme Synthesizer" to "VoodooSupremeSynthesizer", "Wally Beben" to "WallyBeben",
    )

    /** The Modland format folders UADE plays in this build. */
    val uadeModlandFolders: Set<String> = uadeModlandPlayers.filterValues { it in uadePlayers }.keys

    /** Changes whenever the set of playable Modland files does, so stored indexes are rebuilt. */
    val VERSION: Int = 10 * 31 + uadeModlandFolders.hashCode()

    /** Commodore 64 files, played by libsidplayfp: SID tunes and Compute's Sidplayer songs. */
    val c64Extensions: Set<String> = setOf("sid", "psid", "mus", "str")

    /** Modland folders of C64 music the app plays. RealSID needs the C64's own ROMs, which it lacks. */
    val c64ModlandFolders: Set<String> = setOf("HVSC", "Sidplayer", "Stereo Sidplayer")

    /**
     * Console and home computer music. Game Music Emu plays NES, SNES, Game Boy, Mega Drive and Master
     * System, PC Engine, MSX, Atari 8-bit, ZX Spectrum and Amstrad CPC (Modland keeps AY files as
     * ".emul"); sc68 plays Atari ST SNDH and SC68 files, StSound YM files, and ZXTune the modules of the ZX
     * Spectrum's trackers (see [zxExtensions]).
     */
    val consoleExtensions: Set<String> =
        setOf("nsf", "nsfe", "spc", "gbs", "vgm", "vgz", "gym", "hes", "kss", "sap", "ay", "emul", "sndh", "sc68", "ym") +
            setOf("pt3", "pt2", "pt1", "stc", "st13", "zxs", "asc", "sqt", "stp2", "psc", "ftc", "gtr", "vtx", "vt2", "st11", "chi") +
            xsfExtensions

    /**
     * Console music ripped as xSF files, played by the emulators ZXTune bundles: Game Boy Advance (GSF),
     * Nintendo DS (2SF), Nintendo 64 (USF), Sega Saturn (SSF) and Dreamcast (DSF). "Mini" files hold one
     * song and name the game's library file ("gsflib" and so on), which is fetched with them and not listed.
     * PlayStation files (PSF) are not played: their emulator would need Sony's BIOS.
     */
    val xsfExtensions: Set<String>
        get() = setOf("gsf", "minigsf", "2sf", "mini2sf", "usf", "miniusf", "ssf", "minissf", "dsf", "minidsf")

    /**
     * ZX Spectrum tracker modules, played by ZXTune: Pro Tracker 1-3, Vortex Tracker II's text files, Sound
     * Tracker (".stc", ".st13", ".zxs" with its player, and ".st11" in AY_Emul's container), ASC Sound Master,
     * SQ-Tracker, Sound Tracker Pro, Pro Sound Creator and Maker, Fast Tracker, Global Tracker, Chip Tracker's
     * samples, and VTX register dumps. ".stp" and ".psm" are also tracker formats of the Amiga and
     * PC; the app tries ZXTune first and libopenmpt after it.
     */
    val zxExtensions: Set<String> =
        setOf("pt3", "pt2", "pt1", "stc", "st13", "zxs", "asc", "sqt", "stp", "stp2", "psc", "psm", "ftc", "gtr", "vtx", "vt2", "st11", "chi")

    /** Modland folders of console and home computer music the app plays. */
    val consoleModlandFolders: Set<String> = setOf(
        "Nintendo Sound Format", "Nintendo SPC", "Gameboy Sound System", "Video Game Music", "Megadrive GYM",
        "HES", "KSS", "Slight Atari Player", "AY Emul", "SNDH", "SC68", "YM", "Spectrum",
        "Gameboy Sound Format", "Nintendo DS Sound Format", "Ultra64 Sound Format", "Saturn Sound Format",
        "Dreamcast Sound Format",
    )

    /**
     * Video Game Music is filed by system. Game Music Emu has the Sega and Texas Instruments sound chips
     * (SN76489, YM2612, YM2413), not those of the 32X, Mega CD, MSX, Vectrex and others.
     */
    val vgmSystems: Set<String> = setOf(
        "Sega Megadrive", "Sega Master System", "Sega Game Gear", "Sega SG-1000", "Sega SC-3000",
        "BBC Micro", "Colecovision",
    )

    /** For files from the device: is there a replayer for this extension (or Amiga-style prefix)? */
    fun isPlayable(extension: String): Boolean =
        extension.lowercase().let {
            it in trackerExtensions || it in uadeExtensions || it in c64Extensions || it in consoleExtensions
        }

    /** For Modland: [format] is the top folder of the file's [path]. */
    fun isPlayableOnModland(format: String, extension: String, path: String = ""): Boolean {
        val ext = extension.lowercase()
        if (format == "Video Game Music") return path.split('/').getOrNull(1) in vgmSystems && ext in consoleExtensions
        return ext in trackerExtensions || format in uadeModlandFolders ||
            (format in c64ModlandFolders && ext in c64Extensions) ||
            (format in consoleModlandFolders && ext in consoleExtensions) ||
            (format == "Spectrum" && ext in zxExtensions)
    }

    /** The group a file belongs to by its extension (or Amiga-style prefix), for the format filter. */
    fun groupOf(extension: String): FormatGroup = when (extension.lowercase()) {
        in hivelyExtensions -> FormatGroup.HIVELY
        in c64Extensions -> FormatGroup.C64
        in consoleExtensions -> FormatGroup.CONSOLE
        in trackerExtensions -> FormatGroup.TRACKER
        else -> FormatGroup.AMIGA
    }

    val hivelyExtensions: Set<String> = setOf("ahx", "hvl")

    /** Extensions of the tracker group (libopenmpt), without AHX and HVL. */
    val trackerOnlyExtensions: Set<String> = trackerExtensions - hivelyExtensions

    /** Extensions of all groups but the Amiga one, which takes everything else. */
    val namedExtensions: Set<String> get() = trackerExtensions + c64Extensions + consoleExtensions
}

/** Format groups the user can show or hide. */
enum class FormatGroup { TRACKER, HIVELY, AMIGA, C64, CONSOLE }

/** Which [FormatGroup]s are shown. At least one always is. */
data class FormatFilter(
    val tracker: Boolean = true,
    val hively: Boolean = true,
    val amiga: Boolean = true,
    val c64: Boolean = true,
    val console: Boolean = true,
) {
    val showsAll get() = tracker && hively && amiga && c64 && console

    fun shows(group: FormatGroup) = when (group) {
        FormatGroup.TRACKER -> tracker
        FormatGroup.HIVELY -> hively
        FormatGroup.AMIGA -> amiga
        FormatGroup.C64 -> c64
        FormatGroup.CONSOLE -> console
    }

    fun with(group: FormatGroup, shown: Boolean): FormatFilter {
        val next = when (group) {
            FormatGroup.TRACKER -> copy(tracker = shown)
            FormatGroup.HIVELY -> copy(hively = shown)
            FormatGroup.AMIGA -> copy(amiga = shown)
            FormatGroup.C64 -> copy(c64 = shown)
            FormatGroup.CONSOLE -> copy(console = shown)
        }
        return if (next.tracker || next.hively || next.amiga || next.c64 || next.console) next else this
    }

    /** Extensions the database query lets through by name; [amiga] covers all others (see [namedExtensions]). */
    fun shownExtensions(): List<String> = buildList {
        if (tracker) addAll(ModuleFormats.trackerOnlyExtensions)
        if (hively) addAll(ModuleFormats.hivelyExtensions)
        if (c64) addAll(ModuleFormats.c64Extensions)
        if (console) addAll(ModuleFormats.consoleExtensions)
        // Room needs a non-empty list for IN (...).
        if (isEmpty()) add("")
    }
}
