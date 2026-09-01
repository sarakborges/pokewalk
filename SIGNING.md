# LiteWalker signing

Distributed APKs must use one stable signing certificate so Android can install newer versions over older ones.

Expected certificate SHA-256 fingerprint:

`C6:B0:8F:BE:A3:5A:93:46:E4:C4:A4:A2:F8:55:6A:1E:00:B7:52:C6:66:E3:4B:49:BA:DF:4B:73:EB:F1:66:DF`

The private keystore must never be committed to this public repository.

The GitHub Actions workflow builds a release APK without an ephemeral debug key and uploads `LiteWalker-v1.1.5-unsigned.apk` for signing outside GitHub.

Before distributing any APK, verify that its signer certificate SHA-256 fingerprint matches the value above. Never distribute the unsigned or runner-debug-signed artifact as the installable app.

Migration note: APKs distributed before version 0.4.6 were signed with ephemeral GitHub Actions debug keys. Existing installs of those builds had to be uninstalled once before installing the first stable-signed build. Future releases must continue using the same stable signing key.
