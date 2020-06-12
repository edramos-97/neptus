package pt.lsts.neptus.plugins.dataSync.CRDTs;

import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.PlanManeuver;
import pt.lsts.imc.PlanTransition;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.IMCUtils;
import pt.lsts.neptus.mp.Maneuver;
import pt.lsts.neptus.mp.maneuvers.IMCSerialization;
import pt.lsts.neptus.types.mission.ActionType;
import pt.lsts.neptus.types.mission.ConditionType;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.TransitionType;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.io.*;
import java.util.*;

public class PlanCRDT extends CRDT {

    ORSet<PlanManeuver> vertex = new ORSet<>();
    ORSet<PlanTransition> edge = new ORSet<>();

    String initialManeuverID;

    public PlanCRDT() {
    }

    public PlanCRDT(ORSet<PlanManeuver> vertex, ORSet<PlanTransition> edge) {
        this.vertex = vertex;
        this.edge = edge;
    }

    public PlanCRDT(PlanType plan) {
        for (PlanManeuver man : getPlanManeuvers(plan)) {
            vertex.add(man);
        }

        for (PlanTransition trans : getPlanTransitions(plan)) {
            edge.add(trans);
        }

        initialManeuverID = plan.getGraph().getInitialManeuverId();
        name = plan.getId();
    }

    public static void main(String[] args) {
//        ImcId16 set1ID = new ImcId16("0x1111");
//        ImcId16 set2ID = new ImcId16("0x2222");
//        ORSet<String> set1 = new ORSet<String>(set1ID);
//        ORSet<String> set2 = new ORSet<String>(set2ID);
//        ORSet<String> set3 = new ORSet<String>(set2ID);

        /*HashSet hashSet = new HashSet();
        ORSet.Tuple tup1 = new ORSet.Tuple("hello1", 1L,new ImcId16("0x1111"));
        ORSet.Tuple tup2 = new ORSet.Tuple("hello1", 1L,new ImcId16("0x1111"));

        hashSet.add(tup1);
        System.out.println(hashSet.contains(tup2));*/

//        set1.add("hello1");
//        set1.add("hello2");
//        set1.add("hello1");
//
//        LinkedHashMap<String,?> dataObject = set1.toLinkedHashMap("Set1", UUID.randomUUID(), "string");
//
//        set2.updateFromNetwork(dataObject);
//
//        set3.merge(set2);
//
//        set1.add("hello3");
//        dataObject = set1.toLinkedHashMap("Set1", UUID.randomUUID(), "string");
//
//        set2 = new ORSet<String>(set2ID);
//        set2.updateFromNetwork(dataObject);
//
//        set3.merge(set2);
//
//        set3.add("hello from 3");
//        dataObject = set3.toLinkedHashMap("Set1", UUID.randomUUID(), "string");
//
//        set2 = new ORSet<String>(set2ID);
//        set2.updateFromNetwork(dataObject);
//
//        set1.merge(set2);
//
//        System.out.println(set1);
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

    public void addVertex(PlanManeuver v) {
        vertex.add(v);
    }

    public void removeVertex(PlanManeuver v) {
        vertex.remove(v);
    }

    public void addEdge(PlanTransition trans) {
        edge.add(trans);
    }

    public void removeEdge(PlanTransition trans) {
        edge.remove(trans);
    }
    //OPERATIONS END

    public PlanCRDT merge(PlanCRDT plan) {
        vertex.merge(plan.vertex);
        edge.merge(plan.edge);
        return this;
    }

    @Override
    public PlanType payload(Object... params) {
        if (params.length != 1) {
            throw new IllegalArgumentException("PlanCRDT payload requires a signle non-null MissionType Parameter");
        }
        PlanType temp = new PlanType((MissionType) params[0]);

        for (PlanManeuver man : vertex.payload()) {
            Maneuver neptusMan = IMCUtils.parseManeuver(man.getData());
            neptusMan.setId(man.getManeuverId());
            temp.getGraph().addManeuver(neptusMan);
        }

        for (PlanTransition trans : edge.payload()) {
            TransitionType neptusTrans = new TransitionType(trans.getSourceMan(),trans.getDestMan());

            ActionType actionType = new ActionType();
            if(trans.getActions().size() != 0){
                actionType.setAction(trans.getActions().toString());
            } else {
                actionType.setAction("");
            }
            neptusTrans.setAction(actionType);

            ConditionType conditionType = new ConditionType();
            conditionType.setCondition(trans.getConditions());
            neptusTrans.setCondition(conditionType);

            temp.getGraph().addTransition(neptusTrans);
        }

        temp.setId(name);
        temp.getGraph().setInitialManeuver(initialManeuverID);

        try {
            temp.validatePlan();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return temp;
    }

    @Override
    public CRDT updateFromLocal(Object dataObject) {
        PlanType updatedPlan = (PlanType) dataObject;

        initialManeuverID = updatedPlan.getGraph().getInitialManeuverId();

        Set<PlanManeuver> updatedManeuvers = new HashSet<>(getPlanManeuvers(updatedPlan));
        Set<PlanManeuver> currManeuvers = vertex.payload();

        for (PlanManeuver man : currManeuvers) {
            if (!contains(updatedManeuvers,man)) {
                vertex.remove(man);
            }
        }
        for (PlanManeuver man: updatedManeuvers) {
            if(!contains(currManeuvers,man)){
                vertex.add(man);
            }
        }

        Set<PlanTransition> updatedTransitions =
                new HashSet<>(getPlanTransitions(updatedPlan));
        Set<PlanTransition> currTransitions = edge.payload();

        for (PlanTransition trans: currTransitions){
            if(!contains(updatedTransitions,trans)) {
                edge.remove(trans);
            }
        }

        for (PlanTransition trans: updatedTransitions) {
            if(!contains(currTransitions,trans)){
                edge.add(trans);
            }
        }

        return this;
    }

    @Override
    public CRDT updateFromNetwork(LinkedHashMap<String, ?> dataObject) {
        ORSet<PlanManeuver> remoteVertex = new ORSet<>();
        remoteVertex.updateFromNetwork((String) dataObject.get("vertex"));

        ORSet<PlanTransition> remoteTrans = new ORSet<>();
        remoteTrans.updateFromNetwork(((String) dataObject.get("edge")));

        initialManeuverID = (String) dataObject.get("initialMan");

        PlanCRDT remotePlan = new PlanCRDT(remoteVertex, remoteTrans);
        return this.merge(remotePlan);
    }

    @Override
    public LinkedHashMap<String, ?> toLinkedHashMap(String localName, UUID id) {
        return new LinkedHashMap<String, Object>() {{
            put("id", id.toString());
            put("name", localName);
            put("type", CRDTType.PLAN.name());
            HashMap<String, ?> vertexMap = vertex.toLinkedHashMap(null, null, "maneuver");
            put("vertex", vertexMap);
            HashMap<String, ?> edgeMap = edge.toLinkedHashMap(null, null, "transitionType");
            put("edge", edgeMap);
            put("initialMan", initialManeuverID);
        }};
    }

    //    UTILITY
    private boolean contains(Set<? extends IMCMessage> set, IMCMessage element) {
        Iterator<? extends IMCMessage> it = set.iterator();
        while (it.hasNext()) {
            if (sameElements(it.next(),element)) {
                return true;
            }
        }
        return false;
    }

    public Collection<PlanManeuver> getPlanManeuvers(PlanType plan) {
        ArrayList<PlanManeuver> maneuvers = new ArrayList<>();

        for (Maneuver man : plan.getGraph().getAllManeuvers()) {
            if (man instanceof IMCSerialization) {
                PlanManeuver m = new PlanManeuver();
                m.setManeuverId(man.getId());
                m.setValue("data", ((IMCSerialization)man).serializeToIMC());

                maneuvers.add(m);
            }
            else {
                NeptusLog.pub()
                        .error("Error converting to IMC Plan: the maneuver "
                               + man.getId()
                               + " is not compatible with the IMC Protocol");
                return null;
            }
        }

        return maneuvers;
    }

    Collection<PlanTransition> getPlanTransitions(PlanType plan) {
        ArrayList<PlanTransition> planTransitions = new ArrayList<>();

        for (TransitionType tt : plan.getGraph().getTransitions().values()) {
            PlanTransition pt = new PlanTransition();
            pt.setSourceMan(tt.getSourceManeuver());
            pt.setDestMan(tt.getTargetManeuver());
            pt.setConditions(tt.getCondition().toString());
            planTransitions.add(pt);
        }
        return planTransitions;
    }

    private String serialize(Object set) {
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
}
