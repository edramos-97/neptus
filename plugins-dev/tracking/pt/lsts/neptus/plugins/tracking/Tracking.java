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
 * Author: Pedro Gonçalves
 * Apr 21, 2016
 */
package pt.lsts.neptus.plugins.tracking;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.VideoCapture;

import net.miginfocom.swing.MigLayout;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.renderer2d.LayerPriority;
import pt.lsts.neptus.util.ImageUtils;
import pt.lsts.neptus.util.SearchOpenCv;
import pt.lsts.neptus.util.UtilCv;

/**
 * Neptus Plugin for tracking object
 * 
 * @author pgonçalves
 * @category Tracking
 */
@SuppressWarnings("serial")
@Popup(pos = POSITION.RIGHT, width = 640, height = 480, accelerator = 'T')
@LayerPriority(priority = 0)
@PluginDescription(name = "Tracking", version = "1.0", author = "Pedro Gonçalves", description = "Plugin for tracking objects over IPCam", icon = "images/downloader/camera.png")
public class Tracking extends ConsolePanel implements ItemListener {
    
    @NeptusProperty(name = "Cam1 RTPS URL", editable = false)
    private String cam1RtpsUrl = "rtsp://usercam1:usercam1@10.0.10.42:88/videoMain";
    @NeptusProperty(name = "Cam2 RTPS URL", editable = false)
    private String cam2RtpsUrl = "rtsp://usercam2:usercam2@10.0.10.43:88/videoMain";
    
    /*@NeptusProperty(name = "Cam1 RTPS URL", editable = false)
    private String cam1RtpsUrl = "rtsp://10.0.20.207:554/live/ch01_0";
    @NeptusProperty(name = "Cam2 RTPS URL", editable = false)
    private String cam2RtpsUrl = "rtsp://10.0.20.209:554/live/ch01_0";*/
    
    // Width size of Console
    private int widhtConsole;
    // Height size of Console
    private int heightConsole;
    // Size of console
    private Size panelSize;
    // Image resize Cam1
    private Mat finalMatResizeCam1;
    // Image resize Cam2
    private Mat finalMatResizeCam2;
    // flag for state of neptus logo
    private boolean noVideoLogoState;
    // Buffer image for showImage
    private BufferedImage offlineImageCam1;
    private BufferedImage offlineImageCam2;
    private BufferedImage realImageCam1;
    private BufferedImage realImageCam2;
    private boolean refreshTemp;
    // Black Image
    private Scalar black;
    // worker thread designed to acquire image of IPCams
    private Thread captureCam_1;
    private Thread captureCam_2;
    // worker thread designed to manipulation of image
    private Thread manipulationCams;
    // Strut Video Capture Opencv
    private VideoCapture captureCam1;
    // Strut Video Capture Opencv
    private VideoCapture captureCam2;
    // JText of data warning message
    private JLabel warningTextOpenCv;
    // WatchDog variables/objects
    private Thread watchDogCam;
    private long endTimeMillisCam1;
    private boolean virtualEndThreadCam1;
    private long endTimeMillisCam2;
    private boolean virtualEndThreadCam2;
    
    private Mat matCam1;
    private Mat matCam2;
    private boolean closePlugin;
    private int fpsMax;
    private int fpsCam1Value;
    private int fpsCam2Value;
    private Size frameSizeCam1;
    private Size frameSizeCam2;
    private Size frameScaleCam1;
    private Size frameScaleCam2;
    private String textCam1;
    private String textCam2;
    private int realXCoordCam1;
    private int realYCoordCam1;
    private int realXCoordCam2;
    private int realYCoordCam2;
    private boolean showDebug;
    private boolean isCtrlPress;
    // JPopup Menu
    private JPopupMenu popup;
    private boolean startCapture;

    
    /**
     * @param console
     */
    public Tracking(ConsoleLayout console) {
        super(console);
        if (SearchOpenCv.searchJni()) {
            removeAll();
            initVariables();
            getConsoleSize();
            mouseListenerInit();
            keyListenerInit();
            this.setFocusable(true);
        }
        else {
            this.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent evt) {
                    Component c = evt.getComponent();
                    widhtConsole = c.getSize().width;
                    heightConsole = c.getSize().height;
                    panelSize = new Size(widhtConsole, heightConsole);
                }
            });
            errorOpenCv();
        }
    }
    
    /**
     * Error opencv
     */
    private void errorOpenCv() {
        NeptusLog.pub().error("Opencv not found.");
        this.setBackground(Color.BLACK);
        this.setLayout(new MigLayout("filly"));
        warningTextOpenCv = new JLabel("  " + I18n.text("Please install OpenCV 2.4 and its dependencies.") + "  ");
        warningTextOpenCv.setForeground(new Color(252, 68, 35));
        warningTextOpenCv.setFont(new Font("Courier New", Font.ITALIC, 18));
        this.add(warningTextOpenCv);
    }

    /* (non-Javadoc)
     * Get size of console
     */
    public void getConsoleSize() {
        this.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent evt) {
                Component c = evt.getComponent();
                widhtConsole = c.getSize().width;
                heightConsole = c.getSize().height;
                panelSize = new Size(widhtConsole, heightConsole);
                finalMatResizeCam1 = new Mat((int) panelSize.height, (int) panelSize.width, CvType.CV_8UC3);
                finalMatResizeCam2 = new Mat((int) panelSize.height, (int) panelSize.width, CvType.CV_8UC3);
                if (!startCapture)
                    initImage();
            }
        });
    }
    
    /* (non-Javadoc)
     * Initialize Variables
     */
    public void initVariables() {
        fpsMax = 30;;
        showDebug = false;
        isCtrlPress = false;
        noVideoLogoState = false;
        startCapture = false;
        closePlugin = true;
        frameSizeCam1 = new Size(0, 0);
        frameSizeCam2 = new Size(0, 0);
        frameScaleCam1 = new Size(0, 0);
        frameScaleCam2 = new Size(0, 0);
        textCam1 = new String();
        textCam2 = new String();
        black = new Scalar(0);
        captureCam_1 = updaterThreadCam1();
        captureCam_1.start();
        captureCam_2 = updaterThreadCam2();
        captureCam_2.start();
        manipulationCams = updaterThreadManipulation();
        manipulationCams.start();
        setupWatchDogCam(4000);
    }
    
    /* (non-Javadoc)
     * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
     */
    @Override
    public void itemStateChanged(ItemEvent e) {
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        NeptusLog.pub().warn("CLOSING CONSOLE");
        closePlugin = true;
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
    }
    
    /**
     * Fill image with zeros or null video
     */
    public void initImage() {
        if (!noVideoLogoState) {
            if (ImageUtils.getImage("images/novideo.png") == null) {
                finalMatResizeCam1.setTo(black);
                offlineImageCam1 = UtilTracking.resizeBufferedImage(UtilCv.matToBufferedImage(finalMatResizeCam1), panelSize, true);
                finalMatResizeCam2.setTo(black);
                offlineImageCam2 = UtilTracking.resizeBufferedImage(UtilCv.matToBufferedImage(finalMatResizeCam2), panelSize, true);
            }
            else {
                offlineImageCam1 = UtilTracking.resizeBufferedImage(ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), panelSize, true);
                offlineImageCam2 = UtilTracking.resizeBufferedImage(ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), panelSize, true);
            }

            if (offlineImageCam1 != null && offlineImageCam2 != null) {
                showImage(offlineImageCam1, offlineImageCam2);
                noVideoLogoState = true;
            }
        }
        else {
            offlineImageCam1 = UtilTracking.resizeBufferedImage(ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), panelSize, true);
            offlineImageCam2 = UtilTracking.resizeBufferedImage(ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), panelSize, true);
            showImage(offlineImageCam1, offlineImageCam2);
        }
    }
    
    /**
     * Paint Component
     */
    @Override
    protected void paintComponent(Graphics g) {
        if (refreshTemp && realImageCam1 != null && realImageCam2 != null) {
            frameScaleCam1.width = (panelSize.width / frameSizeCam1.width);
            frameScaleCam1.height = (panelSize.height / frameSizeCam1.height);
            frameScaleCam2.width = (panelSize.width / frameSizeCam2.width);
            frameScaleCam2.height = (panelSize.height / frameSizeCam2.height);
            textCam1 =String.format("FPS: %d | Scale(%.2f x %.2f)", fpsCam1Value, frameScaleCam1.width, frameScaleCam1.height);
            textCam2 =String.format("FPS: %d | Scale(%.2f x %.2f)", fpsCam2Value, frameScaleCam2.width, frameScaleCam2.height);
            g.drawImage(realImageCam1, 0, 0, this);
            g.drawImage(realImageCam2, (int) panelSize.width/2, 0, this);
            g.setColor(Color.RED);
            g.drawLine((int)panelSize.width / 2, 0,  (int)panelSize.width / 2, (int)panelSize.height);
            g.drawLine(((int)panelSize.width / 2) - 1, 0,  ((int)panelSize.width / 2) - 1, (int)panelSize.height);
            g.drawLine(((int)panelSize.width / 2) + 1, 0,  ((int)panelSize.width / 2) + 1, (int)panelSize.height);
            
            if (showDebug) {
                g.setColor(Color.BLACK);
                g.setFont(new Font("Serif", Font.BOLD, 14));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(textCam1, (int)panelSize.width / 2 - fm.stringWidth(textCam1) - 10, 20);
                g.drawString(textCam2, (int)panelSize.width - fm.stringWidth(textCam2) - 10, 20);
                g.setColor(Color.YELLOW);
                fm = g.getFontMetrics();
                g.drawString(textCam1, (int)panelSize.width / 2 - fm.stringWidth(textCam1) - 12, 20);
                g.drawString(textCam2, (int)panelSize.width - fm.stringWidth(textCam2) - 12, 20);
                g.dispose();
            }
            
            refreshTemp = false;
        }
        else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, (int) panelSize.width, (int) panelSize.height);
            refreshTemp = false;
        }
    }
    
    /**
     * Print to panel images captured/processed
     */
    // Print Images to console
    private void showImage(BufferedImage image1, BufferedImage image2) {
        realImageCam1 = image1;
        realImageCam2 = image2;
        refreshTemp = true;
        repaint();
    }
    
    /**
     * Tread for capture image of IPCam1
     */
    private Thread updaterThreadCam1() {
        Thread ipCam1 = new Thread("Capture stream of IPCam1") {
            public void run() {
                initImage();
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                }
                matCam1 = new Mat((int) panelSize.height, (int) panelSize.width, CvType.CV_8UC3);
                boolean isAliveIPCams;
                boolean stateIPCams = false;
                while (true) {
                    if (!closePlugin && startCapture) {
                        if (stateIPCams == false) {
                            captureCam1 = new VideoCapture();
                            captureCam1.open(cam1RtpsUrl);
                            if (captureCam1.isOpened()) {
                                stateIPCams = true;
                                NeptusLog.pub().info("Video Strem from IPCam1 is captured");
                                startWatchDogCam();
                            }
                            else {
                                NeptusLog.pub().info("Video Strem from IPCam1 is not captured");
                                closePlugin = true;
                                startCapture = false;
                                stateIPCams = false;
                            }
                        }
                        // IPCam Capture
                        else if (stateIPCams || !captureCam1.isOpened()) {
                            isAliveIPCams = false;
                            resetWatchDogCam1(4000);
                            long startTime = System.currentTimeMillis();
                            while (watchDogCam.isAlive() && !isAliveIPCams) {
                                captureCam1.read(matCam1);
                                isAliveIPCams = true;
                            }

                            if (matCam1.empty()) {
                                NeptusLog.pub().error(I18n.text("ERROR capturing img of IPCam1"));
                                closePlugin = true;
                                startCapture = false;
                                stateIPCams = false;
                                continue;
                            }
                            else {
                                frameSizeCam1.width = matCam1.width();
                                frameSizeCam1.height = matCam1.height();
                            }
                            long stopTime = System.currentTimeMillis();
                            while((stopTime - startTime) < (1000/fpsMax)) {
                                stopTime = System.currentTimeMillis();
                                try {
                                    Thread.sleep(1);
                                }
                                catch (InterruptedException e) {
                                }
                            }
                            fpsCam1Value = (int) (1000 / (stopTime - startTime));
                        }
                    }
                    else if (!closePlugin && !startCapture) {
                        closePlugin = true;
                        stateIPCams = false;
                        captureCam1.release();
                    }
                    else {
                        try {
                            initImage();
                            Thread.sleep(1000);
                            resetWatchDogCam1(4000);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        ipCam1.setDaemon(true);
        return ipCam1;
    }
    
    /**
     * Tread for capture image of IPCam2
     */
    private Thread updaterThreadCam2() {
        Thread ipCams = new Thread("Capture stream of IPCam2") {
            public void run() {
                initImage();
                try {
                    Thread.sleep(1200);
                }
                catch (InterruptedException e) {
                }
                matCam2 = new Mat((int) panelSize.height, (int) panelSize.width, CvType.CV_8UC3);
                boolean isAliveIPCams;
                boolean stateIPCams = false;
                while (true) {
                    if (!closePlugin && startCapture) {
                        if (stateIPCams == false || !captureCam2.isOpened()) {
                            captureCam2 = new VideoCapture();
                            captureCam2.open(cam2RtpsUrl);
                            if (captureCam2.isOpened()) {
                                stateIPCams = true;
                                NeptusLog.pub().info("Video Strem from IPCam2 is captured");
                            }
                            else {
                                NeptusLog.pub().info("Video Strem from IPCam2 is not captured");
                                closePlugin = true;
                                startCapture = false;
                                stateIPCams = false;
                            }
                        }
                        // IPCam Capture
                        else if (stateIPCams) {
                            isAliveIPCams = false;
                            resetWatchDogCam2(4000);
                            long startTime = System.currentTimeMillis();
                            while (watchDogCam.isAlive() && !isAliveIPCams) {
                                captureCam2.read(matCam2);
                                isAliveIPCams = true;
                            }

                            if (matCam2.empty()) {
                                NeptusLog.pub().error(I18n.text("ERROR capturing img of IPCam2"));
                                closePlugin = true;
                                startCapture = false;
                                stateIPCams = false;
                                continue;
                            }
                            else {
                                frameSizeCam2.width = matCam2.width();
                                frameSizeCam2.height = matCam2.height();
                            }
                            long stopTime = System.currentTimeMillis();
                            while((stopTime - startTime) < (1000/fpsMax)) {
                                stopTime = System.currentTimeMillis();
                                try {
                                    Thread.sleep(1);
                                }
                                catch (InterruptedException e) {
                                }
                            }
                            fpsCam2Value = (int) (1000 / (stopTime - startTime));
                        }
                    }
                    else if (!closePlugin && !startCapture) {
                        closePlugin = true;
                        stateIPCams = false;
                        captureCam2.release();
                    }
                    else {
                        try {
                            Thread.sleep(1000);
                            resetWatchDogCam2(4000);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        ipCams.setDaemon(true);
        return ipCams;
    }
    
    /**
     * Thread for processing/treatment of image captured
     */
    private Thread updaterThreadManipulation() {
        Thread manipulationImg = new Thread("Manipulation of images captured") {
            public void run() {
                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                while (true) {
                    if (!closePlugin && startCapture) {
                        if (!matCam1.empty() && !matCam2.empty()) {
                            long startTime = System.currentTimeMillis();
                            offlineImageCam1 = UtilCv.matToBufferedImage(matCam1);
                            offlineImageCam2 = UtilCv.matToBufferedImage(matCam2);
                            
                            showImage(UtilTracking.resizeBufferedImage(offlineImageCam1, panelSize, true), UtilTracking.resizeBufferedImage(offlineImageCam2, panelSize, true));
                            long stopTime = System.currentTimeMillis();
                            while((stopTime - startTime) < (1000 / (fpsMax-10))) {
                                stopTime = System.currentTimeMillis();
                                try {
                                    Thread.sleep(1);
                                }
                                catch (InterruptedException e) {
                                }
                            }
                        }
                    }
                    else {
                        try {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        manipulationImg.setDaemon(true);
        return manipulationImg;
    }
    
    /**
     * Setup WatchDog
     */
    private void setupWatchDogCam(double timeout) {
        watchDogCam = new Thread(new Runnable() {
            @Override
            public void run() {
                methodWatchDogCam(timeout);
            }
        });
    }

    /**
     * Start WatchDog
     */
    private void startWatchDogCam() {
        if (watchDogCam.getState().toString() != "TIMED_WAITING")
            watchDogCam.start();
    }

    /**
     * Reset WatchDog for IPCam1
     */
    private void resetWatchDogCam1(double timeout) {
        endTimeMillisCam1 = (long) (System.currentTimeMillis() + timeout);
        virtualEndThreadCam1 = false;
    }
    
    /**
     * Reset WatchDog for IPCam2
     */
    private void resetWatchDogCam2(double timeout) {
        endTimeMillisCam2 = (long) (System.currentTimeMillis() + timeout);
        virtualEndThreadCam2 = false;
    }

    /**
     * WatchDog Method
     */
    private void methodWatchDogCam(double miliseconds) {
        endTimeMillisCam1 = (long) (System.currentTimeMillis() + miliseconds);
        virtualEndThreadCam1 = false;
        while (true) {
            if (System.currentTimeMillis() > endTimeMillisCam1 && !virtualEndThreadCam1) {
                if (!closePlugin && startCapture) {
                    NeptusLog.pub().error("TIME OUT IPCAM 1");
                    NeptusLog.pub().info("Clossing all Video Stream...");
                    noVideoLogoState = false;
                    closePlugin = true;
                    startCapture = false;
                    showDebug = false;
                    initImage();
                    virtualEndThreadCam1 = true;
                }
            }
            else if (virtualEndThreadCam1) {
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                }
            }
            
            if (System.currentTimeMillis() > endTimeMillisCam2 && !virtualEndThreadCam2) {
                if (!closePlugin && startCapture) {
                    NeptusLog.pub().error("TIME OUT IPCAM 2");
                    NeptusLog.pub().info("Clossing all Video Stream...");
                    noVideoLogoState = false;
                    closePlugin = true;
                    startCapture = false;
                    showDebug = false;
                    initImage();
                    virtualEndThreadCam2 = true;
                }
            }
            else if (virtualEndThreadCam2) {
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                }
            }
            
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
            }
        }
    }
    
    /**
     * Mouse click Listener
     */
    private void mouseListenerInit() {
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    boolean isCam1;
                    if (e.getX() <= panelSize.width / 2) {
                        realXCoordCam1 = (int) ((e.getX() * frameSizeCam1.width) / (panelSize.width / 2));
                        realYCoordCam1 = (int) ((e.getY() * frameSizeCam1.height) / (panelSize.height));
                        isCam1 = true;
                    }
                    else {
                        realXCoordCam2 = (int) (((e.getX() * frameSizeCam1.width) / (panelSize.width / 2)) - frameSizeCam1.width);
                        realYCoordCam2 = (int) ((e.getY() * frameSizeCam1.height) / (panelSize.height));
                        isCam1 = false;
                    }
                    
                    //TODO -> dispatch to imc message
                    if (isCam1)
                        System.out.println("Real Cam1: "+realXCoordCam1+" : "+realYCoordCam1);
                    else
                        System.out.println("Real Cam2: "+realXCoordCam2+" : "+realYCoordCam2);
                }
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popUpMenu(e.getX(), e.getY());
                }
            }
        });
    }
    
    /**
     * PopUp Menu
     */
    private void popUpMenu(int x, int y) {
        popup = new JPopupMenu();
        JMenuItem item1;
        popup.add(item1 = new JMenuItem(I18n.text("Start Capture"), ImageUtils.createImageIcon(String.format("images/downloader/camera.png"))))
                .addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        startCapture = true;
                        closePlugin = false;
                    }
                });
        item1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.ALT_MASK));
        JMenuItem item2;
        popup.add(item2 = new JMenuItem(I18n.text("Close Capture"), ImageUtils.createImageIcon(String.format("images/menus/exit.png"))))
                .addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        startCapture = false;
                        closePlugin = true;
                        showDebug = false;
                    }
                });
        item2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));
        JMenuItem item3;
        popup.add(item3 = new JMenuItem(I18n.text("Force Close Capture"), ImageUtils.createImageIcon(String.format("images/buttons/important.png"))))
                .addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!startCapture && closePlugin) {
                            NeptusLog.pub().warn(I18n.text("Forcing close of capture"));
                            if (captureCam1.isOpened())
                                captureCam1.release();
                            if (captureCam2.isOpened())
                                captureCam2.release();
                        }
                        else {
                            NeptusLog.pub().warn(I18n.text("First try close capture in normal mode"));
                        }
                    }
                });
        item3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.ALT_MASK));
        popup.addSeparator();
        JLabel infoDebug = new JLabel(I18n.text("Info Debug use Ctrl-D"));
        infoDebug.setIcon(ImageUtils.createImageIcon(String.format("images/menus/comment.png")));
        popup.add(infoDebug, JMenuItem.CENTER_ALIGNMENT);
        popup.show(this, x, y);
    }

    /**
     * Key Listener
     */
    private void keyListenerInit() {
        addKeyListener(new KeyListener() {
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL)
                    isCtrlPress = false;
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                
                if (e.getKeyCode() == KeyEvent.VK_CONTROL)
                    isCtrlPress = true;
                
                if (e.getKeyCode() == KeyEvent.VK_D && isCtrlPress) {
                    showDebug = !showDebug;
                }
                else if ((e.getKeyCode() == KeyEvent.VK_S) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                    startCapture = true;
                    closePlugin = false;
                }
                else if ((e.getKeyCode() == KeyEvent.VK_C) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                    startCapture = false;
                    closePlugin = true;
                    showDebug = false;
                }
                else if ((e.getKeyCode() == KeyEvent.VK_F) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                    if (!startCapture && closePlugin) {
                        NeptusLog.pub().warn(I18n.text("Forcing close of capture"));
                        if (captureCam1 != null) {
                            if (captureCam1.isOpened())
                                captureCam1.release();
                        }

                        if (captureCam2 != null) {
                            if (captureCam2.isOpened())
                                captureCam2.release();
                        }
                    }
                    else {
                        NeptusLog.pub().warn(I18n.text("First try close capture in normal mode"));
                    }
                }
            }
        });
    }
}
