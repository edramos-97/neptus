package pt.lsts.neptus.plugins.dataSync;

import org.apache.commons.lang3.ArrayUtils;
import pt.lsts.imc.Event;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.SystemCommBaseInfo;
import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.manager.imc.SystemImcMsgCommInfo;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ElectionManager {

    private static ElectionManager electionManager = null;
    private boolean initialized = false;

    private static ScheduledExecutorService privateExecutor = Executors.newSingleThreadScheduledExecutor();

    private static LinkedList<Consumer<String[]>> systemListeners = new LinkedList<>();
    private static LinkedList<Consumer<ElectionState>> stateListeners = new LinkedList<>();

    private ElectionState state;

    private ImcId16 leaderId = null;
    private boolean isLeader = false;
    private boolean hasLeader = false;
    private int acceptCounter = 0;

    private volatile PerformElection performElection;
    private volatile HandleCandidate handleCandidate;

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
        if (performElection != null) {
            NeptusLog.pub().debug("ElectionManager is starting when a leader search was already running");
            performElection.interrupt();
            try {
                performElection.join(250);
                performElection = null;
            } catch (InterruptedException e) {
                NeptusLog.pub().debug("ElectionManager state reset interrupted waiting for leader search to join");
            }
        }
        if (handleCandidate != null) {
            NeptusLog.pub().debug("ElectionManager is starting when a candidate broadcasting was already running");
            handleCandidate.interrupt();
            try {
                handleCandidate.join(250);
                performElection = null;
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
        handleCandidate = new HandleCandidate(data, sender);
    }

    private void onAccept(String data, ImcId16 sender) {
        if (++acceptCounter >= getActiveSystems().length/2) {
            setLeader(ImcMsgManager.getManager().getLocalId());
            performElection.interrupt();
            ImcMsgManager.getManager().sendMessage(new Event("leader",
                            ImcMsgManager.getManager().getLocalId().toPrettyString()),
                    ImcId16.BROADCAST_ID,
                    "Broadcast");
        }
    }

    // :::::::::::::::::::::::::::::::: Notification methods
    public void registerActiveSystemListener(Consumer<String[]> method) {
        systemListeners.add(method);
    }

    private void notifyActiveSystemListeners(String[] activeSystemNames) {
        for (Consumer<String[]> consumer : systemListeners) {
                consumer.accept(activeSystemNames);
        }
    }

    public void registerStateListener(Consumer<ElectionState> method) {
        stateListeners.add(method);
    }

    private void notifyStateListeners(ElectionState newState) {
        for (Consumer<ElectionState> stateListener : stateListeners) {
            stateListener.accept(newState);
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

    public ImcId16 getLeaderId() {
        return leaderId;
    }

    // :::::::::::::::::::::::::::::::: General Methods
    public void updateConnectedSystems() {
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
                    performElection = new PerformElection(leaderId.toPrettyString());
                    privateExecutor.submit(performElection);
                }
            }
            return;
        }
        if(!hasLeader && (state == ElectionState.IDLE || state == ElectionState.STARTING)){
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

    private class PerformElection extends Thread {
        String candidateId;

        public PerformElection(String candidateId) {
            super();
            this.candidateId = candidateId;
        }

        @Override
        public void run() {
            SystemImcMsgCommInfo leaderInfo = ImcMsgManager.getManager().getCommInfoById(leaderId);
            if(state == ElectionState.ACCEPTING) {
//                Election already underway
                return;
            }

            setState(ElectionState.CANDIDATE);
            ImcMsgManager.getManager()
                    .sendMessage(new Event("candidate", candidateId), ImcId16.BROADCAST_ID, "Broadcast");
            acceptCounter = 0;
            try {
//            wait 5sec
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                if (!hasLeader){
                    NeptusLog.pub().debug("Perform Election thread was interrupted in Election Manager");
                    setLeader(null);
                } else {
                    setState(ElectionState.ELECTED);
                }
            }
        }
    }

    private class HandleCandidate extends Thread {
        String candidateId;
        ImcId16 senderId;

        public HandleCandidate(String candidateId, ImcId16 senderId) {
            super();
            this.candidateId = candidateId;
        }

        @Override
        public void run() {
            ImcId16 lostLeaderId = new ImcId16(candidateId);
            if(!ArrayUtils.contains(getActiveSystems(),lostLeaderId)) {
                setState(ElectionState.ACCEPTING);
                ImcMsgManager.getManager()
                        .sendMessage(new Event("accept", candidateId), senderId, "Broadcast");

                try {
                    Thread.sleep(1500);
                    if(hasLeader && state == ElectionState.ACCEPTING) {
                        setState(ElectionState.ELECTED);
                    }
                } catch (InterruptedException e) {
                    NeptusLog.pub().debug("Candidate broadcasting thread was interrupted in Election Manager");
                }
            }
        }
    }
}
