(ns groundwork-browser-build.pages-check.site-sheets
  "The site's menu sheet, loaded on demand: the site's, on the shared
   sheets."
  (:require [groundwork-browser-build.pages-check.sheets :as sheets]))

(defn start!
  "Opens the menu once the module has loaded."
  []
  (sheets/open-sheet! "site-sheets-start-here"))
