package pt.lsts.neptus.plugins.dataSync.CRDTs;

import java.util.*;

/*Grow only Set*/
public class GSet<E> extends CRDT {

    private Set<E> set;

    public GSet() {
        set = new HashSet<>();
    }

    public GSet(Set<E> set) {
        this.set = new HashSet<>(set);
    }

    public void add(final E elem) {
        set.add(elem);
    }

    public Set<E> lookup() {
        return Collections.unmodifiableSet(set);
    }

    public GSet<E> merge(GSet<E> anotherGSet) {
        final HashSet<E> newSet = new HashSet<>(set);
        newSet.addAll(anotherGSet.getSet());
        return new GSet<>(newSet);
    }

    private Set<E> getSet() {
        return set;
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return new LinkedHashMap<String, Object>() {{
            put("id",id.toString());
            put("name", localName);
            put("main_set",getSet());
            put("type", CRDTType.GSET.name());
        }};
    }

    @Override
    public HashMap<String, Object> payload() {
        return null;
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        GSet<E> newSet = new GSet<>((Set<E>) dataObject);
        return this.merge(newSet);
    }

    public CRDT updateFromNetwork(Object dataObject) {
        GSet<E> newSet = new GSet<>((Set<E>) dataObject);
        return this.merge(newSet);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GSet<?> gSet = (GSet<?>) o;

        return set.equals(gSet.set);

    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }
}
