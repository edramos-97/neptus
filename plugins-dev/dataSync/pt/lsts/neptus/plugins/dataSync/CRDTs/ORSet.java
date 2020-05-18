package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.plugins.dataSync.Operations;

import java.util.*;


/*
Implementation based on the work
Bieniusa, Annette, et al. "An optimized conflict-free replicated set." arXiv preprint arXiv:1210.3368 (2012).
*/
public class ORSet<E> extends CRDT {

    static ImcId16 myId = ImcMsgManager.getManager().getLocalId();

    Set<Tuple<E, Long, ImcId16>> set;
    Map<ImcId16, Long> versionVector;

    VectorClock clock = new VectorClock();

    public ORSet(){
        set = new HashSet<>();
        versionVector = new TreeMap<>();
        versionVector.put(myId, clock.value());
    }

    public ORSet(Set<E> existingSet){
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

    public Set<E> elements() {
        return Operations.mappedd(set, Tuple::getElement);
    }

    public void add (E element) {
        set.add(new Tuple<>(element,clock.valueAndInc() + 1,myId));

        if(clock.value() > versionVector.get(myId)) {
            set = Operations.filtered(set, new Operations.Predicate<Tuple<E, Long,
                    ImcId16>>() {
                @Override
                public boolean call(Tuple<E, Long, ImcId16> tuple) {
                    return !(tuple.getElement().equals(element) && tuple.getTime() > clock.value());
                }
            });
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

        Set<Tuple<E, Long, ImcId16>> temp = ((Set<Tuple<E, Long, ImcId16>>) new HashSet<>(set).clone());
        temp.retainAll(anotherSet.set);

        Set<Tuple<E, Long, ImcId16>> temp1 = Operations.filtered(Operations
                .diff(set, anotherSet.set), tuple -> {
                    Long anotherSetVal = anotherSet.versionVector.get(tuple.getReplicaId());
                    if(anotherSetVal == null) {
                        return tuple.getTime() > 0;
                    } else {
                        return tuple.getTime() > anotherSetVal;
                    }
                });

        Set<Tuple<E, Long, ImcId16>> temp2 = Operations.filtered(Operations
                .diff(anotherSet.set, set), tuple -> {
                    Long setVal = versionVector.get(tuple.getReplicaId());
                    if(setVal == null) {
                        return tuple.getTime() > 0;
                    } else {
                        return tuple.getTime() > setVal;
                    }
                });
        Set<Tuple<E, Long, ImcId16>> union = Operations.union(Operations.union(temp,temp1),temp2);
        Set<Tuple<E, Long, ImcId16>> overflow = Operations.filtered(union, new Operations.Predicate<Tuple<E, Long,
                ImcId16>>() {
            @Override
            public boolean call(Tuple<E, Long, ImcId16> tuple) {
                return !Operations.filtered(union, new Operations.Predicate<Tuple<E, Long, ImcId16>>() {
                    @Override
                    public boolean call(Tuple<E, Long, ImcId16> tuple1) {
                        if(tuple.getElement().equals(tuple1.getElement()) && tuple.getReplicaId().equals(tuple1.getReplicaId())){
                            return tuple.getTime() > tuple1.getTime();
                        } else {
                            return false;
                        }
                    }
                }).isEmpty();
            }
        });
        set = Operations.diff(union, overflow);
        return this;
    }



    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return null;
    }

    @Override
    public Object payload() {
        return null;
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        return null;
    }

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String, ?> dataObject) {
        return null;
    }

    static class Tuple<E,T,I> {
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
    }

    class VectorClock {
        long counter = 0;

        long value() {
            return counter;
        }

        long valueAndInc() {
            return counter++;
        }

        void set(long val) {
            counter = val;
        }
    }
}
