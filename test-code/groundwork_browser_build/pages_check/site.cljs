(ns groundwork-browser-build.pages-check.site
  "A page module: the site. It loads its sheets on demand, with their
   stylesheets first."
  (:require [groundwork-browser-build.api :as styles]))

(def ^:private site-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/site.css"))
(def ^:private shared-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/shared.css"))

(defn start!
  "The site's start, the app's start for its pages."
  []
  (js/console.log "site-start-here" site-styles shared-styles)
  (-> (styles/load-module! {"sheets" "/css/sheets.css" "site-sheets" "/css/site-sheets.css"} "site-sheets")
      (.then #(js/console.log "site sheets loaded"))))
