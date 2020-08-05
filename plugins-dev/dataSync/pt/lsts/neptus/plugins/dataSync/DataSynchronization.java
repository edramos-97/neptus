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
import pt.lsts.neptus.plugins.ConfigurationListener;
import pt.lsts.neptus.plugins.NeptusProperty;
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
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.time.Instant;
import java.util.*;


/**
 * @author Eduardo Ramos
 */
@PluginDescription(name = "Automated Data Synchronization", description = "Options for automated data sharing between" +
                                                                          " CCUs and vehicles")
@Popup(pos = POSITION.RIGHT, width = 800, height = 600)
public class DataSynchronization extends ConsolePanel implements ConfigurationListener {

    @NeptusProperty(name = "List of well-known systems", category = "Wide-area Sync Options", userLevel =
            NeptusProperty.LEVEL.ADVANCED,
            description = "Keeps the addresses of well-known nodes to exchange data with")
    private static String listOfAddresses = "";

    @NeptusProperty(name = "Synchronization Period", category = "Wide-area Sync Options", userLevel =
            NeptusProperty.LEVEL.ADVANCED,
            description = "If this system is elected the local speaker, represents the interval between attempts to " +
                          "synchronized with systems connected over a wide-area " +
                          "network, a value of zero will disable synchronization", units = "minutes")
    private static long syncPeriod = 0;

    @NeptusProperty(name = "Synchronize Plans", category = "Local Network CDRT Sync", userLevel =
            NeptusProperty.LEVEL.ADVANCED,
            description = "Whether Plans should be synchronized in the local network")
    private static boolean syncPlansLocal = true;

    @NeptusProperty(name = "Synchronize Plans", category = "Wide-area Network CRDT Sync", userLevel =
            NeptusProperty.LEVEL.ADVANCED,
            description = "Whether Plans should be synchronized over wide-area connections")
    private static boolean syncPlansWide = true;

    // Interface Components
    private static JList<String> connectedSystems = null;
    private static JLabel localState = null;
    private static JButton setLeaderConfigBtn = null;
    private static JTable crdtTable = null;
    private static JTable remoteSystemsTable = null;

    private static TableCellRenderer dateCellRenderer = new DefaultTableCellRenderer() {
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

            // data is null if operation being notified was a delete
            if(newData != null) {
//            "Name","Original System","Last Update", "ID"
                model.addRow(new Object[]{origName,origSystem, Date.from(Instant.now()),id});
            }
            model.fireTableDataChanged();
        }
    };

    public DataSynchronization(ConsoleLayout console) {
        super(console);
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

    public static void main(String[] args) throws UnknownHostException {
//        JTabbedPane tabsPane = new JTabbedPane(SwingConstants.TOP);
//
//        JSplitPane statusPane = getStatusPanel();
//        statusPane.setDividerLocation(320);
//        tabsPane.add("Status", statusPane);
//        tabsPane.add("tetsing", getTestingPanel())


//        Dictionary<String, String, Pair<String,String>> dict1 = new Dictionary<>();
//        Dictionary<String, String, Pair<String,String>> dict2 = new Dictionary<>();
//        ImcId16 myId = new ImcId16("0x4001");
//        dict2.myId = myId;
//        dict2.versionVector.put(myId,0L);



//        dict1.add(new Pair<>("x", "1"));
//        dict1.add(new Pair<>("x", "1"));
//        System.out.println(dict1.versionVector);
//        dict1.add(new Pair<>("x", "4"));
//        System.out.println(dict1.versionVector);
//        dict1.add(new Pair<>("y", "2"));
//        dict1.add(new Pair<>("z", "3"));
//        System.out.println(dict1.versionVector);
//
//        System.out.println(dict1.payload());
//
//        dict2.merge(dict1);
//        System.out.println(dict2.versionVector);
//        System.out.println(dict2.payload());
//
//        dict2.add(new Pair<>("a","1"));
//        dict1.add(new Pair<>("x","5"));
//
//        dict2.add(new Pair<>("x","6"));

//        dict2.merge(dict1);
//        dict1.merge(dict2);
//        System.out.println(dict1.payload());
//        System.out.println(dict2.payload());
        GuiUtils.testFrame(getStatusPanel(), "DataSync", 800, 600);
    }

    // PROPERTIES PANEL
    private static LinkedHashMap<String,?> getPropertiesAsMap() {
        LinkedHashMap<String, Object> propertiesMap = new LinkedHashMap<>();

        propertiesMap.put("addresses", listOfAddresses);

        if (syncPlansLocal) {
            propertiesMap.put("syncPlansLocal", "");
        }
        if (syncPlansWide) {
            propertiesMap.put("syncPlansWide", "");
        }

        return propertiesMap;
    }

    private void applyProperties(LinkedHashMap<String,?> propertiesMap) {
        String listOfAddresses = (String) propertiesMap.get("addresses");
        if(listOfAddresses != null) {
            this.listOfAddresses = listOfAddresses;
        }
        syncPlansLocal = propertiesMap.containsKey("syncPlansLocal");
        syncPlansWide = propertiesMap.containsKey("syncPlansWide");

        propertiesChanged();
    }

    private static JButton getSetLeaderConfigBtn() {
        JButton setLeaderProps = new JButton("Set Leader Config");
        setLeaderProps.setToolTipText("Send local configuration to be applied in the chosen system");

        LinkedHashMap<String, ?> propertiesMap = getPropertiesAsMap();

        Event evtMsg = new Event("data_configuration", "placeholder");
        evtMsg.setData(propertiesMap);

        setLeaderProps.addActionListener(e -> {
            ImcMsgManager.getManager().sendMessage(evtMsg, ImcId16.BROADCAST_ID, "Broadcast");
        });

        setLeaderProps.setEnabled(false);

        return setLeaderProps;
    }

    // STATUS PANEL
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

//        JLabel label2 = new JLabel("Wide Area Network State");
//        JLabel wideAreaState = new JLabel("IDLE");
//        wideAreaState.setOpaque(true);
//        wideAreaState.setMaximumSize(new Dimension(Short.MAX_VALUE, wideAreaState.getMaximumSize().height * 5));
//        wideAreaState.setHorizontalAlignment(SwingConstants.CENTER);
//        wideAreaState.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2, true));
//        wideAreaState.setBackground(Color.decode(ElectionManager.getManager().getStateColor()));

        statusPanel.add(label1);
        statusPanel.add(localState);
        statusPanel.add(Box.createRigidArea(new Dimension(0, 10)));
//        statusPanel.add(label2);
//        statusPanel.add(wideAreaState);
        statusPanel.add(Box.createVerticalGlue());
        setLeaderConfigBtn = getSetLeaderConfigBtn();
        statusPanel.add(setLeaderConfigBtn);

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

    // CRDT LIST PANEL
    private static JComponent getCRDTInfoPanel() {

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

        return new JScrollPane(crdtTable);
    }

    private static JPopupMenu getCRDTOptionsMenu() {
        int rowAtPoint;
        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem saveAsLocal = new JMenuItem("Save as Local");
        saveAsLocal.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultTableModel model = (DefaultTableModel) crdtTable.getModel();
                Vector o = (Vector) model.getDataVector().get(crdtTable.getSelectedRow());

                UUID id = (UUID) o.get(model.findColumn("ID"));

                JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), String
                        .format("Saved id:%s as local variable", id.toString()));
            }
        });

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultTableModel model = (DefaultTableModel) crdtTable.getModel();
                Vector o = (Vector) model.getDataVector().get(crdtTable.getSelectedRow());

                ImcId16 originalSystem = (ImcId16) o.get(model.findColumn("Original System"));
                String name = (String) o.get(model.findColumn("Name"));

                if(originalSystem.equals(ImcMsgManager.getManager().getLocalId())) {
                    ConsistencyManager.getManager().deleteCRDT(name);
                } else {
                    ConsistencyManager.getManager().deleteCRDT(name + "(" + originalSystem.toPrettyString() + ")");
                }
            }
        });
        popupMenu.add(saveAsLocal);
        popupMenu.add(deleteItem);

        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        int rowAtPoint = crdtTable.rowAtPoint(SwingUtilities.convertPoint(popupMenu, new Point(0, 0),
                                crdtTable));
                        if (rowAtPoint > -1) {
                            crdtTable.setRowSelectionInterval(rowAtPoint, rowAtPoint);
                        }
                    }
                });
            }
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // TODO Auto-generated method stub
            }
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                // TODO Auto-generated method stub
            }
        });

        return popupMenu;
    }

    // WIDE AREA SYSTEMS LIST
    private static JComponent getWideAreaSystems() {
        JPanel remoteSystemsPanel = new JPanel();
        remoteSystemsPanel.setLayout(new BoxLayout(remoteSystemsPanel, BoxLayout.Y_AXIS));
        remoteSystemsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // SYSTEMS TABLE
        String[] column_names = {"Name","Address", "Last Synchronization"};
        DefaultTableModel table_model = new DefaultTableModel(column_names,0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        remoteSystemsTable = new JTable(table_model);
        remoteSystemsTable.getColumn("Last Synchronization").setCellRenderer(dateCellRenderer);

        final JPopupMenu popupMenu = getCRDTOptionsMenu();
        remoteSystemsTable.setComponentPopupMenu(popupMenu);
        remoteSystemsTable.setAutoCreateRowSorter(true);
        // prevent edition of cells
        remoteSystemsTable.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(remoteSystemsTable);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        DefaultTableModel model = (DefaultTableModel) remoteSystemsTable.getModel();
        model.addRow(new Object[]{"Name1", "172.123.245.678", null});

        // ADD SYSTEMS BUTTON
        JButton addSystemBtn = new JButton("Add System...");
        addSystemBtn.addActionListener(e -> {
            System.out.println("Adding new system");
        });
        addSystemBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        remoteSystemsPanel.add(scrollPane);
        remoteSystemsPanel.add(addSystemBtn);

        return remoteSystemsPanel;
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

    @Override
    public void propertiesChanged() {
        if (syncPlansLocal) {
            getConsole().addMissionListener(missionChangeListener);
        } else {
            getConsole().removeMissionListener(missionChangeListener);
        }

        ConsistencyManager.getManager().setShareWideAreaEntry("plan",syncPlansWide);

        String[] addressStrings = listOfAddresses.split(";");
        Set<InetAddress> validAddresses = new HashSet<>();
        for (String addressString : addressStrings) {
            try {
                InetAddress inetAddress = Inet4Address.getByName(addressString);
                if(!inetAddress.isLoopbackAddress() && !inetAddress.isSiteLocalAddress()) {
                    validAddresses.add(inetAddress);
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        ConsistencyManager.getManager().wideAreaSystemsChange(validAddresses);
    }

    public static void updateConnectedSystemsInterface(String[] systemNames) {
        if (connectedSystems != null) {
            connectedSystems.setListData(systemNames);
        }
    }

    public static void updateLocalNetworkState(ElectionManager.ElectionState state) {
        if (localState != null && setLeaderConfigBtn != null) {
            localState.setBackground(Color.decode(ElectionManager.getManager().getStateColor()));

            if(state == ElectionManager.ElectionState.ELECTED) {
                setLeaderConfigBtn.setEnabled(true);
                localState.setText(state.name() + " : " + ElectionManager.getManager().getLeaderId().toPrettyString());
            } else {
                setLeaderConfigBtn.setEnabled(false);
                localState.setText(state.name());
            }
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

//        tabsPane.add("Testing", getTestingPanel());

        JSplitPane statusPane = getStatusPanel();
        statusPane.setDividerLocation(320);
        tabsPane.add("Status", statusPane);

        tabsPane.add("CRDTs Listing", getCRDTInfoPanel());
        tabsPane.add("Wide-Area Systems", getWideAreaSystems());

        add(tabsPane, BorderLayout.CENTER);

        registerListeners();

//        ImcMsgManager.getManager().setLocalId(new ImcId16(0x4100 + new Random().nextInt() % 255));
        ElectionManager.getManager().initialize();
        ConsistencyManager.getManager();
        missionChangeListener.missionUpdated(getConsole().getMission());
    }

}
