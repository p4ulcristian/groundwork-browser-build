# The gzipped copies are GZIPOutputStream's, at the build's level

> The user's decision, 2026-10-27 (keep groundwork minimal; replace the gzip written by hand with the JDK's GZIPOutputStream, its level set through a subclass, `:gzip-level` meaning the same). Since v0.5.2. Changes in part [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) and [Built files are gzipped, not minified](2026-10-22-built-files-are-gzipped-not-minified.md): "the copies are written by hand".

**Decision:** each built file's `.gz` copy is written by the JDK's `GZIPOutputStream` (`groundwork-browser-build.gzip`): its header (no name, no time, so the same file gives the same bytes), the deflated bytes, the CRC and the length. Its Deflater is set to `:gzip-level` before the first byte, so `:gzip-level` means what it meant, zlib's level 1 (fast) to 9 (smallest).

`GZIPOutputStream` takes no level; its Deflater is the protected field `def`, which only a subclass reaches. Clojure's way to a subclass that exposes a protected field is gen-class (`:exposes`), and gen-class makes a class only when compiling ahead of time, which a library taken from git as source isn't. So the subclass is made as the namespace loads, by gen-class's own generator (`clojure.core/generate-class`, what `gen-class` runs before it writes the `.class` file), and defined in a class loader of its own. It adds one public method, `deflater`, returning `def`; every other method is `GZIPOutputStream`'s, as its implementation namespace has no functions.

**Why:** the gzip format is the JDK's to write; the copy by hand (a header of ten bytes, a raw Deflater, a CRC32 and the length, little-endian) was the same format, read by nobody else. With GZIPOutputStream the format is the JDK's and only the level is set here.

**The price:** none in the bytes: at every level 1 to 9 the copies are byte for byte the ones v0.5.1 wrote (checked on cljs/core.cljs before the change; the test checks the header and that the body is the file deflated at the level). The subclass leans on `clojure.core/generate-class`, gen-class's own and private to Clojure, not a public api; a Clojure that changed it fails this library's load and its tests, not a build's bytes.

**Instead of:**
- **the copy by hand**: the format written out a second time
- **`GZIPOutputStream` at its own level**: 6 for every app, `:gzip-level` gone
- **reflection on `def`**: refused by the JDK since Java 16 (java.util.zip isn't open), short of `--add-opens` on every build
- **a Java source file compiled before use** (tools.deps' `:deps/prep-lib`): every app taking the library would run a prep step first
- **ahead-of-time compiled gen-class shipped as a `.class` in the repository**: bytes nobody reads, built by hand
