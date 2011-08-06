(defproject mirror-music "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [commons-io/commons-io "1.4"]
                 [org/jaudiotagger "2.0.3"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.0"]]
  :aot [mirror-music]
  :repositories {"java.net" "http://download.java.net/maven/2"}
  ;; :disable-implicit-clean true
  :manifest {"Main-Class" MirrorMusic})
