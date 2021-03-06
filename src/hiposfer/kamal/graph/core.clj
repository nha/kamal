(ns hiposfer.kamal.graph.core
  (:require [hiposfer.kamal.graph.protocols :as rp])
  (:import (java.util HashMap Map AbstractMap$SimpleImmutableEntry)
           (clojure.lang Seqable IReduceInit IReduce Sequential IPersistentVector APersistentMap)
           (org.teneighty.heap FibonacciHeap Heap Heap$Entry)))

;; ----- utility functions
(defn node? [coll] (and (satisfies? rp/Context coll)
                        (satisfies? rp/Binder coll)
                        (satisfies? rp/Incoherent coll)))

;; NOTE: for convenience we represent the edges as a directed outgoing arc
;;       and reverse it only if necessary (see predecesors)
(defn edge? [coll] (and (satisfies? rp/Link coll)
                        (satisfies? rp/Bidirectional coll)))

(defn arc? [coll] (satisfies? rp/Link coll))

(def graph? "returns true if x implements IPersistentMap" map?)

(defn nodes
  "returns the node objects (no id) of graph. The order of the elements is not
   guaranteed"
  [graph]
  (vals graph))

(defn successors
  "returns a lazy sequence of outgoing arcs. The order of the elements is not
  guaranteed"
  [graph]
  (sequence (mapcat rp/successors) (vals graph)))

(defn predecessors
  "returns a lazy sequence of incoming arcs. The order of the elements is not
  guaranteed"
  [graph]
  (sequence (mapcat rp/predecessors) (vals graph)))

(defn edges
  "returns a lazy sequence of bidirectional arcs (those that conform to edge?)
  in their original direction i.e. no mirrored Links are returned. The order
  of the elements is not guaranteed"
  ([graph]
   (sequence (mapcat edges) (repeat graph) (keys graph)))
  ([graph node-id]
   (sequence (comp (filter edge?)
                   (map #(if (rp/mirror? %) (rp/mirror %) %)))
             (rp/successors (graph node-id)))))

(defn connect
  "connect the nodes src and dst of link"
  [graph link] ;; we assume that both src and dst nodes exists
  (-> (update graph (rp/src link) rp/outbound link)
      (update       (rp/dst link) rp/inbound  link)))

(defn disconnect
  "remove the link between src and dst of link"
  [graph link]
  (let [src (rp/src link)
        dst (rp/dst link)]
    (cond-> graph
      (contains? graph src) (update src rp/disconnect link)
      (contains? graph dst) (update dst rp/disconnect link))))

(defn attach
  "assoc id to node and connect all of its links to the graph. All nodes
  id used by the links MUST exist"
  [graph id node]
  (let [graph2 (assoc graph id node)
        graph3 (reduce connect graph2 (rp/edges node))
        graph4 (reduce connect graph3 (remove edge? (rp/successors node)))]
    (reduce connect graph4 (remove edge? (rp/predecessors node)))))

(defn detach
  "removes a node and all its connections from the graph"
  [graph id]
  (let [graph2 (reduce disconnect graph (rp/successors (graph id)))
        graph3 (reduce disconnect graph2 (rp/predecessors (graph2 id)))]
    (dissoc graph3 id)))

;; -------------------------------
; graph is an {id node}
; NodeInfo is a {:lon :lat :links {node-id arc}}
; Arc is a {:src :dst :way-id}
(defrecord Edge [^long src ^long dst ^long way]
  rp/Link
  (src [_] src)
  (dst [_] dst)
  rp/Passage
  (way [_] way)
  rp/Bidirectional
  (mirror [this]
    (if (:mirror? this)
      (dissoc this :mirror?) ; remove mirror from edge
      (map->Edge {:src dst :dst src :way way :mirror? true})))
  (mirror? [this] (:mirror? this)))


(defrecord Arc [^long src ^long dst ^long way]
  rp/Link
  (src [_] src)
  (dst [_] dst)
  rp/Passage
  (way [_] way))

;; This implementation of NodeInfo only accepts one Link per src/dst node
(defrecord NodeInfo [^double lon ^double lat outgoing incoming]
  rp/Context
  (predecessors [this] (concat (sequence (comp (filter edge?)
                                               (map rp/mirror))
                                         (vals (:outgoing this)))
                               (vals (:incoming this))))
  (successors   [this] (concat (sequence (comp (filter edge?)

                                               (map rp/mirror))
                                         (vals (:incoming this)))
                               (vals (:outgoing this))))
  rp/GeoCoordinate
  (lat [_] lat)
  (lon [_] lon)
  rp/Binder
  (inbound  [this arc-or-edge]
    (assoc-in this [:incoming (rp/src arc-or-edge)] arc-or-edge))
  (outbound [this arc-or-edge]
    (assoc-in this [:outgoing (rp/dst arc-or-edge)] arc-or-edge))
  rp/Incoherent
  (disconnect [this arc-or-edge]
    (-> (update this :outgoing dissoc (rp/dst arc-or-edge))
        (update      :incoming dissoc (rp/src arc-or-edge)))))

;; a Point is a simple longitude, latitude pair used to
;; represent the geometry of a way
(defrecord Point [^double lon ^double lat]
  rp/GeoCoordinate
  (lat [_] lat)
  (lon [_] lon))

;; useful for geojson coordinates manipulation
(extend-type IPersistentVector
  rp/GeoCoordinate
  (lat [this] (second this))
  (lon [this] (first this)))

;; this is specially useful for generative testing: there we use generated
;; nodes and arcs
(extend-type APersistentMap
  rp/Context
  (predecessors [this] (concat (sequence (comp (filter edge?)
                                               (map rp/mirror))
                                         (vals (:outgoing this)))
                               (vals (:incoming this))))
  (successors   [this] (concat (sequence (comp (filter edge?)
                                               (map rp/mirror))
                                         (vals (:incoming this)))
                               (vals (:outgoing this))))
  rp/GeoCoordinate
  (lat [this] (:lat this))
  (lon [this] (:lon this))
  rp/Binder
  (inbound  [this arc-or-edge]
    (assoc-in this [:incoming (rp/src arc-or-edge)] arc-or-edge))
  (outbound [this arc-or-edge]
    (assoc-in this [:outgoing (rp/dst arc-or-edge)] arc-or-edge))
  rp/Incoherent
  (disconnect [this arc-or-edge]
    (-> (update this :outgoing dissoc (rp/dst arc-or-edge))
        (update      :incoming dissoc (rp/src arc-or-edge))))
  ;; arc representation
  rp/Link
  (src [this] (:src this))
  (dst [this] (:dst this))
  rp/Passage
  (way [this] (:way this)))

;; the simplest way to represent the Cost in a graph traversal
(extend-type Number
  rp/Valuable
  (cost [this] this)
  (sum  [this that] (+ this that)))

;; ------------------ DIJKSTRA CORE -------------------

(defmacro trace [k v] `(new AbstractMap$SimpleImmutableEntry ~k ~v))

(defn- init!
  "returns a new MUTABLE fibonacci heap (priority queue) and adds all the
   sources id to the beginning of the queue and to the settled map."
  ^Heap [init-set ^Map settled]
  (let [queue  ^Heap (new FibonacciHeap)]
    (run! (fn [id] (->> (trace id nil)
                        (.insert queue 0)
                        (.put settled id)))
          init-set)
    queue))

(defn- path
  "returns a lazy sequence of immutable map entries starting at from and
  going back until no previous entry is found"
  [^Map settled from]
  (let [entry ^Heap$Entry (.get settled from)
        prev-id           (val (.getValue entry))]
    (cons (trace from (.getKey entry))
          (lazy-seq (when prev-id (path settled prev-id))))))

(defn- relax!
  "calculate the weight of traversing arc and updates it if already in
  the queue or adds it otherwise"
  [value f ^Map settled ^Map unsettled ^Heap$Entry entry ^Heap queue trail node-arcs]
  (if (empty? node-arcs) nil
    (if (.containsKey settled (f (first node-arcs)))
      (recur value f settled unsettled entry queue trail (rest node-arcs))
      (let [arc     (first node-arcs)
            prev-id (key (.getValue entry))
            weight  (rp/sum (value arc trail)
                            (.getKey entry))
            id      (f arc)
            trace2  (trace id prev-id)
            old-entry ^Heap$Entry (.get unsettled id)]
        (if (nil? old-entry)
          (.put unsettled id (.insert queue weight trace2))
          (when (< weight (.getKey old-entry))
            (.setValue old-entry trace2)
            (.decreaseKey queue old-entry weight)))
        (recur value f settled unsettled entry queue trail (rest node-arcs))))))

(defn- produce!
  "returns a lazy sequence of traces by sequentially mutating the
   queue and always returning the path from the latest min priority
   node"
  [graph value arcs f ^Heap queue ^Map settled ^Map unsettled]
  (let [entry  (.extractMinimum queue)
        id     (key (.getValue entry))
        _      (.put settled id entry)
        _      (.remove unsettled id)
        trail  (path settled id)]
    (relax! value f settled unsettled entry queue trail (arcs (graph id)))
    trail))

; inspired by http://insideclojure.org/2015/01/18/reducible-generators/
; A Collection type which can reduce itself faster than first/next traversal over
; its lazy representation. For convenience a lazy implementation is also provided.
;
; The Dijkstra algorithm implemented here works as follows:
; 1 - take a set of start node, assign them a weight of zero and add them as
;     initialization arguments to a priority queue as trace instances
; 2 - poll the trace with the lowest cost from the priority queue
; 3 - if there are no more traces - STOP
; 4 - otherwise call the reducing function on the trace
; 5 - if the value returned is a reduced flag - STOP
; 6 - otherwise get the outgoing or incoming arcs of the current node and
;     add them to the priority queue
; 6.1 - create a new trace by adding the current trace cost with the delta
;       returned by the value function
; 7 - repeat steps 2 to 6 until a STOP condition is reached
;
; From the previous description it should be clear that this implementation does
; not have a fixed stop condition. Therefore it is (hopefully) very flexible
; regarding is usefulness.
; Some possible uses are:
; - single source - single destination shortest path
; - multi source - single destination shortest path
; - multi source - multi destination shortest path
; - single source - any/all destination shortest path
; - shortest path with timeout
;
; the elements necessary to initialize a Dijkstra collection are
; - graph: a {id node} mapping
; - ids: a #{ids}
; - value: a function of current-arc, current-trace -> Valuable implementation
; - arcs: a function of node -> [arc]. Used to get either the incoming or outgoing arcs of a node
; - f: a function of Arc -> id. Used to get the id of the src or dst of an Arc
(deftype Dijkstra [graph ids value arcs f]
  Seqable
  (seq [_]
    (let [settled   (new HashMap) ;{id {weight {id prev}}}
          queue     (init! ids settled) ;[{weight {id prev}}]
          unsettled (new HashMap); {id {weight {id prev}}}
          trailer!  (fn trailer! []
                      (if (.isEmpty queue) (list)
                        (cons (produce! graph value arcs f queue settled unsettled)
                              (lazy-seq (trailer!)))))]
      (trailer!)))
  ;; ------
  ;; this implementation uses mutable internal data structures but exposes only
  ;; immutable data structures.
  ;; Inspired by: http://www.keithschwarz.com/interesting/code/?dir=dijkstra
  IReduceInit
  (reduce [_ rf init]
    ;; Heap.Entry -> {weight {id prev}}
    (let [settled   (new HashMap); {id Heap.Entry}
          queue     (init! ids settled); [Heap.Entry]
          unsettled (new HashMap)]; {id Heap.Entry}
      (loop [ret init
             trail (produce! graph value arcs f queue settled unsettled)]
        (let [rr (rf ret trail)]
          (if (reduced? rr) @rr
            (if (.isEmpty queue) rr
              (recur rr (produce! graph value arcs f queue settled unsettled))))))))
  ;; ------
  IReduce
  (reduce [this rf] (.reduce ^IReduceInit this rf (rf)))
  ;; declaring as Sequential will cause the seq to be used for nth, etc
  Sequential)