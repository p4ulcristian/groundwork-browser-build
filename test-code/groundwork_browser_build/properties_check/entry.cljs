(ns groundwork-browser-build.properties-check.entry
  "A release build whose stylesheets use custom properties each way one
   can be there: declared, registered, set from this code, given a
   fallback, set by the server (the test's :custom-properties-set-elsewhere),
   and one nobody sets."
  (:require [groundwork-browser-build.api :as styles]))

(def ^:private tokens-styles (styles/use-stylesheet "groundwork_browser_build/properties_check/tokens.css"))
(def ^:private card-styles (styles/use-stylesheet "groundwork_browser_build/properties_check/entry.css"))

;; :--nowhere in a comment sets nothing
(defn start! []
  (js/console.log "properties-check" tokens-styles card-styles
                  (clj->js {:style {"--progress" "40%" :--row-height "2rem"}})))
