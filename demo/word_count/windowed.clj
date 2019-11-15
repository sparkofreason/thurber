(ns word-count.windowed
  (:require [thurber :as th]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.tools.logging :as log]
            [word-count.basic])
  (:import (org.apache.beam.sdk.io TextIO)
           (org.joda.time Duration Instant)
           (org.apache.beam.sdk.transforms.windowing Window FixedWindows)))

;; clj-time is used because Beam standardizes on Joda time.

;; Thurber DoFn functions can take extra parameters at the front of their arg
;; lists. `th/partial*` is used to provide these serializable parameters,
;; usually config values, to such functions.
(defn- add-timestamp [{:keys [min-timestamp max-timestamp]} sentence]
  (let [random-timestamp (->> (rand-int (- max-timestamp min-timestamp))
                              (+ min-timestamp)
                              (Instant.))]
    ;; Instead of returning the value to emit, our DoFn functions can emit
    ;; to Beam directly. This is useful in this case for emitting with an
    ;; explicit timestamp. Note that by returning nil/void from our DoFn function,
    ;; Thurber will not emit any elements on our behalf.
    (.outputWithTimestamp th/*process-context* sentence random-timestamp)))

(defn- sink* [elem]
  ;; We can access an element's window from within a DoFn.
  (log/infof "%s in %s" elem th/*element-window*))

(defn- create-pipeline [opts]
  (let [pipeline (th/create-pipeline opts)
        conf (th/get-custom-config pipeline)]
    (doto pipeline
      (th/apply!
       (-> (TextIO/read)
           (.from ^String (:input-file conf)))
       (th/partial* #'add-timestamp conf)
       ;; Here we window into fixed windows. There is no need for Thurber to
       ;; to try to sugar-coat Beam window configuration; Clojure's Java interop
       ;; works perfectly fine in this case.
       (Window/into
        (FixedWindows/of
         (Duration/standardMinutes (:window-size conf))))
       word-count.basic/count-words-xf
       #'word-count.basic/format-as-text
       #'sink*))))

(defn demo! []
  (let [now (t/now)]
    (-> (create-pipeline
         {:custom-config {:input-file "lorem.txt"
                          :window-size 30
                          :min-timestamp (c/to-long (t/now))
                          :max-timestamp (c/to-long (t/plus now (t/hours 1)))}})
        (.run))))