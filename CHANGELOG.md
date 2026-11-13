# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.1] - 2026-11-13

### Fixed
- WAV files written by `WaveFile` declared a RIFF size 8 bytes too large; backpatching the data header no longer grows it.
- `WaveFile` rejects 16-bit writes on 24-bit files, and sample counts that are not whole frames. Reusing a `WaveFile` or overwriting an existing file no longer carries over old sizes or stale trailing bytes.
- `RiffFile` write overloads validate byte counts, and the scalar writes use their real widths.
- `Converter` advances past a frame that failed to decode instead of retrying it, reports output write/close failures, refuses to overwrite its own source, and reports cancelled batch entries as not successful.
- `WaveFileReader` bounds `fmt ` and `data` chunk sizes, treats them as unsigned, avoids `int` overflow in `readSamples`, and closes the previous stream on reopen or a failed open.
- `Converter` stops after 1000 consecutive decode errors instead of looping.
- `jlc` exits 0 for `--help` and non-zero when a batch has failures, accepts `-b` anywhere in the arguments, keeps subdirectories in `-o` output paths, does not follow symlinked directories, and honors `--quiet`.

## [1.2.0] - 2026-11-13

### Added
- `Mp3Decoder.skipFrames(int)`, `writeWav(Path)`, `writeWav(OutputStream)` and `getTags()`.
- `Mp3Tags` for ID3v1 and ID3v2.2/2.3/2.4 text fields and cover art, plus `Mp3Info.read(Path)`, `Mp3Info.probe(InputStream, long)` and `Mp3Info.getDuration()`.
- `Mp3Frame.getDurationMs()`, `Mp3Player.awaitTermination(...)`, `Mp3Player.getTags()` and `AdvancedPlayer.awaitTermination(...)`.
- `SampleBuffer.copy()`.
- Tests for `Decoder.decodeNextFrameToPCM` and `Decoder.convertToWav`, and for the modern decoder, tags and players. The modern tests are split into one class per type.

### Fixed
- `Mp3Decoder` and `Decoder.decodeNextFrame` returned empty buffers for every frame after the first, because the layer decoders keep writing into the first output buffer. Frames now carry a snapshot of the decoded samples, so `Mp3Player` plays real audio and the PCM and WAV methods return real data.

### Changed
- **Breaking**: moved the modern API from `javazoom.jl.modern` to `me.tamkungz.jlayer.modern`; `Mp3Player`, `AdvancedPlayer`, `ModernPlaybackListener` and their exceptions now live in `me.tamkungz.jlayer.modern.player`. Update imports when upgrading from 1.1.x. The original `javazoom.jl.decoder`, `converter` and `player` packages are unchanged.
- `Mp3PlayerException` and `AdvancedPlayerException` now extend `Mp3Exception`.
- `Mp3Frame.getSampleCount()` reports the real number of samples instead of at least 1.
- `Mp3Player` no longer lets a throwing listener stop playback (the exception is logged), reports each state change once, and can call `stop()` from a listener without blocking.
- `AdvancedPlayer` probes up to 8 MiB of a stream before rewinding (was 4 KiB), so streams with a large ID3v2 tag can be played; it also records playback failures (`getLastError()`, rethrown by `playAndWait()`), and closes its source when `build()` fails.
- `Mp3Decoder.Builder.outputBuffer(...)` only accepts a `SampleBuffer`.
- Updated README install coordinates to `me.tamkungz:jlayer-custom` and the current version, and removed a leftover merge conflict marker.
- Updated JUnit and Maven plugin versions, and added Java 25 to the CI matrix.
- Hardened GitHub Actions workflows (least-privilege permissions, pinned action SHAs, timeouts, concurrency).
- Replaced the `publish.yml` workflow with `release.yml`: pushing an `X.Y.Z` tag (or running the workflow manually for an existing tag) builds the project and deploys the signed artifacts to Maven Central, and publishes a GitHub release with the matching CHANGELOG section as release notes.
- Refreshed `docs/API.md` to match the current API.

## [1.1.3] - 2026-02-24

### Added
- Introduced incremental (pull-based) decoding support in modern [`Mp3Decoder`](src/main/java/me/tamkungz/jlayer/modern/Mp3Decoder.java).
  - Added `decodeNextFrame()` for single-frame consumption, returning `null` at EOF.
  - Added `decodeUpTo(int)` for bounded batch decoding with argument validation.
  - Enforced single-pass ownership between iterator-based and pull-based decoding APIs.
  - Existing APIs (`iterator()`, `stream()`, and `decodeAsync()`) remain unchanged in signature and behavior.

- Expanded coverage in `ModernMp3Test`.
  - Added tests for incremental decoding, EOF handling, argument validation, and iterator/pull API exclusivity.
  - Verified full pass: 74 tests, 0 failures, 0 errors.

### Fixed
- Corrected ID3v2 size parsing in modern decoding pipeline to use syncsafe integer handling.
- Restored full signed 16-bit PCM clipping range during synthesis.
- Ensured synthesis filter initialization respects the provided EQ argument.
- Improved bitstream exception mapping to surface readable error messages when available.
- Ensured the probe bitstream in modern `AdvancedPlayer` is always closed, and `close()` now stops playback before marking the player closed.

### Changed
- Updated Maven metadata for `1.1.3`.
- Enriched generated JAR manifest with project website and icon metadata.

## [1.1.1] - 2026-02-23

### Fixed
- Prevented metadata probing from corrupting decode/playback state in modern APIs.
  - [`Mp3Decoder.getInfo()`](src/main/java/me/tamkungz/jlayer/modern/Mp3Decoder.java:219) now uses a dedicated probe stream for path-backed decoders, so calling `getInfo()` no longer breaks later frame iteration on the same instance.
  - [`Mp3Player.getInfo()`](src/main/java/me/tamkungz/jlayer/modern/player/Mp3Player.java:261) now probes info via a separate decoder for path-backed sources, preserving the playback decoder state.

- Improved playback lifecycle safety and error propagation in [`Mp3Player`](src/main/java/me/tamkungz/jlayer/modern/player/Mp3Player.java).
  - Enforced single-use semantics after stop/completion in [`play()`](src/main/java/me/tamkungz/jlayer/modern/player/Mp3Player.java:161) to avoid invalid restarts.
  - Hardened stop/join logic in [`stop()`](src/main/java/me/tamkungz/jlayer/modern/player/Mp3Player.java:207) with interrupt fallback.
  - Ensured audio device opens with frame-derived format in [`ensureAudioDeviceOpen()`](src/main/java/me/tamkungz/jlayer/modern/player/Mp3Player.java:348).
  - Improved failure reporting in [`playAndWait()`](src/main/java/me/tamkungz/jlayer/modern/player/Mp3Player.java:411) by surfacing playback errors.

- Replaced custom stream wrapper with JDK wrapper in modern [`AdvancedPlayer`](src/main/java/me/tamkungz/jlayer/modern/player/AdvancedPlayer.java).
  - Switched to `java.io.FilterInputStream` import and removed local duplicate helper class for cleaner, safer stream wrapping.

### Changed
- Updated modern test expectations in `ModernMp3Test` to reflect corrected behavior.
  - `getInfo()` + same-instance iteration now expected to succeed.
  - `Mp3Player.getInfo()` is now validated as compatible with subsequent playback.

## [1.0.2.1] - Initial Release

### Added
- Initial customized version of JLayer with bug fixes and improvements
- Java MP3 decoder with VBR support
- 95% success rate, API compatible with original JLayer

[1.2.1]: https://github.com/TamKungZ/jlayer-custom/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/TamKungZ/jlayer-custom/compare/1.1.3...1.2.0
[1.1.3]: https://github.com/TamKungZ/jlayer-custom/compare/1.1.1...1.1.3
[1.1.1]: https://github.com/TamKungZ/jlayer-custom/compare/1.1.0...1.1.1
[1.0.2.1]: https://github.com/TamKungZ/jlayer-custom/releases/tag/1.0.2.1
