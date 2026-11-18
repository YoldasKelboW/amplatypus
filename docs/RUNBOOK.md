# Runbook: AMPlatypus – Amiga-Modul-Player für Android

Stand: 12.11.2026 · Auftraggeber: Kelbo

> **An Claude Code:** Lies dieses Runbook vollständig, lege es als `docs/RUNBOOK.md` ins Repo und starte mit Phase 1. Arbeite eine Phase pro Sitzung ab. Halte dich an die festen Regeln unten.

## Ziel

Eine eigenständige, quelloffene Android-App, die große Modul-Archive durchsucht und Amiga-Module (MOD, AHX, DSS, PreTracker usw.) abspielt. Vorbild ist die iOS-App [4champ](https://github.com/sitomani/4champ) von Aleksi Sitomaniemi. Veröffentlicht wird auf GitHub, F-Droid und später Google Play (IzzyOnDroid fällt weg, siehe Phase 6).

- **Name:** AMPlatypus (festgelegt am 18.11.2026; ursprünglicher Arbeitstitel war Paula). Kein „4champ“ im Namen; README und Store-Text sagen, dass die App nicht mit Amiga Music Preservation verbunden ist.
- **Paketname:** `io.github.yoldaskelbow.amplatypus` (festgelegt). Nach dem ersten Release nie mehr ändern.

## Zusammenarbeit mit Kelbo

- Kelbo programmiert nicht selbst, versteht aber Technik und ist eher im Design zu Hause. Erkläre knapp auf Deutsch, liefere testbare APKs und frag nach Testergebnissen statt nach Code-Entscheidungen.
- Testgerät: ein arm64-Smartphone (arm64-v8a).
- Gebaut wird hauptsächlich in GitHub Actions; lokale Builds müssen auch auf schwacher Hardware (8 GB RAM) möglich sein.
- Jede Phase endet mit: Eintrag in `CHANGELOG.md`, getaggtem Pre-Release auf GitHub mit APK und einer kurzen Testliste auf Deutsch.

## Ausgangslage: was 4champ zeigt

- iOS-App in Swift, 88 Dateien, rund 11.500 Zeilen, Clean-Swift-Architektur. Nichts davon läuft auf Android. Übernommen werden Logik, Datenmodell und Abläufe, gebaut wird alles neu in Kotlin.
- **Lizenz:** `LICENSE` lizenziert die App doppelt unter GPL (Text: GPLv2) und MIT. Für portierte Logik die MIT-Option nutzen und den Copyright-Hinweis im Dateikopf behalten.
- **Wiedergabe:** libOpenMPT für die meisten Formate, HivelyTracker-Replayer (BSD-3) für AHX/HVL, UADE für exotische Amiga-Formate. Submodule: `github.com/sitomani/openmpt`, `gitlab.com/sitomani/uade-ios`. UADE steht laut GitLab unter LGPL-2.1, bitte selbst prüfen.
- **Datenquelle von 4champ:** nicht amp.dascene.net direkt, sondern eine private API des Entwicklers. Sie ist seit November 2026 per Schlüssel auf die 4champ-App beschränkt und wird von dieser App **nicht** genutzt.

## Musikquellen: austauschbare Quellen-Schicht

Die App hängt nicht von einer einzigen Quelle ab. Baue eine Schnittstelle `MusicSource` (Suche, Durchblättern, Details, Download, Zufallsmodul), hinter der jede Quelle steckt. UI, Bibliothek, Playlists und Radio kennen nur diese Schnittstelle.

1. **Modland (Standardquelle ab Phase 2).** Großes öffentliches Archiv, über 400.000 Module in über 300 Formaten, verteilt per FTP über den Hauptserver und Mirrors, gepflegt von Coma (Daniel Johansson).
   - Es gibt keine API. Recherchiere zu Beginn von Phase 2, welche Datei-Indizes Modland aktuell anbietet (z. B. eine Gesamtliste aller Dateien) und welche Mirrors HTTPS anbieten. Nichts Ungeprüftes fest einbauen.
   - Die App lädt den Index höchstens einmal pro Woche, speichert ihn als durchsuchbare Datenbank (Room/SQLite mit Volltextsuche) und fragt keine Verzeichnisse live ab.
   - Struktur: Format → Komponist → Modul. Radio = Zufallsauswahl aus dem lokalen Index.
   - Downloads bevorzugt über Mirrors. Bei FTP eine freie Bibliothek (z. B. Apache Commons Net).
2. **AMP (offen).** Der Weg über 4champ.net ist entfallen. Eine AMP-Quelle gibt es erst, wenn Nutzung und Schlüssel direkt mit dem AMP-Team vereinbart und in `docs/PERMISSIONS.md` dokumentiert sind.
3. **Lokale Dateien** über das Storage Access Framework, immer verfügbar.

Weitere Quellen (z. B. The Mod Archive) sind später möglich. Dort vergibt der Administrator API-Schlüssel nur einzeln auf Anfrage, daher erst nach Absprache.

## Feste Regeln

**Lizenzen**
- Eigener Code unter **GPL-3.0-or-later**. Vorher `COPYING` von UADE prüfen. Ist ein Teil GPL-2.0-only, melden statt weitermachen.
- Ein Bildschirm „Lizenzen“ in der App mit allen Drittbibliotheken.
- Eigenständiges Repo, kein GitHub-Fork. README und `NOTICE` nennen 4champ, AMP und Modland und sagen offen, dass die App mit Claude Code entwickelt wird.

**Technik**
- Kotlin, Jetpack Compose mit Material 3, Coroutines, OkHttp, kotlinx.serialization, Room, Media3 `MediaSessionService` für Hintergrundwiedergabe, Benachrichtigung und Sperrbildschirm.
- Audio: libOpenMPT über JNI, Ausgabe über Oboe oder `AudioTrack`, 48 kHz Stereo.
- `minSdk 26`, `targetSdk 36`, aktuelles Android Gradle Plugin, **NDK r28 oder neuer** (16-KB-Seiten, Pflicht bei Google Play).
- Native Bibliotheken als Git-Submodule, per CMake aus dem Quellcode gebaut. Keine vorkompilierten `.so`-Dateien im Repo.

**Nur freie Abhängigkeiten**
- Keine Google Play Services, kein Firebase, kein Crashlytics, keine Analytics, keine Werbung.
- Vorkompilierte Bibliotheken nur aus Maven Central oder Google Maven, nur mit freier Lizenz.

**Server schonen (gilt für jede Quelle)**
- Eigener User-Agent mit App-Name, Version und Repo-URL. Alle Basis-URLs an einer Stelle konfigurierbar.
- Zwischenspeichern, bei Fehlern mit wachsender Wartezeit erneut versuchen.
- Keine Massendownloads, keine Vorab-Downloads. Das Radio lädt höchstens das nächste Modul vor.

**Bauen und Testen**
- Die Cloud-Sitzung erreicht Modland und die Android-SDK-Downloads womöglich nicht. Deshalb wird in **GitHub Actions** gebaut, und Netzwerkcode wird mit gespeicherten Beispieldaten getestet. Kelbo kann Beispiele liefern (URL im Handy-Browser öffnen, Text kopieren).
- Unit-Tests für Index-Parser, API-Parser und Bibliothek. CI muss grün sein, bevor eine Phase fertig ist.
- **Lokale Builds auf schwacher Hardware** (8 GB RAM) ermöglichen und in `docs/BUILDING.md` beschreiben: nur Kommandozeilen-Tools, kein Android Studio; eine Gradle-Property, die Debug-Builds auf `arm64-v8a` beschränkt; empfohlene Einträge für `~/.gradle/gradle.properties`:
  ```
  org.gradle.jvmargs=-Xmx2g
  org.gradle.workers.max=2
  kotlin.daemon.jvmargs=-Xmx1g
  ```
- Reproduzierbare Builds anstreben, damit F-Droid Kelbos eigene Signatur ausliefern kann.

**Signierung**
- Kelbo erzeugt den Release-Schlüssel nach deiner Schritt-für-Schritt-Anleitung auf seinem Laptop. Er kommt nie ins Repo, nur base64-kodiert als GitHub-Secret, plus Sicherungen auf dem zweiten Laptop und einem USB-Stick.
- Vor dem ersten Release deutlich darauf hinweisen: Geht der Schlüssel verloren, kann die App nie mehr aktualisiert werden.

**Store-Metadaten**
- Beschreibungen, Changelogs und Screenshots im Fastlane-Format unter `fastlane/metadata/android/en-US/` und `de-DE/`.

## Phasen

### Phase 1: Analyse und Gerüst
- 4champ mit Submodulen klonen (`git clone --recursive`) und lesen: `Networking/`, `Scenes/Search`, `Scenes/Radio`, `Scenes/Playlists`, `Data/`, `Replay/`.
- `docs/PORTING_NOTES.md`: jede iOS-Funktion mit Android-Gegenstück, alle API-Felder, Entwurf der `MusicSource`-Schnittstelle.
- Android-Projekt (Gradle Kotlin DSL, Version Catalog), CI-Workflow (Build, Lint, Tests, APK als Artefakt), `LICENSE`, `NOTICE`, README, `docs/BUILDING.md`.
- **Fertig, wenn:** CI grün ist und eine leere App auf dem Testgerät startet.

### Phase 2: Modland durchsuchen, laden, abspielen (MVP)
- Modland-Index recherchieren, laden, als durchsuchbare Datenbank speichern, wöchentlich aktualisieren.
- Suche nach Modulname und Komponist, Durchblättern nach Format und Komponist.
- Download, entpacken falls nötig, in der lokalen Bibliothek speichern.
- Wiedergabe mit libOpenMPT, im Hintergrund mit Benachrichtigung, Sperrbildschirm- und Kopfhörer-Tasten.
- **Fertig, wenn:** Kelbo einen Komponisten findet, ein Modul lädt und es bei ausgeschaltetem Bildschirm hört.

### Phase 3: Bibliothek, Playlists, Radio, AMP-Quelle
- Favoriten, mehrere Playlists, Sortierung, Suche in lokalen Modulen, Import lokaler Dateien, Teilen.
- Radio: Zufall aus dem gesamten Index, eigene Favoriten, Verlauf.
- AMP-Quelle nur nach Vereinbarung mit dem AMP-Team (siehe „Musikquellen“).
- **Fertig, wenn:** das Radio 30 Minuten ohne Aussetzer läuft.

### Phase 4: Sonderformate
- HivelyTracker-Replayer für AHX und HVL.
- UADE für DSS, PreTracker und andere: in `uade-ios` nachsehen, wie UADE ohne eigenen Prozess läuft, und das für das NDK übernehmen.
- Lizenzen der UADE-Player-Dateien prüfen. Akzeptiert F-Droid sie nicht, kommt UADE in eine eigene Build-Variante nur für GitHub und IzzyOnDroid.
- **Fertig, wenn:** ein DSS- und ein PreTracker-Modul sauber spielen.

### Phase 5: Design und Feinschliff
- Eigener Name, Icon und Farben nach Kelbos Entwürfen; adaptives Icon und Theme.
- „Läuft gerade“-Ansicht mit Visualizer (Spektrum aus dem PCM-Puffer).
- Deutsch und Englisch, Dark Mode, Tablet-Layout, TalkBack.
- Optional: Android Auto.
- **Fertig, wenn:** sich Store-Screenshots machen lassen, die Kelbo gefallen.

### Phase 6: Veröffentlichung
Reihenfolge: GitHub Releases → F-Droid → Google Play.

- **IzzyOnDroid: fällt weg** (Stand 18.11.2026). Die Aufnahmeregeln lehnen Apps ab, deren Code ganz oder teilweise von KI erzeugt wurde („Vibe-coded apps will be rejected“, https://izzyondroid.org/docs/general/AppInclusionPolicy/). Das trifft dieses Projekt; wir umgehen die Regel nicht.
- **F-Droid:** Build komplett aus dem Quellcode mit freien Werkzeugen, keine proprietären Bibliotheken, eigene App-ID, aktive Wartung. Antrag als Merge-Request in `fdroiddata` auf GitLab. Nur die **free**-Variante; „full“ bleibt auf GitHub.
  - KI-Code: F-Droid hat keine Regel dazu (Inclusion Policy, Stand 11/2026). Im Forum wird ein Filter oder Anti-Feature für KI-Apps diskutiert, beschlossen ist nichts. Wir legen die KI-Hilfe offen (README, NOTICE) und rechnen damit, dass ein späteres Etikett die App trifft.
  - Anti-Features nach heutigem Stand: keine. Module werden erst auf Wunsch geladen und stecken nicht in der App; Modland ist ein offenes Dateiarchiv, kein proprietärer Dienst. Mit AMP-Quelle eventuell „NonFreeNet“ (Hinweis, kein Ausschluss).
  - Signatur: Ist der Build reproduzierbar, liefert F-Droid die APK mit Kelbos eigener Signatur aus; dann passen GitHub- und F-Droid-Version zueinander. Sonst signiert F-Droid selbst, und ein Wechsel zwischen beiden heißt neu installieren.
  - Vor dem Antrag: endgültiger Name und App-ID, Release-Schlüssel, getaggte Version ohne „pre“.
  - Stand 18.11.2026: Name, Schlüssel und Version 1.2.0 sind fertig, das Rezept liegt in `docs/fdroid/`. F-Droid erwartet mindestens 3 Monate aktive Entwicklung (Inclusion Policy, Qualität Punkt 6); der Antrag wird deshalb etwa Mitte Februar 2027 gestellt.
- **Google Play:** Konto für einmalig 25 USD mit Ausweis. Neue persönliche Konten brauchen einen geschlossenen Test mit mindestens 12 Testern über 14 Tage am Stück. Upload als AAB, `targetSdk 36`, 16-KB-kompatible native Bibliotheken, Datenschutzerklärung als Link, Formular zur Datensicherheit, Fragebogen zur Altersfreigabe.
- **Google-Entwicklerverifizierung:** ab 2027 weltweit auch außerhalb des Play Store. Mit Play-Konto lassen sich Paketname und Schlüssel auch für die GitHub-APK registrieren. F-Droid lehnt die Verifizierung ab; die Entscheidung dazu trifft Kelbo 2027.

## Offene Punkte (klärt Kelbo)

- [x] Anfrage zur 4champ.net-API: abgelehnt, die API bleibt der 4champ-App vorbehalten
- [ ] Anfrage an das AMP-Team zu API, Nutzungsregeln und Schlüssel
- [ ] Kurze Info an Coma (Modland) und an das AMP-Team
- [ ] Endgültiger App-Name und Paketname
