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
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.imc.ImcId16;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.console.plugins.MissionChangeListener;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.plugins.dataSync.CRDTs.CRDT;
import pt.lsts.neptus.plugins.dataSync.CRDTs.Operations;
import pt.lsts.neptus.plugins.dataSync.CRDTs.PlanCRDT;
import pt.lsts.neptus.plugins.update.Periodic;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.util.GuiUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.time.Instant;
import java.util.*;


/**
 * @author Eduardo Ramos
 */
@PluginDescription(name = "Automated Data Synchronization", description = "Options for automated data sharing between" +
                                                                          " CCUs and vehicles")
@Popup(pos = POSITION.RIGHT, width = 800, height = 600)
public class DataSynchronization extends ConsolePanel {

    // Interface Components
    private static JList<String> connectedSystems = null;
    private static JLabel localState = null;
    private static JTable crdtTable = null;

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
                ConsistencyManager.getManager().setLoadedComplete(true);
            } else {
                Set<String> removedPlanIDs = Operations.diff(lastPlanList.keySet(),
                        currPlanList.keySet());
                Set<String> newPlanIDs = Operations.diff(currPlanList.keySet(),
                        lastPlanList.keySet());

                Set<String> updatedPlanIDs = Operations.filteredAndMapped(currPlanList.entrySet(),
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
                    // only create those that are not remote plans
                    if(!planID.matches("(.*)\\([0-9a-f]{2}:[0-9a-f]{2}\\)$")) {
                        PlanType newPlan = currPlanList.get(planID);
                        ConsistencyManager.getManager().createCRDT(newPlan.getId(), newPlan, CRDT.CRDTType.PLAN);
                    }
                }

                for (String updatedID : updatedPlanIDs) {
                    PlanType updatedPlan = currPlanList.get(updatedID);
                    PlanType oldPlan = lastPlanList.get(updatedID);
                    if(!Arrays.equals(oldPlan.asIMCPlan().payloadMD5(),(updatedPlan.asIMCPlan().payloadMD5()))) {
                        ConsistencyManager.getManager().updateCRDT(updatedPlan.getId(), updatedPlan);
                    }
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
    ConsistencyManager.PlanChangeListener planChangeListener = new ConsistencyManager.PlanChangeListener() {
        @Override
        public void change(PlanCRDT planCRDT) {
            PlanType newPlan = planCRDT.payload(getConsole().getMission());
            getConsole().getMission().addPlan(newPlan);
            getConsole().getMission().save(true);
            getConsole().updateMissionListeners();
        }
    };
    private ConsistencyManager.CRDTChangeListener crdtChangeListener = new ConsistencyManager.CRDTChangeListener() {
        @Override
        public void change(UUID id, CRDT newData, ImcId16 origSystem, String origName) {
            DefaultTableModel model = (DefaultTableModel) crdtTable.getModel();
            Vector<Vector> dataVector = model.getDataVector();

            int idColIndex = model.findColumn("ID");
            if(idColIndex == -1) {
                NeptusLog.pub().error("Column Id not found in data synchronization table update");
            }

            int removedRow = -1;
            for (int i = 0; i < dataVector.size(); i++) {
                Vector vector = dataVector.get(i);
                if (vector.get(idColIndex) == id) {
                    removedRow = i;
                    break;
                }
            }
            if(removedRow != -1) {
                model.removeRow(removedRow);
            }
//            "Name","Original System","Last Update", "ID"
            model.addRow(new Object[]{origName,origSystem, Date.from(Instant.now()),id});
            model.fireTableDataChanged();
        }
    };

    public DataSynchronization(ConsoleLayout console) {
        super(console);
    }

    public static JSplitPane getStatusPanel() {

        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.PAGE_AXIS));
        statusPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel label1 = new JLabel("Local Network State");
        localState = new JLabel("IDLE");
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

    public static JPanel getTestingPanel() {
        FlowLayout flowLayout = new FlowLayout();
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        panel.setLayout(flowLayout);
        flowLayout.setAlignment(FlowLayout.LEADING);

        panel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);


        JButton sendCRDTDataMsgBtn = new JButton("Send data");
        sendCRDTDataMsgBtn.addActionListener(e -> {
            Event evtMsg = new Event("crdt_data", "test_data");
            ImcMsgManager.getManager().sendMessage(evtMsg, ImcId16.BROADCAST_ID, "Broadcast");
        });
        JButton sendCRDTDeleteMsgBtn = new JButton("Send delete");
        sendCRDTDeleteMsgBtn.addActionListener(e -> {
            Event evtMsg = new Event("crdt_removed", "test_data");
            ImcMsgManager.getManager().sendMessage(evtMsg, ImcId16.BROADCAST_ID, "Broadcast");
        });

        panel.add(sendCRDTDataMsgBtn);
        panel.add(sendCRDTDeleteMsgBtn);

        return panel;
    }

    public static void main(String[] args) {
//        JTabbedPane tabsPane = new JTabbedPane(SwingConstants.TOP);
//
//        JSplitPane statusPane = getStatusPanel();
//        statusPane.setDividerLocation(320);
//        tabsPane.add("Status", statusPane);
//        tabsPane.add("tetsing", getTestingPanel());

        GuiUtils.testFrame(getCRDTInfoPanel(), "DataSync", 800, 600);
    }

    // CRDT LIST PANEL
    private static JComponent getCRDTInfoPanel() {

        TableCellRenderer dateCellRenderer = new DefaultTableCellRenderer() {
//            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss");

            public Component getTableCellRendererComponent(JTable table,
                                                           Object value, boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                if( value instanceof Date) {
                    value = DateFormat.getTimeInstance().format(value);
                }
                return super.getTableCellRendererComponent(table, value, isSelected,
                        hasFocus, row, column);
            }
        };

        String[] column_names = {"Name","Original System","Last Update", "ID"};
        DefaultTableModel table_model = new DefaultTableModel(column_names,0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        crdtTable = new JTable(table_model);
        crdtTable.getColumn("Last Update").setCellRenderer(dateCellRenderer);

        final JPopupMenu popupMenu = getCRDTOptionsMenu();
        crdtTable.setComponentPopupMenu(popupMenu);
        crdtTable.setAutoCreateRowSorter(true);
        // prevent edition of cells
        crdtTable.setEnabled(false);

//        Calendar calendar = Calendar.getInstance();
//        table_model.addRow(new Object[]{"serial","MedName",calendar.getTime(), UUID.randomUUID()});
//        calendar.roll(Calendar.HOUR, -3);
//        table_model.addRow(new Object[]{"serial6","MedName3",calendar.getTime(), UUID.randomUUID()});
//        System.out.println(table_model.getDataVector());

        return new JScrollPane(crdtTable);
    }

    private static JPopupMenu getCRDTOptionsMenu() {
        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem saveAsLocal = new JMenuItem("Save as Local");
        saveAsLocal.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), "Saved as local variable");
            }
        });
        popupMenu.add(saveAsLocal);
        return popupMenu;
    }

    @Subscribe
    public void on(Event evtMsg) {
        System.out.println("New Event Msg: " + evtMsg.getTopic());
        try {
            if (evtMsg.getSrc() != ImcMsgManager.getManager().getLocalId().intValue()) {
                ConsistencyManager.getManager().on(evtMsg);
                ElectionManager.getManager().on(evtMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Periodic(millisBetweenUpdates = 10000)
    public void updateConnectedSystems() {
        ElectionManager.getManager().updateConnectedSystems();
    }

//    ::::::::::::::::::::::::::::::::::::::::: Listeners

    private void registerListeners() {
        this.getConsole().addMissionListener(missionChangeListener);
        ConsistencyManager.getManager().addPlanListener(planChangeListener);
        ConsistencyManager.getManager().addCRDTListener(crdtChangeListener);
        ElectionManager.getManager().registerActiveSystemListener(DataSynchronization::
        updateConnectedSystemsInterface);
        ElectionManager.getManager().registerStateListener(DataSynchronization::updateLocalNetworkState);
    }

    public static void updateConnectedSystemsInterface(String[] systemNames) {
        if (connectedSystems != null) {
            connectedSystems.setListData(systemNames);
        }
    }

    public static void updateLocalNetworkState(ElectionManager.ElectionState state) {
        if (localState != null) {
            localState.setText(state.name());
            localState.setBackground(Color.decode(ElectionManager.getManager().getStateColor()));
        }
        ImcId16 leaderId = ElectionManager.getManager().getLeaderId();
        if(state.equals(ElectionManager.ElectionState.ELECTED) && !leaderId.equals(ImcMsgManager.getManager().getLocalId())) {
            ConsistencyManager.getManager().synchronizeLocalData(leaderId, true,true);
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

        tabsPane.add("Testing", getTestingPanel());
        tabsPane.add("CRDTs", getCRDTInfoPanel());

        JSplitPane statusPane = getStatusPanel();
        statusPane.setDividerLocation(320);
        tabsPane.add("Status", statusPane);

        add(tabsPane, BorderLayout.CENTER);

        registerListeners();

        ElectionManager.getManager().initialize();
        ConsistencyManager.getManager();
        missionChangeListener.missionUpdated(getConsole().getMission());
    }
}
