# showroom is its own library

> **Changed in part** by [files-for-page is the one reader of the build's manifests](2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md) (2026-10-26, v0.5.0): a second public namespace, `groundwork-browser-build.page`, in its own deps root (`page/`, malli only), which an app's server takes in production with `:deps/root "page"`; the build, with shadow-cljs, stays out of production.

> **Changed in part** by [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) (2026-10-22, v0.1.1): its `deps.edn` names malli too, which checks the options.

> Changes in part [The browser build is Groundwork's](2026-10-22-the-browser-build-is-groundworks.md) and [A def names its stylesheet, as a query names its .sql file](2026-10-22-a-def-names-its-stylesheet-as-a-query-names-its-sql.md): their names, and how shadow-cljs is kept out of production.

**Decision:** showroom is a library of its own, in its own public repository, under the MIT license; an app or a framework takes it from git by tag. It was two of Groundwork's libraries: `groundwork.server.browser-build` (the build) and `groundwork.browser.styles` (the `use-stylesheet` macro). Now it is the namespace `showroom`:

- **One public namespace, `showroom.api`, in two halves.** The `.clj` half holds the build (`make-build`, `start-watching!`, `stop-watching!`, `build-for-release!`) and the macro `use-stylesheet`; the `.cljs` half only says `(:require-macros [showroom.api])`, so an app's browser code requires `showroom.api` and writes `(styles/use-stylesheet "app/orders/order_card.css")`. Its part is `showroom.stylesheets`, the bundle and its check.
- **shadow-cljs is a real dependency of showroom**, its only one, named in showroom's `deps.edn`. showroom is the build: it is taken only in the aliases an app builds with (development's watch, the deploy's release build, the tests), never in the app's `:deps`. The app's server loads `showroom.api` only when it watches, by `requiring-resolve`. So a production classpath has neither showroom nor shadow-cljs, and nothing on it needs them.
- **It knows nothing of who uses it:** the build id is `:showroom`, the stylesheets a namespace names are kept under `:showroom/stylesheets` in the compiler's data, and what it throws is typed `:showroom/…` (`:showroom/bad-path`, `:showroom/missing-file`, `:showroom/unnamed-stylesheets`).
- What the build does is unchanged: watched in development with the code swapped into open tabs and a saved stylesheet swapped into the open page; a release build with hashed names (`js/main.<hash>.js`, `css/main.<hash>.css`), a `manifest.edn` for each and a gzipped copy of each file, not minified; the stylesheet checks.

**Why:** the build and the stylesheets it bundles are one idea, what the browser is shown, and neither needed anything of Groundwork. One public namespace is enough: the `.clj` half requires shadow-cljs and the ClojureScript analyzer, and it is only ever loaded where they are, by the compiler expanding the macro inside a build, or by the server while it watches. shadow-cljs named by showroom is pinned once, beside the code that calls it, and showroom's tests run on that version. The name: the room customers see, opposite backroom, where the work is done out of sight; showroom builds what the browser shows.

**The price:** a change to showroom is a new tag, and the app moves to it on purpose. An app has to keep showroom out of its `:deps`; nothing but its own aliases stops it. The macro's namespace loads shadow-cljs's api when the compiler first expands it, which costs nothing inside a build, where it is already loaded.

**Instead of:**
- two public namespaces, one for the build and one for the macro: the macro's namespace would not load shadow-cljs's api, but the macro only ever runs inside shadow-cljs, so the split buys nothing and an app learns two names
- shadow-cljs named by the app beside showroom, which would name none: two pins to keep in step, and showroom's tests would prove nothing about the version the app runs
- staying two folders in Groundwork's tree: no app can take them alone, and nothing proves their deps
- the names workshop, facade and browser-build: showroom was chosen over them
