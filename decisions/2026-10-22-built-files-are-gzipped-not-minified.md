# Built files are gzipped, not minified; no node in the build

> **Changed in part** by [The gzipped copies are GZIPOutputStream's, at the build's level](2026-10-27-the-gzipped-copies-are-gzipoutputstreams.md) (2026-10-27, v0.5.2): the copies are written by GZIPOutputStream, its Deflater set to `:gzip-level` through a subclass, not by hand; the bytes are the same.

> **Changed in part** by [Stylesheets are rewritten for the app's browsers by a pinned lightningcss program](2026-10-23-stylesheets-are-rewritten-for-the-apps-browsers-by-a-pinned-lightningcss.md) (2026-10-23, built on tidy-up, not released): with `:css`, nesting is flattened and prefixes added, by lightningcss as a downloaded program, still with no node; still nothing minified.

> **Changed in part** by [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) (2026-10-22, v0.1.1): the gzip level is `build-for-release!`'s `:gzip-level`, and the copies are written by hand (`GZIPOutputStream` takes no level).

> Changes in part [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md): no lightningcss, no `:targets` setting.

**Decision:** the release build writes a gzipped copy beside every built file (`main.<hash>.js.gz`, `main.<hash>.css.gz`), with the JVM's own gzip. The server sends the copy, `content-encoding: gzip`, to a browser that says it takes gzip, and the file itself to one that doesn't. The CSS bundle is not minified, and nesting isn't flattened: browsers since 2023 read nesting.

**Why:** once gzipped, a stylesheet's spaces and comments cost almost nothing, so a minifier adds little; gzip needs nothing outside the JVM, so the build stays free of node and npm. Compressed once at build, never per request.

**The price:** a few percent bigger than minified and gzipped; browsers older than 2023 get nesting they can't read; no vendor prefixes. If an app needs older browsers, lightningcss (as a downloaded program, still no npm) is the way to add it.

**Instead of:**
- lightningcss through npm (the Oct 17 plan): node in every build, for a few percent
- ph-css on the JVM: a parser to strip spaces, for the same few percent
- compressing each response as it goes out: the same work on every request
