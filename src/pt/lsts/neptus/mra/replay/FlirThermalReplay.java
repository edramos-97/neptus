package pt.lsts.neptus.mra.replay;

import java.awt.Color;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.lsf.LsfIndex;
import pt.lsts.neptus.colormap.ColorMap;
import pt.lsts.neptus.colormap.ColorMapFactory;
import pt.lsts.neptus.colormap.ColormapOverlay;
import pt.lsts.neptus.mp.SystemPositionAndAttitude;
import pt.lsts.neptus.mp.preview.payloads.CameraFOV;
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

    private CameraFOV camFov = CameraFOV.defaultFov();

    public FlirThermalReplay() {
        super("Thermal Replay", cellWidth, true, 0);
        // inverted min max value due to super class implementation
        this.minVal=60.0;
        this.maxVal=0.0;
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
            parsing = true;
            new Thread(() -> {
                Scanner scanner;
                try {
                    scanner = new Scanner(dataFile);
                    // ignore header
                    if(scanner.hasNextLine())
                        scanner.nextLine();
                    while (scanner.hasNextLine()) {
                        String temp = scanner.nextLine();
                        String[] dataFields = temp.split(",");
                        // DataFields=[DateTimeOriginal(0),GPSLatitude(1),GPSLongitude(2),GPSAltitude(3),MAVRoll(4),MAVPitch(5),MAVYaw(6),Temp(°C)(7)]
                        camFov.setState(new SystemPositionAndAttitude(
                                new LocationType(Float.valueOf(dataFields[1]),Float.valueOf(dataFields[2])),
                                Math.toRadians(Double.valueOf(dataFields[4])),
                                Math.toRadians(Double.valueOf(dataFields[5])),
                                Math.toRadians(Double.valueOf(dataFields[6])
                        )));
                        addSample(camFov.getLookAt(),Float.valueOf(dataFields[7]));
                    }
                    generated = generateImage(cm);

                    ImageLayer il = getImageLayer();
                    try {
                        il.saveToFile(new File(index.getLsfFile().getParentFile(),"mra/dvl.layer"));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    scanner.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                parsing=false;
            }, "FlirThermalOverlay").start();
        }
    }

    @Override
    public String[] getObservedMessages() {
        return new String[0];
    }

    @Override
    public void onMessage(IMCMessage message) {  }

    @Override
    public boolean getVisibleByDefault() {
        return false;
    }

    @Override
    public void cleanup() {

    }

    @Override
    public void paint(Graphics2D g, StateRenderer2D renderer) {
        if(dataFile==null || !dataFile.canRead()){
            return;
        }

        if (!parsing)
            super.paint(g, renderer);
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
    }
}
