(ns groundwork-browser-build.gzip
  "A file's gzipped copy, by the JDK's GZIPOutputStream at a level.

   GZIPOutputStream writes the format (the header with no name and no
   time, so the same file gives the same bytes; the deflated bytes; the CRC
   and the length) but takes no level: its Deflater is the protected field
   def, which only a subclass reaches. Clojure's subclass with a protected
   field exposed is gen-class's (:exposes), and gen-class makes a class
   only when compiling ahead of time; this library is taken from git as
   source. So the subclass is made as this namespace loads, by gen-class's
   own generator (clojure.core/generate-class, gen-class's work without the
   writing of a .class file), and defined in a class loader of its own. It
   adds nothing to the stream but a public deflater, which returns def:
   every method is GZIPOutputStream's (its implementation namespace has no
   functions), and the level is set before the first byte is written."
  (:require
   [clojure.java.io :as io])
  (:import
   (clojure.lang DynamicClassLoader Reflector)
   (java.io OutputStream)
   (java.util.zip Deflater)))

(def ^:private levelled-gzip-output-stream
  ;; groundwork_browser_build.gzip.LevelledGzipOutputStream: (new out), .deflater
  (let [options          '{:name          groundwork_browser_build.gzip.LevelledGzipOutputStream
                           :extends       java.util.zip.GZIPOutputStream
                           :implements    []
                           :constructors  {[java.io.OutputStream] [java.io.OutputStream]}
                           :init          nil
                           :post-init     nil
                           :state         nil
                           :methods       []
                           :exposes       {def {:get deflater}}
                           :exposes-methods {}
                           :factory       nil
                           :main          false
                           ;; a namespace never loaded and with no functions: every method is
                           ;; GZIPOutputStream's own
                           :impl-ns       groundwork-browser-build.gzip.no-overrides
                           :load-impl-ns  false
                           :prefix        "-"}
        [_ bytecode]     (#'clojure.core/generate-class options)]
    (.defineClass (DynamicClassLoader. (.getClassLoader DynamicClassLoader))
                  (str (:name options)) bytecode nil)))

(defn make-gzip-output-stream
  "A GZIPOutputStream onto out whose Deflater squeezes at level, 1 (fast)
   to 9 (smallest)."
  ^OutputStream [^OutputStream out level]
  (let [stream (Reflector/invokeConstructor levelled-gzip-output-stream (object-array [out]))]
    (.setLevel ^Deflater (Reflector/invokeInstanceMethod stream "deflater" (object-array 0)) (int level))
    stream))

(defn write-gzipped-copy!
  "file.gz beside the file, squeezed at level (1 to 9)."
  [file level]
  (with-open [in  (io/input-stream (io/file file))
              out (make-gzip-output-stream (io/output-stream (str file ".gz")) level)]
    (io/copy in out)))
