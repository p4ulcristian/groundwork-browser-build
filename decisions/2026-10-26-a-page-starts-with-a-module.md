# A page starts with a module, which comes with every module it depends on

> The user's decision, 2026-10-26, from Iron Rainbow's port onto groundwork-3: its about 30 modules, tuned by hand for size budgets (#597, #971). Since v0.5.0. Changes in part [A build has modules loaded on demand, each with its own stylesheets, linked before its code](2026-10-23-a-build-has-modules-loaded-on-demand-each-styled-first.md) (`main`, the page's one module, is gone; a module is not on demand by itself), [A module names its start](2026-10-23-a-module-names-its-start.md) (the root names its start as every module does; `:entry` is gone) and [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md) (the fixed "one module, `main`"). The files a page links: [files-for-page is the one reader of the build's manifests](2026-10-26-files-for-page-is-the-one-reader-of-the-manifests.md).

**What was wrong:** a build had one module the page loaded, `main` (the build's `:entry`), and `:modules`, each loaded on demand by `load-module!`. Iron Rainbow has a root every page loads (`:base`), a module per app (site, customizer, flex, labs, service-partners, retail-partners, affiliate, playground) that its pages load at once, shared chunks (motion, ui-sheets, partner-shared, glb-viewer) loaded at once by the apps that depend on them and on demand by others (the site loads ui-sheets on demand, through site-sheets), and modules only ever loaded on demand (qr, web-vitals, checkout, flex-maps, …). The library could say only "main, and the rest on demand".

**Decision:**

- **A build is its `:modules`, all of them, one the root.** Each writes out `:entries`, `:depends-on` and `:start` (nil for none). The root is the one module whose `:depends-on` is `#{}`; every other depends on it, directly or through others. `:entry` is gone: the root's `:start` is what the page runs first, and the model is one thing, modules, instead of two, the entry and the modules.

  ```clojure
  :modules {:base       {:entries [app.frontend.base] :depends-on #{} :start nil}
            :ui-sheets  {:entries [ui.overlays.sheet.core] :depends-on #{:base} :start nil}
            :site       {:entries [app.site.frontend.boot] :depends-on #{:base} :start app.site.frontend.boot/start-app!}
            :flex       {:entries [app.flex.frontend.boot] :depends-on #{:base :partner-shared} :start app.flex.frontend.boot/start-app!}
            :site-sheets {:entries [features.site.blocks.frontend.sidebar.view] :depends-on #{:site :ui-sheets} :start nil}
            :qr         {:entries [utils.qr-code.impl] :depends-on #{:base} :start nil}
            …}
  ```

- **A page starts with a module.** The page names one; it and every module it depends on are linked (their stylesheet bundles) and script-tagged (their code) at once, each after those it depends on. Any other module is loaded on demand later by `load-module!`, its stylesheets first, then its code, as before.
- **Coming with the page or on demand is the page's choice, not the module's.** The build doesn't mark a module eager: `:ui-sheets` comes with a flex page, which depends on it, and on demand on a site page, which doesn't. A module whose start is its app's start (`app.flex.frontend.boot/start-app!`) is a page module like any other.
- **Each module keeps its own stylesheet bundle**, as before.

**Why:** a page should download what it shows in one round trip, every file at once, each cached across apps; what only some pages need comes later. The page knows which app it is; the build only knows what depends on what.

**The price:** every app writes its root out as a module (its `:entries`, `:depends-on #{}`, `:start`), and the page names its module. An app moving from v0.4 replaces `:entry app.browser.main/start!` with a root module among its `:modules`, `:main {:entries [app.browser.main] :depends-on #{} :start app.browser.main/start!}` (its other modules keep `:depends-on #{:main}`), and its page shell lists `files-for-page`'s files instead of `main`'s.

**Instead of:**
- **B: one main module, every app loaded on demand by it**: a second round trip before any page shows anything
- **C: a build per app**: shared code copied into every app, nothing cached across apps, eight builds

## Settled while building

Choices made while building, not asked:

1. **The root is found, not named:** the one module with `:depends-on #{}`. A separate `:root` key would say the same thing twice. No root, more than one, a module depending on one the build doesn't have, and modules depending on each other in a circle are refused with the options, `:groundwork-browser-build/bad-options`, every problem at once (`groundwork-browser-build.settings/find-module-problems`).
2. **A module's name is any keyword:** `main` is no longer kept for the entry.
3. **A stylesheet several modules name is in the bundle of the last module, in load order, that each of them is or depends on.** Before, it was in the first one's, which was right only when one loaded after the other; two sibling pages naming the same file would leave it out of the second's page. Now it is where both find it, as shadow-cljs moves a namespace two modules require into a module both load: in a chain, the first, as before.
4. **The watch writes `css/manifest.edn` too, with the plain names:** the page's files are read the same way from a watched build and a released one.
