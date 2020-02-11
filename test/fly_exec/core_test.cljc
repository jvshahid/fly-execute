(ns fly-exec.core-test
  (:require
   [cljs.test :refer [successful?]]
   [process]))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (not (successful? m))
    (process/exit 1)))
