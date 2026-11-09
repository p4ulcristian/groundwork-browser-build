(ns groundwork-browser-build.custom-properties
  "The check that every var(--name) a stylesheet of the build uses without
   a fallback names a custom property that is there when the page runs.
   var(--name) naming one nobody sets is valid CSS: the browser drops the
   whole declaration at computed-value time, without a word, and the
   element falls back to the property's inherited or initial value.

   A custom property is there when:
   - a stylesheet of the build declares it, --name: … in a rule, or
     registers it, @property --name { … } (any module's: custom
     properties inherit, and the build doesn't know which modules a page
     loads together);
   - the build's own ClojureScript names it (find-names-set-from-code): a
     string \"--name\" (a hiccup :style key, .setProperty's argument), a
     keyword :--name (a :style key), or a declaration --name: … inside a
     string (an inline style written as text);
   - the app says it is set elsewhere, :custom-properties-set-elsewhere: by
     the server's HTML, a stylesheet linked outside the build, an npm
     package's JavaScript, or a name the code builds at run time.

   var(--name, fallback) is never refused: the fallback is the value when
   --name is missing. Comments and strings in a stylesheet are not read."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private name-pattern "--[\\w-]+")

(def custom-property-name
  "A custom property's name as the settings write it: \"--name\"."
  (re-pattern (str "(?U)" name-pattern)))

(defn- blank-out
  "The text with each match of pattern replaced by spaces, newlines kept,
   so a line number in it is the line in the file."
  [text pattern]
  (str/replace text pattern #(str/replace % #"[^\n]" " ")))

(defn- strip-comments-and-strings
  "A stylesheet's text without its comments and quoted strings (a
   content: \"var(--x)\" isn't a use), the lines where they were."
  [text]
  (-> text
      (blank-out #"(?s)/\*.*?\*/")
      ;; a run of plain characters at a time (possessive): one step per run, not
      ;; per character, so a long string (a data: URL) doesn't overflow the stack
      (blank-out #"\"(?:[^\"\\\n]++|\\.)*+\"|'(?:[^'\\\n]++|\\.)*+'")))

(defn- find-line [text index]
  (inc (count (filter #{\newline} (subs text 0 index)))))

(defn find-declared-names
  "The custom properties a stylesheet declares (--name: …) or registers
   (@property --name), a set."
  [text]
  (let [text (strip-comments-and-strings text)]
    (into (set (map second (re-seq (re-pattern (str "(?U)(" name-pattern ")\\s*:")) text)))
          (map second)
          (re-seq (re-pattern (str "(?U)@property\\s+(" name-pattern ")")) text))))

(defn find-uses-without-fallback
  "Each var(--name) in a stylesheet with no fallback, in the order
   written: [{:line 7 :custom-property \"--name\"}]. var(--a, var(--b))
   has one: --b."
  [text]
  (let [text    (strip-comments-and-strings text)
        matcher (re-matcher (re-pattern (str "(?U)(?i:var)\\(\\s*(" name-pattern ")\\s*\\)")) text)]
    (loop [uses []]
      (if (.find matcher)
        (recur (conj uses {:line (find-line text (.start matcher)) :custom-property (.group matcher 1)}))
        uses))))

(defn- split-clojure-text
  "Clojure source text as {:strings [each string literal's text, escapes
   as written] :code the rest}, comments (; to the line's end) and
   character literals (\\\" is a character, not a string) left out of both.
   A regex literal #\"…\" is a string here."
  [^String text]
  (let [n (.length text)]
    (loop [i 0 strings (transient []) code (StringBuilder.)]
      (if (>= i n)
        {:strings (persistent! strings) :code (str code)}
        (let [c (.charAt text i)]
          (case c
            \; (let [end (str/index-of text "\n" i)]
                 (recur (if end end n) strings code))
            \\ (recur (min n (+ i 2)) strings (.append code \space))
            \" (let [end (loop [j (inc i)]
                           (cond (>= j n) n
                                 (= \\ (.charAt text j)) (recur (+ j 2))
                                 (= \" (.charAt text j)) j
                                 :else (recur (inc j))))]
                 (recur (inc end) (conj! strings (subs text (inc i) (min end n))) (.append code \space)))
            (recur (inc i) strings (.append code c))))))))

(defn find-names-set-from-code
  "The custom properties a Clojure(Script) source's text names: a string
   that is only the name (\"--name\": a hiccup :style key,
   .setProperty's argument, CSS.registerProperty's), a keyword :--name (a
   :style key) and a declaration --name: … inside a string (an inline
   style written as text), a set. Comments don't count."
  [text]
  (let [{:keys [strings code]} (split-clojure-text text)
        whole-name             (re-pattern (str "(?U)" name-pattern))
        declaration            (re-pattern (str "(?U)(" name-pattern ")\\s*:"))
        keyword                (re-pattern (str "(?U)(?<![\\w:/.*+!?<>=-]):(" name-pattern ")"))]
    (-> #{}
        (into (filter #(re-matches whole-name %)) strings)
        (into (comp (mapcat #(re-seq declaration %)) (map second)) strings)
        (into (map second) (re-seq keyword code)))))

(def ^:private names-by-file
  "find-names-set-from-code's answer for each source file, by its path,
   with the file's last save and size: a watch reads only what changed."
  (atom {}))

(defn find-names-set-from-files
  "Every custom property the source files name (find-names-set-from-code),
   a set; each file read again only after it changed."
  [files]
  (into #{}
        (mapcat (fn [file]
                  (let [file  (io/file file)
                        path  (.getPath file)
                        stamp [(.lastModified file) (.length file)]
                        known (get @names-by-file path)]
                    (if (= stamp (:stamp known))
                      (:names known)
                      (let [names (find-names-set-from-code (slurp file))]
                        (swap! names-by-file assoc path {:stamp stamp :names names})
                        names)))))
        files))

(defn find-undefined-uses
  "Each use, without a fallback, of a custom property none of the
   stylesheets declares and nobody sets: [{:stylesheet path :line 7
   :custom-property \"--name\"}], the stylesheets in their order, each's
   uses in the order written. stylesheets is [[path text] …]; set-from-code
   and set-elsewhere are sets of names."
  [stylesheets set-from-code set-elsewhere]
  (let [there (into (into (set set-from-code) set-elsewhere)
                    (mapcat (comp find-declared-names second))
                    stylesheets)]
    (vec (for [[path text] stylesheets
               use         (find-uses-without-fallback text)
               :when       (not (there (:custom-property use)))]
           (assoc use :stylesheet path)))))

(defn describe-uses
  "The uses, a line each: path:line var(--name)."
  [uses]
  (str/join "\n" (for [{:keys [stylesheet line custom-property]} uses]
                   (str stylesheet ":" line " var(" custom-property ")"))))

(defn tell-undefined-uses!
  "What the build does with uses of custom properties nobody sets, by
   on-undefined (the build's :on-undefined-custom-property):

     a release, :fail   stops: :groundwork-browser-build/undefined-custom-properties
     a release, :warn   writes them to standard error and goes on
     a watch, :fail     the app's log, :error, and goes on watching
     a watch, :warn     the app's log, :warn

   Nothing when there are none. log is the watch's, nil for a release."
  [uses {:keys [release? on-undefined log]}]
  (when (seq uses)
    (let [text (str "var() of custom properties no stylesheet of the build declares, the build's ClojureScript doesn't set "
                    "and :custom-properties-set-elsewhere doesn't name (the browser drops each such declaration; give it a "
                    "real property or a fallback, var(--name, value)):\n  "
                    (str/replace (describe-uses uses) "\n" "\n  "))]
      (cond
        (and release? (= :fail on-undefined))
        (throw (ex-info text {:type :groundwork-browser-build/undefined-custom-properties :uses uses}))

        release?
        (binding [*out* *err*] (println text))

        :else
        (log (if (= :fail on-undefined) :error :warn)
             :groundwork-browser-build/undefined-custom-properties
             {:groundwork-browser-build/uses uses})))))
