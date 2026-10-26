(ns groundwork-browser-build.pages-check.shop
  "A page module: the shop, which has the sheets with the page."
  (:require [groundwork-browser-build.api :as styles]
            [groundwork-browser-build.pages-check.sheets :as sheets]))

(def ^:private shop-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/shop.css"))
(def ^:private shared-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/shared.css"))

(defn start!
  "The shop's start, the app's start for its pages."
  []
  (js/console.log "shop-start-here" shop-styles shared-styles)
  (sheets/open-sheet! "cart"))
