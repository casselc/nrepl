(ns nrepl.target-assert
  "Fail-closed target assertion for hosted source-mode runtime gates."
  (:require [jolt.host :as host]))

(defn assert-expected-target!
  []
  (when-let [expected (System/getenv "JOLT_EXPECTED_ARCH")]
    (let [target (host/target)
          actual (name (:arch target))]
      (when-not (= expected actual)
        (throw
         (ex-info
          "Jolt target architecture does not match the native runtime gate"
          {:expected expected
           :actual actual
           :target target})))
      (println (str "verified Jolt target: " (pr-str target))))))
