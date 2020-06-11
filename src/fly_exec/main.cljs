(ns fly-exec.main
  (:require
   [fly-exec.core :as fly]
   [type-args :as args]
   [clojure.string :as string]
   [js-yaml :as yaml]
   [process]
   [child_process :as proc]))

(defn- default-workspace []
  (str (.-HOME process/env) "/workspace"))

(defn- parse-args [args]
  (let [options     {:help {:alias "h"
                            :type "boolean"}
                     :target {:alias "t"
                              :type "string"}
                     :pipeline {:alias "p"
                                :type "string"}
                     :job {:alias "j"
                           :type "string"}
                     :input {:alias "i"
                             :type "string[]"}
                     :workspace {:alias "w"
                                 :type "string"
                                 :default (default-workspace)}}
        parsed-args (args/parse (clj->js args)
                                (clj->js options))]
    (js->clj parsed-args :keywordize-keys true)))

(defn- exec
  "Execute the given CMD and return a channel with the output"
  [cmd]
  (proc/execSync cmd))

(defn- get-pipeline [target pipeline]
  (let [pipeline-yaml (exec (str "fly -t " target " gp -p " pipeline))]
    (js->clj (yaml/load pipeline-yaml)
             :keywordize-keys true)))

(defn- print-help-and-exit []
  ;; FLY_EXEC_PATH is set by the bash script in bin/fly-exec
  (let  [binary (.-FLY_EXEC_PATH process/env)
         usage "
Usage:
    $0 [options] [extra_arguments]

[options]
    -h                       Show this help message
    -t TARGET                Concourse TARGET name
    -p PIPELINE              Execute a task in this PIPELINE
    -j JOB/TASK              Execute the TASK in the given JOB
    -w WORKSPACE             The path to the workspace. Task inputs
                             and outputs will be assumed to reside
                             in the workspace.
    -i INPUT                 The INPUTs to provide to the task (can
                             be specified multiple times).

Examples:

    $ $0 -t ci -p pipeline -j job/task -i some-input
  ->
    PARAM1=VALUE1 fly -t ci -p pipeline -j job/task execute -c
    /path/to/workspace/repo/job/task/task.yml
    -i some-input=/path/to/workspace/some-input"]

    (println (string/replace usage "$0" binary))
    (process/exit 1)))

(defn -main [& args]
  (let [[{:keys [target workspace pipeline job input help] :as args} _ _] (parse-args args)]
    (when help
      (print-help-and-exit))
    (let [concourse-pipeline   (get-pipeline target pipeline)
          task                 (fly/find-task concourse-pipeline job)
          [job-name task-name] (string/split job #"/")]
      (println (string/join " " (concat (fly/task-params task)
                                        (fly/task-tags task)
                                        (fly/fly-flags target workspace pipeline job-name task)
                                        (fly/task-priv-flag task)
                                        (fly/task-image-flag task)
                                        (fly/task-inputs workspace input)
                                        (fly/task-outputs workspace task)
                                        (fly/task-input-mappings task)))))))
