(defproject com.gfredericks.forks.org.clojure/test.check "0.5.9-78b7cf8"
  :description "A QuickCheck inspired property-based testing library."
  :url "https://github.com/clojure/test.check"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[codox "0.8.10"]]
  :deploy-repositories [["releases" :clojars]]
  :codox {:defaults {:doc/format :markdown}})
