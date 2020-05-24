package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.types.mission.GraphType;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.TransitionType;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.util.*;

public class PlanCRDT extends CRDT {

    ORSet<Maneuver> vertex = new ORSet<>();

    ORSet<TransitionType> edge = new ORSet<>();

    public PlanCRDT() { }

    public PlanCRDT(ORSet<Maneuver> vertex, ORSet<TransitionType> edge) {
        this.vertex = vertex;
        this.edge = edge;
    }

    public PlanCRDT(PlanType info) {
        GraphType graph = info.getGraph();
        TransitionType[] edges = graph.getAllEdges();
        Maneuver[] maneuvers = graph.getAllManeuvers();

        for(Maneuver man: maneuvers) {
            vertex.add(man);
        }

        for (TransitionType trans: edges) {
            edge.add(trans);
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
        vertex.add(v);
    }

    public void removeVertex(Maneuver v){
        vertex.remove(v);
    }

    public void addEdge(TransitionType trans) {
        edge.add(trans);
    }

    public void removeEdge(TransitionType trans) {
        edge.remove(trans);
    }
    //OPERATIONS END

    public PlanCRDT merge(PlanCRDT plan) {
        vertex.merge(plan.vertex);
        edge.merge(plan.edge);
        return this;
    }

    @Override
    public PlanType payload(Object ...params) {
        if(params.length != 1) {
            throw new IllegalArgumentException("PlanCRDT payload requires a signle non-null MissionType Parameter");
        }
        PlanType temp = new PlanType((MissionType)params[0]);
        return temp;
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        PlanType updatedPlan = (PlanType) dataObject;

        Set<Maneuver> updatedManeuvers =
                new HashSet<>(Arrays.asList((updatedPlan.getGraph().getAllManeuvers())));
        Set<Maneuver> currManeuvers = vertex.payload();

        for (Maneuver man: currManeuvers){
            if(!updatedManeuvers.contains(man)) {
                vertex.remove(man);
            }
        }
        for (Maneuver man: updatedManeuvers) {
            vertex.add(man);
        }

        Set<TransitionType> updatedTransitions =
                new HashSet<>(Arrays.asList((updatedPlan.getGraph().getAllEdges())));
        Set<TransitionType> currTransitions = edge.payload();

        for (TransitionType trans: currTransitions){
            if(!updatedTransitions.contains(trans)) {
                edge.remove(trans);
            }
        }

        for (TransitionType trans: updatedTransitions) {
            edge.add(trans);
        }

        return new PlanCRDT();
    }

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String,?> dataObject) {
        ORSet<Maneuver> remoteVertex = (ORSet<Maneuver>) dataObject.get("vertex");
        ORSet<TransitionType> remoteTrans = (ORSet<TransitionType>) dataObject.get("edge");
        PlanCRDT remotePlan = new PlanCRDT(remoteVertex,remoteTrans);
        return this.merge(remotePlan);
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return new LinkedHashMap<String, ORSet<?>>() {{
            put("vertex",vertex);
            put("edge", edge);
        }};
    }
}
