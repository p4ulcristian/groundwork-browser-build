(ns groundwork-browser-build.stylesheets-test
  "Which stylesheets count as unnamed, over a build's resolved sources as
   data: a .css beside a ClojureScript namespace does; one an npm package
   ships beside its JavaScript doesn't."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [groundwork-browser-build.stylesheets :as stylesheets])
  (:import
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)))

(deftest only-a-stylesheet-beside-a-namespace-is-the-apps
  (let [folder (str (Files/createTempDirectory "browser-build-unnamed" (make-array FileAttribute 0)))
        write! (fn [path] (let [f (io/file folder path)] (io/make-parents f) (spit f "x") (str f)))
        state  {:build-sources [0 1]
                :sources       {0 {:type :cljs :file (write! "app/shop/page.cljs") :resource-name "app/shop/page.cljs"}
                                1 {:type :shadow-js :file (write! "node_modules/sheets/dist/index.js")
                                   :resource-name "node_modules/sheets/dist/index.js"}}}]
    (write! "app/shop/page.css")
    (write! "app/shop/named.css")
    (write! "node_modules/sheets/dist/main.css")
    (is (= ["app/shop/page.css"]
           (#'stylesheets/list-unnamed-stylesheets state ["app/shop/named.css"]))
        "the npm package's main.css is its own")))
