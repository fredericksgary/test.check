(defproject com.gfredericks.forks.org.clojure/test.check "0.9.0-p1"
  :description "A QuickCheck inspired property-based testing library."
  :url "https://github.com/clojure/test.check"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :jvm-opts ^:replace ["-Xmx512m" "-server"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [org.clojure/clojurescript "1.7.48"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[codox "0.8.10"]
            [cider/cider-nrepl "0.8.1"]
            [lein-cljsbuild "1.1.0"]]
  :codox {:defaults {:doc/format :markdown}
          :exclude [clojure.test.check.rose-tree
                    clojure.test.check.clojure-test
                    clojure.test.check.properties]}
  :cljsbuild
  {:builds
   [{:id "node-dev"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/node"]
     :notify-command ["node" "resources/run.js"]
     :compiler {:optimizations :none
                :static-fns true
                :target :nodejs
                :output-to "target/cljs/node_dev/tests.js"
                :output-dir "target/cljs/node_dev/out"
                :source-map true}}
    {:id "browser-dev"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/browser"]
     :compiler {:optimizations :none
                :static-fns true
                :output-to "target/cljs/browser_dev/tests.js"
                :output-dir "target/cljs/browser_dev/out"
                :source-map true}}
    {:id "node-adv"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/node"]
     :notify-command ["node" "target/cljs/node_adv/tests.js"]
     :compiler {:optimizations :advanced
                :target :nodejs
                :pretty-print false
                :output-to "target/cljs/node_adv/tests.js"
                :output-dir "target/cljs/node_adv/out"}}
    {:id "browser-adv"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/browser"]
     :compiler {:optimizations :advanced
                :pretty-print false
                :output-to "target/cljs/browser_adv/tests.js"
                :output-dir "target/cljs/browser_adv/out"}}]})
