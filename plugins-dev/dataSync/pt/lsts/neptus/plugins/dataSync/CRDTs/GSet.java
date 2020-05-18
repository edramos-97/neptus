package pt.lsts.neptus.plugins.dataSync.CRDTs;

import java.io.*;
import java.util.*;

/*Grow only Set*/
public class GSet<E> extends CRDT {

    private Set<E> set;

    public GSet() {
        set = new HashSet<>();
    }

    public GSet(Set<E> set) {
        this.set = (Set<E>) new HashSet<>(set).clone();
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
            put("main_set",serialize(getSet()));
            put("type", CRDTType.GSET.name());
        }};
    }

    @Override
    public Set<E> payload() {
        return Collections.unmodifiableSet(set);
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        GSet<E> newSet = new GSet<>((Set<E>) dataObject);
        return this.merge(newSet);
    }

    public CRDT updateFromNetwork(LinkedHashMap<String,?> dataObject) {
        GSet<E> newSet = new GSet<>(deserialize((String) dataObject.get("main_set")));
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

    private String serialize(Set<E> set){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(stream);
            out.writeObject(set);
            String output = Base64.getEncoder().encodeToString(stream.toByteArray());

            out.close();
            stream.close();
            return output;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Set<E> deserialize(String s) {
        final byte[] bytes = Base64.getDecoder().decode(s);
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream in = new ObjectInputStream(stream);
            Set<E> result = (Set<E>) in.readObject();

            in.close();
            stream.close();
            return result;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
