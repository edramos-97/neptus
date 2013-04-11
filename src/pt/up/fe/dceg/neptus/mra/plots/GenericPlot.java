/*
 * Copyright (c) 2004-2013 Universidade do Porto - Faculdade de Engenharia
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
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: José Pinto
 * Nov 13, 2012
 */
package pt.up.fe.dceg.neptus.mra.plots;

import java.util.Arrays;
import java.util.Vector;

import javax.swing.ImageIcon;

import pt.up.fe.dceg.neptus.NeptusLog;
import pt.up.fe.dceg.neptus.imc.IMCMessage;
import pt.up.fe.dceg.neptus.imc.lsf.LsfIndex;
import pt.up.fe.dceg.neptus.mra.MRAPanel;
import pt.up.fe.dceg.neptus.util.ImageUtils;

/**
 * @author zp
 *
 */
public class GenericPlot extends MraTimeSeriesPlot {

    protected String[] fieldsToPlot = null;

    public GenericPlot(String[] fieldsToPlot, MRAPanel panel) {
        super(panel);
        this.fieldsToPlot = fieldsToPlot;
    }
    
    @Override
    public ImageIcon getIcon() {
        return ImageUtils.getIcon("images/menus/graph.png");
    }
    
    @Override
    public String getTitle() {
        return getName();
    }
    
    @Override
    public String getName() {
        return Arrays.toString(fieldsToPlot);
    }    
    
    @Override
    public boolean canBeApplied(LsfIndex index) {        
        for (String field : fieldsToPlot) {
            String messageName = field.split("\\.")[0];
            if (index.getFirstMessageOfType(messageName) == -1)
                return false;
        }
        return true;
    }
    
    public Vector<String> getForbiddenSeries() {
        return forbiddenSeries;
    }

    @Override
    public void process(LsfIndex source) {
        NeptusLog.pub().info("<###>Generic Plot process");
        long t = System.currentTimeMillis();
        for (String field : fieldsToPlot) {
            String messageName = field.split("\\.")[0];
            String variable = field.split("\\.")[1];
            
            for (IMCMessage m : source.getIterator(messageName, 0, (long)(timestep * 1000))) {
                String seriesName = "";

                if(m.getValue("id") != null) {
                    seriesName = m.getSourceName()+"."+source.getEntityName(m.getSrc(), m.getSrcEnt())+"."+field+"."+m.getValue("id");
                } 
                else { 
//                    System.out.println(m.getAbbrev() + " " + source.getEntityName(m.getSrc(), m.getSrcEnt()));
                    seriesName = m.getSourceName()+"."+source.getEntityName(m.getSrc(), m.getSrcEnt())+"."+field;
                }
                
                if (m.getMessageType().getFieldUnits(variable) != null && m.getMessageType().getFieldUnits(variable).startsWith("rad")) {
                    // Special case for angles in radians
                    addValue(m.getTimestampMillis(), seriesName, Math.toDegrees(m.getDouble(variable)));
                }
                else
                    addValue(m.getTimestampMillis(), seriesName, m.getDouble(variable));
            }
        }
        NeptusLog.pub().info("<###>Processed in " + (System.currentTimeMillis() - t));
    }
}
