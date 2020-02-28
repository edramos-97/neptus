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
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.TextMessage;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.messages.listener.MessageInfo;
import pt.lsts.neptus.messages.listener.MessageListener;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import pt.lsts.neptus.plugins.update.Periodic;


/**
 * @author Eduardo Ramos
 */
@PluginDescription(name = "Automated Data Synchronization", description = "Options for automated data sharing between CCUs and vehicles")
@Popup(pos = POSITION.RIGHT, width = 500, height = 600)
public class DataSynchronization extends ConsolePanel {

    JTextArea outputField = new JTextArea();
    MessageListener<MessageInfo, IMCMessage> listener = (messageInfo, imcMessage) -> {

    };

    @Periodic(millisBetweenUpdates = 3 * 1000)
    public void doIt () {
        System.out.println("hello");
        ImcMsgManager.getManager().broadcastToCCUs(new TextMessage("Laptop","MyTextMessage"));
    }

    @Subscribe
    public void on(TextMessage message) {
        System.out.println(message);
    }

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
        ImcMsgManager imcMsgManager = getConsole().getImcMsgManager();
        System.out.println(imcMsgManager.getAllServicesString());
        System.out.println(imcMsgManager.getCommInfo());
    }

    @Override
    public void initSubPanel() {
        removeAll();
        setLayout(new BorderLayout(5, 5));
        JButton refreshBtn = new JButton("refresh");
        refreshBtn.setAction(refreshAction);
        refreshBtn.setSize(100, 30);
        add(outputField, BorderLayout.CENTER);
        add(refreshBtn, BorderLayout.NORTH);

        getConsole().getImcMsgManager().addListener(listener);
    }
}
