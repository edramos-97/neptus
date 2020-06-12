package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.PlanManeuver;
import pt.lsts.imc.PlanTransition;

import java.util.LinkedHashMap;
import java.util.UUID;

public abstract class CRDT {

    public String name;

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

    protected boolean sameElements(IMCMessage elem1, IMCMessage elem2) {
        if (elem1 instanceof PlanManeuver) {
            if (((PlanManeuver) elem1).getManeuverId()
                    .equals(((PlanManeuver) elem2).getManeuverId())) {
                return true;
            }
        } else if (elem1 instanceof PlanTransition) {
            if (((PlanTransition) elem1).getSourceMan()
                        .equals(((PlanTransition) elem2).getSourceMan()) &&
                ((PlanTransition) elem1).getDestMan().equals(((PlanTransition) elem2).getDestMan())) {
                return true;
            }
        } else {
            if (elem1.equals(elem2)) {
                return true;
            }
        }
        return false;
    }
}
