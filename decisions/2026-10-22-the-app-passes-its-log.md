# The app passes its log to a watch; a release build has none

> **Changed in part** by [A stylesheet uses only custom properties that are there](2026-11-09-a-stylesheet-uses-only-custom-properties-that-are-there.md) (2026-11-09, not released): a watch logs `:groundwork-browser-build/undefined-custom-properties` too; a release under `:on-undefined-custom-property :warn` writes those uses to standard error, its one line of browser-build's.

> Changes in part [A def names its stylesheet](2026-10-22-a-def-names-its-stylesheet-as-a-query-names-its-sql.md) (unnamed stylesheets were printed while watching) and [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) (`start-watching!`'s options). Since v0.2.0.

**Decision:** showroom prints nothing. `start-watching!`'s options take the app's log, `(log level event data)` as logbook makes it, as a required key, `:log`, checked by malli as a function with the other options (`:showroom/bad-options`). What it wrote with `println` goes there:

| level | event | data | when |
|---|---|---|---|
| `:warn` | `:showroom/unnamed-stylesheets` | `:showroom/stylesheets`, their classpath paths | a compile while watching finds `.css` files beside the namespaces that none names |
| `:error` | `:showroom/bundle-failed` | `:error` | a check of the named stylesheets, on its thread, failed; it checks again |

The log reaches the bundle hook as one of its arguments in the build, beside `:files-folder`.

`build-for-release!` takes no log. It writes no line of showroom's (a stylesheet no namespace names stops it with an exception, as before), and its options come from `clojure -X`'s `:exec-args`, EDN, where a function can't be written. shadow-cljs's own progress lines ("Compiling …", "Build completed") still go to standard output, as shadow-cljs writes them: they are the compiler's, not showroom's.

showroom does not depend on logbook: it only calls the function it is handed.

**Why:** in development the server's log is the terminal the developer reads, `:readable`; a warning about a forgotten stylesheet belongs in it with everything else the app says, at a level the app can drop. A release has nothing to say but its failure, which the deploy sees as the command failing.

**The price:** `start-watching!` takes one key more (and so does `make-build :watch`, which checks the same options); every caller of v0.1 changes.

**Instead of:**
- `:log` on `build-for-release!` too: required, it would have nothing to write, and `clojure -X:browser-release` couldn't pass it
- a symbol naming the log function, resolved by showroom: an app would write a var for its log just for the build
- shadow-cljs's own lines routed into the log: shadow-cljs logs through its own setup, for every build in the JVM, not per build
