(ns groundwork-browser-build.page
  "Which files a page links, read from the manifests the build wrote:
   the one reader of them. A page starts with one module of the build's
   :modules; it and every module it depends on are linked (their
   stylesheet bundles) and script-tagged (their code) at once, each after
   those it depends on. Every other module is loaded on demand, by
   groundwork-browser-build.api's load-module!, which is handed each
   module's bundle (stylesheets-for-load-module).

     (files-for-page {:files-folder \"target/public\" :module :shop})
     ;; watched:  {:stylesheets [\"/css/base.css\" \"/css/sheets.css\" \"/css/shop.css\"]
     ;;            :scripts     [\"/js/base.js\" \"/js/sheets.js\" \"/js/shop.js\"]}
     ;; released: the hashed names, \"/css/base.3F9A….css\", \"/js/base.8C1D….js\"

     (stylesheets-for-load-module {:files-folder \"target/public\"})
     ;; {\"base\" \"/css/base.css\" \"sheets\" \"/css/sheets.css\" …}, every module's

   Both read <files-folder>/js/manifest.edn and css/manifest.edn, which a
   watch writes at its first compile (the plain names) and a release
   build writes (the hashed names); no manifest is
   :groundwork-browser-build/not-built. Each call reads them: an app's
   server calls once per module it starts pages with and keeps the
   answer. The paths are where the app's server serves them, the files
   folder at /.

   Loaded without shadow-cljs: an app's server takes this half in
   production (deps.edn, :deps/root \"page\"), and the whole library only
   in its build aliases."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [malli.core :as malli]
   [malli.error :as malli-error]))

(def page-options-shape
  "files-for-page's options, every key required: :files-folder, the
   folder the build wrote into (the build's :files-folder); :module, the
   module the page starts with, one of the build's :modules."
  [:map {:closed true}
   [:files-folder :string]
   [:module :keyword]])

(def load-module-options-shape
  "stylesheets-for-load-module's options, every key required:
   :files-folder, the folder the build wrote into."
  [:map {:closed true}
   [:files-folder :string]])

(defn- check-options [shape options]
  (when-let [explanation (malli/explain shape options)]
    (let [errors (malli-error/humanize explanation)]
      (throw (ex-info (str "The options have problems: " (pr-str errors))
                      {:type :groundwork-browser-build/bad-options :errors errors}))))
  options)

(defn- read-manifest
  "The manifest the build wrote into <files-folder>/<kind>, each entry
   {:module-id :output-name …}; none stops, named: build first."
  [files-folder kind]
  (let [manifest (io/file files-folder kind "manifest.edn")]
    (when-not (.exists manifest)
      (throw (ex-info (str "No browser build in " files-folder " (no " kind "/manifest.edn): watch it or release-build it first")
                      {:type :groundwork-browser-build/not-built :files-folder files-folder :manifest (str manifest)})))
    (edn/read-string (slurp manifest))))

(defn- list-modules-loaded-with
  "The module and every module it depends on, directly or through others,
   each after those it depends on; the manifest's order between
   modules that don't depend on each other."
  [js-manifest module-id]
  (let [by-id (into {} (map (juxt :module-id identity)) js-manifest)
        order (into {} (map-indexed (fn [i {:keys [module-id]}] [module-id i])) js-manifest)]
    (letfn [(visit [loaded module-id]
              (if (some #{module-id} loaded)
                loaded
                (conj (reduce visit loaded (sort-by order (get-in by-id [module-id :depends-on])))
                      module-id)))]
      (visit [] module-id))))

(defn files-for-page
  "The files a page that starts with the module links, {:stylesheets
   [...] :scripts [...]}: the module's and every one it depends on, each
   after those it depends on, as served paths (/css/…, /js/…), the plain
   names of a watched build or the hashed names of a released one, as the
   manifests list them. The options are page-options-shape's, checked; a
   module the build doesn't have is :groundwork-browser-build/unknown-module,
   naming those it has."
  [{:keys [files-folder module] :as options}]
  (check-options page-options-shape options)
  (let [js-manifest  (read-manifest files-folder "js")
        css-by-id    (into {} (map (juxt :module-id :output-name)) (read-manifest files-folder "css"))
        known        (mapv :module-id js-manifest)]
    (when-not (some #{module} known)
      (throw (ex-info (str "The build has no module " (pr-str module) "; it has " (pr-str (vec (sort known))))
                      {:type :groundwork-browser-build/unknown-module :module module :modules (vec (sort known))})))
    (let [loaded (list-modules-loaded-with js-manifest module)
          js-by-id (into {} (map (juxt :module-id :output-name)) js-manifest)]
      {:stylesheets (mapv #(str "/css/" (css-by-id %)) loaded)
       :scripts     (mapv #(str "/js/" (js-by-id %)) loaded)})))

(defn stylesheets-for-load-module
  "Every module's stylesheet bundle, by the module's name, as served
   paths: what groundwork-browser-build.api's load-module! is handed, so a
   module loaded on demand links its bundle before its code runs.
   {\"base\" \"/css/base.css\" \"admin\" \"/css/admin.css\"} watched, the
   hashed names released. The options are load-module-options-shape's,
   checked."
  [{:keys [files-folder] :as options}]
  (check-options load-module-options-shape options)
  (into (sorted-map)
        (map (fn [{:keys [module-id output-name]}] [(name module-id) (str "/css/" output-name)]))
        (read-manifest files-folder "css")))
