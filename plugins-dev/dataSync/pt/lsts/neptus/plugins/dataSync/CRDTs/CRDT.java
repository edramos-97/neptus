package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.imc.IMCMessage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;

public abstract class CRDT {

    public String name;

    public abstract LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id);

    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id, String genericType) {
        return null;
    }

    public abstract Object payload(Object... param);

//    return null if no updates were conducted
    public abstract CRDT updateFromLocal(Object dataObject);

    public abstract CRDT updateFromNetwork(LinkedHashMap<String, ?> dataObject);

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    protected boolean sameElements(IMCMessage elem1, IMCMessage elem2) {
        return Arrays.equals(elem1.payloadMD5(),(elem2.payloadMD5()));
    }

    public enum CRDTType {
        PLAN,
        GSET,
        LATESTEVENT
    }
}
