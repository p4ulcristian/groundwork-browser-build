# A release checks, before compiling, that every npm package it needs is the app's own dependency

> **Changed in part** by [A release reads the record of the installer the app names](2026-10-27-a-release-reads-the-record-of-the-installer-the-app-names.md) (2026-10-27): the record is the one of the installer the release's `:package-installer` names, npm's or bun's `bun.lock`.

> **Changed in part** by [The family and its composer are groundwork](https://github.com/p4ulcristian/groundwork/blob/Starting-from-the-bottom/decisions/2026-10-23-the-family-and-its-composer-are-groundwork.md) (2026-10-23): manor, the family, and house, the composer, are both named groundwork now.

> The user's decision, 2026-10-23. Since v0.4.0. Adds to [Built files are gzipped, not minified; no node in the build](2026-10-22-built-files-are-gzipped-not-minified.md).

**Decision:** before a release build compiles any ClojureScript, showroom checks the npm packages the build takes JavaScript from: react, react-dom, `process` (which react's code requires, and shadow-cljs bundles for the browser), and whatever else the build resolves.

- **One nobody installed** stops the build where shadow-cljs finds it missing, told again with what to do: *The build needs the npm package "process" (node_modules/react/cjs/react.production.js requires "process"), which isn't installed: add "process" to package.json's "dependencies" (not "devDependencies": the deploy installs without them, npm ci --omit=dev) and run npm install.*
- **One installed only for development** (in `devDependencies`, or there only because a development package brings it, as the shadow-cljs npm package brings `process`) stops the build too, at a build hook after everything is resolved and before anything is compiled (`showroom.npm/check-npm-packages`, stage `:compile-prepare`), naming each package and the same remedy.
- **What is installed, and why, is npm's own record**: `node_modules/.package-lock.json` in the folder the build runs in, where shadow-cljs resolves `node_modules` from; an entry marked `dev`, `devOptional` or `extraneous`, or none, isn't the app's. A build that takes no npm package reads nothing.
- **Watching isn't checked**: development has its development packages.
- **`process.env` isn't defined by hand**: the app lists `process` among its `dependencies`, as house does.

**Why:** the deploy installs the app's packages without development ones; a package that is on the developer's machine only because a development tool brought it (`process`, by the shadow-cljs npm package) builds there and fails in the deploy, with shadow-cljs's message, which names neither the deploy nor the fix. Checked on the developer's machine, before compiling, it fails where it can be fixed, saying how. npm's record knows why each package is there, which package.json alone doesn't (a package react-dom brings is the app's without being listed).

**The price:** the build reads one more file, and needs it: npm, not another installer. A package installed by hand without saving it may pass (npm records it like the app's own).

**Instead of:**
- defining `process.env.NODE_ENV` in the build: one shim for one package; the next package that needs `process` (or `buffer`) fails the same way
- a list of packages showroom knows (react, react-dom, process): the app's other packages would go unchecked
- checking package.json's `dependencies` only: a package react-dom brings isn't listed, and would be refused
