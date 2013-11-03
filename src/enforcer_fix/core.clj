(ns enforcer-fix.core
  (:require [clojure.java.io :refer [file]]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            ; [clojure.data.zip :refer [children]]
            [clojure.data.zip.xml :refer [xml-> xml1-> text]]))

;; the parent pom dependencies are added to the pom of every child project

;; resolve properties
;; resolve version from dependencyManagement


(declare parse)

(defn parse-module [pom module-xml]
  (let [module-name (text module-xml)
        module-pom-file (file (.getParent (:pom pom)) module-name "pom.xml")]
    (parse pom module-pom-file)))

(defn assoc-modules [pom modules-xml]
  (assoc pom :modules (into [] (map (partial parse-module pom) modules-xml))))

(defn parse-dependency [pom dependency-xml]
  {:groupId (xml1-> dependency-xml :groupId text)
   :artifactId (xml1-> dependency-xml :artifactId text)
   :version (xml1-> dependency-xml :version text)})

(defn assoc-dependencies [pom parent-pom dependencies-xml]
  (assoc pom :dependencies (into (or (:dependencies parent-pom) []) (map (partial parse-dependency pom) dependencies-xml))))

(defn parse [parent-pom pom-file]
  (let [pom-xml (zip/xml-zip (xml/parse pom-file))]
    (-> {:pom pom-file}
        (assoc :groupId (or (xml1-> pom-xml :groupId text) (:groupId parent-pom)))
        (assoc :artifactId (or (xml1-> pom-xml :artifactId text) (:artifactId parent-pom)))
        (assoc :version (or (xml1-> pom-xml :version text) (:version parent-pom)))
        (assoc-dependencies parent-pom (xml-> pom-xml :dependencies :dependency))
        (assoc-modules (xml-> pom-xml :modules :module))
        )))


(defn explode [dep]
  )

(defn dependencies [xml]
  (let [deps (xml-> xml :dependencies :dependency)]
    (explode (map #(hash-map :artifactId (text (xml1-> % :artifactId))
                     :groupId (text (xml1-> % :groupId))
                     :version (text (xml1-> % :version)))
          deps))))

(defn properties [xml]
  (let [props (xml-> xml :properties zip/children)]
    (reduce #(assoc %1 (str "${" (name (:tag %2)) "}") (first (:content %2)))
            {}
            props)))

(defn resolve-properties [props dep]
  (update-in dep [:version] #(if-let [v (get props %)] v %)))

(defn resolve-versions [vers dep]
  (update-in dep
             [:version]
             #(if-let [coll (get vers ((juxt :groupId :artifactId) dep))]
                (:versionId (first coll))
                %)))

(defn find-diverging-dependencies [props vers pom]
  (->> (parse pom)
       (dependencies)
       (map (partial resolve-properties props))
       (map (partial resolve-versions vers))
       (group-by (juxt :groupId :artifactId))
       (filter (fn [[_ v]] (> (count v) 1)))))

(defn find-all-diverging-dependencies [root-pom]
  (let [xml (parse root-pom)
        props (properties xml)
        vers {}
        projects [root-pom "core/box-core-batch/pom.xml"]]
    (map (partial find-diverging-dependencies props vers)
         projects)))



;;(find-all-diverging-dependencies "/Users/alguevara/codigo/kijiji.ca/pom.xml")
