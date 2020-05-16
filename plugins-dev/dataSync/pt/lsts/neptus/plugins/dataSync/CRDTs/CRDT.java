package pt.lsts.neptus.plugins.dataSync.CRDTs;

import java.util.LinkedHashMap;
import java.util.UUID;

public abstract class CRDT {

    public abstract LinkedHashMap<String,?> toLinkedHashMap(String localName, UUID id);

    public abstract Object payload();

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
