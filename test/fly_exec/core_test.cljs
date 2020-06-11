(ns fly-exec.core-test
  (:require-macros [fly-exec.core-test])
  (:require
   [clojure.test :refer [deftest is successful?]]
   [fly-exec.core :as fly]
   [fs]
   [js-yaml :as yaml]))

(def pipeline
  (js->clj (yaml/load (fs/readFileSync "test/fly_exec/fixtures/pipeline.yml"))
           :keywordize-keys true))

(deftest find-task
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")]
    (is (not (nil? task)))
    (is (= "some-task"
           (:task task)))))

(deftest task-tags
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")
        tags (fly/task-tags task)]
    (is (= '("--tag='tag1'" "--tag='tag2'")
           tags))))

(deftest task-params
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")
        params (fly/task-params task)]
    (is (= '("foo='bar'")
           params))))

(deftest fly-flags
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")
        flags (fly/fly-flags "target" "~/workspace" "pipeline" "job" task)]
    (is (= '("fly"
             "-t" "target"
             "execute"
             "-c" "~/workspace/some-input/jobs/job-with-tasks/tasks/some-task/task.yml"
             "-j" "pipeline/job")
           flags))))

(deftest task-outputs
  (let [task      {:file "task.yml"}
        workspace "test/fly_exec/fixtures/"
        outputs   (fly/task-outputs workspace task)]
    (is (= (list "-o" (str "some-output=" workspace "/some-output"))
           outputs))))

(deftest task-priv-flag
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")
        priv (fly/task-priv-flag task)]
    (is (= '("-p")
           priv))))

(deftest task-image-flag
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")
        image (fly/task-image-flag task)]
    (is (= '("--image=input-1")
           image))))

(deftest task-inputs
  (let [inputs (fly/task-inputs "~/workspace" '("input-1"))]
    (is (= '("-i" "input-1=~/workspace/input-1")
           inputs))))

(deftest task-input-mappings
  (let [task (fly/find-task pipeline "job-with-tasks/some-task")
        mappings (fly/task-input-mappings task)]
    (is (= '("-m" "task-input=input-2")
           mappings))))

(defn -main [&args]
  (clojure.test/run-tests))
