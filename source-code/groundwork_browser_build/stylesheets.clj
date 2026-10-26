(ns groundwork-browser-build.stylesheets
  "Each module's stylesheet bundle, made after every compile (a shadow-cljs
   build hook): the stylesheets the module's namespaces named with
   groundwork-browser-build.api's use-stylesheet, in the order the namespaces are found,
   each whole, one after another, after a /*! path */ comment naming it; a
   stylesheet several modules name is in the last module, in load order,
   that each of them is or depends on. With the build's :css, each bundle is then rewritten by the
   pinned lightningcss for the app's browsers (groundwork-browser-build.lightningcss);
   with :css nil, it is written as the files are.

     <files-folder>/css/base.css, admin.css                 while watching
     <files-folder>/css/base.<hash>.css, admin.<hash>.css   a release build
     <files-folder>/css/manifest.edn                        both: [{:module-id :base :output-name \"base.<hash>.css\"} …]

   The check: every .css file beside the build's namespaces must be named
   by one of them. A release build stops on any that isn't; while watching
   they are written to the app's log, :warn :groundwork-browser-build/unnamed-stylesheets."
  (:require
   [groundwork-browser-build.lightningcss :as lightningcss]
   [clojure.edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str])
  (:import
   (java.security MessageDigest)))

(defn- list-stylesheets-named-in [build-state resource-ids]
  (->> resource-ids
       (keep #(get-in build-state [:sources % :ns]))
       (mapcat #(get-in build-state [:compiler-env :cljs.analyzer/namespaces % :groundwork-browser-build/stylesheets]))
       (distinct)
       (vec)))

(defn- list-named-stylesheets
  "Every named stylesheet's path, in the order the namespaces are found."
  [build-state]
  (list-stylesheets-named-in build-state (:build-sources build-state)))

(defn- find-modules-each-loads
  "Each module's id with itself and every module it depends on, directly
   or through others: what is loaded whenever it is. build-modules are in
   load order, each after those it depends on."
  [build-modules]
  (reduce (fn [loads {:keys [module-id depends-on]}]
            (assoc loads module-id (into #{module-id} (mapcat loads) depends-on)))
          {}
          build-modules))

(defn- list-named-stylesheets-by-module
  "Each module's named stylesheets, by module id, in the order the
   namespaces are found. A stylesheet named in one module is in its
   bundle; one named in several is in the bundle of the last module, in
   load order, that each of them is or depends on: in the first of a
   chain, and in a module two sibling pages share (as shadow-cljs moves a
   namespace two modules require), so whichever of them loads, it is in."
  [build-state]
  (let [build-modules (:build-modules build-state)
        load-order    (map :module-id build-modules)
        loads         (find-modules-each-loads build-modules)
        named         (for [{:keys [module-id sources]} build-modules
                            path (list-stylesheets-named-in build-state sources)]
                        [module-id path])
        modules-of    (reduce (fn [by-path [module-id path]] (update by-path path (fnil conj #{}) module-id)) {} named)
        home-of       (fn [path]
                        (let [loaded-by-all (apply set/intersection (map loads (modules-of path)))]
                          (last (filter loaded-by-all load-order))))
        paths         (distinct (map second named))]
    (into {} (for [module-id load-order]
               [module-id (vec (filter #(= module-id (home-of %)) paths))]))))

(defn- list-unnamed-stylesheets
  "The .css files beside the build's own namespaces (in folders, not jars)
   that no namespace names, as classpath paths."
  [build-state named]
  (let [named (set named)]
    (->> (:build-sources build-state)
         (keep #(let [{:keys [file resource-name]} (get-in build-state [:sources %])]
                  (when file [(.getParentFile (io/file file)) (str (.getParent (io/file resource-name)))])))
         (distinct)
         (mapcat (fn [[folder resource-folder]]
                   (for [css (.listFiles ^java.io.File folder)
                         :when (str/ends-with? (.getName css) ".css")]
                     (str resource-folder "/" (.getName css)))))
         (remove named)
         (sort))))

(defn make-bundle-text
  "The bundle: each stylesheet, whole, after a comment naming it, /*! … */,
   which lightningcss keeps."
  [paths]
  (str/join "\n" (for [path paths]
                   (str "/*! " path " */\n" (slurp (io/resource path))))))

(defn make-rewrite
  "How each bundle's text is rewritten: by browser-build's pinned
   lightningcss for the :css :targets, found (or downloaded, within
   download-timeout-ms) in the cache folder and checked now; as it is for
   :css nil."
  [css cache-folder download-timeout-ms]
  (if-let [{:keys [targets]} css]
    (let [program (lightningcss/find-program! cache-folder download-timeout-ms lightningcss/pinned)]
      #(lightningcss/rewrite-css program targets %))
    identity))

(defn- hash-text [text]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes ^String text "UTF-8"))]
    (str/upper-case (apply str (map #(format "%02x" %) (take 8 digest))))))

(defn- write-if-changed! [file text]
  (io/make-parents file)
  (when-not (and (.exists (io/file file)) (= text (slurp file)))
    (spit file text)))

(defn write-bundles!
  "Writes each module's bundle, rewritten (make-rewrite), into the folder:
   <module>.css while watching, <module>.<hash>.css when released, the
   hash of what is written; and the manifest of them all, css/manifest.edn,
   both ways, which groundwork-browser-build.api's files-for-page reads."
  [paths-by-module files-folder release? rewrite]
  (spit (io/file files-folder "css" "manifest.edn")
        (pr-str (vec (for [[module-id paths] (sort-by key paths-by-module)
                           :let [text        (rewrite (make-bundle-text paths))
                                 output-name (if release?
                                               (str (name module-id) "." (hash-text text) ".css")
                                               (str (name module-id) ".css"))]]
                       (do (write-if-changed! (io/file files-folder "css" output-name) text)
                           {:module-id module-id :output-name output-name}))))))

(defn- find-modified-times
  "Each named stylesheet's last save, by path, for those in folders (a
   stylesheet in a jar doesn't change while watching)."
  [paths]
  (into {} (for [path paths
                 :let [url (io/resource path)]
                 :when (and url (= "file" (.getProtocol url)))]
             [path (.lastModified (io/file url))])))

(def last-bundle
  "The last bundles written while watching, for re-bundling when a
   stylesheet is saved: {:paths-by-module {module-id […]} :files-folder …
   :rewrite f :times {path saved-at}}, the times read before the
   stylesheets were, so a save while bundling is seen at the next check."
  (atom nil))

(defn bundle-stylesheets
  "The build hook, after every compile: the check, then the bundle,
   rewritten as the build's :css says."
  {:shadow.build/stage :flush}
  [build-state {:keys [files-folder log css cache-folder lightningcss-download-timeout-ms]}]
  (let [release?        (= :release (:shadow.build/mode build-state))
        named           (list-named-stylesheets build-state)
        unnamed         (list-unnamed-stylesheets build-state named)
        paths-by-module (list-named-stylesheets-by-module build-state)]
    (when (seq unnamed)
      (if release?
        (throw (ex-info (str "Stylesheets no namespace names (name each with styles/use-stylesheet):\n  "
                             (str/join "\n  " unnamed))
                        {:type :groundwork-browser-build/unnamed-stylesheets :stylesheets (vec unnamed)}))
        (log :warn :groundwork-browser-build/unnamed-stylesheets {:groundwork-browser-build/stylesheets (vec unnamed)})))
    (let [rewrite (make-rewrite css cache-folder lightningcss-download-timeout-ms)]
      (if release?
        (write-bundles! paths-by-module files-folder true rewrite)
        (let [times (find-modified-times named)]
          (write-bundles! paths-by-module files-folder false rewrite)
          (reset! last-bundle {:paths-by-module paths-by-module :files-folder files-folder :rewrite rewrite :times times}))))
    build-state))

(defn start-watching-stylesheets!
  "Re-bundles when a named stylesheet was saved after the last bundle was
   written, checked every check-every-ms; shadow-cljs then swaps the new
   bundle into open pages. Each check compares with the times the last
   bundle was made from (by a compile or a check), not with the check
   before: a save before the thread first runs is still seen. Runs on a
   thread of its own, which a failed check doesn't end (it says why and
   checks again). Returns a function that stops it. A failed check is
   written to log, :error :groundwork-browser-build/bundle-failed."
  [check-every-ms log]
  (let [running? (atom true)
        check!   (fn []
                   (let [{:keys [paths-by-module files-folder rewrite times]} @last-bundle
                         now (find-modified-times (distinct (apply concat (vals paths-by-module))))]
                     ;; nothing until a compile has bundled
                     (when (and files-folder (not= times now))
                       (write-bundles! paths-by-module files-folder false rewrite)
                       (swap! last-bundle #(if (= paths-by-module (:paths-by-module %)) (assoc % :times now) %)))))
        thread   (Thread.
                  ^Runnable
                  (fn []
                    (while @running?
                      (try
                        (Thread/sleep (long check-every-ms))
                        (check!)
                        (catch InterruptedException _)
                        (catch Exception e
                          (log :error :groundwork-browser-build/bundle-failed {:error e})))))
                  "browser-build-stylesheets")]
    (doto thread (.setDaemon true) (.start))
    (fn []
      (reset! running? false)
      (.interrupt thread))))
