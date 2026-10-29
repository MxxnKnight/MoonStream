# MoonStream release keystore

- File: `moonstream.jks` (gitignored — never commit it)
- Alias: `moonstream`
- Key: RSA 2048, self-signed, SHA256withRSA, valid 25 years (until ~2051)
- Fingerprint (SHA-256): FD:88:03:AF:4B:22:A8:8E:60:B2:C2:CA:F1:18:F9:54:91:C0:A1:07:9F:56:3A:46:5F:91:7F:A0:B3:36:20:8B

The store/key passwords live ONLY in `../keystore.properties` (project root,
gitignored, file mode 600). `app/build.gradle` reads that file to sign release
builds. If the file is absent, release builds are unsigned.

OTA updates (Phase 5) MUST be signed with this same keystore, or Android will
refuse to install them over the existing app. Back up `moonstream.jks` +
`keystore.properties` somewhere safe (NOT in git).
