package pt.lsts.neptus.plugins.dataSync;

import pt.lsts.imc.Event;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.SystemCommBaseInfo;
import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.manager.imc.SystemImcMsgCommInfo;
import pt.lsts.neptus.data.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ElectionManager {

    private static ElectionManager electionManager = null;
    private boolean initialized = false;

    private static ScheduledExecutorService privateExecutor = Executors.newSingleThreadScheduledExecutor();

    private static LinkedList<Pair<Object, Method>> systemListeners = new LinkedList<>();
    private static LinkedList<Pair<Object, Method>> stateListeners = new LinkedList<>();

    private ElectionState state;
    private ImcId16 leaderId = null;
    private boolean isLeader = false;
    private boolean hasLeader = false;
    private int acceptCounter = 0;

    private volatile LeaderSearch leaderSearch;
    private volatile CandidateBroadcasting candidateBroadcasting;

    private ElectionManager() {
        this.state = ElectionState.IDLE;
    }

    /**
     * @return The singleton manager.
     */
    public static ElectionManager getManager() {
        if (electionManager == null) {
            electionManager = createManager();
        }
        return electionManager;
    }

    private static synchronized ElectionManager createManager() {
        return new ElectionManager();
    }

    /**
     * Resets and initializes the start procedure of the election manager to find an existing leader or elect a new one
     */
    void initialize() {
        resetState();
        privateExecutor.schedule(new Startup(), 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the state of the election manager
     */
    void resetState() {
        initialized = false;
        if (leaderSearch != null) {
            NeptusLog.pub().debug("ElectionManager is starting when a leader search was already running");
            leaderSearch.interrupt();
            try {
                leaderSearch.join(250);
                leaderSearch = null;
            } catch (InterruptedException e) {
                NeptusLog.pub().debug("ElectionManager state reset interrupted waiting for leader search to join");
            }
        }
        if (candidateBroadcasting != null) {
            NeptusLog.pub().debug("ElectionManager is starting when a candidate broadcasting was already running");
            candidateBroadcasting.interrupt();
            try {
                candidateBroadcasting.join(250);
                leaderSearch = null;
            } catch (InterruptedException e) {
                NeptusLog.pub()
                        .debug("ElectionManager state reset interrupted waiting for candidate broadcast to join");
            }
        }
        privateExecutor.shutdownNow();
        try {
            privateExecutor.awaitTermination(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            NeptusLog.pub().debug("ElectionManager state reset interrupted waiting for private executor to join");
        }
        privateExecutor = Executors.newSingleThreadScheduledExecutor();
        setState(ElectionState.RESET);
    }

    public void on(Event eventMsg) {
        String topic = eventMsg.getTopic();
        String data = (String) eventMsg.getValue("data");
        ImcId16 sender = new ImcId16(eventMsg.getSrc());
        switch (topic) {
            case "leader":
                onLeader(data, sender);
                break;
            case "candidate":
                onCandidate(data, sender);
                break;
            case "accept":
                onAccept(data, sender);
                break;
            default:
                NeptusLog.pub().trace("Unknown message topic received in Election Manager");
        }
    }

    // :::::::::::::::::::::::::::::::: Handlers
    private void onLeader(String data, ImcId16 sender) {
        if(data.equals("")){
            if (isLeader) {
                ImcMsgManager.getManager().sendMessage(
                        new Event("leader", ImcMsgManager.getManager().getLocalId().toPrettyString()),
                        sender,
                        null);
            }
            if(!hasLeader) {
                ImcMsgManager.getManager().sendMessage(
                        new Event("leader", ImcMsgManager.getManager().getLocalId().toPrettyString()),
                        sender,
                        null);
                setLeader(ImcMsgManager.getManager().getLocalId());
                //Execute startup in case a leader already existed
                privateExecutor.execute(new Startup());
            }
        } else {
            ImcId16 newLeaderId = new ImcId16(data);
            if(newLeaderId.intValue() != 0xFFFF){
                setLeader(newLeaderId);
            }
        }
    }

    private void onCandidate(String data, ImcId16 sender) {
        if (data.equals("-1")) {
            if (state == ElectionState.IDLE) {
                ImcMsgManager.getManager().sendMessage(
                        new Event("accept", data),
                        sender,
                        null);
                setState(ElectionState.ACCEPTING);
                privateExecutor.schedule(() -> {
                    if (!hasLeader) {
                        setState(ElectionState.IDLE);
                    }
                }, 9000, TimeUnit.MILLISECONDS);
            }
        } else {
            boolean connected = false;
            ImcId16 candidateId = new ImcId16(ImcId16.parseImcId16(data));
            for (ImcId16 id : getActiveSystems()) {
                if (id.equals(candidateId)) {
                    connected = true;
                    break;
                }
            }
            if (!connected) {
                ImcMsgManager.getManager().sendMessage(
                        new Event("accept", data),
                        sender,
                        null);
            }
        }
    }

    private void onAccept(String data, ImcId16 sender) {
        if (++acceptCounter == getActiveSystems().length) {
            isLeader = true;
            hasLeader = true;
            leaderId = ImcMsgManager.getManager().getLocalId();
            setState(ElectionState.ELECTED);
            candidateBroadcasting.interrupt();
            ImcMsgManager.getManager().sendMessage(new Event("leader",
                            ImcMsgManager.getManager().getLocalId().toPrettyString()),
                    ImcId16.BROADCAST_ID,
                    "Broadcast");
        }
//                if same size as network interrupt candidate broadcasting thread
    }

    // :::::::::::::::::::::::::::::::: Notification methods
    public void registerActiveSystemListener(Object obj, Method method) {
        systemListeners.add(new Pair<>(obj, method));
    }

    private void notifyActiveSystemListeners(String[] activeSystemNames) {
        for (Pair<Object, Method> pair : systemListeners) {
            try {
                pair.second().invoke(pair.first(), activeSystemNames);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public void registerStateListener(Object obj, Method method) {
        systemListeners.add(new Pair<>(obj, method));
    }

    private void notifyStateListeners(ElectionState newState) {
        for (Pair<Object, Method> pair : stateListeners) {
            try {
                pair.second().invoke(pair.first(), newState);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    // :::::::::::::::::::::::::::::::: Getters/Setters Methods
    private void setState(ElectionState state) {
        this.state = state;
        System.out.println("setting state to:" + state.name());
        System.out.println(String.format("has leader: %b; is leader: %b leader id: %s;", hasLeader, isLeader,
                leaderId));
        notifyStateListeners(state);
    }

    private void setLeader(ImcId16 id) {
        if (id == null) {
            hasLeader = false;
            leaderId = null;
            isLeader = false;
            setState(ElectionState.IDLE);
            System.out.println("Removed leader");
        } else {
            hasLeader = true;
            leaderId = id;
            isLeader = id.equals(ImcMsgManager.getManager().getLocalId());
            setState(ElectionState.ELECTED);
            System.out.println("Set Leader to system with id: " + leaderId);
        }
    }

    public String getStateColor() {
        return this.state.color;
    }

    public ImcId16[] getActiveSystems() {
        return ImcMsgManager.getManager().getCommInfo().values().stream()
                .filter(SystemImcMsgCommInfo::isActive)
                .map(SystemCommBaseInfo::getSystemCommId)
                .toArray(ImcId16[]::new);
    }

    // :::::::::::::::::::::::::::::::: General Methods
    public void updateConnectedSystems() {
//        Collection<SystemImcMsgCommInfo> activeSys = ImcMsgManager.getManager().getCommInfo().values();
//        Stream<SystemImcMsgCommInfo> leaderSys = activeSys.stream()
//                .filter(SystemImcMsgCommInfo::isActive)
//                .filter((sysCommInfo) -> sysCommInfo.getSystemCommId().equals(leaderId));
//        String[] systemNames = activeSys.stream()
//                .filter(SystemImcMsgCommInfo::isActive)
//                .map(SystemImcMsgCommInfo::toString)
//                .toArray(String[]::new);
//        if (leaderId != null && leaderSys.count() < 1) {
//            candidateBroadcasting = new CandidateBroadcasting(leaderId.toPrettyString());
//            long randTime = 2000 + new Random().nextLong() % 2000;
//            privateExecutor.schedule(candidateBroadcasting, randTime, TimeUnit.MILLISECONDS);
//            hasLeader = false;
//            isLeader = false;
//            leaderId = null;
//            setState(ElectionState.IDLE);
//        }
        if(!initialized) {
            return;
        }

        ImcId16[] activeSystems = getActiveSystems();
        String[] systemNames = Arrays.stream(activeSystems)
                .map((id) -> ImcMsgManager.getManager().getCommInfoById(id).toString()).toArray(String[]::new);

        notifyActiveSystemListeners(systemNames);

        if(hasLeader && !isLeader) {
            SystemImcMsgCommInfo leaderInfo = ImcMsgManager.getManager().getCommInfoById(leaderId);
            if(leaderInfo == null || !leaderInfo.isActive()) {
                // no re-election possible
                if(activeSystems.length == 0) {
                    privateExecutor.submit(new Startup());
                } else {
                    setLeader(null);
                    // TODO: submit election thread
                }
            }
            return;
        }
        if(!hasLeader && state == ElectionState.IDLE) {
            privateExecutor.submit(new Startup());
        }

    }

    // :::::::::::::::::::::::::::::::: Inner Classes
    enum ElectionState {
        RESET("#40bbff"),
        STARTING("#eeff00"),
        IDLE("#40bbff"),
        ELECTED("#85ff65"),
        CANDIDATE("#eeff00"),
        ACCEPTING("#eeff00"),
        ERROR("#ff4130");

        public String color;

        ElectionState(String color) {
            this.color = color;
        }

        @Override
        public String toString() {
            return color;
        }
    }

    private class Startup extends Thread {
        @Override
        public void run() {
            System.out.println("Executing startup procedure");
            setState(ElectionState.STARTING);
            initialized = true;

            if (getActiveSystems().length == 0) {
                setLeader(ImcMsgManager.getManager().getLocalId());
            } else {
                ImcMsgManager.getManager().sendMessage(new Event("leader", ""), ImcId16.BROADCAST_ID, "Broadcast");
            }
        }
    }

    /**
     * Executed the starting procedure to find an existing leader or begin the election a new one. In case neither
     * option is possible enter an idle state
     */
    private class LeaderSearch extends Thread {
        @Override
        public void run() {
            System.out.println("\n\nStarting leader search\n\n");
            setState(ElectionState.STARTING);
//            ask leader
            ImcMsgManager.getManager().sendMessage(new Event("leader", ""), ImcId16.BROADCAST_ID, "Broadcast");
//            wait 3 sec
            try {
                Thread.sleep(2000);

//            check if has leader and return
                if (hasLeader) {
                    return;
                }

//            check if there are other node in the network
                switch (getActiveSystems().length) {
                    case 0:
                        setState(ElectionState.IDLE);
                        break;
                    case 1:
                        candidateBroadcasting = new CandidateBroadcasting("-1");
                        privateExecutor.schedule(candidateBroadcasting, 1000, TimeUnit.MILLISECONDS);
                        break;
                    default:
                        privateExecutor.schedule(new LeaderSearch(), 1000, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                NeptusLog.pub().debug("Leader Search thread was interrupted in Election Manager");
            }
        }
    }

    private class CandidateBroadcasting extends Thread {
        String candidateId;

        public CandidateBroadcasting(String candidateId) {
            super();
            this.candidateId = candidateId;
        }

        @Override
        public void run() {
//            send candidate message
            setState(ElectionState.CANDIDATE);
            ImcMsgManager.getManager()
                    .sendMessage(new Event("candidate", candidateId), ImcId16.BROADCAST_ID, "Broadcast");
            acceptCounter = 0;
            try {
//            wait 5sec
                Thread.sleep(10000);
                if (!hasLeader && !isLeader) {
                    privateExecutor.submit(new LeaderSearch());
                }
            } catch (InterruptedException e) {
                if (!hasLeader && !isLeader)
                    NeptusLog.pub().debug("Candidate broadcasting thread was interrupted in Election Manager");
            }
        }
    }

}
