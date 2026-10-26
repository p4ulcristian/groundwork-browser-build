(ns groundwork-browser-build.pages-check.sheets
  "A chunk two modules share: with the page for the shop, loaded on
   demand by the site (through site-sheets)."
  (:require [groundwork-browser-build.api :as styles]))

(def ^:private sheets-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/sheets.css"))

(defn open-sheet! [what]
  (js/console.log "sheets-module-here" what sheets-styles))
