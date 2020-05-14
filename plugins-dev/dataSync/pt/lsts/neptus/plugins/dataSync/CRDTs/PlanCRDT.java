package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.types.mission.plan.PlanType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;

public class PlanCRDT extends CRDT {

    GSet<PlanVertex> vertexAdd = new GSet<>();
    GSet<PlanVertex> vertexRemove = new GSet<>();

    GSet<PlanEdge> edgeAdd = new GSet<>();
    GSet<PlanEdge> edgeRemove = new GSet<>();

    public PlanCRDT() {

    }

    public PlanCRDT(Object info) {

    }

    // OPERATIONS
    /*lookup v*/
    public boolean lookup(PlanVertex v) {
        return false;
    }

    /*lookup edge from v1 to v2*/
    public boolean lookup(PlanVertex v1, PlanVertex v2) {
        return false;
    }

    public void addVertex (PlanVertex v){

    }

    public void removeVertex(PlanVertex v){

    }

    public void addEdge(PlanVertex v1, PlanVertex v2) {

    }

    public void removeEdge(PlanVertex v1, PlanVertex v2) {

    }
    //OPERATIONS END

    public PlanType value() {
        return null;
    }

    public void merge(PlanCRDT plan) {

    }

    @Override
    public HashMap<String, Object> payload() {
        return new HashMap<String, Object>() {{
            put("vertexadd", vertexAdd);
            put("vertexremove",vertexRemove);
            put("edgeadd", edgeAdd);
            put("edgeremove", edgeRemove);
        }};
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        return new PlanCRDT("hello");
    }

    @Override
    public CRDT updateFromNetwork(Object dataObject) {
        return new PlanCRDT("hello");
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return null;
    }

    static class PlanVertex {

    }

    static class PlanEdge {

    }
}
