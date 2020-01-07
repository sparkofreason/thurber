(ns thurber.java.exp.calendar
  (:require [clojure.test :refer :all]
            [thurber :as th])
  (:import (org.apache.beam.sdk.testing TestPipeline PAssert)
           (org.apache.beam.sdk.transforms Create)
           (org.apache.beam.sdk.transforms.windowing Window IntervalWindow)
           (thurber.java.exp CalendarDayWindowFn CalendarDaySlidingWindowFn)
           (org.joda.time DateTimeZone DateTime Instant)
           (org.apache.beam.sdk.values TimestampedValue)))

(defn- ^Instant dt->instant*
  ([tz-id ^Integer year ^Integer month ^Integer day ^Integer hour ^Integer minute]
   (dt->instant* tz-id year month day hour minute 0 0))
  ([tz-id ^Integer year ^Integer month ^Integer day ^Integer hour ^Integer minute ^Integer second ^Integer millis]
   (-> (DateTime. year month day hour minute second millis)
     (.withZoneRetainFields
       (DateTimeZone/forID tz-id))
     (.withZone (DateTimeZone/forID "UTC"))
     (.toInstant))))

(defn- ->tz* [elem]
  (if-let [tz ^String (:tz elem)]
    (DateTimeZone/forID tz)
    (throw (RuntimeException. (format "no tz: %s" elem)))))

(deftest test-calendar-windows
  (let [p (-> (TestPipeline/create)
            (.enableAbandonedNodeEnforcement true))
        output (th/apply! p
                 (->
                   (Create/timestamped
                     [(TimestampedValue/of {:key :chic :tz "America/Chicago" :color "red"}
                        (dt->instant* "America/Chicago" 2020 12 20 0 0))
                      (TimestampedValue/of {:key :chic :tz "America/Chicago" :color "blue"}
                        (dt->instant* "America/Chicago" 2020 12 20 10 30))
                      (TimestampedValue/of {:key :losa :tz "America/Los_Angeles" :color "green"}
                        (dt->instant* "America/Chicago" 2020 12 20 0 30))])
                   (.withCoder th/nippy))
                 {:th/name "cal-day-window*"
                  :th/xform
                  (Window/into
                    (CalendarDayWindowFn/forTimezoneFn #'->tz*))})
        output-simple (th/apply! output :color)
        output-sliding (th/apply! "sliding:"
                         output
                         {:th/name "sliding-window*"
                          :th/xform
                          (Window/into
                            (CalendarDaySlidingWindowFn/forSizeInDaysAndTimezoneFn (int 30) #'->tz*))}
                         :color)]
    (-> output-simple
      (PAssert/that)
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Chicago" 2020 12 20 0 0)
                   (dt->instant* "America/Chicago" 2020 12 21 0 0)))
      (.containsInAnyOrder ["red" "blue"])
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Los_Angeles" 2020 12 19 0 0)
                   (dt->instant* "America/Los_Angeles" 2020 12 20 0 0)))
      (.containsInAnyOrder ["green"]))
    (-> output-sliding
      (PAssert/that)
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Chicago" 2020 11 20 0 0)
                   (dt->instant* "America/Chicago" 2020 12 20 0 0)))
      (.empty)
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Chicago" 2020 11 21 0 0)
                   (dt->instant* "America/Chicago" 2020 12 21 0 0)))
      (.containsInAnyOrder ["red" "blue"])
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Chicago" 2020 12 20 0 0)
                   (dt->instant* "America/Chicago" 2021 1 19 0 0)))
      (.containsInAnyOrder ["red" "blue"])
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Chicago" 2020 11 21 0 0)
                   (dt->instant* "America/Chicago" 2021 1 20 0 0)))
      (.empty)
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Los_Angeles" 2020 11 19 0 0)
                   (dt->instant* "America/Los_Angeles" 2020 12 19 0 0)))
      (.empty)
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Los_Angeles" 2020 11 20 0 0)
                   (dt->instant* "America/Los_Angeles" 2020 12 20 0 0)))
      (.containsInAnyOrder ["green"])
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Los_Angeles" 2020 12 19 0 0)
                   (dt->instant* "America/Los_Angeles" 2021 1 18 0 0)))
      (.containsInAnyOrder ["green"])
      (.inWindow (IntervalWindow.
                   (dt->instant* "America/Los_Angeles" 2020 12 20 0 0)
                   (dt->instant* "America/Los_Angeles" 2021 1 19 0 0)))
      (.empty))
    (-> (.run p)
      (.waitUntilFinish))))
