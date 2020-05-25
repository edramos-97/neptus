package pt.lsts.neptus.plugins.dataSync.CRDTs;

import java.util.LinkedHashMap;
import java.util.UUID;

public abstract class CRDT {

    public abstract LinkedHashMap<String,?> toLinkedHashMap(String localName, UUID id);
    public LinkedHashMap<String,?> toLinkedHashMap(String localName, UUID id, String genericType){
        return null;
    }

    public abstract Object payload(Object ...param);

    public abstract CRDT updateFromLocal(Object dataObject);

    public abstract CRDT updateFromNetwork(LinkedHashMap<String,?> dataObject);

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public enum CRDTType {
        PLAN,
        GSET,
        LATESTEVENT
    }
}
