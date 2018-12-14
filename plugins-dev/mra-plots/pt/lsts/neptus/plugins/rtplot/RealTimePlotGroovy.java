/*
 * Copyright (c) 2004-2018 Universidade do Porto - Faculdade de Engenharia
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
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
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
 * Author: José Pinto
 * Feb 14, 2013
 */
package pt.lsts.neptus.plugins.rtplot;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.imc.ImcSystem;
import pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.ConfigurationListener;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.PluginUtils;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.plugins.update.Periodic;
import pt.lsts.neptus.util.GuiUtils;

@PluginDescription(name = "Real-Time plot", icon = "pt/lsts/neptus/plugins/rtplot/rtplot.png", description = "Real-Time plots with Groovy scripts")
@Popup(accelerator = 'U', pos = POSITION.CENTER, height = 300, width = 300)
public class RealTimePlotGroovy extends ConsolePanel implements ConfigurationListener {

    private static final long serialVersionUID = 1L;
    private TimeSeriesCollection allTsc = new TimeSeriesCollection();
    private TimeSeriesCollection selTsc = new TimeSeriesCollection();
    private JButton btnEdit, btnClear, cfgEdit;
    private JComboBox<String> sysSel;
    private ItemListener itemListener;
    private JLabel vLabel;
    private JPanel bottom, top;
    private String selectedSys = null;
    private GroovyShell shell;
    private CompilerConfiguration cnfg;
    private ImportCustomizer imports;
    private Writer scriptOutput, scriptError;
    private StringBuffer bufferO, bufferE;
    private boolean updating = false;
    private final ChartPanel chart;
    private List<String> systems = Collections.synchronizedList(new ArrayList<String>());

    /**
     * @return the systems
     */
    public synchronized List<String> getSystems() {
        return systems;
    }

    @NeptusProperty(name = "Periodicity (milliseconds)")
    public int periodicity = 1000;

    @NeptusProperty(name = "Maximum Number of points")
    public int numPoints = 100;

    @NeptusProperty(name = "Current Script")
    public String traceScript = "addSerie(msgs(\"EstimatedState.depth\"))";

    @NeptusProperty(name = "Initial Script")
    public String initScripts = "addSerie(msgs(\"EstimatedState.depth\"))";

    private String previousScritp = "";

    private int numPointsBefore = numPoints;

    public RealTimePlotGroovy(ConsoleLayout c) {
        super(c);
        JFreeChart timeSeriesChart = ChartFactory.createTimeSeriesChart(null, null, null, allTsc, true, true, true);
        chart = new ChartPanel(timeSeriesChart);
        // init shell
        cnfg = new CompilerConfiguration();
        imports = new ImportCustomizer();
        imports.addStarImports("pt.lsts.imc", "java.lang.Math", "pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder");
        imports.addStaticStars("pt.lsts.neptus.plugins.rtplot.PlotScript");
        cnfg.addCompilationCustomizers(imports);
        redirectIO();
        previousScritp = traceScript;
        shell = new GroovyShell(this.getClass().getClassLoader(), cnfg);
        PlotScript.setPlot(this);
        shell.setProperty("out", scriptOutput);
        shell.setProperty("err", scriptError);
        configLayout();
        add(chart, BorderLayout.CENTER);
    }

    /**
     * 
     */
    private void redirectIO() {
        bufferO = new StringBuffer();
        scriptOutput = new Writer() {

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                bufferO.append(cbuf, off, len);

            }

            @Override
            public void flush() throws IOException {
                NeptusLog.pub().debug(I18n.text(bufferO.toString()));
                bufferO = new StringBuffer();

            }

            @Override
            public void close() throws IOException {
            }
        };
        bufferE = new StringBuffer();
        scriptError = new Writer() {

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                bufferE.append(cbuf, off, len);
            }

            @Override
            public void flush() throws IOException {
                NeptusLog.pub().error(I18n.text(bufferE.toString()));
                bufferE = new StringBuffer();
            }

            @Override
            public void close() throws IOException {
                scriptOutput.close();

            }
        };
    }

    public void bind(String var, Object value) {
        shell.setVariable(var, value);
    }

    public void unbind(String var) {
        shell.getContext().getVariables().remove(var);
    }

    /**
     * @param allTsc the TimeSeries to set
     */
    public void addSerie(String id, TimeSeries ts) {
        if (allTsc.getSeries(id) == null) {
            // TODO ts.setMaximumItemCount(numPoints);
            allTsc.addSeries(ts);
        }
        else
            allTsc.getSeries(id).addOrUpdate(ts.getDataItem(0));
    }

    /**
     * ConsolePanel layout configuration
     */
    public void configLayout() {
        setLayout(new BorderLayout());
        bottom = new JPanel(new GridLayout(1, 0));
        btnEdit = new JButton(I18n.text("Settings"));
        bottom.add(btnEdit);
        btnEdit.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                RealTimePlotScript.editSettings(RealTimePlotGroovy.this, selectedSys);
            }
        });
        cfgEdit = new JButton(I18n.text("Config"));
        bottom.add(cfgEdit);
        cfgEdit.addActionListener(new ActionListener() {

            @SuppressWarnings("deprecation")
            @Override
            public void actionPerformed(ActionEvent e) {
                PluginUtils.editPluginProperties(RealTimePlotGroovy.this, true);
            }
        });
        btnClear = new JButton(I18n.text("Clear"));
        bottom.add(btnClear);
        btnClear.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                allTsc.removeAllSeries();
            }
        });

        add(bottom, BorderLayout.SOUTH);
        sysSel = new JComboBox<String>();
        selectedSys = "ALL";
        itemListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (sysSel.getSelectedItem() != null && !updating)
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        String sel = (String) sysSel.getSelectedItem();
                        if (!sel.equals(selectedSys)) {
                            selectedSys = new String(sel);
                            if (selectedSys.equalsIgnoreCase("ALL")) {
                                changeChartSrc(allTsc); // Reestablish initial timeSeries collection
                                Arrays.stream(ImcSystemsHolder.lookupActiveSystemVehicles())
                                        .forEach(s -> systems.add(s.getName()));
                                return;
                            }
                            else {
                                systems.clear();
                                systems.add(selectedSys);
                                selTsc = new TimeSeriesCollection();
                                for (Object s : allTsc.getSeries()) {
                                    TimeSeries serie = (TimeSeries) s;
                                    if (serie.getKey().toString().startsWith(selectedSys))
                                        selTsc.addSeries(serie);
                                }
                                changeChartSrc(selTsc);
                            }
                        }
                    }

            }
        };
        updateComboBox();
        sysSel.addItemListener(itemListener);
        vLabel = new JLabel("System:");
        vLabel.setToolTipText("Select System(s)");
        top = new JPanel(new GridLayout(0, 2));
        top.add(vLabel);
        top.add(sysSel);
        add(top, BorderLayout.NORTH);
    }

    /**
     * 
     */
    public void changeChartSrc(TimeSeriesCollection tsc) {
        JFreeChart timeSeriesChart = ChartFactory.createTimeSeriesChart(null, null, null, tsc, true, true, true);
        chart.setChart(timeSeriesChart);
    }

    /**
     * Update available systems
     */
    public synchronized void updateComboBox() {
        updating = true;
        sysSel.removeAllItems();
        sysSel.addItem("ALL");
        Arrays.stream(ImcSystemsHolder.lookupActiveSystemVehicles()).forEach(i -> sysSel.addItem(i.getName()));
        sysSel.setSelectedItem(selectedSys);
        updating = false;
    }

    @Periodic(millisBetweenUpdates = 1000)
    public boolean update() {
        if (!isShowing())
            return true;
        updateComboBox();
        systems.clear();
        for(ImcSystem system: ImcSystemsHolder.lookupActiveSystemVehicles()) {
            if(selectedSys.equalsIgnoreCase("ALL"))
                systems.add(system.getName());
            else if(selectedSys.equalsIgnoreCase(system.getName()))
                systems.add(selectedSys);
        }
        runScript(traceScript);
        return true;
    }

    @Override
    public void propertiesChanged() {
        if (!traceScript.equals(previousScritp) || numPoints != numPointsBefore) {
            allTsc.removeAllSeries();
        }
        previousScritp = traceScript;
        numPointsBefore = numPoints;
    }

    // protected void parseScript() throws Exception {
    // Pattern p = Pattern.compile("([\\w ]+):(.*)");
    //
    // for (String line : traceScripts.split("\n")) {
    // Matcher m = p.matcher(line);
    // if (m.matches()) {
    // String ss = m.group(2);
    // ss = ss.replaceAll("\\$\\{([^\\}]*)\\}", "state.expr(\"$1\")");
    // String name = m.group(1);
    // Context.enter();
    // Script sc = context.compileString(ss, name, 1, null);
    // Context.exit();
    // scripts.put(name, sc);
    // }
    // }
    // tsc.removeAllSeries();
    // }

    public void plot(JFreeChart p) {
    }

    public Object invokeMethod(Binding b, String method, String... args) {
        return shell.invokeMethod(method, args);
    }

    public void runScript(String script) {
        Object result;
        bind("systems", systems);
        PlotScript.setSystems(systems);
        if (ImcSystemsHolder.lookupActiveSystemVehicles().length > 0) {
            try {
                shell.parse(script);
                result = shell.evaluate(script);
            }
            catch (CompilationFailedException e) {
                GuiUtils.errorMessage(this, "Error Parsing Script", e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void initSubPanel() {
        previousScritp = traceScript;
        propertiesChanged();
    }

    @Override
    public void cleanSubPanel() {
        try {
            scriptOutput.close();
            scriptError.close();
        }
        catch (IOException e) {
            NeptusLog.pub().error(I18n.text("Error closing IO Writer"), e);
            // e.printStackTrace();
        }
    }
}
