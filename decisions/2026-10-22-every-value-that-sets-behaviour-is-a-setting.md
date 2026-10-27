> **Changed in part** by [A build has modules loaded on demand, each with its own stylesheets, linked before its code](2026-10-23-a-build-has-modules-loaded-on-demand-each-styled-first.md) (2026-10-23, v0.3.0): the build's options gain `:modules`, `{}` for none.

# Every value that sets behaviour is a setting; no hidden defaults

> **Changed in part** by [The gzipped copies are GZIPOutputStream's, at the build's level](2026-10-27-the-gzipped-copies-are-gzipoutputstreams.md) (2026-10-27, v0.5.2): the copies are written by GZIPOutputStream, its Deflater set to `:gzip-level` through a subclass, not by hand; the bytes are the same.

> **Changed in part** by [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md) (2026-10-26, v0.5.0): `:entry` is gone and "one module, `main`" isn't fixed: the build is its `:modules`, one the root, each a file in `<files-folder>/js` served at `/js` and a bundle in `<files-folder>/css`.

> **Changed in part** by [The app passes its log to a watch](2026-10-22-the-app-passes-its-log.md) (2026-10-22, v0.2.0): `start-watching!`'s options take the app's log, `:log`, required.

> Changes in part [The browser build is Groundwork's: watched in development, built once by the deploy](2026-10-22-the-browser-build-is-groundworks.md) ("the app gives only its entry"), [Built files are gzipped, not minified](2026-10-22-built-files-are-gzipped-not-minified.md) (the gzip level was the JVM's default) and [showroom is its own library](2026-10-22-showroom-is-its-own-library.md) (shadow-cljs was its only dependency; malli joins it). Since v0.1.1.

**Decision:** every value that sets how showroom builds and watches is written out by the app: a required option, checked by malli before anything is built, the maps closed, a missing or unknown key refused, named, with every other problem at once (`:showroom/bad-options`). No function fills in a value, and no default of shadow-cljs's is left in effect unwritten: showroom hands shadow-cljs its whole configuration, so shadow-cljs reads no `shadow-cljs.edn` and no user config file. What stays fixed is listed below, each with its reason.

- **The build's options**, both maps:

  ```clojure
  [:entry :qualified-symbol]
  [:files-folder :string]
  [:cache-folder :string]                      ; was .shadow-cljs in the working folder
  [:compile-threads [:int {:min 1}]]           ; was the number of processors
  [:output-feature-set (into [:enum] output-feature-sets)]   ; was shadow-cljs's :es-unsupported
  ```

- **`start-watching!`'s options**, those and:

  ```clojure
  [:devtools-host :string]                     ; was 0.0.0.0, every interface
  [:devtools-port [:int {:min 0}]]             ; was 9630, or the next free one
  [:devtools-url [:maybe :string]]             ; nil: the page's own host and the port
  [:nrepl [:maybe [:map {:closed true} [:host :string] [:port [:int {:min 0}]]]]]       ; was on, a random port
  [:socket-repl [:maybe [:map {:closed true} [:host :string] [:port [:int {:min 0}]]]]] ; was on, a random port
  [:hot-reload? :boolean]                      ; was true
  [:reload-strategy [:enum :optimized :full]]  ; was :optimized
  [:hud [:set [:enum :errors :warnings :progress]]]   ; was all three
  [:stylesheet-check-every-ms [:int {:min 1}]] ; was 300
  ```

  shadow-cljs's server listened on every interface, and its page connection can run code in the JVM: the app now says where (`"localhost"` keeps it on the machine). A port in use is refused instead of moved to the next one, so the port in effect is the one written; 0 picks a free one.

- **`build-for-release!`'s options**, the build's and:

  ```clojure
  [:source-maps? :boolean]                     ; was false
  [:gzip-level [:int {:min 1 :max 9}]]         ; was the JVM's default, 6
  ```

  `GZIPOutputStream` takes no level, so the `.gz` copies are written by hand: the same format, no name and no time in the header, so the same file gives the same bytes.

- **`make-build` takes the mode**, `(make-build :watch options)` or `(make-build :release options)`, and the options are checked there.

- **A stylesheet saved right after watching starts is rebundled.** v0.1.0's stylesheet thread took its first look on its own schedule, a virtual thread that could first run after the save: then it took the saved file as the starting point and never rebundled it (3 of 5 runs in a test app, 2026-10-22). Each check now compares with the times the last bundle was made from, read when the bundle was written, and the thread is a platform thread of its own that a failed check doesn't end.

**What stays fixed, and why:**

- **One module, `main`, its files in `<files-folder>/js` and served at `/js`; the bundle in `<files-folder>/css`**: the page shell and the server read these names, and the server serves the files folder at `/`.
- **A release is `:advanced`**: what makes the code small; a failure is read through `:source-maps?`.
- **A release file's name carries the whole hash** (32 hex digits for the code, 16 for the bundle): the server counts any name with enough hex digits as hashed (mailroom's `:hashed-name-min-hex-digits`).
- **While watching, a page finds shadow-cljs at its own host** (`:use-document-host`), unless `:devtools-url` says otherwise, and the bundle is swapped in from `<files-folder>/css` (`:watch-dir`).
- **shadow-cljs compiles on every save** (`:autobuild`), and `start-watching!` returns after the first compile (`:sync`): what watching is.
- **shadow-cljs logs at `:info`, compiles quietly** (`:verbose false`), and keeps its own command-line port on the loopback, a free port: its messages are the developer's, and its command-line tools need the port.
- **No `.nrepl-port` file** in the working folder: an nREPL, when asked for, writes its port into the cache folder.

**Why:** a value left to a default is a value the app doesn't show. A server on every interface that runs code it is sent, a port that moves when another is taken, an nREPL nobody asked for, a cache folder wherever the JVM started, a language level that decides which browsers run the code: each is behaviour someone feels, and each was set by shadow-cljs or showroom, invisible in the app.

**The price:** watching takes 14 options, a release 7; the deploy's command line names each (or the app's `:browser-release` alias holds them in `:exec-args`). An app moving from v0.1.0 writes them out and calls `make-build` with the mode.

**Instead of:**
- defaults the app may override: a good value without thought, but the value in effect is invisible
- a `shadow-cljs.edn` beside the app: shadow-cljs's own place for them, but the page shell and the build would read two places, and the user's config file would still merge in
- the REPLs always on, or always off: some developers connect to shadow-cljs's nREPL, others to the app's own; either way is a choice the app makes
- keeping the 300 ms stylesheet check fixed: a slower disk or a bigger tree wants another number
