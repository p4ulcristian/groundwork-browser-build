# browser-build

What the browser shows, built, for a Clojure app with ClojureScript in the
browser. **The build:** shadow-cljs, given a build browser-build writes as data;
the app gives its modules, one the root and every other depending on it, and
every option. **The page:** a page starts with any one module, which comes
with every module it depends on, linked and script-tagged at once; the others
are loaded on demand, each styled before its code. In development it
is watched in the server's own JVM: compiled now, again on every save, the
new code swapped into open tabs. For the deploy it is built once: hashed
names, a manifest, a gzipped copy of each file. **The stylesheets:** a
namespace names each stylesheet it needs, as a query names its `.sql` file;
the build gathers them into a bundle per module, in the order the namespaces
are found, and checks that none beside the code is left unnamed. **The CSS
step:** with `:css`, each bundle is rewritten by the lightningcss program browser-build pins
program for the browsers the app supports (nesting flattened, `-webkit-`
prefixes added, nothing minified); with `:css nil`, it is written as the
files are.

```clojure
io.github.p4ulcristian/groundwork-browser-build {:git/tag "v0.4.5" :git/sha "268e20f"}
```

Two halves, two public namespaces. **The build**, `groundwork-browser-build.api`,
with a `.clj` half (the build, the macro) and a `.cljs` half (for the
browser's code to require), its parts `groundwork-browser-build.stylesheets`
and `.settings`: needs shadow-cljs, malli and jsonista (and, with `:css`, the
lightningcss program it downloads itself; no node), and only to build: take
the whole library in the aliases you build with, never in `:deps`. **The
page half**, `groundwork-browser-build.page`, its own deps root (`page/`,
malli only): which files a page links, read from the build's manifests; the
app's server runs it in production, so it takes this half in `:deps`, and
production runs without shadow-cljs.

```clojure
;; deps.edn: the page half, which the server runs
:deps {io.github.p4ulcristian/groundwork-browser-build {:git/tag "v0.4.5" :git/sha "268e20f" :deps/root "page"}}
```

## The api

| name | does |
|---|---|
| `start-watching!` `stop-watching!` | development: watch the build in this JVM; code and a saved stylesheet are swapped into open pages; `js/<module>.js`, `css/<module>.css` and a manifest for each, written at the first compile; browser-build's messages go to the app's `:log` |
| `build-for-release!` | the deploy: `js/<module>.<hash>.js`, `css/<module>.<hash>.css`, `manifest.edn` for each kind, and `file.gz` beside each; stops on a stylesheet no namespace names, and, before compiling, on an npm package it needs that isn't installed as one of package.json's `dependencies` (not `devDependencies`) |
| `make-build` | the shadow-cljs build as data, `:watch` or `:release`: the `:modules`, one the root (`:depends-on #{}`) and every other depending on it, directly or through others, each with its `:start` run once its code has loaded ([why](decisions/2026-10-26-a-page-starts-with-a-module.md)); files in `<files-folder>/js`, served at `/js`; each module's stylesheet bundle in `<files-folder>/css` |
| `files-for-page` | `.page`, on the server: the files a page that starts with a module links, `{:stylesheets [...] :scripts [...]}`, the module's and every one it depends on, in dependency order, as served paths; plain names watched, hashed names released; the one reader of the manifests ([why](decisions/2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md)) |
| `stylesheets-for-load-module` | `.page`, on the server: every module's bundle by name, `{"base" "/css/base.css" …}`, for the page to hand `load-module!` |
| `load-module!` | in the browser: a module of `:modules` the page didn't start with, its stylesheets linked first, then its code, as script tags; a promise |
| `use-stylesheet` | a macro, in the browser's code: names a stylesheet by its classpath path; a missing file stops the build at that line |

```clojure
;; deps.edn: the build aliases, never :deps
:browser-build   {:extra-deps {io.github.p4ulcristian/groundwork-browser-build {:git/tag "v0.4.5" :git/sha "268e20f"}}}
:browser-release {:extra-deps {io.github.p4ulcristian/groundwork-browser-build {:git/tag "v0.4.5" :git/sha "268e20f"}}
                  :exec-fn    groundwork-browser-build.api/build-for-release!
                  :exec-args  {:modules            {:base {:entries [app.browser.base] :depends-on #{} :start nil}   ; the root
                                                     :shop {:entries [app.shop.boot] :depends-on #{:base} :start app.shop.boot/start!}
                                                     :site {:entries [app.site.boot] :depends-on #{:base} :start app.site.boot/start!}
                                                     :admin {:entries [app.admin.page] :depends-on #{:shop} :start nil}}   ; :start nil for none
                               :files-folder       "target/public"
                               :cache-folder       ".shadow-cljs"   ; shadow-cljs's cache
                               :compile-threads    4
                               :output-feature-set :es2020          ; what the browsers must understand
                               :resolve            {}               ; npm modules replaced: {"process" {:target :file :file "src/process_shim.js"}}
                               :closure-defines    {}               ; goog-defines: {malli.registry/type "custom"}
                               :warnings-as-errors #{}              ; warnings that stop the build: #{:infer-warning}
                               :silenced-warnings  #{}              ; warnings not shown: #{:unreachable-code}
                               :css                nil              ; or the CSS step, below
                               :lightningcss-download-timeout-ms 60000   ; the pinned program's download, once per platform
                               :source-maps?       false            ; a .map beside each file, readable by anyone
                               :gzip-level         9}}              ; the .gz copies, 1 (fast) to 9 (smallest)

;; the app's server, in development only: loaded when it watches
(def watching ((requiring-resolve 'groundwork-browser-build.api/start-watching!)
               {:log                       log             ; the app's (groundwork-logs's): an unnamed stylesheet, a failed rebundle
                :modules                   modules         ; as the release's
                :files-folder              "target/public"
                :cache-folder              ".shadow-cljs"
                :compile-threads           4
                :output-feature-set        :es2020
                :resolve                   {}
                :closure-defines           {}
                :warnings-as-errors        #{}
                :silenced-warnings         #{}
                :css                       nil             ; or the CSS step, below
                :lightningcss-download-timeout-ms 60000
                :preloads                  []              ; development only: namespaces loaded first, in the root
                :devtools-host             "localhost"     ; where open pages connect for new code; it can run code in this JVM
                :devtools-port             9630            ; a port in use is refused; 0 picks a free one
                :devtools-url              nil             ; nil: the page's own host and that port
                :nrepl                     nil             ; or {:host "localhost" :port 0}
                :socket-repl               nil
                :hot-reload?               true            ; saved code swapped into open pages
                :reload-strategy           :optimized      ; or :full, every namespace
                :hud                       #{:errors :warnings :progress}
                :stylesheet-check-every-ms 300}))

;; the deploy, before the app starts:
;;   clojure -X:browser-release

;; the app's server, watching or released (groundwork-browser-build.page :as page): the files a page that starts with :shop links,
;; and each module's bundle for load-module!, read from the manifests the build wrote
(page/files-for-page {:files-folder "target/public" :module :shop})
;; {:stylesheets ["/css/base.css" "/css/shop.css"] :scripts ["/js/base.js" "/js/shop.js"]}, hashed when released
(page/stylesheets-for-load-module {:files-folder "target/public"})
;; {"admin" "/css/admin.css" "base" "/css/base.css" "shop" "/css/shop.css" "site" "/css/site.css"}

;; the browser's code, src/app/browser/orders/order_card.cljs beside order_card.css
(ns app.browser.orders.order-card
  (:require [groundwork-browser-build.api :as styles]))

(def order-card-styles (styles/use-stylesheet "app/browser/orders/order_card.css"))

(defn order-card [order]
  [:div {:class "order-card"} (:order/number order)])
```

The CSS step, for both builds, the app's browsers only:

```clojure
:css {:targets "chrome >= 119, safari >= 16.4, firefox >= 128"}
```

`:targets` is `<browser> <op> <version>` queries (op `>=` `>` `<=` `<`)
joined by commas, each browser one lightningcss knows (browserslist's
names and aliases, `groundwork-browser-build.lightningcss/browsers`, any case).
browser-build reads them itself before lightningcss runs, which aborts
on a query it can't read: anything else (a browser it doesn't know, an
exact version, a browserslist keyword such as `defaults`) is refused
with the options, named. The program is browser-build's own pin,
lightningcss 1.33.0 and its sha256 on each platform (`linux-x64`
`linux-arm64` `darwin-x64` `darwin-arm64` `windows-x64`), as it pins
shadow-cljs: upgrading lightningcss is a browser-build release. It comes
once per platform from npm's registry (the `lightningcss-cli-<platform>`
tarball, read on the JVM), within `:lightningcss-download-timeout-ms`, is
kept in the cache folder, `<cache-folder>/lightningcss/<version>/<platform>/`,
and is checked when downloaded and every time it runs; a platform the
pin doesn't name (`:groundwork-browser-build/lightningcss-no-platform`), a download
past its time (`:groundwork-browser-build/lightningcss-download-timed-out`), a
checksum that differs (`:groundwork-browser-build/lightningcss-checksum`) or
lightningcss's own refusal (`:groundwork-browser-build/lightningcss-failed`) stops
the build, named. Each file
in a bundle follows a `/*! path */` comment naming it, which lightningcss
keeps ([why](decisions/2026-10-23-stylesheets-are-rewritten-for-the-apps-browsers-by-a-pinned-lightningcss.md)).

browser-build prints nothing: while watching, a stylesheet no namespace names
(`:groundwork-browser-build/unnamed-stylesheets`) and a failed rebundle
(`:groundwork-browser-build/bundle-failed`) go to the app's log, `(log level event data)` as
[groundwork-logs](https://github.com/p4ulcristian/groundwork-logs) makes it, under `:log`. A
release build takes none: it stops on what a watch warns about
([why](decisions/2026-10-22-the-app-passes-its-log.md)).

Every option is required: a missing or unknown one is refused, named, with
every other problem at once, before anything is built. shadow-cljs reads no
`shadow-cljs.edn`: browser-build hands it its whole configuration. What stays
fixed, and why, is in
[the decision](decisions/2026-10-22-every-value-that-sets-behaviour-is-a-setting.md).

A page starts with one module: it and every module it depends on are linked
and script-tagged at once, each after those it depends on; which modules come
with the page and which load on demand is the page's choice, not the
module's (`:sheets` can come with a shop page and load on demand on a site
page). A stylesheet several modules name is in the bundle of the last module,
in load order, that each of them is or depends on. The page shell lists what
`files-for-page` reads from the manifests (`js/manifest.edn`,
`css/manifest.edn`), so the page and the build can't disagree; hashed files
can be cached for ever.

## Tests

```sh
clojure -X:test     # release-builds small builds (the bundle, a missing and an unnamed stylesheet,
                    # the CSS step, several page modules and on-demand ones: the files each page
                    # links), the refusals, and one real watch: compiled, the files a page links,
                    # a saved stylesheet and namespace; downloads lightningcss 1.33.0 from npm's
                    # registry, so needs the network
```

Why it is the way it is: [`decisions/`](decisions/README.md).

MIT license.
