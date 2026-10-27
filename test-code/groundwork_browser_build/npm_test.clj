(ns groundwork-browser-build.npm-test
  "The npm checks, over a build's resolved sources and npm's record (or
   bun.lock, read as npm's record) as data: a package installed only for development is named, one installed
   as the app's own passes, a nested one is found where it is; and a
   package nobody installed is told again with what to do."
  (:require
   [clojure.test :refer [deftest is testing]]
   [groundwork-browser-build.npm :as npm]))

(defn- make-build-state [resources]
  {:build-sources (vec (range (count resources)))
   :sources       (into {} (map-indexed (fn [i r] [i (assoc r :type :shadow-js)])) resources)})

(def ^:private installed
  {""                                            {"name" "app"}
   "node_modules/react"                          {"version" "19.3.1"}
   "node_modules/react-dom"                      {"version" "19.3.1"}
   "node_modules/react-dom/node_modules/scheduler" {"version" "0.27.0"}
   "node_modules/process"                        {"version" "0.11.10" "dev" true}
   "node_modules/left-pad"                       {"version" "1.3.0" "extraneous" true}})

(deftest a-package-installed-only-for-development-is-named
  (let [state (make-build-state [{:package-name "react" :resource-name "node_modules/react/index.js"}
                                 {:package-name "scheduler" :resource-name "node_modules/react-dom/node_modules/scheduler/index.js"}
                                 {:package-name "process" :resource-name "node_modules/process/browser.js"}
                                 {:package-name "left-pad" :resource-name "node_modules/left-pad/index.js"}])]
    (is (= [{:package "left-pad" :installed-as "node_modules/left-pad"}
            {:package "process" :installed-as "node_modules/process"}]
           (npm/find-development-only-packages state installed))
        "react and a package react-dom brings pass; dev and extraneous ones don't"))
  (testing "a package with a dot in its name, which shadow-cljs's resource names munge, is found by its file"
    (is (= [] (npm/find-development-only-packages
               (make-build-state [{:package-name "big.js" :resource-name "node_modules/big_DOT_js/big.js"
                                   :file (java.io.File. "node_modules/big.js/big.js")}])
               {"node_modules/big.js" {"version" "7.0.1"}}))))
  (testing "the app's own packages pass"
    (is (= [] (npm/find-development-only-packages
               (make-build-state [{:package-name "react" :resource-name "node_modules/react/cjs/react.production.js"}])
               installed)))))

(def ^:private bun-lock
  "bun.lock's data: the app needs react-dom (which brings scheduler, nested
   under it, and asks react as its peer) and @scope/ui (which brings
   @scope/icons, nested under it); shadow-cljs, a development package,
   brings process."
  {"workspaces" {"" {"dependencies"    {"react" "^19.0.0" "react-dom" "^19.0.0" "@scope/ui" "^1.0.0"}
                     "devDependencies" {"shadow-cljs" "^3.0.0"}}}
   "packages"   {"react"                  ["react@19.3.1" "" {} "sha512-a"]
                 "react-dom"              ["react-dom@19.3.1" "" {"dependencies" {"scheduler" "^0.27.0"} "peerDependencies" {"react" "^19.3.1"}} "sha512-b"]
                 "react-dom/scheduler"    ["scheduler@0.27.0" "" {} "sha512-c"]
                 "scheduler"              ["scheduler@0.20.0" "" {} "sha512-d"]
                 "@scope/ui"              ["@scope/ui@1.2.0" "" {"dependencies" {"@scope/icons" "^2.0.0"}} "sha512-e"]
                 "@scope/ui/@scope/icons" ["@scope/icons@2.1.0" "" {} "sha512-f"]
                 "shadow-cljs"            ["shadow-cljs@3.5.7" "" {"dependencies" {"process" "^0.11.0"}} "sha512-g"]
                 "process"                ["process@0.11.10" "" {} "sha512-h"]}})

(deftest bun-lock-is-read-as-npms-record
  (let [record (npm/make-record-from-bun-lock bun-lock)]
    (is (= {""                                               {}
            "node_modules/react"                             {"version" "19.3.1"}
            "node_modules/react-dom"                         {"version" "19.3.1"}
            "node_modules/react-dom/node_modules/scheduler"  {"version" "0.27.0"}
            "node_modules/scheduler"                         {"version" "0.20.0" "dev" true}
            "node_modules/@scope/ui"                         {"version" "1.2.0"}
            "node_modules/@scope/ui/node_modules/@scope/icons" {"version" "2.1.0"}
            "node_modules/shadow-cljs"                       {"version" "3.5.7" "dev" true}
            "node_modules/process"                           {"version" "0.11.10" "dev" true}}
           record)
        "a package the app's dependencies reach is the app's, found where node finds it; the rest only for development")
    (is (= [{:package "process" :installed-as "node_modules/process"}]
           (npm/find-development-only-packages
            (make-build-state [{:package-name "react" :resource-name "node_modules/react/index.js"}
                               {:package-name "scheduler" :resource-name "node_modules/react-dom/node_modules/scheduler/index.js"}
                               {:package-name "@scope/icons" :resource-name "node_modules/@scope/ui/node_modules/@scope/icons/index.js"}
                               {:package-name "process" :resource-name "node_modules/process/browser.js"}])
            record))
        "and the check reads it as it reads npm's")))

(deftest a-package-nobody-installed-is-told-again
  (let [thrown (ex-info "outer" {} (ex-info "The required JS dependency \"process\" is not available"
                                            {:tag :shadow.build.resolve/missing-js :require "process"
                                             :require-from "node_modules/react/cjs/react.production.js"}))
        told   (npm/tell-missing-package-again thrown)]
    (is (= {:type :groundwork-browser-build/npm-package-missing :package "process" :required-from "node_modules/react/cjs/react.production.js"}
           (ex-data told)))
    (is (re-find #"package\.json's \"dependencies\" \(not \"devDependencies\"" (ex-message told))))
  (is (= "react" (:package (ex-data (npm/tell-missing-package-again
                                      (ex-info "x" {:tag :shadow.build.resolve/missing-js :require "react/jsx-runtime" :require-from "a.js"})))))
      "a file of a package names the package")
  (let [other (ex-info "something else" {})]
    (is (identical? other (npm/tell-missing-package-again other)))))

(deftest the-refusal-is-told-plainly
  (let [refusal (ex-info "The build needs npm packages that are installed only for development"
                         {:type :groundwork-browser-build/npm-package-only-for-development :packages ["process"]})]
    (is (identical? refusal (npm/tell-npm-problem-plainly (ex-info "Hook [1 groundwork-browser-build.npm/check-npm-packages] failed" {} refusal)))
        "not shadow-cljs's wrapping")))
