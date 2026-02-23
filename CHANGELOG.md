# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.1] - 2026-02-23

### Fixed
- Prevented metadata probing from corrupting decode/playback state in modern APIs.
  - [`Mp3Decoder.getInfo()`](src/main/java/javazoom/jl/modern/Mp3Decoder.java:219) now uses a dedicated probe stream for path-backed decoders, so calling `getInfo()` no longer breaks later frame iteration on the same instance.
  - [`Mp3Player.getInfo()`](src/main/java/javazoom/jl/modern/Mp3Player.java:261) now probes info via a separate decoder for path-backed sources, preserving the playback decoder state.

- Improved playback lifecycle safety and error propagation in [`Mp3Player`](src/main/java/javazoom/jl/modern/Mp3Player.java).
  - Enforced single-use semantics after stop/completion in [`play()`](src/main/java/javazoom/jl/modern/Mp3Player.java:161) to avoid invalid restarts.
  - Hardened stop/join logic in [`stop()`](src/main/java/javazoom/jl/modern/Mp3Player.java:207) with interrupt fallback.
  - Ensured audio device opens with frame-derived format in [`ensureAudioDeviceOpen()`](src/main/java/javazoom/jl/modern/Mp3Player.java:348).
  - Improved failure reporting in [`playAndWait()`](src/main/java/javazoom/jl/modern/Mp3Player.java:411) by surfacing playback errors.

- Replaced custom stream wrapper with JDK wrapper in modern [`AdvancedPlayer`](src/main/java/javazoom/jl/modern/advanced/AdvancedPlayer.java).
  - Switched to `java.io.FilterInputStream` import and removed local duplicate helper class for cleaner, safer stream wrapping.

### Changed
- Updated modern test expectations in [`ModernMp3Test`](src/test/java/javazoom/jl/modern/ModernMp3Test.java) to reflect corrected behavior.
  - `getInfo()` + same-instance iteration now expected to succeed.
  - `Mp3Player.getInfo()` is now validated as compatible with subsequent playback.

## [1.0.2.1] - Initial Release

### Added
- Initial customized version of JLayer with bug fixes and improvements
- Java MP3 decoder with VBR support
- 95% success rate, API compatible with original JLayer

[1.1.1]: https://github.com/TamKungZ/jlayer-custom/compare/1.1.0...1.1.1
[1.0.2.1]: https://github.com/TamKungZ/jlayer-custom/releases/tag/1.0.2.1