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

import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.util.GuiUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;


/**
 * @author Eduardo Ramos
 */
@PluginDescription(name = "Automated Data Synchronization", description = "Options for automated data sharing between CCUs and vehicles")
@Popup(pos = POSITION.RIGHT, width = 500, height = 600)
public class DataSynchronization extends ConsolePanel {

    JTextArea outputField = new JTextArea();


    private Action refreshAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            outputField.setText("");
            outputField.append(getConsole().getImcMsgManager().getCommInfo().toString() + '\n');
            outputField.append(getConsole().getImcMsgManager().getCommStatusAsHtmlFragment() + '\n');
        }
    };

    public DataSynchronization(ConsoleLayout console) {
        super(console);
    }

    @Override
    public void cleanSubPanel() {
        System.out.println("cleaning subpanel");
//        ImcMsgManager imcMsgManager = getConsole().getImcMsgManager();
//        System.out.println(imcMsgManager.getAllServicesString());
//        System.out.println(imcMsgManager.getCommInfo());
    }

    @Override
    public void initSubPanel() {
        removeAll();
        setLayout(new BorderLayout(5, 5));
        JTabbedPane tabsPane = new JTabbedPane(SwingConstants.RIGHT);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setSize(100, 30);
        refreshBtn.setText("Refresh");
        refreshBtn.setAction(refreshAction);
        add(outputField, BorderLayout.CENTER);
        add(refreshBtn, BorderLayout.NORTH);
    }

    public static JSplitPane getStatusPanel() {

        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel,BoxLayout.PAGE_AXIS));
        statusPanel.setBorder(new EmptyBorder(10,10,10,10));

        JLabel label1 = new JLabel("IDLE");
        label1.setOpaque(true);
        label1.setMaximumSize(new Dimension(Short.MAX_VALUE, label1.getMaximumSize().height * 5));
        label1.setHorizontalAlignment(SwingConstants.CENTER);
        label1.setBorder(BorderFactory.createLineBorder(Color.RED,3, true));
        label1.setBackground(Color.green);


        statusPanel.add(label1);
//        statusPanel.add(button2);
//        statusPanel.add(Box.createVerticalGlue());
//        statusPanel.add(button3);


        JButton button4 = new JButton("RESET");
        JSplitPane statusSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,statusPanel,button4);
        statusSplitPane.setEnabled(false);
        statusSplitPane.setDividerLocation(0.40);
        return statusSplitPane;
    }

    public static void main(String[] args) {
        JTabbedPane tabsPane = new JTabbedPane(SwingConstants.TOP);

        JSplitPane statusPane = getStatusPanel();
        tabsPane.add("Status", statusPane);

        GuiUtils.testFrame(tabsPane,"DataSync", 800, 600);

        statusPane.setDividerLocation(0.40);
        statusPane.setDividerSize(0);
    }
}
