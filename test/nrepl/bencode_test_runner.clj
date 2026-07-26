(ns nrepl.bencode-test-runner
  (:require [clojure.test :as test]
            [nrepl.bencode-test]))

(defn -main [& _]
  (let [result (test/run-tests 'nrepl.bencode-test)
        failures (+ (:fail result) (:error result))]
    (println
     {:status (if (zero? failures) :verified :failed)
      :runtime :jvm
      :tests (:test result)
      :assertions (:pass result)
      :failures (:fail result)
      :errors (:error result)})
    (System/exit (if (zero? failures) 0 1))))
