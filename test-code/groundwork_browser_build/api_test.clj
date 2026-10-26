(ns groundwork-browser-build.api-test
  "Release builds of small builds: the code compiles, optimized, into a
   file per module with a hashed name and its manifest; the named
   stylesheets become each module's bundle; a missing or an unnamed
   stylesheet stops the build. A build with several page modules: the
   files a page links, for each module it may start with, in dependency
   order. Every option is required. And one watch: compiled, the files a
   page links, a saved stylesheet rebundled, a saved namespace compiled
   again, stopped."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [groundwork-browser-build.api :as browser-build]
   [groundwork-browser-build.lightningcss :as lightningcss]
   [groundwork-browser-build.page :as page])
  (:import
   (java.net ServerSocket)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.util.zip GZIPInputStream)))

(deftest the-names-of-the-api
  (is (= '#{make-build start-watching! stop-watching! build-for-release! use-stylesheet}
         (set (keys (ns-publics 'groundwork-browser-build.api)))))
  (is (= '#{files-for-page stylesheets-for-load-module page-options-shape load-module-options-shape}
         (set (keys (ns-publics 'groundwork-browser-build.page))))
      "the page half, which an app's server runs in production"))

(defn- make-folder [] (str (Files/createTempDirectory "browser-build-build" (make-array FileAttribute 0))))

(defn- make-one-module
  "A build of one module, the root, starting at start."
  [start]
  {:main {:entries [(symbol (namespace start))] :depends-on #{} :start start}})

(defn- make-release-options [start]
  {:modules            (make-one-module start)
   :files-folder       (make-folder)
   :cache-folder       (make-folder)
   :compile-threads    2
   :output-feature-set :es2020
   :resolve            {}
   :closure-defines    {}
   :warnings-as-errors #{}
   :silenced-warnings  #{}
   :css                nil
   :lightningcss-download-timeout-ms 120000
   :source-maps?       false
   :gzip-level         9})

(def ^:private lines
  "What the tests' log has been handed, [level event data] each."
  (atom []))

(defn- log [level event data] (swap! lines conj [level event data]) nil)

(defn- make-watch-options []
  {:log                       log
   :modules                   {:base {:entries    '[groundwork-browser-build.watch-check.entry]
                                      :depends-on #{}
                                      :start      'groundwork-browser-build.watch-check.entry/start!}
                               :page {:entries    '[groundwork-browser-build.watch-check.page]
                                      :depends-on #{:base}
                                      :start      'groundwork-browser-build.watch-check.page/start!}}
   :files-folder              (make-folder)
   :cache-folder              (make-folder)
   :compile-threads           2
   :output-feature-set        :es2020
   :resolve                   {}
   :closure-defines           {}
   :warnings-as-errors        #{}
   :silenced-warnings         #{}
   :css                       nil
   :lightningcss-download-timeout-ms 120000
   :preloads                  []
   :devtools-host             "localhost"
   :devtools-port             0
   :devtools-url              nil
   :nrepl                     nil
   :socket-repl               nil
   :hot-reload?               true
   :reload-strategy           :optimized
   :hud                       #{:errors :warnings :progress}
   :stylesheet-check-every-ms 100})

(defn- find-refusal [f]
  (try (f) :nothing
       (catch clojure.lang.ExceptionInfo e (select-keys (ex-data e) [:type :errors]))))

(defn- gunzip [file]
  (with-open [in (GZIPInputStream. (io/input-stream file))]
    (slurp in)))

(deftest a-release-build
  (let [{:keys [files-folder] :as options} (make-release-options 'groundwork-browser-build.build-check/start!)]
    (with-out-str (browser-build/build-for-release! options))
    (let [[{:keys [module-id output-name]}] (edn/read-string (slurp (io/file files-folder "js" "manifest.edn")))
          js (io/file files-folder "js" output-name)]
      (is (= :main module-id))
      (is (re-matches #"main\.[0-9A-F]{32}\.js" output-name) "the name carries the whole hash of the content")
      (is (.exists js))
      (is (= (slurp js) (gunzip (str js ".gz"))) "and its gzipped copy, the same bytes")
      (is (not (.exists (io/file (str js ".map")))) "no source map: :source-maps? false"))
    (let [[{:keys [output-name]}] (edn/read-string (slurp (io/file files-folder "css" "manifest.edn")))
          css (io/file files-folder "css" output-name)]
      (is (re-matches #"main\.[0-9A-F]{16}\.css" output-name))
      (is (= "/*! groundwork_browser_build/build_check.css */\n.build-check { color: rebeccapurple; }\n" (slurp css))
          "the named stylesheet, whole, as written: :css nil")
      (is (= (slurp css) (gunzip (str css ".gz")))))))

(def ^:private pages-modules
  "A root, two page modules (the site, the shop), a chunk with the page
   for the shop and on demand for the site, the site's sheets on demand,
   and a module only ever loaded on demand."
  '{:base        {:entries [groundwork-browser-build.pages-check.base] :depends-on #{} :start groundwork-browser-build.pages-check.base/start!}
    :sheets      {:entries [groundwork-browser-build.pages-check.sheets] :depends-on #{:base} :start nil}
    :site        {:entries [groundwork-browser-build.pages-check.site] :depends-on #{:base} :start groundwork-browser-build.pages-check.site/start!}
    :shop        {:entries [groundwork-browser-build.pages-check.shop] :depends-on #{:base :sheets} :start groundwork-browser-build.pages-check.shop/start!}
    :site-sheets {:entries [groundwork-browser-build.pages-check.site-sheets] :depends-on #{:site :sheets}
                  :start groundwork-browser-build.pages-check.site-sheets/start!}
    :qr          {:entries [groundwork-browser-build.pages-check.qr] :depends-on #{:base} :start groundwork-browser-build.pages-check.qr/start!}})

(defn- is-after-its-dependencies?
  "Whether each module's file comes after those of the modules it depends on."
  [paths]
  (let [position (into {} (map-indexed (fn [i path] [(keyword (first (str/split (last (str/split path #"/")) #"\."))) i])) paths)]
    (every? (fn [[module-id i]] (every? #(< (position %) i) (get-in pages-modules [module-id :depends-on]))) position)))

(deftest a-release-build-with-page-modules
  (let [{:keys [files-folder] :as options} (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                  :modules pages-modules)]
    (with-out-str (browser-build/build-for-release! options))
    (let [js-files  (edn/read-string (slurp (io/file files-folder "js" "manifest.edn")))
          css-files (edn/read-string (slurp (io/file files-folder "css" "manifest.edn")))
          name-of   (fn [files module-id] (:output-name (first (filter #(= module-id (:module-id %)) files))))
          js-of     #(slurp (io/file files-folder "js" (name-of js-files %)))
          css-of    #(slurp (io/file files-folder "css" (name-of css-files %)))
          on-disk?  #(.exists (io/file files-folder (subs % 1)))
          files-of  #(page/files-for-page {:files-folder files-folder :module %})]
      (is (= #{:base :sheets :site :shop :site-sheets :qr} (set (map :module-id js-files)) (set (map :module-id css-files)))
          "every module its own file and its own stylesheet bundle")
      (testing "a page links its module and every one it depends on, in dependency order, the hashed names"
        (is (= {:stylesheets [(str "/css/" (name-of css-files :base)) (str "/css/" (name-of css-files :site))]
                :scripts     [(str "/js/" (name-of js-files :base)) (str "/js/" (name-of js-files :site))]}
               (files-of :site))
            "the site: the root, then the site; not the sheets, which it loads on demand")
        (is (= {:stylesheets (mapv #(str "/css/" (name-of css-files %)) [:base :sheets :shop])
                :scripts     (mapv #(str "/js/" (name-of js-files %)) [:base :sheets :shop])}
               (files-of :shop))
            "the shop: the sheets with the page")
        (is (re-matches #"/js/shop\.[0-9A-F]{32}\.js" (last (:scripts (files-of :shop)))))
        (is (re-matches #"/css/shop\.[0-9A-F]{16}\.css" (last (:stylesheets (files-of :shop)))))
        (let [{:keys [stylesheets scripts]} (files-of :site-sheets)]
          (is (= #{:base :site :sheets :site-sheets}
                 (set (map #(keyword (first (str/split (last (str/split % #"/")) #"\."))) scripts))))
          (is (is-after-its-dependencies? scripts) "any module can start a page, each file after those it depends on")
          (is (is-after-its-dependencies? stylesheets))
          (is (every? on-disk? (concat scripts stylesheets)) "each path is a built file, served from the files folder at /")))
      (testing "the code of a shared chunk stays in its module"
        (is (str/includes? (js-of :sheets) "sheets-module-here"))
        (is (not-any? #(str/includes? (js-of %) "sheets-module-here") [:base :site :shop :site-sheets :qr]))
        (is (str/includes? (js-of :qr) "qr-start-here") "an on-demand module's :start kept and run, not dropped as unused")
        (is (str/includes? (js-of :base) (name-of js-files :qr)) "the root knows where each module's hashed file is, for the loader"))
      (testing "each module's own stylesheets in its bundle"
        (is (= "/*! groundwork_browser_build/pages_check/base.css */
.base-check { color: rebeccapurple; }

/*! groundwork_browser_build/pages_check/shared.css */
.shared-check { color: navy; }
" (css-of :base))
            "one named by two sibling pages is in the module both load, the root, so whichever page loads has it")
        (is (= "/*! groundwork_browser_build/pages_check/sheets.css */
.sheets-check { color: teal; }
" (css-of :sheets)))
        (is (= "/*! groundwork_browser_build/pages_check/site.css */
.site-check { color: tomato; }
" (css-of :site)))
        (is (= "/*! groundwork_browser_build/pages_check/shop.css */
.shop-check { color: olive; }
" (css-of :shop)))
        (is (= "" (css-of :site-sheets)) "a module that names none has an empty bundle")
        (is (every? #(.exists (io/file files-folder "css" (str (:output-name %) ".gz"))) css-files) "gzipped too"))
      (testing "load-module! is handed every module's bundle"
        (is (= (into (sorted-map) (map (fn [{:keys [module-id output-name]}] [(name module-id) (str "/css/" output-name)])) css-files)
               (page/stylesheets-for-load-module {:files-folder files-folder})))
        (is (= ["base" "qr" "sheets" "shop" "site" "site-sheets"]
               (keys (page/stylesheets-for-load-module {:files-folder files-folder})))))
      (testing "refusals, named"
        (is (= {:type :groundwork-browser-build/unknown-module}
               (select-keys (find-refusal #(files-of :admin)) [:type])))
        (is (= [:base :qr :sheets :shop :site :site-sheets]
               (try (files-of :admin) (catch clojure.lang.ExceptionInfo e (:modules (ex-data e)))))
            "naming the modules the build has")
        (is (= {:type :groundwork-browser-build/bad-options :errors {:module ["should be a keyword"] :folder ["disallowed key"]
                                                                     :files-folder ["missing required key"]}}
               (find-refusal #(page/files-for-page {:folder files-folder :module "site"}))))
        (is (= :groundwork-browser-build/not-built
               (:type (find-refusal #(page/files-for-page {:files-folder (make-folder) :module :site}))))
            "no manifest: build first")))))

(deftest a-build-has-one-root-and-every-other-module-depends-on-it
  (let [problems #(get-in (find-refusal (fn [] (browser-build/make-build :release (assoc (make-release-options 'app.main/start!) :modules %))))
                          [:errors :modules])]
    (is (= ["no root: one module, which every other depends on, has :depends-on #{}"]
           (problems {})))
    (is (= ["more than one root, each with :depends-on #{}: [:a :b]; one depends on nothing, every other on it"]
           (problems '{:a {:entries [app.a] :depends-on #{} :start nil}
                       :b {:entries [app.b] :depends-on #{} :start nil}})))
    (is (= ["depend on modules the build doesn't have: {:b [:nosuch]}"]
           (problems '{:a {:entries [app.a] :depends-on #{} :start nil}
                       :b {:entries [app.b] :depends-on #{:a :nosuch} :start nil}})))
    (is (= ["depend on each other in a circle: :b -> :c -> :b"]
           (problems '{:a {:entries [app.a] :depends-on #{} :start nil}
                       :b {:entries [app.b] :depends-on #{:a :c} :start nil}
                       :c {:entries [app.c] :depends-on #{:b} :start nil}})))
    (is (= ["no root: one module, which every other depends on, has :depends-on #{}; depend on each other in a circle: :a -> :a"]
           (problems '{:a {:entries [app.a] :depends-on #{:a} :start nil}}))
        "every problem at once")
    (is (= {:modules {:a {:start ["missing required key"]}}}
           (:errors (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'app.main/start!)
                                                                        :modules '{:a {:entries [app.a] :depends-on #{}}})))))
        "the root names its start too, nil for none")))

(def ^:private targets "chrome >= 119, safari >= 16.4, firefox >= 128")

(def ^:private downloaded
  "The pinned program, downloaded once for the tests that only need it there."
  (delay (lightningcss/find-program! (make-folder) 120000 lightningcss/pinned)))

(defn- put-program-in! [cache-folder]
  (let [file (io/file cache-folder "lightningcss" (:version lightningcss/pinned) (lightningcss/find-platform)
                      (.getName (io/file @downloaded)))]
    (io/make-parents file)
    (io/copy (io/file @downloaded) file)
    cache-folder))

(defn- find-type-of-cause
  "The :type of the first ex-info along the causes that has one."
  [^Throwable e]
  (some #(:type (ex-data %)) (take-while some? (iterate #(.getCause ^Throwable %) e))))

(defn- find-release-refusal [options]
  (try (with-out-str (browser-build/build-for-release! options)) :built
       (catch Exception e (find-type-of-cause e))))

(deftest a-release-build-rewrites-its-stylesheets-for-the-apps-browsers
  (let [{:keys [files-folder cache-folder] :as options} (assoc (make-release-options 'groundwork-browser-build.css-check.entry/start!)
                                                              :css {:targets targets})]
    (with-out-str (browser-build/build-for-release! options))
    (let [[{:keys [output-name]}] (edn/read-string (slurp (io/file files-folder "css" "manifest.edn")))
          css (slurp (io/file files-folder "css" output-name))]
      (is (str/includes? css "/*! groundwork_browser_build/css_check/entry.css */") "the comment naming the file, kept")
      (is (str/includes? css ".css-check .title {") "nesting flattened, for Safari 16.4")
      (is (str/includes? css "-webkit-backdrop-filter: blur(4px);") "the prefix Safari needs")
      (is (str/includes? css "color: tomato;") "not minified")
      (is (= css (gunzip (io/file files-folder "css" (str output-name ".gz"))))))
    (is (.exists (io/file cache-folder "lightningcss" "1.33.0" (lightningcss/find-platform)))
        "downloaded into the build's cache folder"))
  (testing "a cached program whose checksum differs stops the build, named"
    (let [cache-folder (put-program-in! (make-folder))
          program      (io/file cache-folder "lightningcss" (:version lightningcss/pinned) (lightningcss/find-platform)
                                (.getName (io/file @downloaded)))]
      (spit program "not the pinned program")
      (is (= :groundwork-browser-build/lightningcss-checksum
             (find-release-refusal (assoc (make-release-options 'groundwork-browser-build.css-check.entry/start!)
                                          :cache-folder cache-folder :css {:targets targets}))))))
  (testing "a platform the pins don't name stops, named, before anything is downloaded"
    (let [cache-folder (make-folder)]
      (is (= :groundwork-browser-build/lightningcss-no-platform
             (find-type-of-cause (try (lightningcss/find-program! cache-folder 120000
                                                                  (update lightningcss/pinned :sha256 dissoc (lightningcss/find-platform)))
                                      (catch Exception e e)))))
      (is (not (.exists (io/file cache-folder "lightningcss"))))))
  (testing "a download past its time limit stops the build, named"
    (is (= :groundwork-browser-build/lightningcss-download-timed-out
           (find-release-refusal (assoc (make-release-options 'groundwork-browser-build.css-check.entry/start!)
                                        :css {:targets targets} :lightningcss-download-timeout-ms 1)))))
  (testing "lightningcss's own refusal stops the build, with its words"
    (is (= :groundwork-browser-build/lightningcss-failed
           (find-type-of-cause (try (lightningcss/rewrite-css "false" targets ".a { color: red }") (catch Exception e e))))
        "a program that exits with an error")))

(deftest the-css-step-is-written-out
  (testing "the app writes only its targets: the program is browser-build's pin"
    (is (= {:css {:lightningcss ["disallowed key"]}}
           (:errors (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                                        :css {:targets targets :lightningcss lightningcss/pinned}))))))
    (is (= {:css {:targets ["missing required key"]}}
           (:errors (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                                        :css {}))))))
    (is (= {:lightningcss-download-timeout-ms ["missing required key"]}
           (:errors (find-refusal #(browser-build/make-build :release (dissoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                                        :lightningcss-download-timeout-ms)))))
        "the download's time limit is written out, with :css nil too"))
  (testing "browser-build reads the targets itself, before lightningcss runs (which aborts on what it can't read)"
    (let [refusal #(:errors (find-refusal (fn [] (browser-build/build-for-release! (assoc (make-release-options 'groundwork-browser-build.css-check.entry/start!)
                                                                                         :css {:targets %})))))]
      (is (str/starts-with? (first (get-in (refusal "chrome >= 119, nosuch >= 1") [:css :targets]))
                            "names browsers lightningcss doesn't know: [\"nosuch\"]; it knows [\"and_chr\" ")
          "an unknown browser, named, refused with the options")
      (is (= ["browser-build reads only \"<browser> >= <version>\" queries (or > <= <), joined by commas, not [\"not dead\" \"safari 16.4\"]"]
             (get-in (refusal "not dead, safari 16.4, chrome >= 119") [:css :targets]))
          "a query it doesn't read: an exact version, a browserslist keyword")
      (is (= ["browser-build reads only \"<browser> >= <version>\" queries (or > <= <), joined by commas, not [\"\"]"]
             (get-in (refusal "chrome >= 119,, safari >= 16.4") [:css :targets]))
          "an empty query")
      (is (nil? (lightningcss/check-targets "Chrome >= 119, ios >= 16.4, fx > 127, and_chr <= 140.0.1"))
          "browsers by any case and their aliases, and each op"))))

(deftest a-missing-stylesheet-stops-the-build
  (is (thrown? Exception
               (with-out-str (browser-build/build-for-release! (make-release-options 'groundwork-browser-build.missing-check.entry/start!))))))

(deftest a-stylesheet-no-namespace-names-stops-a-release
  (let [message (try (with-out-str (browser-build/build-for-release! (make-release-options 'groundwork-browser-build.unnamed-check.entry/start!)))
                     nil
                     (catch Exception e (pr-str e)))]
    (is (re-find #"forgotten\.css" (str message)))
    (is (not (re-find #"named\.css\n" (str message))))))

(deftest every-option-is-required
  (testing "build-for-release! names each missing option, before anything is built"
    (let [{:keys [type errors]} (find-refusal #(browser-build/build-for-release! {:modules (make-one-module 'app.browser.main/start!)
                                                                                  :files-folder "target/public"}))]
      (is (= :groundwork-browser-build/bad-options type))
      (is (= #{:cache-folder :compile-threads :output-feature-set :resolve :closure-defines :warnings-as-errors
               :silenced-warnings :css :lightningcss-download-timeout-ms :source-maps? :gzip-level}
             (set (keys errors))))))
  (testing "a build has no :entry: the root module's :start is the page's first"
    (is (= {:entry ["disallowed key"]}
           (:errors (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'app.main/start!) :entry 'app.main/start!)))))))
  (testing "start-watching! names each missing option, before shadow-cljs starts"
    (let [{:keys [type errors]} (find-refusal #(browser-build/start-watching! (dissoc (make-watch-options) :devtools-host :hud :nrepl :log)))]
      (is (= :groundwork-browser-build/bad-options type))
      (is (= #{:devtools-host :hud :nrepl :log} (set (keys errors))) "the app's log among them")))
  (testing "an unknown option too"
    (is (= {:type :groundwork-browser-build/bad-options :errors {:gzip-levle ["disallowed key"]}}
           (find-refusal #(browser-build/build-for-release! (assoc (make-release-options 'groundwork-browser-build.build-check/start!) :gzip-levle 9))))))
  (testing "the build says what the options say"
    (let [build (browser-build/make-build :watch (assoc (make-watch-options) :hot-reload? false :hud #{} :reload-strategy :full))]
      (is (= {:autoload false :hud false :reload-strategy :full}
             (select-keys (:devtools build) [:autoload :hud :reload-strategy]))))
    (is (= :advanced (get-in (browser-build/make-build :release (make-release-options 'groundwork-browser-build.build-check/start!))
                             [:compiler-options :optimizations])))
    (is (= {:main  {:entries '[app.main] :depends-on #{} :init-fn 'app.main/start!}
            :admin {:entries '[app.admin] :depends-on #{:main} :init-fn 'app.admin/start!}
            :quiet {:entries '[app.quiet] :depends-on #{:main}}}
           (:modules (browser-build/make-build :release (update (make-release-options 'app.main/start!)
                                                          :modules assoc
                                                          :admin {:entries '[app.admin] :depends-on #{:main} :start 'app.admin/start!}
                                                          :quiet {:entries '[app.quiet] :depends-on #{:main} :start nil}))))
        "a module's :start is its :init-fn; nil, none")
    (is (= {:modules {:admin {:start ["missing required key"]}}}
           (:errors (find-refusal #(browser-build/make-build :release (update (make-release-options 'app.main/start!)
                                                                        :modules assoc :admin {:entries '[app.admin] :depends-on #{:main}})))))
        "a module names its start, nil for none")
    (let [build (browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                     :resolve {"process" {:target :file :file "shim.js"} "@maptiler/sdk" false}
                                                     :closure-defines '{malli.registry/type "custom"}
                                                     :warnings-as-errors #{:infer-warning}))]
      (is (= {"process" {:target :file :file "shim.js"} "@maptiler/sdk" false} (get-in build [:js-options :resolve])))
      (is (= '{malli.registry/type "custom"} (get-in build [:compiler-options :closure-defines])))
      (is (= #{:infer-warning} (get-in build [:compiler-options :warnings-as-errors]))))
    (testing "silenced warnings are shadow-cljs's :warnings, each false; preloads only while watching"
      (is (= {:unreachable-code false}
             (get-in (browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                          :silenced-warnings #{:unreachable-code}))
                     [:compiler-options :warnings])))
      (is (= {} (get-in (browser-build/make-build :release (make-release-options 'groundwork-browser-build.build-check/start!))
                        [:compiler-options :warnings])))
      (is (= '[app.dev.tools] (get-in (browser-build/make-build :watch (assoc (make-watch-options) :preloads '[app.dev.tools]))
                                      [:devtools :preloads])))
      (is (= {:preloads ["disallowed key"]}
             (:errors (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                                          :preloads '[app.dev.tools])))))
          "a release has no preloads")
      (is (= {:silenced-warnings ["silenced and among :warnings-as-errors: [:infer-warning]"]}
             (:errors (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                                          :warnings-as-errors #{:infer-warning}
                                                                          :silenced-warnings #{:infer-warning})))))
          "a warning is silenced or stops the build, not both"))
    (is (= :groundwork-browser-build/bad-options
           (:type (find-refusal #(browser-build/make-build :release (assoc (make-release-options 'groundwork-browser-build.build-check/start!)
                                                                      :resolve {"x" {:target :file}})))))
        "a replacement says what it is replaced with")))

(def ^:private entry-file (io/file "test-code/groundwork_browser_build/watch_check/entry.cljs"))
(def ^:private css-file (io/file "test-code/groundwork_browser_build/watch_check/entry.css"))
(def ^:private stray-file (io/file "test-code/groundwork_browser_build/watch_check/stray.css"))

(defn- wait-until [f]
  (loop [n 0]
    (cond (try (f) (catch Exception _ false)) true
          (> n 300) false
          :else (do (Thread/sleep 100) (recur (inc n))))))

(deftest watching
  (testing "a devtools port in use is refused, not moved"
    (with-open [taken (ServerSocket. 0)]
      (is (thrown? Exception (with-out-str (browser-build/start-watching! (assoc (make-watch-options) :devtools-port (.getLocalPort taken))))))))
  (let [entry-text (slurp entry-file)
        css-text   (slurp css-file)
        {:keys [files-folder cache-folder] :as options} (let [options (make-watch-options)]
                                                          (put-program-in! (:cache-folder options))
                                                          (assoc options :css {:targets targets}))
        output     (java.io.StringWriter.)]
    (try
      (let [watching (binding [*out* output] (browser-build/start-watching! options))
            bundle   (io/file files-folder "css" "base.css")
            code     (io/file files-folder "js" "cljs-runtime" "groundwork_browser_build.watch_check.entry.js")]
        (try
          (testing "the first compile: the page's code and its bundle, through the CSS step"
            (is (.exists (io/file files-folder "js" "base.js")))
            (is (str/includes? (slurp code) "watch-check-one"))
            (is (str/includes? (slurp bundle) "color: #639;") "rebeccapurple, as lightningcss writes it"))
          (testing "a page links its module and the root, in dependency order, the plain names"
            (is (= {:stylesheets ["/css/base.css" "/css/page.css"] :scripts ["/js/base.js" "/js/page.js"]}
                   (page/files-for-page {:files-folder files-folder :module :page})))
            (is (= {:stylesheets ["/css/base.css"] :scripts ["/js/base.js"]}
                   (page/files-for-page {:files-folder files-folder :module :base})))
            (is (= {"base" "/css/base.css" "page" "/css/page.css"}
                   (page/stylesheets-for-load-module {:files-folder files-folder})))
            (is (every? #(.exists (io/file files-folder (subs % 1)))
                        (mapcat val (page/files-for-page {:files-folder files-folder :module :page}))))
            (is (str/includes? (slurp (io/file files-folder "css" "page.css")) ".watch-page")))
          (testing "shadow-cljs wrote its port into the cache folder, not .shadow-cljs"
            (is (.exists (io/file cache-folder "http.port"))))
          (testing "a stylesheet saved right after the start is rebundled, through the CSS step"
            (spit css-file ".watch-check { & .inner { color: tomato; } }\n")
            (is (wait-until #(str/includes? (slurp bundle) ".watch-check .inner {"))))
          (testing "a saved namespace is compiled again; a stylesheet no namespace names is written to the log"
            (reset! lines [])
            (spit stray-file ".stray { color: red; }\n")
            (spit entry-file (str/replace entry-text "watch-check-one" "watch-check-two"))
            (is (wait-until #(str/includes? (slurp code) "watch-check-two")))
            (is (wait-until #(some #{[:warn :groundwork-browser-build/unnamed-stylesheets {:groundwork-browser-build/stylesheets ["groundwork_browser_build/watch_check/stray.css"]}]}
                                   @lines))))
          (finally
            (binding [*out* output] (is (= :stopped (browser-build/stop-watching! watching)))))))
      (finally
        (io/delete-file stray-file true)
        (spit entry-file entry-text)
        (spit css-file css-text)))))
