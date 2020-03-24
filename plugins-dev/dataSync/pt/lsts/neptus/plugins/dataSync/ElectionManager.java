package pt.lsts.neptus.plugins.dataSync;

import com.google.common.eventbus.Subscribe;
import pt.lsts.imc.Event;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.imc.ImcId16;

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
                NeptusLog.pub().debug("ElectionManager state reset interrupted waiting for candidate broadcast to join");
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
        switch (topic) {
            case "leader":
//                if this is leader send reliably
                break;
            case "candidate":
//                add to counter
//                if same size ad network interrupt candidate broadcasting thread
                break;
            default:
                NeptusLog.pub().debug("Unknown message topic received in Election Manager");
        }
    }

    private void setState(ElectionState state) {
        this.state = state;
    }

    enum ElectionState {
        RESET,
        STARTING,
        IDLE,
        ELECTED,
        CANDIDATE,
        ACCEPTING,
    }

    /**
     * Executed the starting procedure to find an existing leader or begin the election a new one. In case neither
     * option is possible enter an idle state
     */
    private class LeaderSearch extends Thread {
        @Override
        public void run() {
            setState(ElectionState.STARTING);
//            ask for leader
//            wait 3 sec
//            check if has leader and return

//            check if there are other node in the network
//            if not then sleep
//            if there is just one
//            start candidate thread and detach
//            else retry ask for leader

//            CATCH INTERRUPT
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
//            wait 5sec

//            CATCH INTERRUPT
        }
    }

}
