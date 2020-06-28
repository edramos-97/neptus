package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.data.Pair;

import java.io.Serializable;
import java.util.*;


public class Dictionary<K, V, E extends Pair<K,V>> extends CRDT implements Serializable {

    public ImcId16 myId = ImcMsgManager.getManager().getLocalId();

    public Set<Tuple<E, Long, ImcId16>> set;
    public Map<ImcId16, Long> versionVector;

    VectorClock clock = new VectorClock();

    public Dictionary() {
        set = new HashSet<>();
        versionVector = new TreeMap<>();
        versionVector.put(myId, clock.value());
    }

    public Dictionary(Set<E> existingSet) {
        set = new HashSet<>();
        versionVector = new TreeMap<>();
        HashSet<E> tempSet = new HashSet<E>(existingSet);
        tempSet = (HashSet<E>) tempSet.clone();
        for (E e : tempSet) {
            add(e);
        }
    }

    public boolean lookup(E element) {
        Set<Tuple<E, Long, ImcId16>> result = Operations.filtered(set, tuple -> tuple.getElement().equals(element));
        return !result.isEmpty();
    }

    public void add(E element) {
        if(lookup(element)){
            return;
        }
        clock.incrementVal();

        if (clock.value() > versionVector.get(myId)) {
            set = Operations.filtered(set, new Operations.Predicate<Tuple<E, Long,
                    ImcId16>>() {
                @Override
                public boolean call(Tuple<E, Long, ImcId16> tuple) {
                    return !tuple.getElement().first().equals(element.first()) || tuple.getTime() > clock.value();
                }
            });
            set.add(new Tuple<>(element, clock.value(), myId));
            versionVector.put(myId, clock.value());
        }
    }

    public void remove(E element) {
        set = Operations.filtered(set, new Operations.Predicate<Tuple<E, Long, ImcId16>>() {
            @Override
            public boolean call(Tuple<E, Long, ImcId16> tuple) {
                return !tuple.getElement().equals(element);
            }
        });
    }

    public Dictionary<K, V, E> merge(Dictionary<K, V, E> anotherSet) {
//        Set<Tuple<E, Long, ImcId16>> temp = new HashSet<>(set);
//        temp.retainAll(anotherSet.set);
//        temp = anotherSet.set;
        Set<Tuple<E, Long, ImcId16>> temp = Operations.filtered(anotherSet.set, new Operations.Predicate<Tuple<E, Long,
                ImcId16>>() {
            @Override
            public boolean call(Tuple<E, Long, ImcId16> tuple) {
                for (Tuple<E, Long, ImcId16> innerTuple : set) {
                    E innerElem = innerTuple.getElement();
                    E elem = tuple.getElement();
                    if (innerElem.equals(elem)) {
                        return true;
                    }
                }
                return false;
            }
        });

        Set<Tuple<E, Long, ImcId16>> temp1 = Operations.filtered(Operations
                .diff(set, anotherSet.set), tuple -> {
            Long anotherSetVal = anotherSet.versionVector.get(tuple.getReplicaId());
            if (anotherSetVal == null) {
                return tuple.getTime() > 0;
            } else {
                return tuple.getTime() > anotherSetVal;
            }
        });

        Set<Tuple<E, Long, ImcId16>> temp2 = Operations.filtered(Operations
                .diff(anotherSet.set, set), tuple -> {
            Long setVal = versionVector.get(tuple.getReplicaId());
            if (setVal == null) {
                return tuple.getTime() > 0;
            } else {
                return tuple.getTime() > setVal;
            }
        });
        Set<Tuple<E, Long, ImcId16>> union = Operations.union(Operations.union(temp, temp1), temp2);
        Set<Tuple<E, Long, ImcId16>> overflow = Operations.filtered(union, new Operations.Predicate<Tuple<E, Long,
                ImcId16>>() {
            @Override
            public boolean call(Tuple<E, Long, ImcId16> tuple) {
                return !Operations.filtered(union, new Operations.Predicate<Tuple<E, Long, ImcId16>>() {
                    @Override
                    public boolean call(Tuple<E, Long, ImcId16> tuple1) {
                        if (tuple.getElement().equals(tuple1.getElement()) && tuple.getReplicaId()
                                .equals(tuple1.getReplicaId()) || tuple.getElement().first().equals(tuple1.getElement().first())) {
                            return tuple.getTime() > tuple1.getTime();
                        } else {
                            return false;
                        }
                    }
                }).isEmpty();
            }
        });
        set = Operations.diff(union, overflow);

        // Keep max val from both set's version vectors
        for (Map.Entry<ImcId16, Long> entry : anotherSet.versionVector.entrySet()) {
            if (versionVector.containsKey(entry.getKey())) {
                if (versionVector.get(entry.getKey()) < entry.getValue()) {
                    versionVector.put(entry.getKey(), entry.getValue());
                }
            } else {
                versionVector.put(entry.getKey(), entry.getValue());
            }
        }

        return this;
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return null;
    }

    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id, String genericType) {
        LinkedHashMap<String, Object> map = new LinkedHashMap();
//        map.put("set", Operations.mapped(set, new Operations.Mapper<Tuple<E, Long, ImcId16>, String>() {
//            @Override
//            public String call(Tuple<E, Long, ImcId16> element) {
//                IMCMessage msg = element.getElement();
//                String id;
//                if (msg instanceof PlanManeuver) {
//                    id = ((PlanManeuver) msg).getManeuverId();
//                } else if (msg instanceof PlanTransition) {
//                    id = ((PlanTransition) msg).getSourceMan();
//                } else {
//                    id = "";
//                }
//                return "(" + id + "," + element.getTime() + "," + element
//                        .getReplicaId() +
//                       ")";
//            }
//        }));
//        map.put("msg_set", Operations.mapped(set, new Operations.Mapper<Tuple<E, Long, ImcId16>, IMCMessage>() {
//            @Override
//            public IMCMessage call(Tuple<E, Long, ImcId16> element) {
//                return element.getElement();
//            }
//        }));
        map.put("set", set);
        map.put("versionVector", versionVector);
        map.put("type", genericType);
        return map;
    }

    @Override
    public Set<E> payload(Object... params) {
        return Collections.unmodifiableSet(Operations.mapped(set, Tuple::getElement));
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        return null;
    }

    public CRDT updateFromNetwork(String dataString, Collection<E> dataSet) {
        LinkedHashMap<String, Object> dataMap = parseDataString(dataString);

        HashMap<String, Tuple<E, Long, ImcId16>> emptyTuples =
                (HashMap<String, Tuple<E, Long, ImcId16>>) dataMap.get("set");

        HashSet<Tuple<E, Long, ImcId16>> tuples = new HashSet<>();

//        for (E e : dataSet) {
//        }

        dataMap.put("set",tuples);

        return updateFromNetwork(dataMap);
    }

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String, ?> dataObject) {
        set = (Set<Tuple<E, Long, ImcId16>>) dataObject.get("set");
        versionVector = (Map<ImcId16, Long>) dataObject.get("versionVector");

        return this;
    }

    // EXTRA LOGIC
    private LinkedHashMap<String, Object> parseDataString(String dataString) {
        String[] split = dataString.substring(1, dataString.length() - 1).split(", (?=[a-zA-Z])");
        LinkedHashMap<String, Object> dataMap = new LinkedHashMap<>();
        for (String s : split) {
            String[] entry = s.split("=", 2);
            dataMap.put(entry[0], entry[1]);
        }

        HashMap<String, Tuple<String, Long, ImcId16>> tuples = new HashMap<>();
        String setString = (String) dataMap.get("set");
        String[] setElems = setString.substring(2, setString.length() - 2).split("\\), \\(");
        for (String setElemStr : setElems) {
            String[] tupleElemsStr = setElemStr.split(",");
            Tuple<String, Long, ImcId16> tempTuple = new Tuple<>(null, Long
                    .parseLong(tupleElemsStr[1]), new ImcId16(tupleElemsStr[2]));
            tuples.put(tupleElemsStr[0], tempTuple);
        }
        dataMap.put("set", tuples);

        HashMap<ImcId16, Long> versionVector = new HashMap<>();
        String versionVectorString = (String) dataMap.get("versionVector");
        String[] vectorElems = versionVectorString.substring(1, versionVectorString.length() - 1).split(", ");
        for (String vectorElemStr : vectorElems) {
            String[] mapElemsStr = vectorElemStr.split("=");
            versionVector.put(new ImcId16(mapElemsStr[0]), Long.parseLong(mapElemsStr[1]));
        }
        dataMap.put("versionVector", versionVector);
        return dataMap;
    }

    static class Tuple<E, T, I> implements Serializable {
        E element;
        T time;
        I replicaId;

        public Tuple(E element, T time, I replicaId) {
            this.element = element;
            this.time = time;
            this.replicaId = replicaId;
        }

        public E getElement() {
            return element;
        }

        public T getTime() {
            return time;
        }

        public I getReplicaId() {
            return replicaId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != this.getClass()) return false;

            return ((Tuple) obj).getElement().equals(element) &&
                   ((Tuple) obj).getReplicaId().equals(replicaId) &&
                   ((Tuple) obj).getTime().equals(time);
        }

        @Override
        public int hashCode() {
            String temp = replicaId.toString() + time.toString() + element.toString();
            return temp.hashCode();
        }
    }

    class VectorClock implements Serializable {
        long counter = 0;

        long value() {
            return counter;
        }

        long incrementVal() {
            return ++counter;
        }

        void set(long val) {
            counter = val;
        }
    }
}
