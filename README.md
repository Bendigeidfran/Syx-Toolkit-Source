# Songs of Syx Modding Toolkit

A desktop app for making Songs of Syx mods. It reads the game you have installed and writes only to your local mods folder, never to the game itself or the Steam Workshop.

It's in alpha. A check can miss a problem, and a clean result doesn't prove a mod works in the game. Only running it does.

## Download

Get the latest zip from [Releases](../../releases), unzip it anywhere, and start `Syx Toolkit.exe`. The first run asks for your Songs of Syx folder. Java doesn't need to be installed, because the app brings its own runtime. It runs on Windows only for now.

## What you can do

- Create a local mod, or open one you already have, and browse its files.
- Create and edit many of the game's asset types, including animals, races, resources, crops and rooms. The form uses the game's own tags, shows each default, and checks the values before saving. Tag descriptions come from the [Songs of Syx Modding Handbook](https://bendigeidfran.github.io/Syx-Modding-Handbook/).
- Edit the mod's info.
- Check the text files the game parses for syntax errors.
- Edit a race's portrait, or see its sprites on the map, and watch them redraw as you edit. These are simulations that follow the game's rules.
- Generate normal maps for your sprite sheets, in the style of the game's own sheets of that type.
- Convert a mod's WAV, MP3, AIFF or AU files to Ogg.
- Compare two game versions and see what the update means for a mod's compiled jar, then save that as a report.
- Connect your own AI agent, optionally read-only, so it can do the same work. The toolkit holds no keys and calls no AI service.

## Building from source

You need Windows, PowerShell and a Java 21 JDK, such as [Eclipse Temurin](https://adoptium.net/). Run these from the project root. Each script also reads its folder from an environment variable: `SYX_JDK`, `SYX_JAVA` and `SYX_GAME`.

```powershell
.\scripts\fetch-libs.ps1                                  # once: downloads the libraries into lib\, checksum-verified
.\scripts\build.ps1   -Jdk  "<jdk folder>"                # compiles to out\
.\scripts\test.ps1    -Java "<jdk folder>" -Game "<game>" # runs the tests; without -Game, game-dependent ones skip
.\scripts\run.ps1     -Java "<jdk folder>"                # runs the app from out\
.\scripts\package.ps1 -Jdk  "<jdk folder>"                # builds the app in dist\ and a release zip beside it
```

## Game code and data

This repository and the app contain none of the game's code, data or art. The toolkit reads them from your own installation while it runs. The game's developer has agreed to the toolkit being distributed on that basis.

The files in `manifests/` describe two game versions for the version comparison. They hold class and member names, signatures and one-way hashes only, never game code, and the developer has agreed to them being shared.

## Licence

The toolkit is by Bendigeidfran, under [Creative Commons Attribution-ShareAlike 4.0](LICENSE). The libraries in the app keep their own licences, which are listed in `THIRD-PARTY-NOTICES.txt` in the app folder.

Songs of Syx belongs to its developer and isn't covered by this licence.
