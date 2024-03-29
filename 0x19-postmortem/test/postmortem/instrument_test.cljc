(ns postmortem.instrument-test
  (:require [clojure.test :refer [deftest is testing]]
            [postmortem.core :as pm]
            [postmortem.instrument :as pi]
            [postmortem.test-ns :as test-ns :refer [f g h]]
            [postmortem.xforms :as xf]))

(deftest ^:eftest/synchronized basic-workflow-test
  (is (= [`f] (pi/instrument `f)))
  (f 1)
  (f 2)
  (f 3)
  (is (= '[{:args (1)} {:args (1) :ret 2}
           {:args (2)} {:args (2) :ret 3}
           {:args (3)} {:args (3) :ret 4}]
         (pm/log-for `f)))
  (pm/reset!)

  (try (f -1) (f 0) (catch #?(:clj Throwable :cljs :default) _))
  (is (= `[{:args (-1)} {:args (-1) :ret 0} {:args (0)} {:args (0) :err ~test-ns/err}]
         (pm/log-for `f)))
  (pm/reset!)

  (is (= [`f] (pi/unstrument)))
  (f 1)
  (is (= nil (pm/log-for `f)))
  (pm/reset!))

(deftest ^:eftest/synchronized transducers-test
  (is (= [`f] (pi/instrument `f {:xform (filter (fn [{[x] :args}] (odd? x)))})))
  (mapv f [1 2 3 4 5])
  (is (= '[{:args (1)} {:args (1) :ret 2}
           {:args (3)} {:args (3) :ret 4}
           {:args (5)} {:args (5) :ret 6}]
         (pm/log-for `f)))
  (pm/reset!)

  (pi/instrument `f {:xform (xf/take-last 3)})
  (mapv f [1 2 3 4 5])
  (is (= '[{:args (4) :ret 5} {:args (5)} {:args (5) :ret 6}]
         (pm/log-for `f)))
  (pm/reset!)

  (is (= [`f] (pi/unstrument `f))))

(deftest ^:eftest/synchronized session-test
  (let [sess (pm/make-session)]
    (is (= [`f] (pi/instrument `f {:session sess})))
    (f 1)
    (f 2)
    (f 3)
    (is (= '[{:args (1)} {:args (1) :ret 2}
             {:args (2)} {:args (2) :ret 3}
             {:args (3)} {:args (3) :ret 4}]
           (pm/log-for sess `f)))
    (is (= nil (pm/log-for `f)))
    (is (= [`f] (pi/unstrument)))))

(deftest ^:eftest/synchronized multiple-fns-test
  (testing "instrument and unstrument accepts a coll of symbols"
    (is (= `[g h] (pi/instrument `[g h])))
    (g 3)
    (is (= '[{:args (3)} {:args (1)} {:args (1) :ret 2} {:args (3) :ret 8}]
           (pm/log-for `g)))
    (is (= '[{:args (2)} {:args (0)} {:args (0) :ret 0} {:args (2) :ret 6}]
           (pm/log-for `h)))
    (pm/reset!)
    (is (= `[g h] (pi/unstrument `[g h]))))

  (testing "If a symbol identifies ns, all symbols in that ns will be enumerated"
    (is (= `#{test-ns/f test-ns/g test-ns/h} (set (pi/instrument 'postmortem.test-ns))))
    (is (= `#{test-ns/f test-ns/g test-ns/h} (set (pi/unstrument 'postmortem.test-ns)))))

  (testing "xform will be applied to functions that were instrumented at once"
    (pi/instrument `[g h] {:xform (filter :ret)})
    (g 3)
    (is (= '[{:args (1) :ret 2} {:args (3) :ret 8}]
           (pm/log-for `g)))
    (is (= '[{:args (0) :ret 0} {:args (2) :ret 6}]
           (pm/log-for `h)))
    (pm/reset!)
    (is (= `#{g h} (set (pi/unstrument)))))

  (testing "session will be shared among functions that were instrumented at once"
    (let [sess (pm/make-session)]
      (pi/instrument `[g h] {:session sess})
      (g 3)
      (is (= '[{:args (3)} {:args (1)} {:args (1) :ret 2} {:args (3) :ret 8}]
             (pm/log-for sess `g)))
      (is (= '[{:args (2)} {:args (0)} {:args (0) :ret 0} {:args (2) :ret 6}]
             (pm/log-for sess `h)))
      (is (= nil (pm/log-for `g)))
      (is (= nil (pm/log-for `h)))
      (pi/unstrument))))

(defmulti eval* :type)
(defmethod eval* :literal [{:keys [val]}]
  val)
(defmethod eval* :add [{:keys [lhs rhs]}]
  (+ (eval* lhs) (eval* rhs)))
(defmethod eval* :mul [{:keys [lhs rhs]}]
  (* (eval* lhs) (eval* rhs)))

(def ast
  {:type :add
   :lhs {:type :mul
         :lhs {:type :literal :val 2}
         :rhs {:type :literal :val 3}}
   :rhs {:type :literal :val 1}})

(deftest ^:eftest/synchronized multimethod-test
  (is (= [`eval*] (pi/instrument `eval*)))
  (eval* ast)
  (is (= '[{:args ({:type :add
                    :lhs {:type :mul
                          :lhs {:type :literal :val 2}
                          :rhs {:type :literal :val 3}}
                    :rhs {:type :literal :val 1}})}
           {:args ({:type :mul
                    :lhs {:type :literal :val 2}
                    :rhs {:type :literal :val 3}})}
           {:args ({:type :literal :val 2})}
           {:args ({:type :literal :val 2}) :ret 2}
           {:args ({:type :literal :val 3})}
           {:args ({:type :literal :val 3}) :ret 3}
           {:args ({:type :mul
                    :lhs {:type :literal :val 2}
                    :rhs {:type :literal :val 3}})
            :ret 6}
           {:args ({:type :literal :val 1})}
           {:args ({:type :literal :val 1}) :ret 1}
           {:args ({:type :add
                    :lhs {:type :mul
                          :lhs {:type :literal :val 2}
                          :rhs {:type :literal :val 3}}
                    :rhs {:type :literal :val 1}})
            :ret 7}]
         (pm/log-for `eval*)))
  (pm/reset-key! `eval*)

  (pi/instrument `eval* {:xform (filter #(= :literal (apply :type (:args %))))})
  (eval* ast)
  (is (= '[{:args ({:type :literal :val 2})}
           {:args ({:type :literal :val 2}) :ret 2}
           {:args ({:type :literal :val 3})}
           {:args ({:type :literal :val 3}) :ret 3}
           {:args ({:type :literal :val 1})}
           {:args ({:type :literal :val 1}) :ret 1}]
         (pm/log-for `eval*)))

  (is (= [`eval*] (pi/unstrument `eval*)))
  (pm/reset!))

(declare my-odd?)

(defn my-even? [n]
  (or (= n 0)
      (my-odd? (dec n))))

(defn my-odd? [n]
  (and (not= n 0)
       (my-even? (dec n))))

(deftest ^:eftest/synchronized depth-test
  (pi/instrument `[my-even? my-odd?] {:with-depth true})
  (my-even? 5)

  (is (= '[{:depth 1 :args (5)}
           {:depth 3 :args (3)}
           {:depth 5 :args (1)}
           {:depth 5 :args (1) :ret false}
           {:depth 3 :args (3) :ret false}
           {:depth 1 :args (5) :ret false}]
         (pm/log-for `my-even?)))
  (is (= '[{:depth 2 :args (4)}
           {:depth 4 :args (2)}
           {:depth 6 :args (0)}
           {:depth 6 :args (0) :ret false}
           {:depth 4 :args (2) :ret false}
           {:depth 2 :args (4) :ret false}]
         (pm/log-for `my-odd?)))

  (pi/unstrument `[my-even? my-odd?])
  (pm/reset!))

