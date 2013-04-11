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
 * Author: Paulo Dias
 * 2010/01/24
 */
package pt.up.fe.dceg.neptus.util.comm.manager.imc;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import pt.up.fe.dceg.neptus.NeptusLog;
import pt.up.fe.dceg.neptus.imc.AcousticSystemsQuery;
import pt.up.fe.dceg.neptus.imc.IMCDefinition;
import pt.up.fe.dceg.neptus.imc.IMCMessage;
import pt.up.fe.dceg.neptus.mystate.MyState;
import pt.up.fe.dceg.neptus.types.coord.LocationType;
import pt.up.fe.dceg.neptus.types.vehicle.VehicleType.SystemTypeEnum;
import pt.up.fe.dceg.neptus.util.DateTimeUtil;
import pt.up.fe.dceg.neptus.util.NetworkInterfacesUtil;
import pt.up.fe.dceg.neptus.util.comm.IMCSendMessageUtils;
import pt.up.fe.dceg.neptus.util.comm.IMCUtils;
import pt.up.fe.dceg.neptus.util.comm.manager.imc.ImcSystem.IMCAuthorityState;
import pt.up.fe.dceg.neptus.util.conf.ConfigFetch;
import pt.up.fe.dceg.neptus.util.conf.GeneralPreferences;

/**
 * @author pdias
 *
 */
public class AnnounceWorker {

	public static final String NONE_IP = "NONE";
	
	public static final String USE_REMOTE_IP = "SENDER_REMOTE_IP";
	
	/*
  <message id="77" name="Announce" abbrev="Announce" source="vehicle,ccu" used-by="*">
    <description>A system description that is to be broadcasted to other systems</description>
    <field name="System Name" abbrev="sys_name" type="plaintext"/>
    <field name="System Type" abbrev="sys_type" type="uint8_t" unit="Enumerated" enum-def="SystemType"/>
    <field name="Control Owner" abbrev="owner" type="uint16_t">
      <description>
        The IMC system ID.
      </description>
    </field>
    <field name="Latitude WGS-84" abbrev="lat" type="fp64_t" unit="rad" min="-1.5707963267948966" max="1.5707963267948966">
      <description>
        WGS-84 Latitude of target waypoint.
      </description>
    </field>
    <field name="Longitude WGS-84" abbrev="lon" type="fp64_t" unit="rad" min="-3.141592653589793" max="3.141592653589793">
      <description>
        WGS-84 Longitude of target waypoint.
      </description>
    </field>
    <field name="Height WGS-84" abbrev="height" type="fp32_t" unit="m">
      <description>
        Altitude above WGS-84 geoid (UAVs), should be negative when underwater (AUVs).
      </description>
    </field>
    <field name="Services" abbrev="services" type="plaintext" unit="TupleList">
      <description>Example: "URL=http://192.168.106.34/dune/imc/;"</description>
    </field>
  </message>
	 */
	
	private Timer timer = null;
	private TimerTask ttaskAnnounceMulticast = null, ttaskAnnounceBroadcast = null, ttaskEntityListAndPlanDB = null, ttaskHeartbeat = null;
	private IMCMessage announceMessage = null;
	private int periodMulticast = 10000;
	private int periodBroadcast = 7000;
	private int periodEntityListRequest = 30000;
	private int periodHeartbeatRequest = 1000;
	
	private ImcMsgManager imcManager = null;
	private IMCDefinition imcDefinition = null;
	
	/**
	 * 
	 */
	public AnnounceWorker(ImcMsgManager imcManager, IMCDefinition imcDefinition) {
		this.imcManager = imcManager;
		this.imcDefinition = imcDefinition;
	}
	
	/**
	 * @return the announceMessage
	 */
	public IMCMessage getAnnounceMessage() {
		if (announceMessage == null) {
			try {
                announceMessage = imcDefinition.create("Announce");
            }
            catch (Exception e1) {
                e1.printStackTrace();
            }
            
			if (announceMessage == null)
				return null;

			announceMessage.setValue("sys_name", "ccu-neptus");
			announceMessage.setValue("sys_type","CCU");
			announceMessage.setValue("owner", ImcId16.NULL_ID.longValue());
			announceMessage.setValue("lat", 0);
			announceMessage.setValue("lon", 0);
			announceMessage.setValue("height", 0);
			//announceMessage.setValue("ad_info", "");
			announceMessage.setValue("services", "");
		}

		announceMessage.setValue("owner", ImcId16.NULL_ID.longValue());
		LocationType loc = new LocationType(MyState.getLocation().convertToAbsoluteLatLonDepth());
		//NeptusLog.pub().info("<###>       " + loc);
		announceMessage.setValue("lat", loc.getLatitudeAsDoubleValueRads());
		announceMessage.setValue("lon", loc.getLongitudeAsDoubleValueRads());
		announceMessage.setValue("height", loc.getHeight());
		
//		announceMessage.setValue("ad_info", imcManager
//				.getAnnounceServicesList());

		return announceMessage;
	}

	private IMCMessage getAnnounceMessageUpdated() {
		getAnnounceMessage();

		announceMessage.setValue("owner", ImcId16.NULL_ID.longValue());
		LocationType loc = new LocationType(MyState.getLocation().convertToAbsoluteLatLonDepth());
		//NeptusLog.pub().info("<###>       " + loc);
		announceMessage.setValue("lat", loc.getLatitudeAsDoubleValueRads());
		announceMessage.setValue("lon", loc.getLongitudeAsDoubleValueRads());
		announceMessage.setValue("height", loc.getHeight());
		
//		announceMessage.setValue("ad_info", imcManager
//				.getAnnounceServicesList());
		announceMessage.setValue("services", getAllServices());
		announceMessage.setTimestamp(System.currentTimeMillis()/1000.0);

		return announceMessage;
	}

	public String getAllServices() {
		return "neptus:///uid/" + DateTimeUtil.getUID() + "/;"
				+ "neptus:///version/" + (ConfigFetch.getNeptusVersion() + "_" 
				+ ConfigFetch.getCompilationDate()) + "_r" + ConfigFetch.getScmRev() + "/;"
				+ "imc+info///version/" + imcDefinition.getVersion() + "/;"
				+ imcManager.getAnnounceServicesList();
	}
	
	/**
	 * @param announceMessage the announceMessage to set
	 */
	public void setAnnounceMessage(IMCMessage announceMessage) {
		this.announceMessage = announceMessage;
	}
	
	/**
	 * @return the period
	 */
	public long getPeriodMulticast() {
		return periodMulticast;
	}
	
	/**
	 * @param period the period to set
	 */
	public void setPeriodMulticast(int period) {
		this.periodMulticast = period;
	}
	
	/**
	 * @return the periodBroadcast
	 */
	public long getPeriodBroadcast() {
		return periodBroadcast;
	}
	
	/**
	 * @param periodBroadcast the periodBroadcast to set
	 */
	public void setPeriodBroadcast(int period) {
		this.periodBroadcast = period;
	}
	
	/**
	 * @return the periodEntityListRequest
	 */
	public long getPeriodEntityListRequest() {
		return periodEntityListRequest;
	}
	
	/**
	 * @param periodEntityListRequest the periodEntityListRequest to set
	 */
	public void setPeriodEntityListRequest(int periodEntityListRequest) {
		this.periodEntityListRequest = periodEntityListRequest;
	}
	
	/**
     * @return the periodHeartbeatRequest
     */
    public long getPeriodHeartbeatRequest() {
        periodHeartbeatRequest = GeneralPreferences.heartbeatTimePeriodMillis;
        return periodHeartbeatRequest;
    }
    
	public synchronized boolean startAnnounceAndPeriodicRequests() {
		if (timer != null || ttaskAnnounceMulticast != null || ttaskAnnounceBroadcast != null) // || getAnnounceMessage() == null)
			return false;
		timer = new Timer(this.getClass().getSimpleName(), true);
		ttaskAnnounceMulticast = getTtaskAnnounceMulticast();
		ttaskAnnounceBroadcast = getTtaskAnnounceBroadcast();
		ttaskEntityListAndPlanDB = getTtaskEntityListAndPlanDB();
		ttaskHeartbeat = getTtaskHeartbeat();
		timer.scheduleAtFixedRate(ttaskAnnounceMulticast,  500, getPeriodMulticast());
		timer.scheduleAtFixedRate(ttaskAnnounceBroadcast, 900, getPeriodBroadcast());
		timer.scheduleAtFixedRate(ttaskEntityListAndPlanDB, 1000, getPeriodEntityListRequest());
		timer.scheduleAtFixedRate(ttaskHeartbeat, 2000, getPeriodHeartbeatRequest());
		return true;
	}
	
	public synchronized void stopAnnounce() {
		if (ttaskAnnounceMulticast != null) {
			ttaskAnnounceMulticast.cancel();
			ttaskAnnounceMulticast = null;
		}
		if (ttaskAnnounceBroadcast != null) {
			ttaskAnnounceBroadcast.cancel();
			ttaskAnnounceBroadcast = null;
		}
        if (ttaskEntityListAndPlanDB != null) {
            ttaskEntityListAndPlanDB.cancel();
            ttaskEntityListAndPlanDB = null;
        }
        if (ttaskHeartbeat != null) {
            ttaskHeartbeat.cancel();
            ttaskHeartbeat = null;
        }
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}
	

	/**
	 * @return the ttaskAnnounceMulticast
	 */
	private TimerTask getTtaskAnnounceMulticast() {
		return ttaskAnnounceMulticast = new TimerTask() {
			@Override
			public void run() {
				imcManager.sendMessage(getAnnounceMessageUpdated(),
						ImcId16.ANNOUNCE, "Multicast");
			}
		};
	}

	
	/**
	 * @return the ttaskAnnounceBroadcast
	 */
	private TimerTask getTtaskAnnounceBroadcast() {
		return ttaskAnnounceBroadcast = new TimerTask() {
			@Override
			public void run() {
				imcManager.sendMessage(getAnnounceMessageUpdated(),
						ImcId16.ANNOUNCE, "Broadcast");
			}
		};
	}

	/**
	 * @return the ttaskEntityListAndPlanDB
	 */
	private TimerTask getTtaskEntityListAndPlanDB() {
		return ttaskEntityListAndPlanDB = new TimerTask() {
			@Override
			public void run() {
				for (ImcSystem sys : ImcSystemsHolder.lookupAllSystems()) {
					sendEntityListRequestMsg(sys);
					sendPlanDBMsgs(sys);
					sendBeaconsRequestMsgs(sys);
					sendAcousticSystemsQueryMsg(sys);
				}
			}
		};
	}
	
	/**
     * @return the ttaskHeartbeat
     */
    public TimerTask getTtaskHeartbeat() {
        return ttaskHeartbeat = new TimerTask() {
            @Override
            public void run() {
                for (ImcSystem sys : ImcSystemsHolder.lookupAllSystems()) {
                    if (sys.getAuthorityState() != IMCAuthorityState.NONE
                            && sys.getAuthorityState() != IMCAuthorityState.OFF) {
                        sendHeartbeat(sys);
                    }
                }
            }
        };
    }
    
	/**
	 * @param msg
	 */
	public int getImcUdpPortFromMessage(IMCMessage msg) {
		InetSocketAddress[] retAdr = getImcIpsPortsFromMessage(msg, "imc+udp");
		if (retAdr.length == 0)
			return 0;
		else
			return retAdr[0].getPort(); 
	}

	public InetSocketAddress[] getImcIpsPortsFromMessageImcUdp(IMCMessage msg) {
		return getImcIpsPortsFromMessage(msg, "imc+udp");
	}

	public InetSocketAddress[] getImcIpsPortsFromMessageImcTcp(IMCMessage msg) {
	    return getImcIpsPortsFromMessage(msg, "imc+tcp");
	}

	public InetSocketAddress[] getImcIpsPortsFromMessage(IMCMessage msg, String scheme) {
		String services = msg.getString("services");
		return getImcIpsPortsFromMessage(services, scheme);
	}
	
	public InetSocketAddress[] getImcIpsPortsFromMessage(String services, String scheme) {
//		System.out.println(services);
		String[] listSer = services.split(";");
		LinkedList<String> ipList = new LinkedList<String>();
		LinkedList<Integer> portList = new LinkedList<Integer>();
		for (String rs : listSer) {
			try {
				if (!rs.trim().startsWith(scheme+":"))
					continue;
				
				URI url1 = URI.create(rs.trim());
				String host = url1.getHost();
				int port = url1.getPort();
				if (port == -1 || port == 0)
					continue;
//				boolean reachable = NetworkInterfacesUtil.testForReachability(host);
//				if (!reachable)
//					continue;
				portList.add(port);
				ipList.add(host);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		InetSocketAddress[] addL = new InetSocketAddress[portList.size()];
		for (int i= 0; i < portList.size(); i++) {
			addL[i] = new InetSocketAddress(ipList.get(i), portList.get(i));
		}
		return addL;
	}

	
	/**
	 * @param msg
	 * @return
	 */
	public String getImcServicesFromMessage(IMCMessage msg) {
		String adInfo = msg.getString("services");
		return adInfo;
	}

	/**
     * @param sys
     */
    void sendEntityListRequestMsg(ImcSystem sys) {
        try {
        	NeptusLog.pub().debug("Sending '" + sys.name + " | "
        			+ sys.getId() + "' EntityList request...");
        	IMCMessage msg = imcDefinition.create("EntityList", "op", 1);
        	if (msg == null)
        		msg = imcDefinition.create("Aliases", "op", 1);
        	imcManager.sendMessage(msg, sys.getId(), null);
        }
        catch (Exception e) {
        	NeptusLog.pub().warn(e);
        }
    }
    
    private void sendHeartbeat(ImcSystem sys) {
        try {
            NeptusLog.pub().debug("Sending '" + sys.name + " | "
                    + sys.getId() + "' Heartbeat...");
            IMCMessage msg = imcDefinition.create("Heartbeat");
            msg.setTimestamp(System.currentTimeMillis()/1000.0);
            
            if (sys.isUDPOn() && imcManager.isUdpOn())
                imcManager.sendMessage(msg, sys.getId(), ImcMsgManager.TRANSPORT_UDP);
            
            if (sys.isTCPOn() && imcManager.isTcpOn())
                imcManager.sendMessage(msg, sys.getId(), ImcMsgManager.TRANSPORT_TCP);
        }
        catch (Exception e) {
            NeptusLog.pub().warn(e);
        }
    }
    
    private void sendPlanDBMsgs(ImcSystem sys) {
        try {
            if (System.currentTimeMillis() - sys.getPlanDBControl().getRemoteState().getLastStateUpdated() < 20000) {
                return;
            }
                
            NeptusLog.pub().debug("Sending '" + sys.name + " | "
                    + sys.getId() + "' PlanDB request...");
            IMCMessage msg = imcDefinition.create("PlanDB", "type", "REQUEST", "op", "GET_STATE",
                    "request_id", IMCSendMessageUtils.getNextRequestId());
            imcManager.sendMessage(msg, sys.getId(), null);
        }
        catch (Exception e) {
            NeptusLog.pub().warn(e);
        }
    }

    private void sendBeaconsRequestMsgs(ImcSystem sys) {
        try {
            if (sys.getType() != SystemTypeEnum.VEHICLE)
                return;
            
            if (sys.retrieveData(ImcSystem.LBL_CONFIG_KEY) != null &&
                    System.currentTimeMillis() - sys.retrieveDataTimeMillis(ImcSystem.LBL_CONFIG_KEY) < 20000) {
                return;
            }
                
            NeptusLog.pub().debug("Sending '" + sys.name + " | "
                    + sys.getId() + "' LblConfig request...");
            IMCMessage msg = imcDefinition.create("LblConfig", "op", "GET_CFG");
            imcManager.sendMessage(msg, sys.getId(), null);
        }
        catch (Exception e) {
            NeptusLog.pub().warn(e);
        }
    }
    
    private void sendAcousticSystemsQueryMsg(ImcSystem sys) {
        try {
            if (!sys.isServiceProvided("acoustic"))
                return;
                
            NeptusLog.pub().info("Sending '" + sys.name + " | "
                    + sys.getId() + "' AcousticSystemsQuery request...");
            IMCMessage msg = new AcousticSystemsQuery(imcDefinition);
            imcManager.sendMessage(msg, sys.getId(), null);
        }
        catch (Exception e) {
            NeptusLog.pub().warn(e);
        }
    }

    /**
	 * @param resSys
	 */
	public static final void processUidFromServices(ImcSystem sys) {
		String uid = IMCUtils.getUidFromServices(sys.getServicesProvided());
		//NeptusLog.pub().info("<###>------------------------ "+((System.currentTimeMillis()-ConfigFetch.getNeptusInitializationTime())/1E3) + "s UUI -> " + sys.getName()+" "+uid + "  "+sys.lastUid);
		if (sys.getLastUid() == null || "".equalsIgnoreCase(sys.getLastUid())) {
			if (uid != null)
				sys.setLastUid(uid);
			sys.setOnIdErrorState(false);
		}
		else {
			if (sys.getLastUid().equalsIgnoreCase(uid)) {
				if (System.currentTimeMillis() - sys.getLastIdErrorStateReceived() > 3000)
					sys.setOnIdErrorState(false);
			}
			else {
				sys.setLastUid(uid);
				sys.setOnIdErrorState(true);
			}
		}
	}

	public static void main(String[] args) {
		// dune://1288093292613093000/;
		// imc+udp://169.254.161.13:6002/;
		// imc+udp://169.254.75.192:6002/;
		// imc+udp://192.168.56.1:6002/;
		// http://169.254.161.13:8080/dune;
		// http://169.254.75.192:8080/dune;
		// http://192.168.56.1:8080/dune
				
		// dune://1294925553839635/;
		// imc+udp://192.168.106.189:6002/;
		// imc+udp://172.16.13.1:6002/;
		// imc+udp://172.16.216.1:6002/;
		// http://192.168.106.189:8080/dune;
		// http://172.16.13.1:8080/dune;
		// http://172.16.216.1:8080/dune
		
		// dune://9175615404553/;
		// imc+udp://192.168.106.30:6002/;
		// http://192.168.106.30:8080/dune

		URI url1 = URI.create("imc+udp://SENDER-REMOTE-IP:6002/path/ye");
		url1 = URI.create("imc+udp://192.168.106.30:6002/");
		
		System.out.println(url1.getScheme());
		System.out.println(url1.getHost());
		System.out.println(url1.getPort());
		System.out.println(url1.getAuthority());
		System.out.println(url1.getUserInfo());
		System.out.println(url1.getRawAuthority());
		System.out.println(url1.getPath());
		System.out.println(NetworkInterfacesUtil.testForReachability(url1.getHost()));
		
		try {
			Enumeration<NetworkInterface> nintf = java.net.NetworkInterface.getNetworkInterfaces();
			while (nintf.hasMoreElements()) {
				NetworkInterface ni = nintf.nextElement();
				if (ni.isUp()) {
					NeptusLog.pub().info("<###>>>>>  "+ni);
					NeptusLog.pub().info("<###>      isLoopback "+ni.isLoopback());
					NeptusLog.pub().info("<###>      isPointToPoint "+ni.isPointToPoint());
					NeptusLog.pub().info("<###>      multicast "+ni.supportsMulticast());
					NeptusLog.pub().info("<###>      virtual "+ni.isVirtual());
					NeptusLog.pub().info("<###>      mtu "+ni.getMTU());
					NeptusLog.pub().info("<###>      "+ni.getInetAddresses());
					Enumeration<InetAddress> iadde = ni.getInetAddresses();
					while (iadde.hasMoreElements()) {
						InetAddress inetAddress = (InetAddress) iadde
								.nextElement();
						NeptusLog.pub().info("<###>      "+"      "+inetAddress.getHostAddress());
					}
					NeptusLog.pub().info("<###>      "+ni.getInterfaceAddresses());
					for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
						NeptusLog.pub().info("<###>      "+"      "+ia.getAddress().getHostAddress()
								+" "+(ia.getBroadcast()!=null?ia.getBroadcast().getHostAddress():""));
					}
				}
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}