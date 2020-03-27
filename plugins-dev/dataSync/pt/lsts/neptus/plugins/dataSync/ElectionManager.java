package pt.lsts.neptus.plugins.dataSync;

import com.google.common.eventbus.Subscribe;
import pt.lsts.imc.Event;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.SystemCommBaseInfo;
import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.manager.imc.SystemImcMsgCommInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ElectionManager {

    private static ElectionManager electionManager = null;

    private static ScheduledExecutorService privateExecutor = Executors.newSingleThreadScheduledExecutor();

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
        return createManager();
    }

    private static synchronized ElectionManager createManager() {
        if (electionManager == null) {
            electionManager = new ElectionManager();
        }
        return electionManager;
    }

    /**
     * Resets and initializes the start procedure of the election manager to find an existing leader or elect a new one
     */
    void initialize() {
        resetState();
        leaderSearch = new LeaderSearch();
        leaderSearch.start();
    }

    /**
     * Resets the state of the election manager
     */
    void resetState() {
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
        setState(ElectionState.RESET);
    }

    @Subscribe
    public void on(Event eventMsg) {
        String topic = eventMsg.getTopic();
        String data = "-1";
        ImcId16 sender = new ImcId16(eventMsg.getSrc());
//        String data = eventMsg.getData()
        switch (topic) {
            case "leader":
//                if this is leader send reliable
                if (isLeader) {
                    ImcMsgManager.getManager().sendReliablyNonBlocking(
                            new Event("leader", ImcMsgManager.getManager().getLocalId().toPrettyString()),
                            sender,
                            null);
                }
                break;
            case "candidate":

                ImcId16[] activeSystems = ImcMsgManager.getManager().getCommInfo().values().stream()
                        .filter(SystemImcMsgCommInfo::isActive)
                        .map(SystemCommBaseInfo::getSystemCommId)
                        .toArray(ImcId16[]::new);

                if (data.equals("-1") && state == ElectionState.IDLE) {
                    ImcMsgManager.getManager().sendReliablyNonBlocking(
                            new Event("accept",data),
                            sender,
                            null);
                } else {
                    ImcId16 candidateId = new ImcId16(ImcId16.parseImcId16(data));
                }
                break;
            case "accept":
                long noActiveSystems = ImcMsgManager.getManager().getCommInfo().values().stream()
                        .filter(SystemImcMsgCommInfo::isActive)
                        .count();
                if(++acceptCounter == noActiveSystems){
                    isLeader = true;
                    hasLeader = true;
                    leaderId = ImcMsgManager.getManager().getLocalId();
                    setState(ElectionState.ELECTED);
                    candidateBroadcasting.interrupt();
                }
//                if same size as network interrupt candidate broadcasting thread
                break;
            default:
                NeptusLog.pub().debug("Unknown message topic received in Election Manager");
        }
    }

    private void setState(ElectionState state) {
        this.state = state;
        System.out.println("setting state to:" + state.name());
        System.out.println(String.format("has leader: %b; is leader: %b leader id: %s;", hasLeader, isLeader,
                leaderId.toPrettyString()));
    }

    public String getStateColor() {
        return this.state.color;
    }

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

    /**
     * Executed the starting procedure to find an existing leader or begin the election a new one. In case neither
     * option is possible enter an idle state
     */
    private class LeaderSearch extends Thread {
        @Override
        public void run() {
            setState(ElectionState.STARTING);
//            ask leader
            ImcMsgManager.getManager().sendMessage(new Event("leader", ""), ImcId16.BROADCAST_ID, "broadcast");
//            wait 3 sec
            try {
                Thread.sleep(2000);

//            check if has leader and return
                if (hasLeader) {
                    setState(ElectionState.ELECTED);
                }

//            check if there are other node in the network
                long noActiveSystems = ImcMsgManager.getManager().getCommInfo().values().stream()
                        .filter(SystemImcMsgCommInfo::isActive)
                        .count();
                if (noActiveSystems > 0) {
//            start candidate thread and detach
                    privateExecutor.submit(new CandidateBroadcasting("-1"));
                } else {
//                    if not then sleep
                    setState(ElectionState.IDLE);
                }

                Thread.sleep(2000);

                if (hasLeader || isLeader) {
                    setState(ElectionState.ELECTED);
                } else {
                    privateExecutor.submit(new LeaderSearch());
                    return;
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
            ImcMsgManager.getManager().sendMessage(new Event("candidate", "-1"), ImcId16.BROADCAST_ID, "broadcast");
            acceptCounter = 0;
            try {
//            wait 5sec
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                if (!hasLeader && !isLeader) {
                    NeptusLog.pub().debug("Candidate broadcasting thread was interrupted in ");
                }
            }
        }
    }

}
