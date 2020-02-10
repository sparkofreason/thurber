(ns java
  (:require [badigeon.javac :as javac]))

(defn -main
  [& args]
  (javac/javac "src"
               {:compile-path "classes"}))
