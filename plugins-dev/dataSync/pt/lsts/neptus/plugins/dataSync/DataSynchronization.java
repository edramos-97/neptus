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
import pt.lsts.neptus.comm.manager.imc.ImcSystem;
import pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.console.plugins.planning.plandb.IPlanDBListener;
import pt.lsts.neptus.console.plugins.planning.plandb.PlanDBState;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.plugins.update.Periodic;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.util.GuiUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;


/**
 * @author Eduardo Ramos
 */
@PluginDescription(name = "Automated Data Synchronization", description = "Options for automated data sharing between" +
        " CCUs and vehicles")
@Popup(pos = POSITION.RIGHT, width = 800, height = 600)
public class DataSynchronization extends ConsolePanel {

    // Interface Components
    static JList<String> connectedSystems = null;

    @Subscribe
    public void on(Event evtMsg){
        System.out.println("Local mgs: " + evtMsg.getTopic());
        if(evtMsg.getSrc() != ImcMsgManager.getManager().getLocalId().intValue()){
            ConsistencyManager.getManager().on(evtMsg);
            ElectionManager.getManager().on(evtMsg);
        }
    }

    @Periodic(millisBetweenUpdates = 5000)
    public void updateConnectedSystems() {
        ElectionManager.getManager().updateConnectedSystems();
    }

//    @Periodic(millisBetweenUpdates = 1000)
//    public void hello() {
//
//    }

//    private Action refreshAction = new AbstractAction() {
//        @Override
//        public void actionPerformed(ActionEvent e) {
//            outputField.setText("");
//            outputField.append(getConsole().getImcMsgManager().getCommInfo().toString() + '\n');
//            outputField.append(getConsole().getImcMsgManager().getCommStatusAsHtmlFragment() + '\n');
//        }
//    };

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
        systemsPanel.setLayout(new BorderLayout(10,10));
        systemsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel label3 = new JLabel("Connected Systems");
        connectedSystems = new JList<>();
        connectedSystems.setBorder(new LineBorder(Color.BLACK,2,true));
        connectedSystems.setBackground(Color.WHITE);

        systemsPanel.add(label3, BorderLayout.NORTH);
//        systemsPanel.add(Box.createRigidArea(new Dimension(0,10)));
        systemsPanel.add(connectedSystems,BorderLayout.CENTER);

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

    void updateConnectedSystems(String[] systemNames) {
        if (connectedSystems != null) {
            connectedSystems.setListData(systemNames);
        }
    }

    private void addDataUpdateListeners() {
        ImcSystem localImcSystem = ImcSystemsHolder.lookupSystem(ImcMsgManager.getManager().getLocalId());
        if(localImcSystem != null) {
            localImcSystem.getPlanDBControl().addListener(planChangeListener);
            System.out.println("Added Plan Listener");
        }
    }

    @Override
    public void cleanSubPanel() {
        System.out.println("cleaning subpanel");
    }

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
    }

    IPlanDBListener planChangeListener = new IPlanDBListener() {
        @Override
        public void dbInfoUpdated(PlanDBState updatedInfo) {
            System.out.println("Info Updated");
        }

        @Override
        public void dbPlanReceived(PlanType spec) {
            System.out.println("Plan Received");
        }

        @Override
        public void dbPlanSent(String planId) {
            System.out.println("Plan Sent");
        }

        @Override
        public void dbPlanRemoved(String planId) {
            System.out.println("Plan Removed");
        }

        @Override
        public void dbCleared() {
            System.out.println("DB Cleared");
        }
    };
}
