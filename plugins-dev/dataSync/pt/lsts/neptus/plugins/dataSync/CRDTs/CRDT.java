package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.imc.IMCMessage;
import pt.lsts.neptus.comm.manager.imc.ImcId16;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public void setName(String name) {
        this.name = name;
    }

    public String getTrueName() {
        Pattern r = Pattern.compile("(.*)\\(([0-9a-f]{2}:[0-9a-f]{2})\\)$");
        Matcher m = r.matcher(name);
        if(m.matches()) {
            return m.group(1);
        } else {
            return name;
        }
    }

    public ImcId16 getRemoteOriginSystem() {
        Pattern r = Pattern.compile("(.*)\\(([0-9a-f]{2}:[0-9a-f]{2})\\)$");
        Matcher m = r.matcher(name);
        if(m.matches()) {
            return new ImcId16(m.group(2));
        } else {
            return null;
        }
    }

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
