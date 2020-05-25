package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.types.mission.GraphType;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.TransitionType;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.io.*;
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
        ORSet<Maneuver> remoteVertex = new ORSet<Maneuver>();
        remoteVertex.updateFromNetwork((LinkedHashMap<String, ?>)deserialize((String) dataObject.get("vertex")));

        ORSet<TransitionType> remoteTrans = new ORSet<>();
        remoteTrans.updateFromNetwork((LinkedHashMap<String, ?>)deserialize((String) dataObject.get("edge")));

        PlanCRDT remotePlan = new PlanCRDT(remoteVertex,remoteTrans);
        return this.merge(remotePlan);
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return new LinkedHashMap<String, Object>() {{
            put("id",id.toString());
            put("name", localName);
            put("type", CRDTType.PLAN.name());
            HashMap<String,?> vertexMap = vertex.toLinkedHashMap(null, null, "maneuver");
            put("vertex",serialize(vertexMap));
            HashMap<String, ?> edgeMap = edge.toLinkedHashMap(null, null, "transitionType");
            put("edge", serialize(edgeMap));
        }};
    }

    private String serialize(Object set){
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

    private Object deserialize(String s) {
        final byte[] bytes = Base64.getDecoder().decode(s);
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream in = new ObjectInputStream(stream);
            Object result = in.readObject();

            in.close();
            stream.close();
            return result;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        TransitionType trans = new TransitionType("goto1","goto2");
        TransitionType trans1 = new TransitionType("goto1" ,"goto2");
        trans1.setId(trans.getId());
        System.out.println(trans.equals(trans1));
    }
}
