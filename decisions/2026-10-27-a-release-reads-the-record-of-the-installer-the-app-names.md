# A release reads the record of the installer the app names: npm's or bun's

> Made by Claude for p4ulcristian on 2026-10-27, while porting Iron Rainbow's browser build onto v0.5.0 (Iron Rainbow installs its npm packages with bun, which writes no `node_modules/.package-lock.json`); for the owner to confirm or turn down. Since v0.5.1. Changes in part [A release checks, before compiling, that every npm package it needs is the app's own dependency](2026-10-23-a-release-checks-its-npm-packages-are-the-apps-own.md): its price "npm, not another installer".

**Decision:** `build-for-release!` takes `:package-installer`, required: `:npm` or `:bun`, what put `node_modules` in place. The check before compiling reads that installer's own record, in the folder the build runs in:

- `:npm`: `node_modules/.package-lock.json`, as before: an entry marked `dev`, `devOptional` or `extraneous`, or none, isn't the app's.
- `:bun`: `bun.lock`. A package is the app's when the app's dependencies reach it (`package.json`'s `dependencies` and `optionalDependencies`, as `bun.lock`'s workspace lists them), through each package's `dependencies`, `optionalDependencies` and `peerDependencies`, each found where node finds it, in the nearest `node_modules` up the tree; any other is installed only for development. `bun.lock`'s keys are the installed paths: `react-dom/scheduler` is `node_modules/react-dom/node_modules/scheduler`.

bun's lockfile is read into npm's record's shape (`make-record-from-bun-lock`), and the one check reads either.

**Why:** an app installs with the installer it uses everywhere else; bun's lockfile says what bun installed and from which `package.json` line, so the same question ("is every package the build takes code from the app's own?") has the same answer from it. Choosing by which file is there would be a default nobody wrote.

**The price:** browser-build works out bun's "why" itself (npm's record has it written in); a package reached only as a peer of the app's own is counted as the app's, as npm installs it.

**Instead of:**
- **the app installing with npm for the release** (`package-lock.json` beside `bun.lock`): two lockfiles of one tree, which drift; or npm reorganising bun's tree (on Iron Rainbow's, 180 packages moved)
- **the app writing npm's record from `bun.lock` itself**: a file named as npm's, which npm would trust later
- **the check skipped for bun**: the deploy's missing package found in the deploy again
