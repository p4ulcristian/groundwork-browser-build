(ns groundwork-browser-build.pages-check.qr
  "A module only ever loaded on demand, with a stylesheet and a start."
  (:require [groundwork-browser-build.api :as styles]))

(def ^:private qr-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/qr.css"))

(js/console.log "qr-module-here" qr-styles)

(defn start! [] (js/console.log "qr-start-here"))
