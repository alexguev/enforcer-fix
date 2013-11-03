(ns enforcer-fix.core
  (:require [clojure.java.io :refer [file]]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            ; [clojure.data.zip :refer [children]]
            [clojure.data.zip.xml :refer [xml-> xml1-> text]]))

;; the parent pom dependencies are added to the pom of every child project
;; resolve version from properties
;; resolve version from dependencyManagement
;; resolve version from property {project.version}

;; use project to resolve dependencies of project dependencies
;; use local maven repo to resolve dependencies of project dependencies

(declare parse)

(defn parse-module [pom module-xml]
  (let [module-name (text module-xml)
        module-pom-file (file (.getParent (:pom pom)) module-name "pom.xml")]
    (parse pom module-pom-file)))

(defn assoc-modules [pom modules-xml]
  (assoc pom :modules (into [] (map (partial parse-module pom) modules-xml))))

;; FIXME we are resolving version rather than property here
(defn resolve-property [pom dependency]
  (update-in dependency [:version]
             (fn [v]
               (when v
                 (let [k (second (re-find #"\{(.+)\}" v))]
                   (or (get-in pom [:properties k]) v)))
               )))

; FIXME one resolve-version that resolves a version from either properties or dependencyManagement
(defn resolve-version [pom dependency]
  (let [k-fn (juxt :groupId :artifactId)
        m (group-by k-fn (:dependencyManagement pom))]  ;; FIXME optimize don't calculate this for every dependency
    (if-let [d (first (get m (k-fn dependency)))]
      (assoc dependency :version (:version d))
      dependency)))

(defn resolve-project-version [pom {:keys [version] :as dependency}]
  (if (= "${project.version}" version)
    (assoc dependency :version (:version pom))
    dependency))

(defn parse-dependency [dependency-xml]
  {:groupId (xml1-> dependency-xml :groupId text)
   :artifactId (xml1-> dependency-xml :artifactId text)
   :version (xml1-> dependency-xml :version text)})

(defn parse-dependencies [pom parent-pom dependencies-xml]
  (->> dependencies-xml
       (map parse-dependency)
       (map (partial resolve-property pom))   ;; FIXME better names for these three
       (map (partial resolve-version pom))
       (map (partial resolve-project-version pom))
       ))

(defn assoc-dependencies [pom parent-pom k dependencies-xml]
  (assoc pom k (into (or (get parent-pom k) [])
                     (parse-dependencies pom parent-pom dependencies-xml))))


(defn parse-property [property-xml]
  [(name (:tag property-xml)) (first (:content property-xml))])


(defn assoc-properties [pom parent-pom properties-xml]
  (assoc pom :properties (merge (:properties parent-pom)
                                (apply hash-map (mapcat parse-property properties-xml)))))

(defn parse [parent-pom pom-file]
  (let [pom-xml (zip/xml-zip (xml/parse pom-file))]
    (-> {:pom pom-file}
        (assoc :groupId (or (xml1-> pom-xml :groupId text) (:groupId parent-pom)))
        (assoc :artifactId (or (xml1-> pom-xml :artifactId text) (:artifactId parent-pom)))
        (assoc :version (or (xml1-> pom-xml :version text) (:version parent-pom)))
        (assoc-properties parent-pom (xml-> pom-xml :properties zip/children))
        (assoc-dependencies parent-pom :dependencyManagement (xml-> pom-xml :dependencyManagement :dependencies :dependency))
        (assoc-dependencies parent-pom :dependencies (xml-> pom-xml :dependencies :dependency))
        (assoc-modules (xml-> pom-xml :modules :module))
        )))
