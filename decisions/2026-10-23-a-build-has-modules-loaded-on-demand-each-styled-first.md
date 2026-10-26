# A build has modules loaded on demand, each with its own stylesheets, linked before its code

> **Changed in part** by [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md) (2026-10-26, v0.5.0): `main` and `:entry` are gone; the build is its `:modules`, one the root (`:depends-on #{}`), and a page starts with any one of them, which comes with every module it depends on, linked and script-tagged at once; `load-module!` loads the others on demand. A stylesheet several modules name is in the last module, in load order, that each of them is or depends on, not the first one's. And by [files-for-page is the one reader of the build's manifests](2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md) (2026-10-26, v0.5.0): the page shell lists what `files-for-page` answers, and hands `load-module!` what `stylesheets-for-load-module` answers.

> **Added to** by [A module names its start](2026-10-23-a-module-names-its-start.md) (2026-10-23, built on tidy-up, not released): each module writes out `:start`, its shadow-cljs `:init-fn`, nil for none.

> **Changed in part** by [The family and its composer are groundwork](https://github.com/p4ulcristian/groundwork/blob/Starting-from-the-bottom/decisions/2026-10-23-the-family-and-its-composer-are-groundwork.md) (2026-10-23): manor, the family, and house, the composer, are both named groundwork now.

> The user's decision, 2026-10-23 (house's "routing extras": loading on demand). Since v0.3.0. Carries over house's "A build can have several modules" and Groundwork v0.5.0's `groundwork.browser.loader`. Changes in part [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md): the build's options gain `:modules`.

**Decision:**

- **The build's options gain `:modules`**, required, `{}` for none: the modules loaded on demand, besides `main` (the entry's, which the page loads), each its `:entries` (namespaces) and `:depends-on` (the modules it needs first, `main` or another).

  ```clojure
  :modules {:admin {:entries [app.admin.page] :depends-on #{:main}}}
  ```

- **shadow-cljs's module loader is always on** (`:module-loader true`): a release build writes each module's hashed file and puts their addresses into `main`, so the page needs nothing more; while watching, shadow-cljs knows them.
- **Each module has its own stylesheet bundle**: the stylesheets its namespaces named, in the order they are found; one named in two modules is in the first one's. Watching, `css/<module>.css`; released, `css/<module>.<hash>.css`, the manifest listing each with its `:module-id`. `main`'s is the page's, as before.
- **`load-module!`, in the browser** (`showroom.api`'s ClojureScript half): `(load-module! stylesheets-by-module module-name)`, a promise. It loads the module and those it depends on through the module loader, as script tags, and links their bundles (addresses the app hands in, from the page) first; the code runs once they are in. A bundle that fails to load doesn't hold the code back.

**What stays fixed, and why:** script tags, never code fetched and evaluated: a content security policy without `eval` allows them, and the other way fails without a word. The bundle names, `<module>.css` and `<module>.<hash>.css`: what the app's server reads, as `main`'s before.

**Why:** an app with several areas (a shop's admin, an editor) shouldn't make every visitor download all of it. A module's styles come with it, not in `main`'s bundle, or the split saves only half; and linked before its code, so its screen never shows unstyled for a moment.

**The price:** every build carries the module loader (closure's module manager), even with `{}`. The release options gain a key every app writes. The page's own module is `main` only: a page opened fresh on a module's address loads `main`, then the module (the app shows a placeholder meanwhile).

**Instead of:**
- the modules as a free map of shadow-cljs options: what shadow-cljs reads unchecked is behaviour the app can't see
- one stylesheet bundle for every module: every visitor downloads every area's styles
- the module loader only when `:modules` isn't empty: `load-module!` would fail at run time in an app that has none, instead of loading nothing
- the page shell listing a module's scripts for a page opened fresh on it: the shell would need each page's module and the loader's state set by hand
