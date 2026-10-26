(ns groundwork-browser-build.pages-check.base
  "The root module of a build with several page modules: every page loads
   it first."
  (:require [groundwork-browser-build.api :as styles]))

(def ^:private base-styles (styles/use-stylesheet "groundwork_browser_build/pages_check/base.css"))

(defn start!
  "The root's :start, run on every page before the page's module."
  []
  (js/console.log "base-start-here" base-styles))
