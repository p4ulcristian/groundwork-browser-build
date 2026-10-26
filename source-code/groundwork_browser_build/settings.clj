(ns groundwork-browser-build.settings
  "browser-build's options, as malli shapes, checked before anything is built.
   Nothing has a default: every key is written out (nil where it is a
   choice), and a missing or unknown one refuses, named, with every other
   problem at once. Both maps are closed."
  (:require
   [groundwork-browser-build.lightningcss :as lightningcss]
   [clojure.string :as str]
   [malli.core :as malli]
   [malli.error :as malli-error]))

(def output-feature-sets
  "What the compiled code may use, in shadow-cljs's words, oldest first.
   :es-unsupported leaves the code as it is written (shadow-cljs's own
   choice when none is given)."
  [:es5 :es2017 :es2018 :es2019 :es2020 :es2021
   :browser-2020 :browser-2021 :browser-2022 :browser-2023 :browser-2024 :browser-2025
   :es-next :es-unsupported])

(defn- find-circle
  "A circle of modules, each depending on the next and the last on the
   first, as [:a :b :a], or nil. Depth first, each module walked once."
  [modules]
  (let [done (volatile! #{})]
    (letfn [(visit [path module-id]
              (cond
                (some #{module-id} path) (conj (vec (drop-while #(not= module-id %) path)) module-id)
                (@done module-id)        nil
                :else (or (some #(visit (conj path module-id) %)
                                (sort (get-in modules [module-id :depends-on])))
                          (do (vswap! done conj module-id) nil))))]
      (some #(visit [] %) (sort (keys modules))))))

(defn find-module-problems
  "What is wrong with how the modules depend on each other, every problem
   at once, a vector of texts, empty for none: a module depending on one
   the build doesn't have; not exactly one root (the module whose
   :depends-on is #{}); modules depending on each other in a circle. With
   one root and no circle, every other module depends on the root,
   directly or through others."
  [modules]
  (let [unknown (into (sorted-map)
                      (for [[module-id {:keys [depends-on]}] modules
                            :let [missing (vec (sort (remove #(contains? modules %) depends-on)))]
                            :when (seq missing)]
                        [module-id missing]))
        roots   (vec (sort (for [[module-id {:keys [depends-on]}] modules :when (empty? depends-on)] module-id)))
        circle  (find-circle modules)]
    (cond-> []
      (seq unknown)  (conj (str "depend on modules the build doesn't have: " (pr-str unknown)))
      (empty? roots) (conj "no root: one module, which every other depends on, has :depends-on #{}")
      (next roots)   (conj (str "more than one root, each with :depends-on #{}: " (pr-str roots) "; one depends on nothing, every other on it"))
      circle         (conj (str "depend on each other in a circle: " (str/join " -> " circle))))))

(def modules-shape
  "Every module of the build, by name, every key written out: each its
   :entries, the namespaces it holds; :depends-on, the modules it needs
   loaded first, #{} for the root; and :start, the function run once when
   its code has loaded (shadow-cljs's :init-fn), or nil for none. One
   module is the root, :depends-on #{}, and every other depends on it,
   directly or through others; a circle is refused (find-module-problems).

   A page starts with any one of them: it and every module it depends on
   are linked and script-tagged at once (groundwork-browser-build.api's
   files-for-page); any other is loaded on demand (load-module!). Whether a
   module comes with the page or on demand is the page's choice, not the
   module's.

     {:base   {:entries [app.browser.base] :depends-on #{} :start nil}
      :sheets {:entries [app.ui.sheets] :depends-on #{:base} :start nil}
      :shop   {:entries [app.shop.boot] :depends-on #{:base :sheets} :start app.shop.boot/start!}
      :site   {:entries [app.site.boot] :depends-on #{:base} :start app.site.boot/start!}}"
  [:and
   [:map-of :keyword
    [:map {:closed true}
     [:entries [:vector {:min 1} :symbol]]
     [:depends-on [:set :keyword]]
     [:start [:maybe :qualified-symbol]]]]
   [:fn {:error/fn (fn [{:keys [value]} _] (str/join "; " (find-module-problems value)))}
    #(empty? (find-module-problems %))]])

(def resolve-shape
  "npm modules the build replaces, by name, {} for none: with a file
   ({:target :file :file \"src/process_shim.js\"}), a global the page
   already has ({:target :global :global \"THREE\"}), another npm package
   ({:target :npm :require \"preact/compat\"}), or nothing (false)."
  [:map-of :string
   [:or
    [:= false]
    [:map {:closed true} [:target [:= :file]] [:file :string]]
    [:map {:closed true} [:target [:= :global]] [:global :string]]
    [:map {:closed true} [:target [:= :npm]] [:require :string]]]])

(def css-shape
  "The CSS step, or nil: each bundle as the files are written. Else
   {:targets \"chrome >= 119, safari >= 16.4, firefox >= 128\"}: the
   browsers the app supports, \"<browser> >= <version>\" queries (or > <=
   <) joined by commas, each browser one lightningcss knows
   (lightningcss/browsers), read by browser-build before lightningcss
   runs (lightningcss/check-targets). Each bundle is rewritten by
   lightningcss for them (nesting flattened, -webkit- prefixes added,
   nothing minified). The program is browser-build's own pin
   (lightningcss/pinned), downloaded once per platform from npm's
   registry into the cache folder and checked against its sha256 when
   downloaded and every time it runs."
  [:maybe [:map {:closed true}
           [:targets [:and
                      [:string {:min 1}]
                      [:fn {:error/fn (fn [{:keys [value]} _] (lightningcss/check-targets value))}
                       #(nil? (lightningcss/check-targets %))]]]]])

(def ^:private build-entries
  [[:modules modules-shape]
   [:files-folder :string]
   [:cache-folder :string]
   [:compile-threads [:int {:min 1}]]
   [:output-feature-set (into [:enum] output-feature-sets)]
   [:resolve resolve-shape]
   [:closure-defines [:map-of :symbol [:or :string :boolean number?]]]
   [:warnings-as-errors [:set :keyword]]
   [:silenced-warnings [:set :keyword]]
   [:css css-shape]
   [:lightningcss-download-timeout-ms [:int {:min 1}]]])

(def ^:private repl-server-shape
  [:maybe [:map {:closed true}
           [:host :string]
           [:port [:int {:min 0}]]]])

(def watch-options-shape
  "start-watching!'s options, every key required.

   The build:
   :modules: every module of the build (modules-shape), the root among
   them: {:base {:entries [app.browser.base] :depends-on #{} :start nil}
   :site {:entries [app.site.boot] :depends-on #{:base} :start
   app.site.boot/start!}}. A page starts with one of them, which comes
   with every module it depends on (files-for-page); the others are
   loaded on demand (load-module!).
   :files-folder: where the files go, the folder the app's server serves at
   /: <files-folder>/js and <files-folder>/css.
   :cache-folder: shadow-cljs's cache (and its port files), \".shadow-cljs\".
   :compile-threads: how many namespaces compile at once.
   :output-feature-set: what the compiled code may use (output-feature-sets).
   :resolve: npm modules replaced (resolve-shape), {} for none.
   :closure-defines: goog-define values the build is compiled with,
   {malli.registry/type \"custom\"}, {} for none.
   :warnings-as-errors: the compiler warnings that stop the build,
   #{:infer-warning}, #{} for none.
   :silenced-warnings: the compiler warnings not shown at all,
   #{:unreachable-code}, #{} for none; none of them may also stop the
   build.
   :css: the CSS step (css-shape): {:targets \"…\"}, each bundle rewritten
   for the app's browsers by browser-build's pinned lightningcss, or nil,
   as the files are written.
   :lightningcss-download-timeout-ms: how long the pinned lightningcss may
   take to download (once per platform, into the cache folder), connecting
   and the whole tarball; past it the build stops,
   :groundwork-browser-build/lightningcss-download-timed-out. Written out with :css
   nil too, which downloads nothing.
   :preloads: namespaces loaded first, in the root module, while watching only
   (development tools: a debugger's), [] for none; a release build has
   none.

   shadow-cljs's server, which open pages connect to for new code:
   :devtools-host: the address it listens on, \"localhost\" (\"0.0.0.0\"
   lets other machines connect, and run code in the JVM through it).
   :devtools-port: its port; a port in use is refused, not moved; 0 picks
   a free one.
   :devtools-url: nil, and a page connects to http://<its own host>:<the
   port>; or the URL it connects to (behind a proxy).
   :nrepl, :socket-repl: nil for none, or {:host :port} (port 0 picks a
   free one) for shadow-cljs's nREPL and socket REPL.

   Hot reload:
   :hot-reload?: a saved namespace's new code is swapped into open pages.
   :reload-strategy: :optimized reloads the changed namespaces and those
   that use them; :full reloads every namespace.
   :hud: what open pages show over themselves while compiling, a set of
   :errors :warnings :progress, #{} for nothing.
   :stylesheet-check-every-ms: how often the named stylesheets are checked
   for a save, which rebundles them and swaps the bundle into open pages.

   :log: the app's log function, (log level event data), made by groundwork-logs:
   a stylesheet no namespace names (:groundwork-browser-build/unnamed-stylesheets) and a
   rebundle that failed (:groundwork-browser-build/bundle-failed) are written to it."
  (into [:map {:closed true}]
        (concat build-entries
                [[:log [fn? {:error/message "should be the app's log function, made by groundwork-logs"}]]
                 [:preloads [:vector :symbol]]
                 [:devtools-host :string]
                 [:devtools-port [:int {:min 0}]]
                 [:devtools-url [:maybe :string]]
                 [:nrepl repl-server-shape]
                 [:socket-repl repl-server-shape]
                 [:hot-reload? :boolean]
                 [:reload-strategy [:enum :optimized :full]]
                 [:hud [:set [:enum :errors :warnings :progress]]]
                 [:stylesheet-check-every-ms [:int {:min 1}]]])))

(def release-options-shape
  "build-for-release!'s options, every key required: the build's
   (:modules :files-folder :cache-folder :compile-threads
   :output-feature-set :resolve :closure-defines :warnings-as-errors
   :silenced-warnings :css :lightningcss-download-timeout-ms, as
   start-watching! takes them; no :preloads, which
   are for development), and
   :source-maps?: a .map beside each file, so the browser shows the
   ClojureScript a failure came from (anyone can read it);
   :gzip-level: how hard the .gz copies are squeezed, 1 (fast) to 9
   (smallest).

   No :log: a release build writes no line of browser-build's (a stylesheet no
   namespace names stops it), and its options come from clojure -X's
   :exec-args, where a function can't be written."
  (into [:map {:closed true}]
        (concat build-entries
                [[:source-maps? :boolean]
                 [:gzip-level [:int {:min 1 :max 9}]]])))

(defn check-data-for-shape
  "Returns the data; throws {:type kind :errors …} with every problem at
   once when it doesn't fit the shape."
  [what kind shape data]
  (when-let [explanation (malli/explain shape data)]
    (let [errors (malli-error/humanize explanation)]
      (throw (ex-info (str what " have problems: " (pr-str errors))
                      {:type kind :errors errors}))))
  data)

(defn- find-warnings-both-silenced-and-stopping
  "A warning can't be silenced and stop the build: {:silenced-warnings
   [\"…\"]}, or nil."
  [{:keys [warnings-as-errors silenced-warnings]}]
  (let [both (sort (filter (set warnings-as-errors) silenced-warnings))]
    (when (seq both)
      {:silenced-warnings [(str "silenced and among :warnings-as-errors: " (vec both))]})))

(defn- check-build-options [shape options]
  (check-data-for-shape "The options" :groundwork-browser-build/bad-options shape options)
  (when-let [errors (find-warnings-both-silenced-and-stopping options)]
    (throw (ex-info (str "The options have problems: " (pr-str errors))
                    {:type :groundwork-browser-build/bad-options :errors errors})))
  options)

(defn check-watch-options [options]
  (check-build-options watch-options-shape options))

(defn check-release-options [options]
  (check-build-options release-options-shape options))
