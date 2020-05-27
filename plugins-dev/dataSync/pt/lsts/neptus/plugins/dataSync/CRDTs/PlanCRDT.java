package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.types.mission.GraphType;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.TransitionType;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.io.*;
import java.util.*;

public class PlanCRDT extends CRDT {

    ORSet<Maneuver> vertex = new ORSet<>(new ImcId16("0x0000"));

    ORSet<TransitionType> edge = new ORSet<>(new ImcId16("0x0000"));

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

        for (Maneuver man : vertex.payload()) {
            temp.getGraph().addManeuver(man);
        }

        for (TransitionType trans: edge.payload()) {
            temp.getGraph().addTransition(trans);
        }

        temp.setId(name);

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
            if(!currManeuvers.contains(man)){
                vertex.add(man);
            }
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

        return this;
    }

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String,?> dataObject) {
        ORSet<Maneuver> remoteVertex = new ORSet<Maneuver>(new ImcId16("0x0000"));
        remoteVertex.updateFromNetwork((LinkedHashMap<String, ?>)deserialize((String) dataObject.get("vertex")));

        ORSet<TransitionType> remoteTrans = new ORSet<>(new ImcId16("0x0000"));
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
        ImcId16 set1ID = new ImcId16("0x1111");
        ImcId16 set2ID = new ImcId16("0x2222");
        ORSet<String> set1 = new ORSet<String>(set1ID);
        ORSet<String> set2 = new ORSet<String>(set2ID);
        ORSet<String> set3 = new ORSet<String>(set2ID);

        /*HashSet hashSet = new HashSet();
        ORSet.Tuple tup1 = new ORSet.Tuple("hello1", 1L,new ImcId16("0x1111"));
        ORSet.Tuple tup2 = new ORSet.Tuple("hello1", 1L,new ImcId16("0x1111"));

        hashSet.add(tup1);
        System.out.println(hashSet.contains(tup2));*/

        set1.add("hello1");
        set1.add("hello2");
        set1.add("hello1");

        LinkedHashMap<String,?> dataObject = set1.toLinkedHashMap("Set1", UUID.randomUUID(), "string");

        set2.updateFromNetwork(dataObject);

        set3.merge(set2);

        set1.add("hello3");
        dataObject = set1.toLinkedHashMap("Set1", UUID.randomUUID(), "string");

        set2 = new ORSet<String>(set2ID);
        set2.updateFromNetwork(dataObject);

        set3.merge(set2);

        set3.add("hello from 3");
        dataObject = set3.toLinkedHashMap("Set1", UUID.randomUUID(), "string");

        set2 = new ORSet<String>(set2ID);
        set2.updateFromNetwork(dataObject);

        set1.merge(set2);

        System.out.println(set1);
    }
}
