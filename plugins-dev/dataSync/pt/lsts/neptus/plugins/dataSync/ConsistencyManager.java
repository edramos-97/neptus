package pt.lsts.neptus.plugins.dataSync;

import pt.lsts.imc.Event;
import pt.lsts.imc.IMCDefinition;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.transports.ImcTcpTransport;
import pt.lsts.neptus.plugins.dataSync.CRDTs.*;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.util.conf.GeneralPreferences;

import java.net.InetAddress;
import java.util.*;

public class ConsistencyManager {

    private static ConsistencyManager consistencyManager = null;

    HashMap<String, UUID> removedNameToID = new HashMap<>();
    HashMap<String, UUID> nameToID = new HashMap<>();
    HashMap<UUID, CRDT> IDToCRDT = new HashMap<>();

    ImcTcpTransport tcpTransport;

    HashMap<String,InetAddress> wellKnownPeers = new HashMap<>();
    Set<InetAddress> activeConnections = new HashSet<>();

    Vector<ChangeListener> planListeners = new Vector<>();

    public ConsistencyManager() {
        super();
//        testThread2.start();
    }

    /**
     * @return The singleton manager.
     */
    public static ConsistencyManager getManager() {
        if (consistencyManager == null) {
            return createManager();
        } else {
            return consistencyManager;
        }
    }

    private static synchronized ConsistencyManager createManager() {
        consistencyManager = new ConsistencyManager();
        return consistencyManager;
    }

//    ::::::::::::::::::::::::::::::::::::::::: CRDT-CRUD operations

    // CREATE
    public <K,V> UUID createCRDT(String name, Map<K,V> dataObject, CRDT.CRDTType crdtType) {
        CRDT newCRDT;
        switch (crdtType) {
            case LATESTEVENT:
                newCRDT = new LastEventSet<K,V>();
                break;
            default:
                return null;
        }
        return createCRDT(name, newCRDT);
    }

    public <T> UUID createCRDT(String name, Set<T> dataObject, CRDT.CRDTType crdtType){
        CRDT newCRDT;
        switch (crdtType) {
            case GSET:
                newCRDT = new GSet<>(dataObject);
                break;
            default:
                return null;
        }
        return createCRDT(name, newCRDT);
    }

    public UUID createCRDT(String name, Object dataObject, CRDT.CRDTType crdtType) {
        CRDT newCRDT;
        switch (crdtType) {
            case PLAN:
                newCRDT = new PlanCRDT((PlanType) dataObject);
                break;
            default:
                return null;
        }
        return createCRDT(name, newCRDT);
    }

    public UUID createCRDT(String name, CRDT crdtObject) {
        if(nameToID.get(name) != null) {
            NeptusLog.pub().warn("Overwriting CRDT object with name \"" + name + "\"");
        }
        UUID newID = UUID.randomUUID();
        removedNameToID.remove(name);
        nameToID.put(name,newID);
        IDToCRDT.put(newID, crdtObject);

        shareLocal(name,newID,crdtObject);

        return newID;
    }

    public CRDT createCRDT(CRDT.CRDTType crdtType) {
        CRDT newCRDT = null;
        switch (crdtType) {
            case GSET:
                newCRDT = new GSet<>();
                break;
            case LATESTEVENT:
                newCRDT = new LastEventSet<>();
                break;
            case PLAN:
                newCRDT = new PlanCRDT();
                break;
        }
        return newCRDT;
    }

    // UPDATE
    public UUID updateCRDT(String name, Object dataObject) {
//        TODO: verify name existence
        UUID crdtID = nameToID.get(name);
        NeptusLog.pub().debug("Local update to CRDT object with id: " + crdtID);
        CRDT oldCRDT = IDToCRDT.get(crdtID);
        CRDT updatedCrdt = oldCRDT.updateFromLocal(dataObject);
        if (updatedCrdt != null) {
            IDToCRDT.put(crdtID, updatedCrdt);
            shareLocal(name,crdtID, updatedCrdt);
        }
        return crdtID;
    }

    public void updateFromNetwork(LinkedHashMap<String,?> crdtData, ImcId16 sender) {
        String senderID = sender.toPrettyString();
        try {
            String remoteName = (String)crdtData.get("name");
            UUID id = UUID.fromString((String)crdtData.get("id"));
            CRDT.CRDTType type = CRDT.CRDTType.valueOf((String) crdtData.get("type"));

            if(!IDToCRDT.containsKey(id)) {
                CRDT newCRDT = createCRDT(type);
                newCRDT = newCRDT.updateFromNetwork(crdtData);
                newCRDT.name = remoteName + "(" + senderID + ")";
                IDToCRDT.put(id, newCRDT);
                nameToID.put(remoteName + "(" + senderID + ")", id);
            } else {
                CRDT localCRDT = IDToCRDT.get(id);
                CRDT updatedCRDT = localCRDT.updateFromNetwork(crdtData);
                IDToCRDT.put(id,updatedCRDT);
            }
            notifyCRDTChanges(id);
        } catch (Exception e) {
            e.printStackTrace();
            NeptusLog.pub().debug("Invalid data received in CRDT message");
        }
    }

    // DELETE
    public UUID deleteCRDT(String name) {
        UUID removedUUID= nameToID.remove(name);
        removedNameToID.put(name, removedUUID);
        return removedUUID;
    }

    public void deleteFromNetwork(LinkedHashMap<String,?> crdtData, ImcId16 sender) {
        UUID id = UUID.fromString((String)crdtData.get("id"));

        IDToCRDT.remove(id);

        Iterator<Map.Entry<String,UUID>> iterator = nameToID.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String,UUID> entry = iterator.next();
            if (id.equals(entry.getValue())) {
                iterator.remove();
            }
        }
    }

    // READ
    public CRDT getCRDT(UUID id) {
        return IDToCRDT.get(id);
    }

    private void notifyCRDTChanges(UUID id) {
        CRDT crdt = IDToCRDT.get(id);
        System.out.println("\nNotified changes on id:" + id + "\n");
        System.out.print("Updated Object:");
//        System.out.println(IDToCRDT.get(id).payload());
        if(crdt instanceof PlanCRDT){
            notifyPlanListeners((PlanCRDT) crdt);
        }
    }

//    ::::::::::::::::::::::::::::::::::::::::: Change handlers

    public void addPlanListener(ChangeListener mcl) {
        if (!planListeners.contains(mcl))
            planListeners.add(mcl);
    }

    public void notifyPlanListeners(PlanCRDT plan) {
        for (ChangeListener list: planListeners) {
            list.change(plan);
        }
    }

//    ::::::::::::::::::::::::::::::::::::::::: Msg Senders

    private void shareLocal(String localName, UUID id, CRDT crdtData) {
        LinkedHashMap<String,?> data = crdtData.toLinkedHashMap(localName, id);

        /*LinkedHashMap<String, String> data = new LinkedHashMap<>();

        data.put("key","value");
        data.put("key2","value2");*/

        Event evtMsg = new Event("crdt_data", "placeholder");

        evtMsg.setData(data);

        System.out.println("\n\n\n MY PLAN CRDT MSG");
        System.out.println(evtMsg);

        ImcMsgManager.getManager().sendMessage(evtMsg, ImcId16.BROADCAST_ID, "Broadcast");
    }

    private void deleteLocal (UUID id) {
        LinkedHashMap<String,Object> data = new LinkedHashMap<>();
        data.put("id", id.toString());

        Event evtMsg = new Event("crdt_removed", "");
        evtMsg.setData(data);
        ImcMsgManager.getManager().sendMessage(evtMsg, ImcId16.BROADCAST_ID, "Broadcast");
    }

//    ::::::::::::::::::::::::::::::::::::::::: Msg handlers

    public void on(Event evt) {
        String topic = evt.getTopic();
        LinkedHashMap<String,?> data = parseEventDataString(evt.getString("data"));

        switch(topic) {
            case "crdt_data":
                updateFromNetwork(data, new ImcId16(evt.getSrc()));
                break;
            case "crdt_removed":
                deleteFromNetwork(data, new ImcId16(evt.getSrc()));
                break;
            case "crdt_request":
                // TODO: analyze requested id's and send local version
                break;
            default:
                NeptusLog.pub().trace("Unknown topic received in consistency manager: " + topic);
        }
    }

//    ::::::::::::::::::::::::::::::::::::::::: Other
    private LinkedHashMap<String, String> parseEventDataString(String dataString) {
        LinkedHashMap<String, String> myDataMap = new LinkedHashMap<>();
        String[] split = dataString.split(";");
        System.out.println(Arrays.toString(split));
        for (int i = 0; i < split.length-1; i++) {
            String[] split1 = split[i].split("=",2);
            myDataMap.put(split1[0],split1[1]);
        }
        return myDataMap;
    }

    public void createTcpTransport() {
        int localport = GeneralPreferences.commsLocalPortTCP;

        if (tcpTransport == null) {
            tcpTransport = new ImcTcpTransport(localport, IMCDefinition.getInstance());
        }
        else {
            tcpTransport.setBindPort(localport);
        }
        tcpTransport.reStart();

        if (tcpTransport.isOnBindError()) {
            for (int i = 1; i < 10; i++) {
                tcpTransport.stop();
                tcpTransport.setBindPort(localport + i);
                tcpTransport.reStart();
                if (!tcpTransport.isOnBindError())
                    break;
            }
        }
    }

    public static void main(String[] args) {
        String test = "";
//        String test = "{set=[(VP4oAkMA+n7KjfW410EAAAD///8FAEdvdG8zwgEQJyGl9d+AAOc" +
//                   "/V8PKTcByw78AAABAAQAAgD8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAbBQ=,3,0x41ef), " +
//                   "(VP4oAkMA+n7KjfW410EAAAD///8FAEdvdG81wgEQJwrL+L+UAOc" +
//                   "/vXvpX3Nzw78AAABAAQAAgD8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAhWQ=,5,0x41ef), " +
//                   "(VP4oAkMA+n7KjfW410EAAAD///8FAEdvdG8ywgEQJ6E1Y+pwAOc" +
//                   "/PTCNWAlzw78AAABAAQAAgD8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcnc=,2,0x41ef), " +
//                   "(VP4oAkMA+n7KjfW410EAAAD///8FAEdvdG8xwgEQJ7N2S3Z0AOc" +
//                   "/eKNcfbhzw78AAABAAQAAgD8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA3cM=,1,0x41ef), " +
//                   "(VP4oAkMA+n7KjfW410EAAAD///8FAEdvdG80wgEQJ2zl4tKUAOc" +
//                   "/Ein1ijRzw78AAABAAQAAgD8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFy4=,4,0x41ef)], " +
//                   "versionVector={0x41ef=5}, type=maneuver}";
//        String[] split = test.substring(1,test.length()-1).split(", (?=[a-zA-Z])");
//        LinkedHashMap<String, String> dataMap = new LinkedHashMap<>();
//        for (String s : split) {
//            String[] entry = s.split("=",2);
//            dataMap.put(entry[0],entry[1]);
//        }
//
//        String setString = dataMap.get("set");
//        String[] setElems = setString.substring(2, setString.length() - 2).split("\\), \\(");
//        for (String setElemStr : setElems) {
//            String[] tupleElemsStr = setElemStr.split(",");
//            System.out.println(String.format("Tuple: (%s,%s,%s)", tupleElemsStr));
//            // put in tuple set
//        }
//
//        String versionVectorString = dataMap.get("versionVector");
//        String[] vectorElems = versionVectorString.substring(1, versionVectorString.length() - 1).split(", ");
//        for (String vectorElemStr : vectorElems) {
//            String[] mapElemsStr = vectorElemStr.split("=");
//            System.out.println(String.format("Version Entry: %s = %d", new ImcId16(mapElemsStr[0]),
//                    Long.parseLong(mapElemsStr[1])));
//            // fill version vector
//        }
    }

    private void testFunction() {
        HashMap<String,Long> map = new HashMap();

        HashSet<String> lastPlanList = new HashSet<>();
        lastPlanList.add("Common");
        lastPlanList.add("Removed");

        HashSet<String> currPlanList = new HashSet<>();
        currPlanList.add("Common");
        currPlanList.add("New");

        Set<String> removedPlans = Operations.diff(lastPlanList,
                currPlanList);
        Set<String> newPlans = Operations.diff(currPlanList,
                lastPlanList);
        Set<String> possibleUpdates = Operations.diff(currPlanList,newPlans);
        System.out.println(newPlans);
        System.out.println(removedPlans);
        System.out.println(possibleUpdates);
    }

    Thread testThread = new Thread() {
        @Override
        public void run() {
            super.run();
            System.out.println("\n\nStarting testing Thread in 3sec\n\n");
            HashSet<String> testArg = new HashSet<>();
            testArg.add("Hello");
            testArg.add("Goodbye");

            ConsistencyManager.getManager().createCRDT("Hello",testArg,
                    CRDT.CRDTType.GSET);

            try {
                Thread.sleep(10000);
                testArg.add("This");
                ConsistencyManager.getManager().updateCRDT("Hello", testArg);
                Thread.sleep(10000);
                testArg.add("is");
                ConsistencyManager.getManager().updateCRDT("Hello", testArg);
                Thread.sleep(10000);
                testArg.add("working");
                ConsistencyManager.getManager().updateCRDT("Hello", testArg);
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    Thread testThread2 = new Thread() {
        @Override
        public void run() {
            super.run();

            Event myMsg = new Event("local","Hello");
            for (int i = 0; i < 20; i++) {
                ImcMsgManager.getManager().sendMessage(myMsg,ImcId16.BROADCAST_ID,"Broadcast");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Ended message sending");
        }
    };

    public interface ChangeListener {
        public void change(Object newData);
    }
}
