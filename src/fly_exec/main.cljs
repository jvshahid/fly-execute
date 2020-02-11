(ns fly-exec.main
  (:require
   [fly-exec.core :as fly]
   [type-args :as args]
   [clojure.core.async :refer [<! >! chan go map]]
   [clojure.string :as string]
   [js-yaml :as yaml]
   [process]
   [child_process :as proc]))

(def home (.-HOME process/env))

(defn- parse-args [args]
  (let [options     {:help {:alias "h"
                            :description "Display this help"
                            :type "boolean"}
                     :target {:alias "t"
                              :desc "The fly target"
                              :type "string"}
                     :pipeline {:alias "p"
                                :desc "The pipeline"
                                :type "string"}
                     :job {:alias "j"
                           :desc "The job and task you wish to execute, e.g. job/task"
                           :type "string"}
                     :input {:alias "i"
                             :desc "The inputs to override"
                             :type "string[]"}
                     :workspace {:alias "w"
                                 :desc "The workspace relative to which the inputs will be specified"
                                 :type "string"
                                 :default (str home "/workspace")}}
        parsed-args (args/parse (clj->js args)
                                (clj->js options))]
    (js->clj parsed-args :keywordize-keys true)))

(defn- exec
  "Execute the given CMD and return a channel with the output"
  [cmd]
  (let [output (chan)]
    (proc/exec cmd (fn [err stdout stderr]
                     (if err
                       (throw err)
                       (go (>! (or err output) stdout)))))
    output))

(defn- get-pipeline [target pipeline]
  (map (comp #(js->clj %1 :keywordize-keys true) yaml/load)
       [(exec (str "fly -t " target " gp -p " pipeline))]))

(defn- print-help-and-exit []
  (println "Printing help")
  (process/exit 1))

(defn -main [& args]
  (let [[{:keys [target workspace pipeline job input help] :as args} _ others] (parse-args args)]
    (when help
      (print-help-and-exit))
    (go
      (let [concourse-pipeline   (<! (get-pipeline target pipeline))
            task                 (fly/find-task concourse-pipeline job)
            [job-name task-name] (string/split job #"/")]
        (println (string/join " " (concat (fly/task-params task)
                                          (fly/fly-flags target workspace pipeline job-name task)
                                          (fly/task-priv-flag task)
                                          (fly/task-image-flag task)
                                          (fly/task-inputs workspace input)
                                          (fly/task-input-mappings task))))))))



