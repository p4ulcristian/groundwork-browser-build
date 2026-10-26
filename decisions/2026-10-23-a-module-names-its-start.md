# A module names its start

> **Changed in part** by [A page starts with a module](2026-10-26-a-page-starts-with-a-module.md) (2026-10-26, v0.5.0): the build's `:entry` is gone; the root module names its `:start` as every module does, and a page module's start may be its app's start.

> The lean of Iron Rainbow's port (GROUNDWORK-GAPS.md, G2), mechanical: built without a separate ask, 2026-10-23. Adds to [A build has modules loaded on demand, each styled first](2026-10-23-a-build-has-modules-loaded-on-demand-each-styled-first.md). Built on tidy-up, not released: browser-build; the composer's part comes later.

**What was wrong:** 11 of Iron Rainbow's modules have a start: Flex's installs the error reporter, registers Flex's registry and dispatches `[:flex.shell/started]`. A module was `{:entries :depends-on}`; only main had a start (the build's `:entry`).

**Decision:** a module writes out its start, required, nil for none:

```clojure
:modules {:flex  {:entries [app.flex.page] :depends-on #{:main} :start app.flex.boot/start!}
          :admin {:entries [app.admin.page] :depends-on #{:main} :start nil}}
```

It is shadow-cljs's own `:init-fn`: it runs once, when the module's code has loaded, before the page that loaded it is drawn (parlor draws a page whose module loads once `load-module!`'s promise settles).

**Why:** one line, and shadow-cljs does the work.

**The price:** every module writes `:start`.

**Instead of:**
- **each start's work moved into main's start**: every visitor downloads and runs Flex's setup
- **parlor's start taking `:on-module-loaded {:flex [:flex/start]}`**: a second place names the module

## Settled while building

Choices made while building, not asked:

1. **`:start` is a namespaced symbol or nil,** checked with the other options; a module without the key is refused, `:browser-build/bad-options`, naming it.
2. **The function is shadow-cljs's `:init-fn` as it is:** called with no arguments, once per page, never again on a hot reload (shadow-cljs's own rule for `:init-fn`); code that must run again after a save goes in the watch's reload hooks, as for main.
