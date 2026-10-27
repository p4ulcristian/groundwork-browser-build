(ns groundwork-browser-build.gzip-test
  "A file's gzipped copy: GZIPOutputStream's format, at the level asked."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [groundwork-browser-build.gzip :as gzip])
  (:import
   (java.io ByteArrayOutputStream)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.util Arrays)
   (java.util.zip Deflater DeflaterOutputStream GZIPInputStream)))

(defn- read-bytes ^bytes [file] (Files/readAllBytes (.toPath (io/file file))))

(defn- deflate-raw
  "The bytes deflated at level, with no zlib or gzip wrapping."
  ^bytes [^bytes data level]
  (let [out      (ByteArrayOutputStream.)
        deflater (Deflater. (int level) true)]
    (with-open [deflated (DeflaterOutputStream. out deflater)]
      (.write deflated data))
    (.end deflater)
    (.toByteArray out)))

(deftest a-gzipped-copy-is-gzip-at-its-level
  (let [folder (str (Files/createTempDirectory "browser-build-gzip" (make-array FileAttribute 0)))
        file   (io/file folder "main.js")
        text   (apply str (for [i (range 4000)] (str "function f" (mod (* i 7919) 1000) "(a){return a*" i ";}\n")))]
    (spit file text)
    (doseq [level [1 6 9]]
      (gzip/write-gzipped-copy! file level)
      (let [gzipped (read-bytes (str file ".gz"))
            n       (alength gzipped)]
        (testing (str "level " level)
          (is (= text (with-open [in (GZIPInputStream. (io/input-stream (str file ".gz")))] (slurp in)))
              "it reads back as the file")
          (is (= [0x1f 0x8b 8 0 0 0 0 0 0 0xff] (map #(bit-and 0xff %) (take 10 gzipped)))
              "the header: no name, no time, so the same file gives the same bytes")
          (is (Arrays/equals (deflate-raw (.getBytes text "UTF-8") level) (Arrays/copyOfRange gzipped 10 (- n 8)))
              "the body is the file deflated at that level"))))
    (testing "a higher level, fewer bytes"
      (gzip/write-gzipped-copy! file 1)
      (let [fast (alength (read-bytes (str file ".gz")))]
        (gzip/write-gzipped-copy! file 9)
        (is (< (alength (read-bytes (str file ".gz"))) fast))))
    (testing "the same file, the same bytes"
      (gzip/write-gzipped-copy! file 9)
      (let [first-copy (read-bytes (str file ".gz"))]
        (gzip/write-gzipped-copy! file 9)
        (is (Arrays/equals first-copy (read-bytes (str file ".gz"))))))
    (testing "an empty file"
      (spit file "")
      (gzip/write-gzipped-copy! file 9)
      (is (= "" (with-open [in (GZIPInputStream. (io/input-stream (str file ".gz")))] (slurp in)))))))
