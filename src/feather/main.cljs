(ns feather.main
  (:require
   [type-args :as args]
   [clojure.core.async :refer [<! >! chan go map]]
   [clojure.string :refer [join]]
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

(defn- task-params [task]
  (let [params (:params task)]
    (for [[param value] params]
      (str (name param)
           "="
           value))))

(defn- fly-flags [target workspace pipeline job task]
  (let [file (:file task)]
    (list "fly -t" target
          "execute -c" (str workspace "/" file)
          "-j" (str pipeline "/" job))))

(defn- task-priv-flag [task]
  (let [privileged? (:privileged task)]
    (if privileged?
      (list "-p"))))

(defn- task-image-flag [task]
  (let [image (:image task)]
    (if image
      (list (str "--image=" image)))))

(defn- task-inputs [workspace inputs]
  (for [input inputs]
    (str "-i " input "=" workspace "/" input)))

(defn- task-input-mappings [task]
  (let [input-mappings (:input_mapping task)]
    (for [[new-name res] input-mappings]
      (str "-m "
           (name new-name)        ; convert keyword to a string
           "=" res))))

(defn- print-help-and-exit []
  (println "Printing help")
  (process/exit 1))

(defn -main [& args]
  (let [[{:keys [target workspace pipeline job input help] :as args} _ others] (parse-args args)
        [job task] (clojure.string/split job #"/")]
    (when help
      (print-help-and-exit))
    (go
      (let [concourse-pipeline    (<! (get-pipeline target pipeline))
            pipeline-jobs         (:jobs pipeline)
            concourse-job         (first (filter #(= job (:name %))
                                                 pipeline-jobs))
            concourse-task        (first (filter #(= task (:task %))
                                                 (:plan concourse-job)))]
        (println (join " " (concat (task-params task)
                                   (fly-flags target workspace pipeline job task)
                                   (task-priv-flag task)
                                   (task-image-flag task)
                                   (task-inputs workspace input)
                                   (task-input-mappings task))))))))



