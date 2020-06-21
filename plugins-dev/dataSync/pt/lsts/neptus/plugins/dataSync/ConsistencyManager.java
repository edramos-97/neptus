package pt.lsts.neptus.plugins.dataSync;

import pt.lsts.imc.Event;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.transports.ImcTcpTransport;
import pt.lsts.neptus.plugins.dataSync.CRDTs.*;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsistencyManager {

    private static ConsistencyManager consistencyManager = null;

    private boolean loadedComplete = false;

    private static ScheduledExecutorService privateExecutor = Executors.newSingleThreadScheduledExecutor();

    HashMap<String, UUID> removedNameToID = new HashMap<>();
    HashMap<String, UUID> nameToID = new HashMap<>();
    HashMap<UUID, CRDT> IDToCRDT = new HashMap<>();

    ImcTcpTransport tcpTransport;

    HashMap<String,InetAddress> wellKnownPeers = new HashMap<>();

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

    public void setLoadedComplete(boolean loadedComplete) {
        this.loadedComplete = loadedComplete;
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

        if(loadedComplete) {
            shareLocal(name,newID,crdtObject);
        }

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
                String name = "";
                Pattern r = Pattern.compile("(.*)\\(([0-9a-f]{2}:[0-9a-f]{2})\\)$");
                Matcher m = r.matcher(remoteName);
                if(m.matches()){
                    ImcId16 remoteID = new ImcId16(m.group(2));
                    if(remoteID.equals(ImcMsgManager.getManager().getLocalId())) {
                        name = m.group(1);
                    } else {
                        name = remoteName;
                    }
                } else {
                    name = remoteName + "(" +senderID + ")";
                }
                newCRDT.setName(name);
                IDToCRDT.put(id, newCRDT);
                nameToID.put(name, id);
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

    // REQUESTS
    private String buildDataRequest(boolean requestAllData) {
        if(requestAllData) {
            return "all";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : nameToID.keySet()) {
            if(s.matches("(.*)\\(([0-9a-f]{2}:[0-9a-f]{2})\\)$")) {
                stringBuilder.append(s);
            } else {
                ImcId16 localId = ImcMsgManager.getManager().getLocalId();
                stringBuilder.append(s).append("(").append(localId.toPrettyString()).append(")");
            }
            stringBuilder.append(",");
        }
        if(stringBuilder.length() > 0){
            return stringBuilder.substring(0,stringBuilder.length()-1);
        } else {
            return stringBuilder.toString();
        }
    }

    private void handleDataRequest(LinkedHashMap<String,?> data, ImcId16 src) {
        String namesList = (String) data.get("listOfNames");
        boolean requestBack = Boolean.parseBoolean((String) data.get("requestBack"));
        List<String> split = Arrays.asList(namesList.split(","));
        if(split.contains("all")) {
            answerFullDataRequest(src);
        } else {
            for (String requestEntry : split) {
                Pattern r = Pattern.compile("(.*)\\(([0-9a-f]{2}:[0-9a-f]{2})\\)$");
                Matcher m = r.matcher(requestEntry);
                if(m.matches()) {
                    ImcId16 entryID = new ImcId16(m.group(2));
                    String localName;
                    if(entryID.equals(ImcMsgManager.getManager().getLocalId())) {
                        localName = m.group(1);
                    } else {
                        localName = requestEntry;
                    }
                    UUID id = nameToID.get(localName);
                    if(id != null) {
                        CRDT crdt = IDToCRDT.get(id);
                        shareIndividual(localName,id,crdt,src);
                    }
                }
            }
        }
        if(requestBack) {
            synchronizeLocalData(src,false,true);
        }
    }

    private void answerFullDataRequest(ImcId16 src) {
        for (Map.Entry<String, UUID> crdtNameEntry : nameToID.entrySet()) {
            UUID id = crdtNameEntry.getValue();
            CRDT crdt = IDToCRDT.get(id);
            shareIndividual(crdtNameEntry.getKey(),id,crdt,src);
        }
    }

    public void synchronizeLocalData(ImcId16 destination, boolean requestBack, boolean requestAllData) {
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        data.put("listOfNames", buildDataRequest(requestAllData));
        data.put("requestBack", String.valueOf(requestBack));

        Event evtMsg = new Event("crdt_request", "placeholder");

        evtMsg.setData(data);

        System.out.println("\n\n\n SENT LOCAL DATA REQUEST MESSAGE");
        System.out.println(evtMsg);

        ImcMsgManager.getManager().sendMessage(evtMsg, destination, "");
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

        Event evtMsg = new Event("crdt_data", "placeholder");

        evtMsg.setData(data);

        System.out.println("\n\n\n SENT LOCAL UPDATE MESSAGE");
        System.out.println(evtMsg);

        ImcMsgManager.getManager().sendMessage(evtMsg, ImcId16.BROADCAST_ID, "Broadcast");
    }

    private void shareIndividual(String localName, UUID id, CRDT crdtData, ImcId16 dest) {
        LinkedHashMap<String,?> data = crdtData.toLinkedHashMap(localName, id);

        Event evtMsg = new Event("crdt_data", "placeholder");

        evtMsg.setData(data);

        System.out.println("\n\n\n SENT INDIVIDUAL MESSAGE");

        ImcMsgManager.getManager().sendMessage(evtMsg, dest,"");
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
                handleDataRequest(data, new ImcId16(evt.getSrc()));
                break;
            default:
                NeptusLog.pub().trace("Unknown topic received in consistency manager: " + topic);
        }
    }

    //    ::::::::::::::::::::::::::::::::::::::::: Other
    private LinkedHashMap<String, String> parseEventDataString(String dataString) {
        LinkedHashMap<String, String> myDataMap = new LinkedHashMap<>();
        String[] split = dataString.split(";");
        for (int i = 0; i < split.length-1; i++) {
            String[] split1 = split[i].split("=",2);
            myDataMap.put(split1[0],split1[1]);
        }
        return myDataMap;
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
