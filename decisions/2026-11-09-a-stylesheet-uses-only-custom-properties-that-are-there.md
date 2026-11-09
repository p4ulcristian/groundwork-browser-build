# A stylesheet uses only custom properties that are there

> The user's decision, 2026-11-09: the check lives in browser-build, so every app that builds with it has it. Built the same day, not released. Changes in part [A namespace names its stylesheets](2026-10-17-a-namespace-names-its-stylesheets.md) ("the app's own taste in CSS stays the app's to check": a property nobody sets isn't taste) and [The app passes its log to a watch; a release build has none](2026-10-22-the-app-passes-its-log.md) (a release writes these to standard error under `:warn`).

**Decision:** when the build gathers the stylesheets, every `var(--name)` without a fallback in a named stylesheet must name a custom property that is there:

- **declared or registered by a stylesheet of the build**, any module's: `--name: …` in a rule, or `@property --name { … }`;
- **set by the build's own ClojureScript**, read from its `.cljs` and `.cljc` files in folders: a string that is only the name (`{"--progress" pct}` in a hiccup `:style`, `(.setProperty style "--progress" …)`), a keyword `:--name` (a `:style` key), or a declaration `--name: …` inside a string (an inline style written as text); comments don't count;
- **or named by the app as set elsewhere**, a required setting.

`var(--name, fallback)` is never refused. Comments and quoted strings in a stylesheet are not read. Two new required options, on both builds:

```clojure
:on-undefined-custom-property    :fail        ; or :warn
:custom-properties-set-elsewhere #{}          ; #{"--name" …}: the server's HTML, a stylesheet linked outside the build,
                                              ; an npm package's JavaScript, a name the code builds at run time
```

| | `:fail` | `:warn` |
|---|---|---|
| a release | stops: `:groundwork-browser-build/undefined-custom-properties`, `:uses` each `{:stylesheet :line :custom-property}` | writes the same lines to standard error, builds |
| a watch | the app's log at `:error` | the app's log at `:warn` |

A watch never stops for it: it logs after every compile and every rebundle of a saved stylesheet (`{:groundwork-browser-build/uses […]}`), and the bundle is written as always. Each use is named by the stylesheet's classpath path and the line in the file: `app/orders/order_card.css:7 var(--space-7)`.

**Why:** `var(--name)` naming a property nobody sets is valid CSS. The browser drops the whole declaration when it computes the value, without a word, and the element takes the inherited or initial value: a padding of 0, a header as wide as the page. lightningcss rewrites syntax for the browsers and doesn't look. An app found ten such uses on its main branch after one stylesheet that declared a page gutter was deleted with the page it belonged to; the pages using the gutter lost it silently. browser-build already reads every stylesheet of the build, so it can say which name nobody sets, where, before the page ships.

What the code sets is read from the code, not listed: a property the code stops setting is undefined again at the next compile, which a list would never notice. The list is only for what the build can't see. Whether a missing property stops a release is the app's to say, with no default: an app adopting the check with uses it hasn't fixed yet ships with `:warn`.

**The price:**
- The code scan reads names, not data flow: a string `"--x"` passed to `getPropertyValue` counts as setting `--x`, and a name built at run time (`(str "--" n)`) isn't seen; that name goes in `:custom-properties-set-elsewhere`.
- Declarations count across the whole build, not per page: a property declared only in a module a page doesn't load still counts as there for that page.
- A `--name:` inside `@container style(…)` counts as a declaration.
- A vendored stylesheet whose library sets its properties from JavaScript needs those names written out; the check doesn't read npm packages.
- Every app moving to this version writes the two options out; a watch logs, a release with `:fail` stops, on what it finds.

**Instead of:**
- **only a list of names set from code**: the app writes down what the code already says, and the list goes stale (a name left in it after the code stops setting it hides the next undefined use)
- **reading npm packages' JavaScript too**: megabytes of minified code each compile, any string `"--x"` in it counting, for names an app can write once
- **a check in each app's CI** (a script over the stylesheets): each app writes and keeps its own, and nothing checks an app that has none
- **`:fail` only**: an app with existing uses couldn't ship until every one was fixed
- **stopping a watch, or failing its compile, on `:fail`**: the page wouldn't get the developer's other changes while one property is fixed
- **a release taking `:log` to warn through**: its options come from `clojure -X`'s EDN, where a function can't be written
