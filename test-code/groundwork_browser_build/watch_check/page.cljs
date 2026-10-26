(ns groundwork-browser-build.watch-check.page
  "The page module the watching test starts pages with, on the root."
  (:require [groundwork-browser-build.api :as styles]))

(def ^:private page-styles (styles/use-stylesheet "groundwork_browser_build/watch_check/page.css"))

(defn start! [] (js/console.log "watch-page-here" page-styles))
