(ns user.generate-cheatsheet)

(def content
  '[{:section-header "Simple Generators"
     :vars [{:code (gen/return x)
             :args {x "any value"}
             :doc "A constant generator that always generates `x`."}
            {:code gen/boolean
             :doc "Generates booleans (`true` and `false`)."}
            {:code gen/nat
             :doc "Generates small non-negative integers (useful for generating sizes of things)."}
            {:code gen/large-integer
             :doc "Generates a large range of integers"}]}])

(defn convert-backticks
  [s]
  (clojure.string/replace s #"`(.*?)`" (fn [[_ s]]
                                         (str "<code>" s "</code>"))))

(defn print-markdown
  []
  (println "# test.check cheatsheet\n\n<table>")
  (doseq [{:keys [section-header vars]} content]
    (printf "<thead><th colspan=\"3\">%s</th></thead>\n" section-header)
    (println "<thead><th>Thing</th><th>Args</th><th>What it do</th></thead>")
    (doseq [{:keys [code args doc]} vars]
      (printf "<tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>\n"
              (pr-str code)
              (if args
                (clojure.string/join "<br />"
                                     (for [[k v] args]
                                       (format "<code>%s</code> - %s" k v)))
                "N/A")
              (convert-backticks doc))))
  (println "</table>"))

(spit "doc/cheatsheet.md" (with-out-str (print-markdown)))
