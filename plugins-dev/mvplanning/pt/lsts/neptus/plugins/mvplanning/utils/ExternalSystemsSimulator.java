/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
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
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: tsmarques
 * 20 July 2016
 */

package pt.lsts.neptus.plugins.mvplanning.utils;

import com.google.common.eventbus.Subscribe;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.mp.MapChangeEvent;
import pt.lsts.neptus.systems.external.ExternalSystem;
import pt.lsts.neptus.systems.external.ExternalSystemsHolder;
import pt.lsts.neptus.types.map.AbstractElement;
import pt.lsts.neptus.types.vehicle.VehicleType;

/**
 * @author tsmarques
 * @date 6/20/16
 */
public class ExternalSystemsSimulator {
    public ExternalSystemsSimulator() {

    }

    @Subscribe
    public void mapChanged(MapChangeEvent event) {
        if (event == null || event.getChangedObject() == null)
            return;

        AbstractElement elem = event.getChangedObject();
        if (elem.getType().equals("Mark") && elem.getId().startsWith("ext_")) {
            String id = elem.getId();
            ExternalSystem extSys;

            if(event.getEventType() == MapChangeEvent.OBJECT_ADDED) {
                extSys = new ExternalSystem(id);
                extSys.setLocation(elem.getCenterLocation());
                extSys.setType(VehicleType.SystemTypeEnum.VEHICLE);
                extSys.setActive(true);

                ExternalSystemsHolder.registerSystem(extSys);
                NeptusLog.pub().info("Simulating, static, external system with id: " + elem.getId());
            }
            else if(event.getEventType() == MapChangeEvent.OBJECT_REMOVED) {
                extSys = ExternalSystemsHolder.lookupSystem(id);
                extSys.setActive(false);
                NeptusLog.pub().info("External system with id: " + elem.getId() + ", not active anymore");
            }
            else if(event.getEventType() == MapChangeEvent.OBJECT_CHANGED) {
                extSys = ExternalSystemsHolder.lookupSystem(id);
                extSys.setLocation(elem.getCenterLocation());
                NeptusLog.pub().info("External system with id: " + elem.getId() + ", was moved");
            }
        }
    }
}
