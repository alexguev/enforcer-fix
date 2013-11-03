(ns enforcer-fix.core-test
  (:use clojure.test
        clojure.java.io
        [clojure.data :refer [diff]]
        enforcer-fix.core))

(def zing-core-common {:pom (file "resources/zing-core/zing-core-common/pom.xml")
                       :groupId "foo.baz"
                       :artifactId "zing-core-common"
                       :version "1.0.0"
                       :modules []
                       :dependencies [{:groupId "super.library" :artifactId "core" :version "1.0.0"}
                                      {:groupId "net.tools" :artifactId "core" :version "1.0.0"}]
                       })

(def zing-core-batch {:pom (file "resources/zing-core/zing-core-batch/pom.xml")
                      :groupId "foo.baz"
                      :artifactId "zing-core-batch"
                      :version "1.0.0"
                      :modules []
                      :dependencies [{:groupId "super.library" :artifactId "core" :version "1.0.0"}
                                     {:groupId "foo.baz" :artifactId "zing-core-common" :version "1.0.0"}]
                      })

(def zing-core {:pom (file "resources/zing-core/pom.xml")
                :groupId "foo.baz"
                :artifactId "zing-core"
                :version "1.0.0"
                :dependencies [{:groupId "super.library" :artifactId "core" :version "1.0.0"}]
                :modules [zing-core-common zing-core-batch]})

(def zing {:pom (file "resources/pom.xml")
           :groupId "foo.baz"
           :artifactId "zing"
           :version "1.0.0"
           :dependencies [{:groupId "super.library" :artifactId "core" :version "1.0.0"}]
           :modules [zing-core]})

(deftest test-parse
  (testing "parse:
    - zing
            -zing-core
                       - zing-core-common
                       - zing-core-batch"
    (is (= (parse {} (file "resources/pom.xml"))
           zing))))


;(parse {} (file "/Users/alguevara/codigo/kijiji.ca/pom.xml"))


(parse {} (file "resources/pom.xml"))

(let [p (parse {} (file "resources/pom.xml"))]
  (->> p
       :modules
       first
       :modules
       first))


(run-tests)