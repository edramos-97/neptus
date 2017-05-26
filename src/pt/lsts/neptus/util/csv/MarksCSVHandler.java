/*
 * Copyright (c) 2004-2017 Universidade do Porto - Faculdade de Engenharia
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
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: tsm
 * 22 Apr 2017
 */
package pt.lsts.neptus.util.csv;

import com.google.common.eventbus.Subscribe;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.events.NeptusEvents;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.map.MarkElement;

/**
 * @author tsm
 */
public class MarksCSVHandler {
    public static boolean exportCsv(String exportPathStr, List<MarkElement> marks, String del) {
        File exportPath = new File(exportPathStr);

        try{
            PrintWriter writer = new PrintWriter(exportPath, "UTF-8");

            for(MarkElement m : marks) {
                LocationType centerLoc = m.getCenterLocation().getNewAbsoluteLatLonDepth();
                writer.write(m.getId() + del + centerLoc.getLatitudeDegs() + del + centerLoc.getLatitudeDegs());
            }

            writer.close();
            NeptusLog.pub().info("Exported " + marks.size() + " marks");

            return true;
        } catch (Exception e) { // stop on any exception
            e.printStackTrace();
            NeptusLog.pub().error("Something happened and couldn't write to file");
            return false;
        }
    }

    public static List<MarkElement> importCsv(String importPathStr, String del) {
        List<MarkElement> marks = new ArrayList<>();
        File importFile = new File(importPathStr);

        if(!importFile.exists() || importFile.isDirectory()) {
            NeptusLog.pub().error("Import file not found or is a directory: " + importPathStr);
            return null;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(importFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String parts[] = line.split(del);

                if(parts.length != 3) {
                    NeptusLog.pub().error("Was expecting 3 columns, got " + parts.length + " on line " + line);
                    return null;
                }

                double lat = Double.valueOf(parts[1]);
                double lon = Double.valueOf(parts[2]);

                MarkElement m = new MarkElement();
                m.setId(parts[0]);
                m.setCenterLocation(new LocationType(lat, lon));
                marks.add(m);
            }
        } catch (Exception e) { // stop on any exception
            e.printStackTrace();

            NeptusLog.pub().error("Couldn't read import file");
            return null;
        }

        NeptusLog.pub().info("Imported " + marks.size() + " marks");
        return marks;
    }
}
