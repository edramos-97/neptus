package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.types.mission.GraphType;
import pt.lsts.neptus.types.mission.TransitionType;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.util.LinkedHashMap;
import java.util.UUID;

public class PlanCRDT extends CRDT {

    GSet<Maneuver> vertexAdd = new GSet<>();
    GSet<Maneuver> vertexRemove = new GSet<>();

    GSet<TransitionType> edgeAdd = new GSet<>();
    GSet<TransitionType> edgeRemove = new GSet<>();

    public PlanCRDT() { }

    public PlanCRDT(PlanType info) {
        GraphType graph = info.getGraph();
        TransitionType[] edges = graph.getAllEdges();
        Maneuver[] maneuvers = graph.getAllManeuvers();

        for(Maneuver man: maneuvers) {
            vertexAdd.add(man);
        }

        for (TransitionType trans: edges) {
            edgeAdd.add(trans);
        }
    }

    // OPERATIONS
    /*lookup v*/
    public boolean lookup(Maneuver v) {
        return false;
    }

    /*lookup edge from v1 to v2*/
    public boolean lookup(Maneuver v1, Maneuver v2) {
        return false;
    }

    public void addVertex (Maneuver v){

    }

    public void removeVertex(Maneuver v){

    }

    public void addEdge(Maneuver v1, Maneuver v2) {

    }

    public void removeEdge(Maneuver v1, Maneuver v2) {

    }
    //OPERATIONS END

    public PlanType value() {
        return null;
    }

    public void merge(PlanCRDT plan) {

    }

    @Override
    public PlanType payload() {
        return null;
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        return new PlanCRDT(null);
    }

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String,?> dataObject) {
        return new PlanCRDT(null);
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return null;
    }
}
