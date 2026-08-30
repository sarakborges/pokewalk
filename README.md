# PokeWalk

Android app for gradually writing walking distance and step records to Health Connect.

Current baseline: **v0.4.9** (`versionCode 13`).

The current Health Connect writer intentionally mirrors the pre-v0.4.5 behavior as closely as possible: it writes only `DistanceRecord` and `StepsRecord` in one-minute chunks, using a fresh random UUID per run and `Metadata.activelyRecorded(Device.TYPE_PHONE)`.

## Build

The GitHub Actions workflow builds an unsigned release APK. Distributed APKs must be signed with the stable certificate documented in [`SIGNING.md`](SIGNING.md).

## Project

- Android min SDK: 28
- Android target/compile SDK: 36
- Health Connect: `androidx.health.connect:connect-client:1.1.0`
- Package: `com.example.pokewalklite`
