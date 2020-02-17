(ns fly-exec.core
  (:require [clojure.string :as string]
            [fs]
            [js-yaml :as yaml]))

(defn find-task [pipeline job+task]
  (let [[job task]     (string/split job+task #"/")
        pipeline-jobs  (:jobs pipeline)
        concourse-job  (first (filter #(= job (:name %))
                                      pipeline-jobs))
        concourse-task (first (filter #(= task (:task %))
                                      (:plan concourse-job)))]
    concourse-task))

(defn task-params [task]
  (let [params (:params task)]
    (for [[param value] params]
      (str (name param)
           "="
           "'" value "'"))))

(defn- task-file [workspace task]
  (let [file (:file task)]
    (str workspace "/" file)))

(defn fly-flags [target workspace pipeline job task]
  (list "fly"
        "-t" target
        "execute"
        "-c" (task-file workspace task)
        "-j" (str pipeline "/" job)))

(defn task-priv-flag [task]
  (let [privileged? (:privileged task)]
    (if privileged?
      (list "-p"))))

(defn task-image-flag [task]
  (let [image (:image task)]
    (if image
      (list (str "--image=" image)))))

(defn task-outputs
  "Return the task outputs flags given the WORKSPACE and the TASK yaml definition."
  [workspace task]
  (let [task-file (task-file workspace task)
        task-def (js->clj (yaml/load (fs/readFileSync task-file))
                          :keywordize-keys true)
        outputs (:outputs task-def)]
    (mapcat
     #(list "-o" (str (:name %1)
                      "=" workspace "/"
                      (:name %1)))
     outputs)))

(defn task-inputs [workspace inputs]
  (mapcat
   #(list "-i" (str %1 "=" workspace "/" %1))
   inputs))

(defn task-input-mappings [task]
  (let [input-mappings (:input_mapping task)]
    (apply concat
           (for [[new-name res] input-mappings]
             (list "-m" (str (name new-name)        ; convert keyword to a string
                             "=" res))))))

