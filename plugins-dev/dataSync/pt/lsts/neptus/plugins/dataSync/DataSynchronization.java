/*
 * Copyright (c) 2004-2020 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Eduardo Ramos
 * 18/02/2020
 */

package pt.lsts.neptus.plugins.dataSync;

import com.google.common.eventbus.Subscribe;
import pt.lsts.imc.Event;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.console.plugins.MissionChangeListener;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.plugins.dataSync.CRDTs.CRDT;
import pt.lsts.neptus.plugins.dataSync.CRDTs.PlanCRDT;
import pt.lsts.neptus.plugins.update.Periodic;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.util.GuiUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * @author Eduardo Ramos
 */
@PluginDescription(name = "Automated Data Synchronization", description = "Options for automated data sharing between" +
        " CCUs and vehicles")
@Popup(pos = POSITION.RIGHT, width = 800, height = 600)
public class DataSynchronization extends ConsolePanel {

    // Interface Components
    static JList<String> connectedSystems = null;

    public DataSynchronization(ConsoleLayout console) {
        super(console);
    }

    public static JSplitPane getStatusPanel() {

        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.PAGE_AXIS));
        statusPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel label1 = new JLabel("Local Network State");
        JLabel localState = new JLabel("IDLE");
        localState.setOpaque(true);
        localState.setMaximumSize(new Dimension(Short.MAX_VALUE, localState.getMaximumSize().height * 5));
        localState.setHorizontalAlignment(SwingConstants.CENTER);
        localState.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2, true));
        localState.setBackground(Color.decode(ElectionManager.getManager().getStateColor()));

        JLabel label2 = new JLabel("Wide Area Network State");
        JLabel wideAreaState = new JLabel("IDLE");
        wideAreaState.setOpaque(true);
        wideAreaState.setMaximumSize(new Dimension(Short.MAX_VALUE, wideAreaState.getMaximumSize().height * 5));
        wideAreaState.setHorizontalAlignment(SwingConstants.CENTER);
        wideAreaState.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2, true));
        wideAreaState.setBackground(Color.decode(ElectionManager.getManager().getStateColor()));

        statusPanel.add(label1);
        statusPanel.add(localState);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        statusPanel.add(label2);
        statusPanel.add(wideAreaState);
        statusPanel.add(Box.createVerticalGlue());

        JPanel systemsPanel = new JPanel();
        systemsPanel.setLayout(new BorderLayout(10, 10));
        systemsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel label3 = new JLabel("Connected Systems");
        connectedSystems = new JList<>();
        connectedSystems.setBorder(new LineBorder(Color.BLACK, 2, true));
        connectedSystems.setBackground(Color.WHITE);

        systemsPanel.add(label3, BorderLayout.NORTH);
//        systemsPanel.add(Box.createRigidArea(new Dimension(0,10)));
        systemsPanel.add(connectedSystems, BorderLayout.CENTER);

        JSplitPane statusSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statusPanel, systemsPanel);
        statusSplitPane.setEnabled(false);
        statusSplitPane.setDividerSize(0);
        return statusSplitPane;
    }

    public static void main(String[] args) {
        JTabbedPane tabsPane = new JTabbedPane(SwingConstants.TOP);

        JSplitPane statusPane = getStatusPanel();
        statusPane.setDividerLocation(320);
        tabsPane.add("Status", statusPane);

        GuiUtils.testFrame(tabsPane, "DataSync", 800, 600);
    }

    @Subscribe
    public void on(Event evtMsg) {
        System.out.println("Local mgs: " + evtMsg.getTopic());
        if (evtMsg.getSrc() != ImcMsgManager.getManager().getLocalId().intValue()) {
            ConsistencyManager.getManager().on(evtMsg);
            ElectionManager.getManager().on(evtMsg);
        }
    }

    @Periodic(millisBetweenUpdates = 5000)
    public void updateConnectedSystems() {
        ElectionManager.getManager().updateConnectedSystems();
    }

    void updateConnectedSystems(String[] systemNames) {
        if (connectedSystems != null) {
            connectedSystems.setListData(systemNames);
        }
    }

    private void addDataUpdateListeners() {
        this.getConsole().addMissionListener(missionChangeListener);
        ConsistencyManager.getManager().addPlanListener(planChangeListener);
    }

    @Override
    public void cleanSubPanel() {
        System.out.println("cleaning subpanel");
    }

//    ::::::::::::::::::::::::::::::::::::::::: Listeners

    MissionChangeListener missionChangeListener = new MissionChangeListener() {
        private TreeMap<String, PlanType> lastPlanList = null;

        @Override
        public void missionReplaced(MissionType mission) {

        }

        @Override
        public void missionUpdated(MissionType mission) {
            ConsistencyManager manager = ConsistencyManager.getManager();
            TreeMap<String, PlanType> currPlanList = mission.getIndividualPlansList();
//            :::::::: Handle plan changes
            if (lastPlanList == null) {
                for (Map.Entry<String, PlanType> entry : currPlanList.entrySet()) {
                    manager.createCRDT(entry.getKey(), entry.getValue(), CRDT.CRDTType.PLAN);
                }
                lastPlanList = new TreeMap<>();
            } else {
                Set<String> removedPlanIDs = Operations.diff(lastPlanList.keySet(),
                        currPlanList.keySet());
                Set<String> newPlanIDs = Operations.diff(currPlanList.keySet(),
                        lastPlanList.keySet());

                Set<String> updatedPlanIDs = Operations.filteredAndMapped(currPlanList
                                .entrySet(),
                        element -> !lastPlanList.entrySet().contains(element) && !newPlanIDs.contains(element.getKey()),
                        Map.Entry::getKey);


                System.out.println("\n\nnew plans:" + newPlanIDs);
                System.out.println("removed plans:" + removedPlanIDs);
                System.out.println("updated plans:" + updatedPlanIDs + "\n\n");

                // delete CRDT from removed plans
                for (String removedID : removedPlanIDs) {
                    ConsistencyManager.getManager().deleteCRDT(removedID);
                }

                for (String planID : newPlanIDs) {
                    PlanType newPlan = currPlanList.get(planID);
                    ConsistencyManager.getManager().createCRDT(newPlan.getId(), newPlan, CRDT.CRDTType.PLAN);
                }

                for (String updatedID : updatedPlanIDs) {
                    PlanType updatedPlan = currPlanList.get(updatedID);
                    ConsistencyManager.getManager().updateCRDT(updatedPlan.getId(), updatedPlan);
                }

                /*Set<Map.Entry<String,PlanType>> possibleUpdates = Operations.diff(newPlans,currPlanList.entrySet());
                Set<Map.Entry<String,PlanType>> updates = new HashSet<>();
                for (Map.Entry<String,PlanType> possibleUpdate:possibleUpdates) {
                    PlanType oldPlan = lastPlanList.get(possibleUpdate.getKey());
                    PlanType currPlan = currPlanList.get(possibleUpdate.getKey());
                    if(!oldPlan.equals(currPlan)){
                        updates.add(possibleUpdate);
                    }
                }*/
            }
            lastPlanList.clear();
            lastPlanList.putAll(currPlanList);
//            ::::::::
        }
    };

    ConsistencyManager.ChangeListener planChangeListener = new ConsistencyManager.ChangeListener() {
        @Override
        public void change(Object planCRDT) {
            PlanType newPlan = ((PlanCRDT) planCRDT).payload(getConsole().getMission());
            getConsole().getMission().addPlan(newPlan);
            getConsole().getMission().save(true);
            getConsole().updateMissionListeners();
            getConsole().setPlan(newPlan);
        }
    };

    @Override
    public void initSubPanel() {
        removeAll();
        setLayout(new BorderLayout(5, 5));

        JTabbedPane tabsPane = new JTabbedPane(SwingConstants.TOP);

        JSplitPane statusPane = getStatusPanel();
        statusPane.setDividerLocation(320);
        tabsPane.add("Status", statusPane);

        add(tabsPane, BorderLayout.CENTER);

        addDataUpdateListeners();

//        ElectionManager.getManager().initialize();
        ConsistencyManager.getManager();
        missionChangeListener.missionUpdated(getConsole().getMission());
    }
}
