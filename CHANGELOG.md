# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-02-17

### Added
- Buffer overflow protection to [`SampleBuffer`](src/main/java/javazoom/jl/decoder/SampleBuffer.java)
  - Bounds checking in [`append()`](src/main/java/javazoom/jl/decoder/SampleBuffer.java) and [`appendSamples()`](src/main/java/javazoom/jl/decoder/SampleBuffer.java) methods to prevent `ArrayIndexOutOfBoundsException` when buffer capacity is exceeded
  - Samples are now safely dropped when buffer is full instead of causing crashes
- New `javazoom.jl.modern` package as a simplified wrapper API for JLayer

### Fixed
- Race condition in [`AdvancedPlayer`](src/main/java/javazoom/jl/modern/advanced/AdvancedPlayer.java) thread cleanup
  - Increased thread join timeout from 1s to 5s in [`close()`](src/main/java/javazoom/jl/modern/advanced/AdvancedPlayer.java) method to ensure playback thread completes before closing input stream
  - Added thread interruption logic with additional 1s grace period for threads that don't terminate within the timeout
  - Suppressed `JavaLayerException` in [`runPlayback()`](src/main/java/javazoom/jl/modern/advanced/AdvancedPlayer.java) when thrown during normal shutdown (closed/stopped state) to prevent spurious errors
  - Fixes intermittent "Stream closed" `IOException` in `AdvancedPlayerTest`

### Changed
- **BREAKING CHANGE**: Version bumped to 1.1.0

## [1.0.2.1] - Initial Release

### Added
- Initial customized version of JLayer with bug fixes and improvements
- Java MP3 decoder with VBR support
- 95% success rate, API compatible with original JLayer

[1.1.0]: https://github.com/TamKungZ/jlayer-custom/compare/1.0.2.1...1.1.0
[1.0.2.1]: https://github.com/TamKungZ/jlayer-custom/releases/tag/1.0.2.1
