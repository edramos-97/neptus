/*
 * Copyright (c) 2004-2019 Universidade do Porto - Faculdade de Engenharia
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
 * Author: pdias
 * Apr 25, 2018
 */
package pt.lsts.neptus.plugins.envdisp.gui;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jdesktop.swingx.JXBusyLabel;

import com.l2fprod.common.propertysheet.DefaultProperty;
import com.l2fprod.common.propertysheet.Property;

import net.miginfocom.swing.MigLayout;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.data.Pair;
import pt.lsts.neptus.gui.InfiniteProgressPanel;
import pt.lsts.neptus.gui.PropertiesProvider;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.ConfigurationListener;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.NeptusProperty.LEVEL;
import pt.lsts.neptus.plugins.PluginUtils;
import pt.lsts.neptus.plugins.envdisp.loader.NetCDFLoader;
import pt.lsts.neptus.plugins.envdisp.painter.GenericNetCDFDataPainter;
import pt.lsts.neptus.util.AngleUtils;
import pt.lsts.neptus.util.FileUtil;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.ImageUtils;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 * @author pdias
 *
 */
@SuppressWarnings("serial")
public class LayersListPanel extends JPanel implements PropertiesProvider, ConfigurationListener {

    private static final String NCGRP_EXTENSION = "ncgrp";

    private static final ImageIcon LOGOIMAGE_ICON = new ImageIcon(
            ImageUtils.getScaledImage("pt/lsts/neptus/plugins/envdisp/netcdf-radar.png", 32, 32));
    static final ImageIcon VIEW_IMAGE_ICON = ImageUtils.createImageIcon("images/menus/view.png");

    static enum UpOrDown {
        UP,
        DOWN
    }

    private Date dateLimit = null;
    @NeptusProperty(name = "Lat min", userLevel = LEVEL.REGULAR)
    private double latDegMin = -90;
    @NeptusProperty(name = "Lat max", userLevel = LEVEL.REGULAR)
    private double latDegMax = 90;
    @NeptusProperty(name = "Lon min", userLevel = LEVEL.REGULAR)
    private double lonDegMin = -180;
    @NeptusProperty(name = "Lon max", userLevel = LEVEL.REGULAR)
    private double lonDegMax = 180;
    private double depthMin = 0;
    @NeptusProperty(name = "Depth max", userLevel = LEVEL.REGULAR)
    private double depthMax = 1000;
    
    private AtomicLong plotCounter = new AtomicLong();
    private File recentFolder = new File(".");

    // GUI
    private Window parentWindow = null;
    private JPanel holder;
    private JPanel buttonBarPanel;
    private JScrollPane scrollHolder;
    private JXBusyLabel busyPanel;
    
    private JButton addButton;
    private AbstractAction addAction;
    private JButton hideAllButton;
    private AbstractAction hideAllAction;
    private JButton saveOpenVizButton;
    private AbstractAction saveOpenVizAction;
    private JButton loadVizButton;
    private AbstractAction loadVizAction;
    
    private JSpinner spinnerLatMin;
    private JSpinner spinnerLatMax;
    private JSpinner spinnerLonMin;
    private JSpinner spinnerLonMax;
    private JSpinner spinnerDepthMax;
    
    public LayersListPanel() {
        this(null);
    }
    
    public <W extends Window> LayersListPanel(W parentWindow) {
        this.parentWindow = parentWindow;
        this.setPreferredSize(new Dimension(600, 400));
        initializeActions();
        initialize();
    }

    private void initialize() {
        setLayout(new MigLayout("ins 0, wrap 1"));

        buttonBarPanel = new JPanel(new MigLayout("ins 10"));
        
        JLabel logoLabel = new JLabel(LOGOIMAGE_ICON);
        
        busyPanel = InfiniteProgressPanel.createBusyAnimationInfiniteBeans(20);
        busyPanel.setVisible(false);
        
        Dimension buttonDimension = new Dimension(80, 30);
        addButton = new JButton(addAction);
        addButton.setSize(buttonDimension);

        hideAllButton = new JButton(hideAllAction);

        saveOpenVizButton = new JButton(saveOpenVizAction);
        loadVizButton = new JButton(loadVizAction);

        spinnerLatMin = new JSpinner(new SpinnerNumberModel(latDegMin, -90, 90, 1));
        spinnerLatMin.setSize(new Dimension(100, 20));
        spinnerLatMin.setToolTipText(I18n.text("This sets min lat value when loading."));
        ((JSpinner.NumberEditor) spinnerLatMin.getEditor()).getTextField().setEditable(true);
        ((JSpinner.NumberEditor) spinnerLatMin.getEditor()).getTextField().setBackground(Color.WHITE);
        spinnerLatMin.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                double val = (double) spinnerLatMin.getValue();
                setLatDegMin(val);
            }
        });

        spinnerLatMax = new JSpinner(new SpinnerNumberModel(latDegMax, -90, 90, 1));
        spinnerLatMax.setSize(new Dimension(100, 20));
        spinnerLatMax.setToolTipText(I18n.text("This sets max lat value when loading."));
        ((JSpinner.NumberEditor) spinnerLatMax.getEditor()).getTextField().setEditable(true);
        ((JSpinner.NumberEditor) spinnerLatMax.getEditor()).getTextField().setBackground(Color.WHITE);
        spinnerLatMax.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                double val = (double) spinnerLatMax.getValue();
                setLatDegMax(val);
            }
        });

        spinnerLonMin = new JSpinner(new SpinnerNumberModel(lonDegMin, -180, 180, 1));
        spinnerLonMin.setSize(new Dimension(100, 20));
        spinnerLonMin.setToolTipText(I18n.text("This sets min lon value when loading."));
        ((JSpinner.NumberEditor) spinnerLonMin.getEditor()).getTextField().setEditable(true);
        ((JSpinner.NumberEditor) spinnerLonMin.getEditor()).getTextField().setBackground(Color.WHITE);
        spinnerLonMin.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                double val = (double) spinnerLonMin.getValue();
                setLonDegMin(val);
            }
        });
        
        spinnerLonMax = new JSpinner(new SpinnerNumberModel(lonDegMax, -180, 180, 1));
        spinnerLonMax.setSize(new Dimension(100, 20));
        spinnerLonMax.setToolTipText(I18n.text("This sets max lon value when loading."));
        ((JSpinner.NumberEditor) spinnerLonMax.getEditor()).getTextField().setEditable(true);
        ((JSpinner.NumberEditor) spinnerLonMax.getEditor()).getTextField().setBackground(Color.WHITE);
        spinnerLonMax.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                double val = (double) spinnerLonMax.getValue();
                setLonDegMax(val);
            }
        });

        spinnerDepthMax = new JSpinner(new SpinnerNumberModel(depthMax, 0, 10000, 1));
        spinnerDepthMax.setSize(new Dimension(100, 20));
        spinnerDepthMax.setToolTipText(I18n.text("This sets max depth value when loading."));
        ((JSpinner.NumberEditor) spinnerDepthMax.getEditor()).getTextField().setEditable(true);
        ((JSpinner.NumberEditor) spinnerDepthMax.getEditor()).getTextField().setBackground(Color.WHITE);
        spinnerDepthMax.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                double val = (double) spinnerDepthMax.getValue();
                setDepthMax(val);
            }
        });

        buttonBarPanel.add(logoLabel);
        buttonBarPanel.add(addButton, "sg button");
        buttonBarPanel.add(loadVizButton, "sg button");
        buttonBarPanel.add(new JLabel(I18n.text("Lat max") + ":"));
        buttonBarPanel.add(spinnerLatMax);
        buttonBarPanel.add(new JLabel(I18n.text("Lon max") + ":"));
        buttonBarPanel.add(spinnerLonMax);
        buttonBarPanel.add(new JLabel(I18n.text("Depth max") + ":"), "span 1 2");
        buttonBarPanel.add(spinnerDepthMax, "span 1 2, wrap");
        buttonBarPanel.add(busyPanel);
        buttonBarPanel.add(hideAllButton, "sg button");
        buttonBarPanel.add(saveOpenVizButton, "sg button");
        buttonBarPanel.add(new JLabel(I18n.text("Lat min") + ":"));
        buttonBarPanel.add(spinnerLatMin);
        buttonBarPanel.add(new JLabel(I18n.text("Lon min") + ":"));
        buttonBarPanel.add(spinnerLonMin);
        
        add(buttonBarPanel, "w 100%");

        holder = new JPanel();
        holder.setLayout(new MigLayout("ins 5, flowy", "grow, fill", ""));
        holder.setSize(400, 600);
        
        scrollHolder = new JScrollPane(holder);
        add(scrollHolder, "w 100%, h 100%");
    }
    
    private void initializeActions() {
        addAction = new AbstractAction(I18n.text("Add")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                File[] fxList = NetCDFLoader.showChooseANetCDFMultipleToOpen(parentWindow, recentFolder);
                if (fxList == null)
                    return;
                
                JButton source = (JButton) e.getSource();
                source.setEnabled(false);
                setBusy(true);
                
                for (File fx : fxList) {
                    try {
                        NetcdfFile dataFile = NetcdfFile.open(fx.getPath());
                        
                        Variable choiceVarOpt = NetCDFLoader.showChooseVar(fx.getName(), dataFile, parentWindow);
                        if (choiceVarOpt != null) {
                            Future<GenericNetCDFDataPainter> fTask = NetCDFLoader.loadNetCDFPainterFor(fx.getPath(),
                                    dataFile, choiceVarOpt.getShortName(), plotCounter.getAndIncrement(), dateLimit,
                                    new Pair<Double, Double>(latDegMin, latDegMax),
                                    new Pair<Double, Double>(lonDegMin, lonDegMax),
                                    new Pair<Double, Double>(depthMin, depthMax));
                            SwingWorker<GenericNetCDFDataPainter, Void> sw = new SwingWorker<GenericNetCDFDataPainter, Void>() {
                                @Override
                                protected GenericNetCDFDataPainter doInBackground() throws Exception {
                                    return fTask.get();
                                }
                                
                                @Override
                                protected void done() {
                                    try {
                                        GenericNetCDFDataPainter viz = get();
                                        if (viz != null) {
                                            // PluginUtils.editPluginProperties(viz, parentWindow, true);
                                            addVisualizationLayer(viz);
                                        }
                                    }
                                    catch (Exception e) {
                                        NeptusLog.pub().error(e.getMessage(), e);
                                        GuiUtils.errorMessage(parentWindow,
                                                I18n.textf("Loading netCDF variable %s", choiceVarOpt.getShortName()),
                                                e.getMessage());
                                    }
                                    NetCDFLoader.deleteNetCDFUnzippedFile(fx);
                                    source.setEnabled(true);
                                    setBusy(false);
                                }
                            };
                            sw.execute();
                        }
                        else {
                            source.setEnabled(true);
                            NetCDFLoader.deleteNetCDFUnzippedFile(fx);
                            setBusy(false);
                        }
                        
                        recentFolder = fx;
                    }
                    catch (Exception e1) {
                        e1.printStackTrace();
                        source.setEnabled(true);
                        NetCDFLoader.deleteNetCDFUnzippedFile(fx);
                        setBusy(false);
                    }
                }
            }
        };

        hideAllAction = new AbstractAction(I18n.text("Hide All")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                getAllVizConfigPanels().forEach(p -> p.setVizVisible(false));
            }
        };
        
        saveOpenVizAction = new AbstractAction(I18n.text("Save Group")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                Document doc = null;
                doc = DocumentHelper.createDocument();
                Element root = doc.addElement("netcdf");

                List<GenericNetCDFDataPainter> lst = getVarLayersList();
                if (!lst.isEmpty()) {
                    lst.stream().forEach(v -> {
                        String xml = PluginUtils.getConfigXmlWithDefaults(v);
                        try {
                            Element el = DocumentHelper.parseText(xml).getRootElement();
                            el.detach();
                            el.setName("viz");
                            root.add(el);
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    
                    JFileChooser jfc = GuiUtils.getFileChooser(recentFolder, I18n.text("netCDF open group"), NCGRP_EXTENSION);
                    int result = jfc.showDialog(SwingUtilities.windowForComponent(LayersListPanel.this), I18n.text("Save"));
                    if (result == JFileChooser.CANCEL_OPTION)
                        return;
                    File fx = jfc.getSelectedFile();
                    String ext = FileUtil.getFileExtension(fx);
                    if (NCGRP_EXTENSION.equalsIgnoreCase(ext))
                        ext = "";
                    else
                        ext = "." + NCGRP_EXTENSION;
                    fx = new File(fx.getAbsolutePath() + ext);

                    try {
                        FileUtils.write(fx, FileUtil.getAsPrettyPrintFormatedXMLString(doc.asXML()), false);
                        recentFolder = fx;
                    }
                    catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                else {
                    GuiUtils.confirmDialog(SwingUtilities.windowForComponent(LayersListPanel.this), I18n.text("Information"),
                            I18n.text("Nothing to save!"), ModalityType.DOCUMENT_MODAL);
                }
            }
        }; 

        loadVizAction = new AbstractAction(I18n.text("Load Group")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                JButton source = (JButton) e.getSource();
                source.setEnabled(false);
                setBusy(true);
                
                JFileChooser jfc = GuiUtils.getFileChooser(recentFolder, I18n.text("netCDF open group"), NCGRP_EXTENSION);
                int result = jfc.showDialog(SwingUtilities.windowForComponent(LayersListPanel.this), I18n.text("Open"));
                if (result == JFileChooser.CANCEL_OPTION) {
                    source.setEnabled(true);
                    setBusy(false);
                    return;
                }
                File fx = jfc.getSelectedFile();
                try {
                    LinkedHashMap<Future<GenericNetCDFDataPainter>, Element> workers = new LinkedHashMap<>();
                    
                    String xml = FileUtils.readFileToString(fx);
                    Document doc = DocumentHelper.parseText(xml);
                    @SuppressWarnings("unchecked")
                    List<Node> entries = doc.getRootElement().selectNodes("viz");
                    
                    entries.stream().forEach(v -> {
                        Element props = (Element) v.detach();
                        props.setName("properties");
                        
                        Properties properties = new Properties();
                        String xmlProps = props.asXML();
                        xmlProps = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n" + xmlProps;
                        try {
                            ByteArrayInputStream bais = new ByteArrayInputStream(xmlProps.getBytes("UTF-8"));
                            properties.loadFromXML(bais);

                            String fxProps = (String) properties.get("netCDFFile");
                            File fxNC = new File(fxProps);
                            NetcdfFile dataFile = NetcdfFile.open(fxNC.getPath());

                            String varProps = (String) properties.get("varName");

                            Future<GenericNetCDFDataPainter> fTask = NetCDFLoader.loadNetCDFPainterFor(fxNC.getPath(),
                                    dataFile, varProps, plotCounter.getAndIncrement(), dateLimit,
                                    new Pair<Double, Double>(latDegMin, latDegMax),
                                    new Pair<Double, Double>(lonDegMin, lonDegMax),
                                    new Pair<Double, Double>(depthMin, depthMax));

                            workers.put(fTask, props);
                        }
                        catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    });
                    
                    SwingWorker<Void, GenericNetCDFDataPainter> sw = new SwingWorker<Void, GenericNetCDFDataPainter>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            workers.keySet().forEach(w -> {
                                while (true) {
                                    try {
                                        GenericNetCDFDataPainter viz = w.get(100, TimeUnit.MILLISECONDS);
                                        if (viz != null) {
                                            PluginUtils.setConfigXml(viz, workers.get(w).asXML());
                                            publish(viz);
                                        }
                                        break;
                                    }
                                    catch (TimeoutException e) {
                                        continue;
                                    }
                                    catch (InterruptedException | ExecutionException e) {
                                        e.printStackTrace();
                                        break;
                                    }
                                }
                            });
                            return null;
                        }
                        
                        /* (non-Javadoc)
                         * @see javax.swing.SwingWorker#process(java.util.List)
                         */
                        @Override
                        protected void process(List<GenericNetCDFDataPainter> chunks) {
                            chunks.forEach(v -> addVisualizationLayer(v));
                        }
                        
                        @Override
                        protected void done() {
                            source.setEnabled(true);
                            setBusy(false);
                        }
                    };
                    sw.execute();
                }
                catch (Exception e1) {
                    e1.printStackTrace();
                    source.setEnabled(true);
                    setBusy(false);
                }
            }
        }; 
    }

    private void setBusy(boolean busy) {
        busyPanel.setVisible(busy);
        busyPanel.setBusy(busy);;
    }

    /**
     * @return the parentWindow
     */
    public Window getParentWindow() {
        return parentWindow;
    }
    
    /**
     * @param parentWindow the parentWindow to set
     */
    public <W extends Window> void setParentWindow(W parentWindow) {
        this.parentWindow = parentWindow;
    }
    
    /**
     * This list is ordered by paint in most foreground to the less.
     * (So painting in reverse order is necessary.)
     * 
     * @return the varLayersList
     */
    public List<GenericNetCDFDataPainter> getVarLayersList() {
        return getAllVizConfigPanels().map(c -> c.getViz()).collect(Collectors.toList());
    }
    
    /**
     * @return
     */
    private Stream<VizConfigPanel> getAllVizConfigPanels() {
        return Stream.of(holder.getComponents()).filter(c -> c instanceof VizConfigPanel).map(c -> (VizConfigPanel) c);
    }

    private void addVisualizationLayer(GenericNetCDFDataPainter viz) {
        new VizConfigPanel(holder, viz);
    }
    
    
    /**
     * @return the dateLimit
     */
    public Date getDateLimit() {
        return dateLimit;
    }

    /**
     * @param dateLimit the dateLimit to set
     */
    public void setDateLimit(Date dateLimit) {
        this.dateLimit = dateLimit;
    }

    /**
     * @return the latDegMin
     */
    public double getLatDegMin() {
        return latDegMin;
    }

    /**
     * @param latDegMin the latDegMin to set
     */
    public void setLatDegMin(double latDegMin) {
        this.latDegMin = Math.max(-90, Math.min(90, AngleUtils.nomalizeAngleDegrees180(latDegMin)));
    }

    /**
     * @return the latDegMax
     */
    public double getLatDegMax() {
        return latDegMax;
    }

    /**
     * @param latDegMax the latDegMax to set
     */
    public void setLatDegMax(double latDegMax) {
        this.latDegMax = Math.max(-90, Math.min(90, AngleUtils.nomalizeAngleDegrees180(latDegMax)));
    }

    /**
     * @return the lonDegMin
     */
    public double getLonDegMin() {
        return lonDegMin;
    }

    /**
     * @param lonDegMin the lonDegMin to set
     */
    public void setLonDegMin(double lonDegMin) {
        this.lonDegMin = Math.max(-180, Math.min(180, AngleUtils.nomalizeAngleDegrees180(lonDegMin)));
    }

    /**
     * @return the lonDegMax
     */
    public double getLonDegMax() {
        return lonDegMax;
    }

    /**
     * @param lonDegMax the lonDegMax to set
     */
    public void setLonDegMax(double lonDegMax) {
        this.lonDegMax = Math.max(-180, Math.min(180, AngleUtils.nomalizeAngleDegrees180(lonDegMax)));
    }

    /**
     * @return the depthMax
     */
    public double getDepthMax() {
        return depthMax;
    }

    /**
     * @param depthMax the depthMax to set
     */
    public void setDepthMax(double depthMax) {
        this.depthMax = Math.max(0, depthMax);
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.plugins.ConfigurationListener#propertiesChanged()
     */
    @Override
    public void propertiesChanged() {
        setDateLimit(dateLimit);
        setLatDegMin(latDegMin);
        spinnerLatMin.setValue(latDegMin);
        setLatDegMax(latDegMax);
        spinnerLatMax.setValue(latDegMax);
        setLonDegMin(lonDegMin);
        spinnerLonMin.setValue(lonDegMin);
        setLonDegMax(lonDegMax);
        spinnerLonMax.setValue(lonDegMax);
        setDepthMax(depthMax);
        spinnerDepthMax.setValue(depthMax);
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.gui.PropertiesProvider#getProperties()
     */
    @Override
    public DefaultProperty[] getProperties() {
        DefaultProperty[] layerProps = PluginUtils.getPluginProperties(this);
        return layerProps;
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.gui.PropertiesProvider#setProperties(com.l2fprod.common.propertysheet.Property[])
     */
    @Override
    public void setProperties(Property[] properties) {
        PluginUtils.setPluginProperties(this, properties);
        propertiesChanged();
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.gui.PropertiesProvider#getPropertiesDialogTitle()
     */
    @Override
    public String getPropertiesDialogTitle() {
        return null;
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.gui.PropertiesProvider#getPropertiesErrors(com.l2fprod.common.propertysheet.Property[])
     */
    @Override
    public String[] getPropertiesErrors(Property[] properties) {
        return null;
    }

    public Element asElement() {
        String xml = PluginUtils.getConfigXml(this);
        try {
            Element root = DocumentHelper.parseText(xml).getRootElement();
//            getVarLayersList().stream().forEach(v -> {
//                String xmlElm = PluginUtils.getConfigXml(v);
//                System.out.println(xmlElm);
//                try {
//                    Element el = DocumentHelper.parseText(xmlElm).getRootElement();
//                    el.detach();
//                    el.setName("viz");
//                    root.add(el);
//                }
//                catch (Exception ex) {
//                    ex.printStackTrace();
//                }
//            });
            
            return root;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return null;
    }

    public void parseXmlElement(Element elem) {
        PluginUtils.setConfigXml(this, elem.detach().asXML());
        
//        @SuppressWarnings("unchecked")
//        List<Element> vizElmList = elem.element("aux").element("properties").elements("viz");
//        for (Element elm : vizElmList) {
//            elm.detach().setName("properties");
//            
//        }
    }
    
    public static void main(String[] args) {
        GuiUtils.testFrame(new LayersListPanel(), "", 620, 350);
    }
}
