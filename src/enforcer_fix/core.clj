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

(defn parse-dependency [dependency-xml]
  {:groupId (xml1-> dependency-xml :groupId text)
   :artifactId (xml1-> dependency-xml :artifactId text)
   :version (xml1-> dependency-xml :version text)})


(defn resolve-property [pom dependency]
  (update-in dependency [:version]
             (fn [v]
               (let [k (second (re-find #"\{(.*)\}" v))]
                 (or (get-in pom [:properties k]) v)))))

(defn parse-dependencies [pom parent-pom dependencies-xml]
  (->> dependencies-xml
       (map parse-dependency)
       (map (partial resolve-property pom))
       ))

(defn assoc-dependencies [pom parent-pom dependencies-xml]
  (assoc pom :dependencies (into (or (:dependencies parent-pom) [])
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
        (assoc-dependencies parent-pom (xml-> pom-xml :dependencies :dependency))
        (assoc-modules (xml-> pom-xml :modules :module))
        )))
