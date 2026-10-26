(ns groundwork-browser-build.api
  "How the page looks: a namespace names the stylesheets it needs, as a
   query names its .sql file; and a module loaded on demand comes with its
   stylesheets before its code.

     (ns app.browser.orders.order-card
       (:require [groundwork-browser-build.api :as styles]))

     (def order-card-styles (styles/use-stylesheet \"app/orders/order_card.css\"))

     [:div {:class \"order-card\"} …]   ; classes are plain text, the CSS plain CSS

     ;; a module of the build's :modules the page didn't start with, when it needs it:
     (-> (styles/load-module! {\"admin\" \"/css/admin.3F9A….css\"} \"admin\")
         (.then (fn [_] …)))   ; its code has run

   use-stylesheet is a macro (groundwork-browser-build.api's .clj half): it runs while
   compiling, so a missing file stops the build at that line. The build
   gathers each module's named stylesheets into its bundle, in the order
   the namespaces are found; the page links the bundles of the module it
   starts with and of those it depends on
   (groundwork-browser-build.page's files-for-page): /css/<module>.css
   while watching (saving a stylesheet updates the open page), a hashed
   name when built.

   load-module! loads a module through shadow-cljs's module loader, as
   script tags (never code fetched and evaluated: a content security
   policy without eval allows them), and links its bundle, and those of
   the modules loading with it, first: the code runs once they are in, so
   nothing shows unstyled. A bundle that fails to load doesn't hold the
   code back: a screen without its styles beats no screen."
  (:require-macros [groundwork-browser-build.api])
  (:require
   [shadow.loader :as loader]))

(defonce ^:private known-stylesheets (atom {}))

(defn- is-stylesheet-linked?
  "Whether the page links this stylesheet already, whatever a watch added
   to its address since (a saved one is swapped in under ?r=…)."
  [address]
  (let [path (.-pathname (js/URL. address js/document.baseURI))]
    (boolean (some #(= path (.-pathname (js/URL. (.-href %))))
                   (array-seq (js/document.querySelectorAll "link[rel=\"stylesheet\"]"))))))

(defn- link-stylesheet!
  "A promise, settled once the stylesheet has loaded or failed to."
  [address]
  (if (is-stylesheet-linked? address)
    (js/Promise.resolve nil)
    (js/Promise.
     (fn [resolve _]
       (let [link (js/document.createElement "link")]
         (set! (.-rel link) "stylesheet")
         (set! (.-href link) address)
         (set! (.-onload link) #(resolve nil))
         (set! (.-onerror link) #(resolve nil))
         (.appendChild js/document.head link))))))

(defn- link-stylesheets-of-modules! [module-ids]
  (js/Promise.all (to-array (for [module-id module-ids
                                  :let [address (get @known-stylesheets module-id)]
                                  :when address]
                              (link-stylesheet! address)))))

(defonce ^:private module-loader
  ;; the page's module loader, set up once: script tags, stylesheets first
  (delay
    (let [^js module-loader (loader/getModuleLoader)
          load-modules      (.-loadModules module-loader)]
      (.setUseScriptTags module-loader true)
      (set! (.-loadModules module-loader)
            (fn [ids module-infos options]
              (-> (link-stylesheets-of-modules! (array-seq ids))
                  (.then (fn [_] (.call load-modules module-loader ids module-infos options))))))
      module-loader)))

(defn load-module!
  "Loads a module of the build's :modules, by name, and the modules it
   depends on, each once (those the page started with are loaded
   already): their stylesheets (stylesheets-by-module, each module's
   bundle's address, {\"admin\" \"/css/admin.3F9A….css\"}, which the page
   carries: groundwork-browser-build.page's stylesheets-for-load-module)
   first, then their code. A promise, settled once the code has run, or
   failed."
  [stylesheets-by-module module-name]
  (swap! known-stylesheets merge stylesheets-by-module)
  @module-loader
  (js/Promise. (fn [resolve reject]
                 (.addCallbacks (loader/load module-name) resolve reject))))
