; author: Lukas Chrpa

(define (domain LSTS)
(:requirements :typing :durative-actions :fluents :equality :timed-initial-literals)
(:types location vehicle payload interest task - object
        area oi - interest
        auv - vehicle
        camera sidescan multibeam ctd rhodamine edgetech - payload
)
(:predicates (at ?v - vehicle ?l - location) ;position of the vehicle
             (base ?v - vehicle ?l - location) ;base (depot) of the vehicle
             (at_oi ?o - oi ?l - location) ; position of the object of interest
             (entry ?a - area ?l - location) ;entry point to the area of interest
             (exit  ?a - area ?l - location) ;exit point to the area of interest
             (having ?p - payload ?v - vehicle) ;the vehicle has the payload
             (task_desc ?t - task ?i - interest ?p - payload) ; a description of the task that sample (or survey) the point of interest by the payload
	     (sampled ?t - task ?v - vehicle) ;obtained data from sampling or surveilance by the vehicle
             ;(surveyed ?v - vehicle ?a - area)
             ;(communicated_data ?t - task) ;data are acquired for the vehicle
             (completed ?t - task) ;task ?t has been completed
             (free ?l - location) ;no vehicle in the location
             (available ?a - area) ;no vehicle is surveiling the area
             (can-move ?v - vehicle) ;vehicle can perform the move action
             (ready ?v - vehicle) ;vehicle is ready to be released
)

(:functions (distance ?l1 ?l2 - location)
            (surveillance_distance ?a - area)
            (speed ?v - vehicle)
            (base-returns) ; "cost" of returning to the depots (each time some vehicle moves to its depot the value is incremented by 1000)
            (from-base ?v - vehicle) ; how long the vehicle hasn't been in its depot
            (max-to-base ?v - vehicle) ; maximum time for the vehicle to be outside its depot
            (tasks-completed)
         ;   (battery-level ?v - vehicle)
         ;   (battery-consumption-move ?v - vehicle) ;battery consumption of the vehicle per 1 distance unit
         ;   (battery-consumption-payload ?p - payload) ;;battery consumption of the payload per 1 time unit
)

(:durative-action move-to-oi
:parameters (?v - vehicle ?l1 ?l2 - location ?o - oi)
:duration (= ?duration (/ (distance ?l1 ?l2)(speed ?v)))
:condition (and (at start (at ?v ?l1))
                (at start (can-move ?v))
                (at end (free ?l2))
                (over all (at_oi ?o ?l2))
                (over all (not (= ?l1 ?l2)))
                (over all (<= (from-base ?v)(max-to-base ?v)))
                ;(at start (< (+ (from-base ?v)(/ (distance ?l1 ?l2)(speed ?v))) (max-to-base ?v)))
                ;(at start (>= (battery-level ?v)(* (battery-consumption-move ?v)(distance ?l1 ?l2))))
           )
:effect (and (at start (not (at ?v ?l1)))
             (at end (at ?v ?l2))
             (at start (free ?l1))
             (at end (not (free ?l2)))
             (at end (not (can-move ?v)))
             (at start (increase (from-base ?v)(/ (distance ?l1 ?l2)(speed ?v))))
             ;(at start (decrease (battery-level ?v)(* (battery-consumption-move ?v)(distance ?l1 ?l2))))
        )
)

(:durative-action move-to-area
:parameters (?v - vehicle ?l1 ?l2 - location ?a - area)
:duration (= ?duration (/ (distance ?l1 ?l2)(speed ?v)))
:condition (and (at start (at ?v ?l1))
                (at start (can-move ?v))
                (at end (free ?l2))
                (over all (entry ?a ?l2))
                (over all (not (= ?l1 ?l2)))
                (over all (<= (from-base ?v)(max-to-base ?v)))
                ;(at start (< (+ (from-base ?v)(/ (distance ?l1 ?l2)(speed ?v))) (max-to-base ?v)))
                ;(at start (>= (battery-level ?v)(* (battery-consumption-move ?v)(distance ?l1 ?l2))))
           )
:effect (and (at start (not (at ?v ?l1)))
             (at end (at ?v ?l2))
             (at start (free ?l1))
             (at end (not (free ?l2)))
             (at end (not (can-move ?v)))
             (at start (increase (from-base ?v)(/ (distance ?l1 ?l2)(speed ?v))))
             ;(at start (decrease (battery-level ?v)(* (battery-consumption-move ?v)(distance ?l1 ?l2))))
        )
)

(:durative-action move-to-base
:parameters (?v - vehicle ?l1 ?l2 - location)
:duration (= ?duration (/ (distance ?l1 ?l2)(speed ?v)))
:condition (and (at start (at ?v ?l1))
                (at start (can-move ?v))
                (at end (free ?l2))
                (over all (base ?v ?l2))
                (over all (not (= ?l1 ?l2)))
               ; (over all (<= (from-base ?v)(max-to-base ?v)))
                (at start (<= (+ (from-base ?v)(/ (distance ?l1 ?l2)(speed ?v))) (max-to-base ?v)))
                ;(at start (>= (battery-level ?v)(* (battery-consumption-move ?v)(distance ?l1 ?l2))))
           )
:effect (and (at start (not (at ?v ?l1)))
             (at end (at ?v ?l2))
             (at start (free ?l1))
             (at end (not (free ?l2)))
             (at end (not (can-move ?v)))
             ;(at start (increase (base-returns) (* 10 (- (max-to-base ?v)(+ (from-base ?v)(/ (distance ?l1 ?l2)(speed ?v)))))))
             ;(at start (increase (base-returns) 1000))
             ;(at end (assign (from-base ?v) 0))
             ;(at start (decrease (battery-level ?v)(* (battery-consumption-move ?v)(distance ?l1 ?l2))))
        )
)

(:durative-action sample
:parameters (?v - vehicle ?l - location ?t -task ?o -oi ?p - payload)
:duration (= ?duration 60)
:condition (and (over all (at_oi ?o ?l))
                (at start (task_desc ?t ?o ?p))
                (over all (at ?v ?l))
                (over all (having ?p ?v))
                (at start (not (completed ?t)))
                (over all (<= (from-base ?v)(max-to-base ?v)))
                ;(at start (< (+ (from-base ?v) 60) (max-to-base ?v)))
              ;  (at start (>= (battery-level ?v)(* (battery-consumption-payload ?p) 10)))
           )
:effect (and (at end (sampled ?t ?v))
             (at end (can-move ?v))
             (at end (completed ?t))
             (at end (not (task_desc ?t ?o ?p)))
             (at end (increase (tasks-completed) 1))
             (at start (increase (from-base ?v) 60))
            ; (at start (decrease (battery-level ?v)(* (battery-consumption-payload ?p) 10)))
        )
)

(:durative-action survey-one-payload
:parameters (?v - vehicle ?l1 ?l2 - location ?t -task ?a -area ?p - payload)
:duration (= ?duration (/ (surveillance_distance ?a)(speed ?v)))
:condition (and (over all (entry ?a ?l1))
                (over all (exit ?a ?l2))
                (over all (having ?p ?v))
                (at start (task_desc ?t ?a ?p))
                (at start (at ?v ?l1))
                (at end (free ?l2))
                (at start (available ?a))
                (at start (not (completed ?t)))
                (over all (<= (from-base ?v)(max-to-base ?v)))
                ;(at start (< (+ (from-base ?v)(/ (surveillance_distance ?a)(speed ?v))) (max-to-base ?v)))
                ;(at start (>= (battery-level ?v)(+ (* (battery-consumption-move ?v)(surveillance_distance ?a))(* (battery-consumption-payload ?p) (/ (surveillance_distance ?a)(speed ?v))))))
           )
:effect (and (at end (sampled ?t ?v))
             (at start (not (available ?a)))
             (at end (available ?a))
             (at start (not (at ?v ?l1)))
             (at end (at ?v ?l2))
             (at start (free ?l1))
             (at end (not (free ?l2)))
             (at end (can-move ?v))
             (at end (completed ?t))
             (at end (not (task_desc ?t ?a ?p)))
             (at end (increase (tasks-completed) 1))
             (at start (increase (from-base ?v)(/ (surveillance_distance ?a)(speed ?v))))
             ;(at start (decrease (battery-level ?v)(+ (* (battery-consumption-move ?v)(surveillance_distance ?a))(* (battery-consumption-payload ?p) (/ (surveillance_distance ?a)(speed ?v))))))
         )
)

(:durative-action survey-two-payload
:parameters (?v - vehicle ?l1 ?l2 - location ?t ?t2 -task ?a -area ?p ?p2 - payload)
:duration (= ?duration (/ (surveillance_distance ?a)(speed ?v)))
:condition (and (over all (entry ?a ?l1))
                (over all (exit ?a ?l2))
                (over all (having ?p ?v))
                (at start (task_desc ?t ?a ?p))
                (over all (having ?p2 ?v))
                (at start (task_desc ?t2 ?a ?p2))
                (over all (not (= ?t ?t2)))
                (at start (at ?v ?l1))
                (at end (free ?l2))
                (at start (available ?a))
                (at end (can-move ?v))
                (at start (not (completed ?t)))
                (over all (<= (from-base ?v)(max-to-base ?v)))
                ;(at start (< (+ (from-base ?v)(/ (surveillance_distance ?a)(speed ?v))) (max-to-base ?v)))
                ;(at start (>= (battery-level ?v)(+ (* (battery-consumption-move ?v)(surveillance_distance ?a))(* (+ (battery-consumption-payload ?p)(battery-consumption-payload ?p2)) (/ (surveillance_distance ?a)(speed ?v))))))
          )
:effect (and (at end (sampled ?t ?v))
             (at end (sampled ?t2 ?v))
             (at start (not (available ?a)))
             (at end (available ?a))
             (at start (not (at ?v ?l1)))
             (at end (at ?v ?l2))
             (at start (free ?l1))
             (at end (not (free ?l2)))
             (at end (completed ?t))
             (at end (not (task_desc ?t ?a ?p)))
             (at end (not (task_desc ?t ?a ?p2)))
             (at end (increase (tasks-completed) 2))
             (at start (increase (from-base ?v)(/ (surveillance_distance ?a)(speed ?v))))
             ;(at start (decrease (battery-level ?v)(+ (* (battery-consumption-move ?v)(surveillance_distance ?a))(* (+ (battery-consumption-payload ?p)(battery-consumption-payload ?p2)) (/ (surveillance_distance ?a)(speed ?v))))))
       )
)

(:durative-action survey-three-payload
:parameters (?v - vehicle ?l1 ?l2 - location ?t ?t2 ?t3 -task ?a -area ?p ?p2 ?p3 - payload)
:duration (= ?duration (/ (surveillance_distance ?a)(speed ?v)))
:condition (and (over all (entry ?a ?l1))
                (over all (exit ?a ?l2))
                (over all (having ?p ?v))
                (at start (task_desc ?t ?a ?p))
                (over all (having ?p2 ?v))
                (at start (task_desc ?t2 ?a ?p2))
                (over all (having ?p3 ?v))
                (at start (task_desc ?t3 ?a ?p3))
                (over all (not (= ?t ?t2)))
                (over all (not (= ?t ?t3)))
                (over all (not (= ?t3 ?t2)))
                (at start (at ?v ?l1))
                (at end (free ?l2))
                (at start (available ?a))
                (at end (can-move ?v))
                (at start (not (completed ?t)))
                (over all (<= (from-base ?v)(max-to-base ?v)))
                ;(at start (< (+ (from-base ?v)(/ (surveillance_distance ?a)(speed ?v))) (max-to-base ?v)))
                ;(at start (>= (battery-level ?v)(+ (* (battery-consumption-move ?v)(surveillance_distance ?a))(* (+ (battery-consumption-payload ?p)(+ (battery-consumption-payload ?p2)(battery-consumption-payload ?p3))) (/ (surveillance_distance ?a)(speed ?v))))))
           )
:effect (and (at end (sampled ?t ?v))
             (at end (sampled ?t2 ?v))
             (at end (sampled ?t3 ?v))
             (at start (not (available ?a)))
             (at end (available ?a))
             (at start (not (at ?v ?l1)))
             (at end (at ?v ?l2))
             (at start (free ?l1))
             (at end (not (free ?l2)))
             (at end (completed ?t))
             (at end (not (task_desc ?t ?a ?p)))
             (at end (not (task_desc ?t ?a ?p2)))
             (at end (not (task_desc ?t ?a ?p3)))
             (at end (increase (tasks-completed) 3))
             (at start (increase (from-base ?v)(/ (surveillance_distance ?a)(speed ?v))))
             ;(at start (decrease (battery-level ?v)(+ (* (battery-consumption-move ?v)(surveillance_distance ?a))(* (+ (battery-consumption-payload ?p)(+ (battery-consumption-payload ?p2)(battery-consumption-payload ?p3))) (/ (surveillance_distance ?a)(speed ?v))))))
        )
)

;(:durative-action communicate
;:parameters (?v - vehicle ?l - location ?t - task)
;:duration (= ?duration 60)
;:condition (and (over all (base ?v ?l))
;                (over all (at ?v ?l))
;                (at start (sampled ?t ?v))
;                (at start (can-move ?v))
;           )
;:effect (and (at end (communicated_data ?t))
;             (at start (not (can-move ?v)))
;             (at end (can-move ?v))
;             (at end (not (sampled ?t ?v)))
;        )
;)

;dummy action
(:action getready
:parameters (?v - vehicle)
:precondition (ready ?v)
:effect (and (not (ready ?v))(can-move ?v))
)

)
