(ns groundwork-browser-build.api
  "The browser's code, built by shadow-cljs from a build browser-build writes as
   data; the app gives its modules (one the root, every other depending
   on it) and every option (groundwork-browser-build.settings: every key
   required, none filled in). And the macro a namespace names its
   stylesheets with, which the build bundles.

     ;; development: compiles, then again on every save, and swaps the new
     ;; code into open tabs (the app's server starts it when it watches)
     (def watching (browser-build/start-watching!
                    {:log log                                  ; the app's (groundwork-logs's)
                     :modules {:base {:entries [app.browser.base] :depends-on #{} :start nil}
                               :shop {:entries [app.shop.boot] :depends-on #{:base} :start app.shop.boot/start!}}
                     :files-folder \"target/public\"
                     :cache-folder \".shadow-cljs\" :compile-threads 4 :output-feature-set :es2020
                     :resolve {} :closure-defines {} :warnings-as-errors #{} :silenced-warnings #{}
                     :css nil :lightningcss-download-timeout-ms 60000 :preloads [] :devtools-host \"localhost\" :devtools-port 9630 :devtools-url nil
                     :nrepl nil :socket-repl nil
                     :hot-reload? true :reload-strategy :optimized :hud #{:errors :warnings :progress}
                     :stylesheet-check-every-ms 300}))
     (browser-build/stop-watching! watching)

     ;; the deploy, once, before the app starts:
     ;;   clojure -X:browser-release :modules … :files-folder '\"target/public\"' …
     ;; writes target/public/js/<module>.<hash>.js and css/<module>.<hash>.css
     ;; for each module, and a manifest for each kind, which
     ;; groundwork-browser-build.page reads: the files a page starting with
     ;; a module links (files-for-page), and each module's bundle for
     ;; load-module! (stylesheets-for-load-module)

     ;; in the app's browser code (groundwork-browser-build.api's ClojureScript half):
     (def order-card-styles (styles/use-stylesheet \"app/orders/order_card.css\"))
     (styles/load-module! {\"admin\" \"/css/admin.3F9A….css\"} \"admin\")   ; its stylesheet, then its code

   shadow-cljs is needed only to build: production starts without it, so
   the app's server loads this namespace only when it watches. shadow-cljs
   reads no shadow-cljs.edn: its whole configuration is written here, from
   the options."
  (:require
   [cljs.analyzer :as analyzer]
   [cljs.env :as env]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server :as shadow-server]
   [shadow.cljs.devtools.server.common :as shadow-common]
   [shadow.cljs.devtools.server.runtime :as shadow-runtime]
   [shadow.runtime.services :as shadow-services]
   [groundwork-browser-build.npm :as npm]
   [groundwork-browser-build.settings :as settings]
   [groundwork-browser-build.stylesheets :as stylesheets])
  (:import
   (java.io OutputStream)
   (java.util.zip CRC32 Deflater DeflaterOutputStream)))

(defn- make-shadow-modules
  "shadow-cljs's modules: each of the build's, its :start as its :init-fn
   (none for nil); the root, :depends-on #{}, is shadow-cljs's default
   module, which carries the module loader."
  [modules]
  (into {}
        (for [[module-id {:keys [entries depends-on start]}] modules]
          [module-id (cond-> {:entries entries :depends-on depends-on}
                       start (assoc :init-fn start))])))

(defn make-build
  "The shadow-cljs build, :watch or :release, from start-watching!'s or
   build-for-release!'s options: the :modules, one the root and every
   other depending on it, each its :start run once its code has loaded. A
   page starts with any one of them, which comes with every module it
   depends on, script-tagged (groundwork-browser-build.page's
   files-for-page); any other is loaded on demand, after those it depends
   on (load-module!, through shadow-cljs's module loader, always on). The
   files in <files-folder>/js, served at /js (the app's server serves the
   files folder at /), and js/manifest.edn listing them. After every
   compile the stylesheets each module's namespaces named become that
   module's bundle in <files-folder>/css, and css/manifest.edn lists
   them; while watching, shadow-cljs swaps a changed bundle into open
   pages. With :css, each bundle is rewritten by browser-build's pinned
   lightningcss first."
  [mode {:keys [modules files-folder cache-folder output-feature-set log resolve closure-defines
               warnings-as-errors silenced-warnings css lightningcss-download-timeout-ms] :as options}]
  (case mode
    :watch   (settings/check-watch-options options)
    :release (settings/check-release-options options))
  (let [{:keys [devtools-url hot-reload? reload-strategy hud preloads]} options]
    (cond-> {:build-id         :browser-build
             :target           :browser
             :output-dir       (str files-folder "/js")
             :asset-path       "/js"
             :modules          (make-shadow-modules modules)
             :module-loader    true
             ;; a release build has no log: it stops where a watch writes a warning
             :build-hooks      [(list `stylesheets/bundle-stylesheets {:files-folder files-folder :log log
                                                                     :css css :cache-folder cache-folder
                                                                     :lightningcss-download-timeout-ms lightningcss-download-timeout-ms})]
             :js-options       {:resolve resolve}
             :compiler-options {:output-feature-set output-feature-set
                                :closure-defines    closure-defines
                                :warnings-as-errors warnings-as-errors
                                :warnings           (zipmap silenced-warnings (repeat false))}}
      (= :release mode)
      (-> (assoc-in [:compiler-options :optimizations] :advanced)
          ;; every npm package it takes code from is the app's own dependency, before compiling
          (update :build-hooks conj (list `npm/check-npm-packages (:package-installer options)))
          ;; the name carries the whole hash of the content (32 hex digits)
          (assoc :module-hash-names true))

      (= :watch mode)
      (assoc :devtools (cond-> {:watch-dir         files-folder
                                :autoload          hot-reload?
                                :reload-strategy   reload-strategy
                                :hud               (if (seq hud) hud false)
                                :use-document-host true
                                :log               true
                                :preloads          preloads}
                         devtools-url (assoc :devtools-url devtools-url))))))

(defn- make-shadow-config
  "shadow-cljs's whole configuration, written out, in place of a
   shadow-cljs.edn (which it would read from the working folder, with the
   user's own config file)."
  [{:keys [cache-folder compile-threads devtools-host devtools-port nrepl socket-repl]} mode]
  (cond-> {:cache-root      cache-folder
           :cache-dir       cache-folder   ; where shadow-cljs looks for another server of this project
           :builds          {}
           :compile-threads compile-threads
           :log             {:level :info}}
    (= :watch mode)
    (assoc :http        {:host devtools-host :port devtools-port :strict true}
           :dev-http    {}
           :nrepl       (if nrepl (assoc nrepl :write-port-file false) false)
           :socket-repl (or socket-repl false))))

(defn start-watching!
  "Starts shadow-cljs in this JVM and watches the build: compiled now,
   again on every save, the new code swapped into open tabs. The options
   are groundwork-browser-build.settings/watch-options-shape's, every key required, checked
   first; browser-build's own messages (a stylesheet no namespace names, a
   rebundle that failed) go to their :log, the app's. Returns once the
   first compile is done."
  [options]
  (let [build (make-build :watch options)]
    (reset! stylesheets/last-bundle nil)
    (shadow-server/start! (make-shadow-config options :watch))
    (try
      (shadow/watch* build {:autobuild true :sync true :verbose false})
      (assoc options :stop-watching-stylesheets
             (stylesheets/start-watching-stylesheets! (:stylesheet-check-every-ms options) (:log options)))
      (catch Throwable e
        (shadow-server/stop!)
        (throw e)))))

(defn stop-watching!
  "Stops watching, and shadow-cljs."
  [{:keys [stop-watching-stylesheets]}]
  (stop-watching-stylesheets)
  (shadow/stop-worker :browser-build)
  (shadow-server/stop!)
  :stopped)

(defn- write-gzipped-copy!
  "file.gz beside the file, squeezed at level (1 to 9), for the server to
   send to browsers that take gzip. Written by hand, as GZIPOutputStream
   does, since it takes no level: the header (no name, no time, so the same
   file gives the same bytes), the deflated bytes, the CRC and the length."
  [file level]
  (let [bytes (java.nio.file.Files/readAllBytes (.toPath (io/file file)))
        crc   (doto (CRC32.) (.update ^bytes bytes))
        int-le (fn [^long n] (byte-array (map #(unchecked-byte (bit-and 0xff (bit-shift-right n (* 8 %)))) (range 4))))]
    (with-open [^OutputStream out (io/output-stream (str file ".gz"))]
      (.write out (byte-array (map unchecked-byte [0x1f 0x8b 8 0 0 0 0 0 0 0xff])))
      (let [deflater (Deflater. (int level) true)
            deflated (DeflaterOutputStream. out deflater)]
        (.write deflated ^bytes bytes)
        (.finish deflated)
        (.end deflater))
      (.write out ^bytes (int-le (.getValue crc)))
      (.write out ^bytes (int-le (alength ^bytes bytes))))))

(defn- gzip-built-files! [files-folder level]
  (doseq [kind ["js" "css"]
          {:keys [output-name]} (edn/read-string (slurp (io/file files-folder kind "manifest.edn")))]
    (write-gzipped-copy! (io/file files-folder kind output-name) level)))

(defn- run-in-own-shadow-runtime
  "Runs (f) with a shadow-cljs runtime of this configuration, made for it
   and stopped after; or in the one running (while watching)."
  [config f]
  (if (shadow-runtime/get-instance)
    (f)
    (let [runtime (-> {:config config}
                      (shadow-services/init (shadow-common/get-system-config config))
                      (shadow-services/start-all))]
      (try
        (shadow-runtime/set-instance! runtime)
        (f)
        (finally
          (shadow-runtime/reset-instance!)
          (shadow-services/stop-all runtime))))))

(defn build-for-release!
  "Compiles the browser's code once, optimized, each module's file named
   with a hash of its content (cached for ever, never stale), the
   manifests listing them (js/manifest.edn, css/manifest.edn, which
   groundwork-browser-build.page reads), and a gzipped copy of
   each (file.gz), which the server sends to browsers that take gzip. The
   CSS isn't minified: gzipped, its spaces and comments cost almost
   nothing. Before any code is compiled, every npm package the build takes
   code from must be installed as the app's own dependency (in
   package.json's dependencies, not devDependencies): one missing, or
   installed only for development, stops the build, named
   (groundwork-browser-build.npm). A stylesheet beside the namespaces that none names stops it.
   The options are groundwork-browser-build.settings/release-options-shape's, every key
   required, checked first. For the deploy: clojure -X:browser-release
   :modules … :files-folder … (and every other option)."
  [{:keys [files-folder source-maps? gzip-level] :as options}]
  (let [build (make-build :release options)]
    (try
      (run-in-own-shadow-runtime (make-shadow-config options :release)
        #(shadow/release* build {:debug false :source-maps source-maps? :pseudo-names false :verbose false}))
      (catch Throwable e
        (throw (npm/tell-npm-problem-plainly e))))
    (gzip-built-files! files-folder gzip-level)
    :built))

(defmacro use-stylesheet
  "Names a stylesheet this namespace needs, by its path on the classpath,
   as a query names its .sql file:

     (def order-card-styles (styles/use-stylesheet \"app/orders/order_card.css\"))

   Runs on the JVM while shadow-cljs compiles the namespace, where files
   can be read: a missing file stops the build here. The build gathers
   each module's named stylesheets into that module's bundle, in the
   order the namespaces are found. Returns the path."
  [path]
  (when-not (string? path)
    (throw (ex-info "use-stylesheet takes the path as text, written out" {:type :groundwork-browser-build/bad-path :path path})))
  (when-not (io/resource path)
    (throw (ex-info (str "No stylesheet " path " on the classpath (named in " analyzer/*cljs-ns* ")")
                    {:type :groundwork-browser-build/missing-file :path path :ns analyzer/*cljs-ns*})))
  (swap! env/*compiler* update-in [:cljs.analyzer/namespaces analyzer/*cljs-ns* :groundwork-browser-build/stylesheets]
         (fn [paths] (if (some #{path} paths) paths (conj (or paths []) path))))
  path)
