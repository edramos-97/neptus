package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.plugins.dataSync.Operations;
import pt.lsts.neptus.types.XmlOutputMethods;
import pt.lsts.neptus.types.mission.TransitionType;

import java.io.Serializable;
import java.util.*;


/*
Implementation based on the work
Bieniusa, Annette, et al. "An optimized conflict-free replicated set." arXiv preprint arXiv:1210.3368 (2012).
*/
public class ORSet<E extends XmlOutputMethods> extends CRDT implements Serializable {

    ImcId16 myId = ImcMsgManager.getManager().getLocalId();

    Set<Tuple<E, Long, ImcId16>> set;
    Map<ImcId16, Long> versionVector;

    VectorClock clock = new VectorClock();

    public ORSet() {
        set = new HashSet<>();
        versionVector = new TreeMap<>();
        versionVector.put(myId, clock.value());
    }

    public ORSet(Set<E> existingSet) {
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
        clock.incrementVal();

        if (clock.value() > versionVector.get(myId)) {
            set = Operations.filtered(set, new Operations.Predicate<Tuple<E, Long,
                    ImcId16>>() {
                @Override
                public boolean call(Tuple<E, Long, ImcId16> tuple) {
                    return !tuple.getElement().equals(element) || tuple.getTime() > clock.value();
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

    public ORSet<E> merge(ORSet<E> anotherSet) {

        HashSet<Tuple<E, Long, ImcId16>> temp = new HashSet<>(set);
        temp.retainAll(anotherSet.set);

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
                                .equals(tuple1.getReplicaId())) {
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
        map.put("set", Operations.mapped(set, new Operations.Mapper<Tuple<E, Long, ImcId16>, Tuple<String, Long,
                ImcId16>>() {
            @Override
            public Tuple<String, Long, ImcId16> call(Tuple<E, Long, ImcId16> element) {
                return new Tuple<>(element.getElement().asXML(),element.getTime(), element.getReplicaId());
//                return new Tuple<>(element.getElement().toString(), element.getTime(), element.getReplicaId());
            }
        }));
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

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String, ?> dataObject) {
        String type = (String) dataObject.get("type");
        set = Operations.mapped((Set<Tuple<String, Long, ImcId16>>) dataObject
                .get("set"), new Operations.Mapper<Tuple<String, Long, ImcId16>,
                Tuple<E,
                        Long, ImcId16>>() {
            @Override
            public Tuple<E, Long, ImcId16> call(Tuple<String, Long, ImcId16> tuple) {
                E newElement;
                switch (type) {
                    case "maneuver":
                        newElement = (E) Maneuver.createFromXML(tuple.getElement());
                        break;
                    case "transitionType":
                        newElement = (E) TransitionType.createFromXml(tuple.getElement());
                        break;
//                    case "string":
//                        newElement = (E) new String(tuple.getElement());
//                        break;
                    default:
                        newElement = null;
                }
                return new Tuple<>(newElement, tuple.getTime(), tuple.getReplicaId());
            }
        });

        versionVector = (Map<ImcId16, Long>) dataObject.get("versionVector");

        return null;
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
            if (obj instanceof Tuple) {
                return ((Tuple) obj).getElement().equals(element) &&
                        ((Tuple) obj).getReplicaId().equals(replicaId) &&
                        ((Tuple) obj).getTime().equals(time);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            String temp = replicaId.toString()+time.toString()+element.toString();
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
