(ns groundwork-browser-build.custom-properties-test
  "Which var(--name) uses count as undefined, over stylesheet and source
   texts as data: a declared or registered property is there, one the
   code sets is, one the app names as set elsewhere is, a fallback makes
   any use fine; comments and strings are not read. And what each
   :on-undefined-custom-property does with what is left."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [groundwork-browser-build.custom-properties :as custom-properties]))

(deftest declared-and-registered-properties
  (is (= #{"--space-8" "--surface" "--angle"}
         (custom-properties/find-declared-names
          ":root { --space-8: 8px; --surface : hsl(0 0% 10%); }
           @property --angle { syntax: \"<angle>\"; inherits: false; initial-value: 0deg; }
           /* --in-a-comment: 1px; */
           .quote::before { content: \"--in-a-string: 1px\"; }"))))

(deftest uses-without-a-fallback
  (is (= [{:line 2 :custom-property "--gap"}
          {:line 3 :custom-property "--b"}
          {:line 4 :custom-property "--spaced"}
          {:line 5 :custom-property "--upper"}]
         (custom-properties/find-uses-without-fallback
          ".a {
             gap: var(--gap);
             color: var(--a, var(--b));
             margin: var( --spaced );
             padding: VAR(--upper);
             border: var(--with-fallback, 1px solid);
             /* width: var(--in-a-comment); */
             content: \"var(--in-a-string)\";
           }"))
      "the inner var() of a fallback is a use too; the line is the file's"))

(deftest a-long-string-is-read-in-runs
  (is (= [{:line 1 :custom-property "--gap"}]
         (custom-properties/find-uses-without-fallback
          (str ".a { background: url(\"data:image/svg+xml," (apply str (repeat 200000 "a")) "\"); gap: var(--gap); }")))
      "a 200 KB data: URL, no stack overflow"))

(deftest names-the-code-sets
  (is (= #{"--progress" "--row-height" "--accent" "--inline" "--second"}
         (custom-properties/find-names-set-from-code
          "(ns app.card)
           ;; \"--in-a-comment\" and :--in-a-comment set nothing
           (defn card [pct]
             [:div {:style {\"--progress\" (str pct \"%\") :--row-height \"2rem\"}}
              [:span {:style \"--inline: 4px; --second : 1px\"}]])
           (defn paint! [el] (.setProperty (.-style el) \"--accent\" \"red\"))
           (def quote-char \\\")
           (def not-a-name \"--sentence is not a name\")
           (def namespaced :app/--not-this)"))
      "a string that is only the name, a keyword, a declaration in a string; not a comment, a sentence or a namespaced keyword"))

(def ^:private stylesheets
  [["app/tokens.css" ":root { --space-8: 8px; }\n@property --angle { syntax: \"<angle>\"; inherits: false; initial-value: 0deg; }"]
   ["app/card.css" (str ".card {\n"
                        "  padding: var(--space-8);\n"
                        "  rotate: var(--angle);\n"
                        "  width: var(--progress);\n"
                        "  color: var(--accent, tomato);\n"
                        "  margin: var(--from-server);\n"
                        "  gap: var(--nowhere);\n"
                        "}")]])

(deftest undefined-uses
  (testing "declared in any stylesheet of the build, registered, set from code, given a fallback: there"
    (is (= [{:stylesheet "app/card.css" :line 6 :custom-property "--from-server"}
            {:stylesheet "app/card.css" :line 7 :custom-property "--nowhere"}]
           (custom-properties/find-undefined-uses stylesheets #{"--progress"} #{}))))
  (testing "set elsewhere, as the app says"
    (is (= [{:stylesheet "app/card.css" :line 7 :custom-property "--nowhere"}]
           (custom-properties/find-undefined-uses stylesheets #{"--progress"} #{"--from-server"})))
    (is (= [] (custom-properties/find-undefined-uses stylesheets #{"--progress"} #{"--from-server" "--nowhere"}))))
  (testing "a property only the code sets, once the code stops setting it, is undefined again"
    (is (= ["--progress" "--from-server" "--nowhere"]
           (map :custom-property (custom-properties/find-undefined-uses stylesheets #{} #{}))))))

(def ^:private uses [{:stylesheet "app/card.css" :line 7 :custom-property "--nowhere"}])

(deftest what-each-setting-does
  (testing "a release, :fail: stops, naming each use"
    (let [e (try (custom-properties/tell-undefined-uses! uses {:release? true :on-undefined :fail :log nil})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (= {:type :groundwork-browser-build/undefined-custom-properties :uses uses} (ex-data e)))
      (is (str/includes? (ex-message e) "app/card.css:7 var(--nowhere)"))))
  (testing "a release, :warn: standard error, and goes on"
    (let [err (java.io.StringWriter.)]
      (binding [*err* err]
        (is (nil? (custom-properties/tell-undefined-uses! uses {:release? true :on-undefined :warn :log nil}))))
      (is (str/includes? (str err) "app/card.css:7 var(--nowhere)"))))
  (testing "a watch: the app's log, :error for :fail, :warn for :warn; nothing thrown"
    (doseq [[on-undefined level] [[:fail :error] [:warn :warn]]]
      (let [lines (atom [])]
        (custom-properties/tell-undefined-uses! uses {:release? false :on-undefined on-undefined
                                                      :log (fn [& line] (swap! lines conj (vec line)))})
        (is (= [[level :groundwork-browser-build/undefined-custom-properties {:groundwork-browser-build/uses uses}]]
               @lines)))))
  (testing "none: nothing at all"
    (is (nil? (custom-properties/tell-undefined-uses! [] {:release? true :on-undefined :fail :log nil})))))
