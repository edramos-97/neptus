package pt.lsts.neptus.mra.replay;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import pt.lsts.imc.EntityState;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.lsf.LsfIndex;
import pt.lsts.neptus.colormap.ColorMap;
import pt.lsts.neptus.colormap.ColorMapFactory;
import pt.lsts.neptus.colormap.ColorMapUtils;
import pt.lsts.neptus.colormap.ColormapOverlay;
import pt.lsts.neptus.mp.SystemPositionAndAttitude;
import pt.lsts.neptus.mp.preview.payloads.CameraFOV;
import pt.lsts.neptus.mra.MRAProperties;
import pt.lsts.neptus.mra.importers.IMraLogGroup;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.renderer2d.ImageLayer;
import pt.lsts.neptus.renderer2d.LayerPriority;
import pt.lsts.neptus.renderer2d.StateRenderer2D;
import pt.lsts.neptus.types.coord.LocationType;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

@LayerPriority(priority = -60)
@PluginDescription(name = "Flir Thermal Replay")
public class FlirThermalReplay extends ColormapOverlay implements LogReplayLayer {

    @NeptusProperty(name = "Cell width")
    public static int cellWidth = 3;

    private ColorMap cm = ColorMapFactory.createJetColorMap();

    private LsfIndex index;
    private File dataFile = null;
    private boolean parsed =false, parsing = false;
    private double replayTimeStamp;
    private int cameraTimeOffset;

    private Runnable runnable = null;
    private Thread imageProcessThread = null;

    private CameraFOV camFov = CameraFOV.defaultFov();

    private ArrayList<ThermalData> locations = new ArrayList<>();

    public FlirThermalReplay() {
        super("Thermal Replay", cellWidth, true, 0);
        this.cameraTimeOffset = MRAProperties.thermalCameraOffset;
        this.clamp=false;
    }

    @Override
    public boolean canBeApplied(IMraLogGroup source, Context context) {
        File[] temp = source.getDir().listFiles((File dir, String name)-> name.equals("FlirThermalData.csv"));
        if (temp != null && temp.length == 1) {
            dataFile=new File(temp[0].getPath());
            return dataFile.canRead();
        }
        return false;
    }

    @Override
    public String getName() {
        return "Flir Thermal Replay";
    }

    @Override
    public void parse(IMraLogGroup source) {
        this.index = source.getLsfIndex();
        if (!parsed) {
            super.cellWidth = cellWidth;
            parsed = true;
            runnable = () -> {
                System.out.println("PARSING DATA");
                parsing = true;
                Scanner scanner;
                try {
                    scanner = new Scanner(dataFile);
                    // ignore header
                    if (scanner.hasNextLine())
                        scanner.nextLine();
                    LocationType vehicleLocation;
                    while (scanner.hasNextLine()) {
                        String temp = scanner.nextLine();
                        String[] dataFields = temp.split(",");
                        // DataFields=[DateTimeOriginal(0),GPSLatitude(1),GPSLongitude(2),GPSAltitude(3),MAVRoll(4),MAVPitch(5),MAVYaw(6),Temp(°C)(7)]
                        vehicleLocation = new LocationType(Float.valueOf(dataFields[1]), Float.valueOf(dataFields[2]));
                        camFov.setState(new SystemPositionAndAttitude(
                                vehicleLocation,
                                Math.toRadians(Double.valueOf(dataFields[4])),
                                Math.toRadians(Double.valueOf(dataFields[5])),
                                Math.toRadians(Double.valueOf(dataFields[6])
                                )));
                        LocationType lookAt = camFov.getLookAt();
                        addSample(lookAt, Float.valueOf(dataFields[7]));
                        locations.add(new ThermalData(vehicleLocation, Double.valueOf(dataFields[0]), Double.valueOf(dataFields[7])));
                    }

                    this.maxVal = MRAProperties.maxThermalValue;
                    this.minVal = MRAProperties.minThermalValue;

                    generated = generateImage(ColorMapUtils.invertColormap(cm, 255));

                    ImageLayer il = getImageLayer();
                    try {
                        il.saveToFile(new File(index.getLsfFile().getParentFile(), "mra/dvl.layer"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    scanner.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                parsing = false;
                //cm = ColorMapUtils.invertColormap(cm, 255);
            };
            imageProcessThread = new Thread(runnable, "FlirThermalOverlay");
            imageProcessThread.start();
        }
    }

    @Override
    public String[] getObservedMessages() {
        return new String[0];
    }

    @Override
    public void onMessage(IMCMessage message) { }

    @Subscribe
    public void on(EntityState message) {
        replayTimeStamp = message.getTimestamp();
    }

    @Override
    public boolean getVisibleByDefault() {
        return false;
    }

    @Override
    public void cleanup() {

    }

    @Override
    public void paint(Graphics2D go, StateRenderer2D renderer) {
        if(dataFile==null || !dataFile.canRead()){
            return;
        }

        if(MRAProperties.regenerateThermalImage &&
                !imageProcessThread.isAlive() &&
                (this.maxVal != MRAProperties.maxThermalValue || this.minVal != MRAProperties.minThermalValue)) {
            imageProcessThread = new Thread(runnable,"FlirThermalOverlay");
            imageProcessThread.start();
        }

        double tempMaxVal = MRAProperties.maxThermalValue;
        double tempMinVal = MRAProperties.minThermalValue;

        Graphics2D g = (Graphics2D) go.create();

        if (!parsing) {
            super.paint(g, renderer);
        }
        else {
            JLabel lbl = new JLabel("Parsing Thermal Data...");
            lbl.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            lbl.setOpaque(true);
            lbl.setBackground(new Color(255,255,255,128));
            lbl.setSize(lbl.getPreferredSize());

            g.setTransform(renderer.getIdentity());
            g.translate(10, 10);
            lbl.paint(g);
            g.setTransform(renderer.getIdentity());
        }
        g.setTransform(renderer.getIdentity());

        /*LocationType lastLT = null;
        lastLT = locations.get(0).getLt();
        Point2D pt = renderer.getScreenPosition(lastLT);
        g.setColor(Color.green);
        g.fill(new Ellipse2D.Double(pt.getX() - 4, pt.getY() - 4, 8, 8));*/

        for (int i = 1; i < locations.size(); i++) {
            ThermalData thermalData =locations.get(i);
            LocationType lt = thermalData.getLt();
            Point2D pt = renderer.getScreenPosition(lt);
            /*g.setColor(Color.red);

            int x = (int) pt.getX();
            int y = (int) pt.getY();

            g.translate(x,y);
            g.rotate(lastLT.getXYAngle(lt)+Math.PI);

            float zoomOffset = renderer.getZoom();

            int[] xPoints = {0,  3, -3};
            int[] yPoints = {4, - 4, - 4};
            g.fill(new Polygon(xPoints, yPoints,3));

            g.setTransform(renderer.getIdentity());
            lastLT = lt;*/

            if(thermalData.getTimestamp()+ cameraTimeOffset > replayTimeStamp){
                double amplitude = tempMaxVal - tempMinVal;
                g.setColor(cm.getColor((thermalData.getTemperature() - tempMinVal) / (amplitude)));
                g.fill(new Ellipse2D.Double(pt.getX()-6,pt.getY()-6,12,12));
                break;
            }

            g.setTransform(renderer.getIdentity());
        }

        g.dispose();
    }

    private class ThermalData {
        LocationType lt;
        double timestamp;
        double temperature;

        ThermalData(LocationType lt, double timestamp, double temperature) {
            this.lt = lt;
            this.timestamp = timestamp;
            this.temperature = temperature;
        }

        public LocationType getLt() {
            return lt;
        }

        public double getTimestamp() {
            return timestamp;
        }

        public double getTemperature() {
            return temperature;
        }
    }
}
