(ns fly-exec.core
  (:require [clojure.string :as string]))

(defn find-task [pipeline job]
  (let [[job task]     (string/split job #"/")
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
           value))))

(defn fly-flags [target workspace pipeline job task]
  (let [file (:file task)]
    (list "fly"
          "-t" target
          "execute"
          "-c" (str workspace "/" file)
          "-j" (str pipeline "/" job))))

(defn task-priv-flag [task]
  (let [privileged? (:privileged task)]
    (if privileged?
      (list "-p"))))

(defn task-image-flag [task]
  (let [image (:image task)]
    (if image
      (list (str "--image=" image)))))

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

