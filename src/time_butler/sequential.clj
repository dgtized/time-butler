(ns time-butler.sequential
  "Helper functions for sequences")

(defn in-seq
  "Return a function for comparing a pair of nested values to see if they are
  sequential."
  [valuefn nextfn]
  (fn [[a b]]
    (= (nextfn (valuefn a)) (valuefn b))))

(defn collapse
  "Collapse a sequence `coll` by detecting any sequential pairs with `in-seq?`
  collapsing them with `range`.

  `in-seq?` should be a function detecting if an element pair is sequential.

    (collapse (fn [[a b]] (= (inc a) b)) identity [0 1 2 4]) => '((0 1 2) 4)
  "
  [in-seq? range coll]
  (cond (empty? coll) '()
        (and (in-seq? coll) (in-seq? (rest coll)))
        (let [sequential (->> (map vector coll (rest coll))
                              (take-while in-seq?)
                              (map second))
              length (inc (count sequential))]
          (cons (range (take length coll))
                (collapse in-seq? range (drop length coll))))
        :else
        (cons (first coll) (collapse in-seq? range (rest coll)))))
