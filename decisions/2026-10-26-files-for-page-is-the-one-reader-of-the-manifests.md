# files-for-page is the one reader of the build's manifests

> The user's decision, 2026-10-26, from Iron Rainbow's port onto groundwork-3. Since v0.5.0. Builds on [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md). Changes in part [showroom is its own library](2026-10-22-showroom-is-its-own-library.md): a second public namespace, `groundwork-browser-build.page`, its own deps root, which an app's server takes in production.

**What was wrong:** the page shell read the build's manifests itself: groundwork, the composer, has its own reader (its page.clj), and Iron Rainbow's page head would have needed another until it runs on the composer. Three readers of one format, kept in step by hand.

**Decision:** browser-build answers which files a page that starts with a module links:

```clojure
(files-for-page {:files-folder "target/public" :module :flex})
;; watched:  {:stylesheets ["/css/base.css" "/css/ui-sheets.css" "/css/motion.css" "/css/partner-shared.css" "/css/flex.css"]
;;            :scripts     ["/js/base.js" "/js/ui-sheets.js" "/js/motion.js" "/js/partner-shared.js" "/js/flex.js"]}
;; released: the same modules, the hashed names the manifests list
```

Each a served path, in dependency order, the module's and every one it depends on; for a watched build the plain names, for a released one the hashed names. It is the one reader of the manifests: the composer and Iron Rainbow's page head (until it runs on the composer) call it. The files of modules loaded on demand are `load-module!`'s business, not the page's.

**Why:** the build writes the manifests and is the one place that knows their format; a second reader breaks without a word when it changes.

**The price:** the page shell depends on browser-build at run time, in production, where it didn't before (below: only its page half, without shadow-cljs).

**Instead of:**
- **A: Iron Rainbow reading the manifests with its own function until the switch**: three readers of one format, Iron Rainbow's thrown away at the switch

## Settled while building

Choices made while building, not asked:

1. **The page half is its own namespace and its own deps root**, `groundwork-browser-build.page` in `page/` (`page/deps.edn`, malli only). The page shell runs in production, where shadow-cljs must not be: an app takes the page half in `:deps` with `:deps/root "page"`, and the whole library, whose `:paths` hold `page/source-code` too, in its build aliases, where the alias's coordinate replaces it. Weighed: the whole library in production's `:deps` (shadow-cljs's dependencies, ring, jackson and the closure compiler among them, on the server's classpath and resolved against the app's own versions).
2. **`stylesheets-for-load-module`** answers, from the same reader, every module's bundle by name, `{"base" "/css/base.css" …}`, which the page carries for `load-module!`: without it the composer would keep its own reader for that map.
3. **`:module` is a keyword**, as the build's module names are. A module the build doesn't have is refused, `:groundwork-browser-build/unknown-module`, naming those it has; no manifest is `:groundwork-browser-build/not-built`; bad options are `:groundwork-browser-build/bad-options`, every problem at once.
4. **The order is the dependency order** walked from the module, depth first, the manifest's order between modules that don't depend on each other.
5. **Both manifests are read on every call:** the server calls once per module it starts pages with and keeps the answer, as it read the manifests once before. A watch writes the manifests at its first compile, so the server calls after `start-watching!` returns.
6. **Every module in the list is linked, an empty bundle too:** while watching, a stylesheet a module names later is swapped into the open page only through a link it already has.
