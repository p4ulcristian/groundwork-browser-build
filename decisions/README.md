# browser-build decisions

One file per decision: `YYYY-MM-DD-what-we-decided.md`. The title is the decision. Each file says what we decided, why, and what we chose against. A decision changes by a new file; the old one says so at the top (`Replaced by` or `Changed in part by`), and nothing else in it is rewritten.

browser-build was named showroom until 2026-10-23 ([showroom is renamed browser-build](2026-10-23-showroom-is-renamed-browser-build.md)); the decisions before it keep that name.

All but the last four were made while showroom was two libraries inside Groundwork, a Clojure framework (groundwork.server.browser-build and groundwork.browser.styles, and Groundwork's styles before them); they name its parts (its settings, its page shell, `start-app!`) where they came from. The ones they name that stayed with Groundwork: "Groundwork starts the browser and owns the build", which the build builds on, and "Groundwork is frozen", whose freeze the styles lifted. Groundwork now names both the family of libraries this one belongs to and the composer that puts them together; the decisions that stayed with Groundwork are in the composer's `decisions/`.

## In force

- [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md), changed in part by [A def names its stylesheet](2026-10-22-a-def-names-its-stylesheet-as-a-query-names-its-sql.md) and [Built files are gzipped, not minified](2026-10-22-built-files-are-gzipped-not-minified.md)
- [The browser build is Groundwork's: watched in development, built once by the deploy](2026-10-22-the-browser-build-is-groundworks.md), changed in part by [showroom is its own library](2026-10-22-showroom-is-its-own-library.md) and [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md)
- [Built files are gzipped, not minified; no node in the build](2026-10-22-built-files-are-gzipped-not-minified.md), changed in part by [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md), added to by [A release checks its npm packages are the app's own](2026-10-23-a-release-checks-its-npm-packages-are-the-apps-own.md), changed in part by [Stylesheets are rewritten for the app's browsers by a pinned lightningcss program](2026-10-23-stylesheets-are-rewritten-for-the-apps-browsers-by-a-pinned-lightningcss.md)
- [A def names its stylesheet, as a query names its .sql file](2026-10-22-a-def-names-its-stylesheet-as-a-query-names-its-sql.md), changed in part by [showroom is its own library](2026-10-22-showroom-is-its-own-library.md) and [The app passes its log to a watch](2026-10-22-the-app-passes-its-log.md)
- [showroom is its own library](2026-10-22-showroom-is-its-own-library.md), changed in part by [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) and [files-for-page is the one reader of the build's manifests](2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md)
- [Every value that sets behaviour is a setting; no hidden defaults](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md), changed in part by [The app passes its log to a watch](2026-10-22-the-app-passes-its-log.md), [A build has modules loaded on demand](2026-10-23-a-build-has-modules-loaded-on-demand-each-styled-first.md) and [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md)
- [The app passes its log to a watch; a release build has none](2026-10-22-the-app-passes-its-log.md)
- [A build has modules loaded on demand, each with its own stylesheets, linked before its code](2026-10-23-a-build-has-modules-loaded-on-demand-each-styled-first.md), added to by [A module names its start](2026-10-23-a-module-names-its-start.md), changed in part by [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md) and [files-for-page is the one reader of the build's manifests](2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md)
- [A release checks, before compiling, that every npm package it needs is the app's own dependency](2026-10-23-a-release-checks-its-npm-packages-are-the-apps-own.md), changed in part by [A release reads the record of the installer the app names](2026-10-27-a-release-reads-the-record-of-the-installer-the-app-names.md)
- [A build names its module replacements, its defines and the warnings that stop it; no pass-through](2026-10-23-a-build-names-its-module-replacements-defines-and-warnings-that-stop-it.md), added to by [A build names the warnings it silences, and a watch its preloads](2026-10-23-a-build-names-the-warnings-it-silences-and-the-watch-its-preloads.md)
- [showroom is renamed browser-build](2026-10-23-showroom-is-renamed-browser-build.md)
- [A module names its start](2026-10-23-a-module-names-its-start.md) (built on tidy-up, not released), changed in part by [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md)
- [A build names the warnings it silences, and a watch its preloads](2026-10-23-a-build-names-the-warnings-it-silences-and-the-watch-its-preloads.md) (built on tidy-up, not released)
- [Stylesheets are rewritten for the app's browsers by a pinned lightningcss program](2026-10-23-stylesheets-are-rewritten-for-the-apps-browsers-by-a-pinned-lightningcss.md) (built on tidy-up, not released)
- [A page starts with a module, which comes with every module it depends on](2026-10-26-a-page-starts-with-a-module.md) (v0.5.0)
- [files-for-page is the one reader of the build's manifests](2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md) (v0.5.0)
- [A release reads the record of the installer the app names: npm's or bun's](2026-10-27-a-release-reads-the-record-of-the-installer-the-app-names.md) (v0.5.1)

## Replaced

- [The styles tree's order is cascade layers](2026-10-13-the-styles-trees-order-is-cascade-layers.md), replaced by [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md)
- [The styles tree](2026-10-15-the-styles-tree.md), replaced by [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md)
- [A page loads the stylesheets its code uses](2026-10-15-a-page-loads-the-stylesheets-its-code-uses.md), replaced by [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md)
- [A stylesheet paints only its own room](2026-10-15-a-stylesheet-paints-only-its-own-room.md), replaced by [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md)
- [A class is a namespaced keyword](2026-10-15-a-class-is-a-namespaced-keyword.md), replaced by [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md)
