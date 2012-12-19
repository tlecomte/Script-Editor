package plugins.tprovoost.scripteditor.gui;

import icy.gui.component.button.IcyButton;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.network.NetworkUtil;
import icy.preferences.IcyPreferences;
import icy.preferences.XMLPreferences;
import icy.resource.icon.IcyIcon;
import icy.system.SystemUtil;
import icy.system.thread.ThreadUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import plugins.tprovoost.scripteditor.main.scriptinghandlers.ScriptingHandler;
import plugins.tprovoost.scripteditor.scriptingconsole.BindingsScriptFrame;

public class ScriptingEditor extends IcyFrame
{

    private JTabbedPane tabbedPane;
    private JButton addPaneButton;
    private String currentDirectoryPath;
    private ArrayList<String> previousFiles = new ArrayList<String>();
    private static final int ctrlMask = SystemUtil.getCtrlMask();
    private static final int MAX_RECENT_FILES = 5;
    private XMLPreferences prefs = IcyPreferences.pluginsRoot().node("scripteditor");
    private JMenu menuOpenRecent;

    public ScriptingEditor()
    {
	super("Script Editor", true, true, true);

	// load preferences
	XMLPreferences openedFiles = prefs.node("openedFiles");
	for (XMLPreferences key : openedFiles.getChildren())
	{
	    previousFiles.add(key.get("name", ""));
	}

	setJMenuBar(createJMenuBar());

	JPanel mainPanel = new JPanel(new BorderLayout());
	tabbedPane = new JTabbedPane();
	tabbedPane.addChangeListener(new ChangeListener()
	{

	    @Override
	    public void stateChanged(ChangeEvent arg0)
	    {
		Component comp = tabbedPane.getSelectedComponent();
		if (!(comp instanceof ScriptingPanel))
		    return;
		ScriptingPanel panel = (ScriptingPanel) comp;
		final ScriptingHandler handler = panel.getScriptHandler();
		ThreadUtil.bgRun(new Runnable()
		{

		    @Override
		    public void run()
		    {
			int max = 20;
			int i = 0;
			while (handler == null && i < max)
			{
			    ThreadUtil.sleep(500);
			    ++i;
			}
		    }
		});

	    }
	});

	addPaneButton = new IcyButton(new IcyIcon("plus"));
	addPaneButton.setBorderPainted(false);
	addPaneButton.setPreferredSize(new Dimension(20, 20));
	addPaneButton.setMinimumSize(new Dimension(20, 20));
	addPaneButton.setMaximumSize(new Dimension(20, 20));
	addPaneButton.setSize(20, 20);

	addPaneButton.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		createNewPane();
	    }
	});
	createNewPane();

	mainPanel.add(tabbedPane);
	setContentPane(mainPanel);
    }

    public ScriptingPanel createNewPane()
    {
	return createNewPane("Untitled");
    }

    /**
     * Creates a new Scripting Pane in the JTabbedPane and returns it. <br/>
     * Automatically removes the "+" tab and re-add it after the panel.
     * 
     * @param name
     *            : this is not a file path. Please use
     *            {@link #openFile(String)} instead to open a file with a file
     *            path.
     * 
     * @return
     * 
     * @see {@link #createNewPane()}, {@link #openFile(File)},
     *      {@link #openFile(String)}
     */
    public ScriptingPanel createNewPane(String name)
    {
	ScriptingPanel panelCreated = new ScriptingPanel(name);
	panelCreated.setTabbedPane(tabbedPane);
	int idx = tabbedPane.getTabCount() - 1;
	if (idx != -1)
	    tabbedPane.removeTabAt(idx);
	else
	    idx = 0;
	tabbedPane.addTab(name, panelCreated);
	tabbedPane.setTitleAt(idx, name + "*");
	tabbedPane.repaint();
	tabbedPane.setTabComponentAt(idx, new ButtonTabComponent(tabbedPane));
	tabbedPane.addTab("+", new JLabel());
	tabbedPane.setTabComponentAt(idx + 1, addPaneButton);
	tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 2);
	return panelCreated;
    }

    /**
     * Open the file f into the editor as a new tab.
     * 
     * @param f
     * @throws IOException
     */
    public void openFile(File f) throws IOException
    {
	String filename = f.getName();
	boolean exists = false;
	for (int i = 0; i < tabbedPane.getTabCount(); ++i)
	    if (tabbedPane.getTitleAt(i).contentEquals(filename))
	    {
		tabbedPane.setSelectedIndex(i);
		exists = true;
	    }
	if (exists)
	{
	    return;
	}
	ScriptingPanel panel = createNewPane(filename);
	panel.openFile(f);
	String path = f.getPath();
	XMLPreferences openedFiles = prefs.node("openedFiles");
	if (!previousFiles.contains(path))
	{
	    previousFiles.add(path);
	    XMLPreferences key = openedFiles.node(path);
	    key.put("name", path);
	}
	if (previousFiles.size() > MAX_RECENT_FILES)
	{
	    filename = previousFiles.get(0);
	    XMLPreferences key = openedFiles.node(filename);
	    if (key.exists())
	    {
		openedFiles.remove(filename);
	    }
	    previousFiles.remove(0);
	}
	updateRecentFiles();
    }

    private void updateRecentFiles()
    {
	menuOpenRecent.removeAll();
	ArrayList<String> copy = new ArrayList<String>(previousFiles);
	for (int i = 0; i < copy.size(); ++i)
	{
	    String path = previousFiles.get(i);
	    JMenuItem item = createRecentFileItem(path);
	    if (item == null)
	    {
		previousFiles.remove(path);
	    }
	    else
	    {
		menuOpenRecent.add(item);
	    }
	}
    }

    private JMenuItem createRecentFileItem(String filename)
    {
	final File f = new File(filename);
	JMenuItem toReturn = null;
	if (f.exists())
	{
	    toReturn = new JMenuItem(f.getName());
	    toReturn.addActionListener(new ActionListener()
	    {

		@Override
		public void actionPerformed(ActionEvent e)
		{
		    try
		    {
			openFile(f);
		    }
		    catch (IOException e1)
		    {
			e1.printStackTrace();
		    }
		}
	    });
	}
	return toReturn;
    }

    /**
     * Open the file f into the editor as a new tab.
     * 
     * @param f
     * @throws IOException
     */
    public void openStream(String name, InputStream stream) throws IOException
    {
	ScriptingPanel panel = createNewPane(name);
	panel.openStream(stream);
    }

    /**
     * Open the file f into the editor as a new tab.
     * 
     * @param f
     * @throws IOException
     */
    public void openFile(String s) throws IOException
    {
	openFile(new File(s));
    }

    /**
     * Displays a JFileChoose and let the user choose its file, then open it.
     * 
     * @see ScriptingEditor#openFile(File)
     */
    public void showOpenFileDialog()
    {
	JFileChooser fc;
	if (currentDirectoryPath == "")
	    fc = new JFileChooser();
	else
	    fc = new JFileChooser(currentDirectoryPath);
	fc.setMultiSelectionEnabled(true);
	if (fc.showOpenDialog(getFrame()) == JFileChooser.APPROVE_OPTION)
	{
	    File[] files = fc.getSelectedFiles();
	    for (File f : files)
		try
		{
		    openFile(f);
		}
		catch (IOException e)
		{
		    new FailedAnnounceFrame(f.getName() + " is not a valid file");
		}
	}
    }

    /**
     * Displays a JFileChoose and let the user choose its file, then open it.
     * 
     * @see ScriptingEditor#openFile(File)
     */
    public void showSaveFileDialog(ScriptingPanel panel)
    {
	JFileChooser fc;
	if (currentDirectoryPath == "")
	    fc = new JFileChooser();
	else
	    fc = new JFileChooser(currentDirectoryPath);
	if (fc.showSaveDialog(getFrame()) == JFileChooser.APPROVE_OPTION)
	{
	    if (panel.getLanguage().contentEquals("javascript"))
	    {
		fc.setFileFilter(new FileNameExtensionFilter("Javascript files", "js"));
	    }
	    else if (panel.getLanguage().contentEquals("python"))
	    {
		fc.setFileFilter(new FileNameExtensionFilter("Python files", "py"));
	    }
	    File file = fc.getSelectedFile();
	    panel.saveFileAs(file);
	}
    }

    /**
     * Creates the JMenuBar of the {@link ScriptingEditor}.
     * 
     * @return
     */
    private JMenuBar createJMenuBar()
    {
	JMenuBar toReturn = new JMenuBar();

	// MENU FILE
	JMenu menuFile = new JMenu("File");
	JMenuItem menuNew = new JMenuItem("New");
	menuNew.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		createNewPane("Untitled");
	    }
	});
	menuFile.add(menuNew);
	menuFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrlMask));

	JMenuItem menuOpen = new JMenuItem("Open...");
	menuOpen.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		showOpenFileDialog();
	    }
	});
	menuOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ctrlMask));
	menuOpen.setDisplayedMnemonicIndex(0);
	menuFile.add(menuOpen);

	menuOpenRecent = new JMenu("Open Recent");
	menuFile.add(menuOpenRecent);

	JMenuItem menuSave = new JMenuItem("Save");
	menuSave.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		Component comp = tabbedPane.getSelectedComponent();
		if (comp instanceof ScriptingPanel)
		{
		    ScriptingPanel panel = ((ScriptingPanel) comp);
		    if (panel.getSaveFile() == null)
		    {
			showSaveFileDialog(panel);
		    }
		    else if (panel.isDirty())
		    {
			panel.saveFile();
		    }
		}
	    }
	});
	menuSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrlMask));
	menuFile.add(menuSave);

	JMenuItem menuSaveAs = new JMenuItem("Save As");
	menuSaveAs.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		Component comp = tabbedPane.getSelectedComponent();
		if (comp instanceof ScriptingPanel)
		{
		    showSaveFileDialog((ScriptingPanel) comp);
		}
	    }
	});
	menuFile.add(menuSaveAs);

	menuFile.add(new JSeparator());

	JMenuItem menuClose = new JMenuItem("Close");
	menuClose.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		int i = tabbedPane.getSelectedIndex();
		if (i != -1)
		{
		    tabbedPane.remove(i);
		}
	    }
	});
	menuFile.add(menuClose);

	// MENU EDIT
	JMenu menuEdit = new JMenu("Edit");
	JMenuItem menuUndo = new JMenuItem("Undo");
	menuUndo.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		Component comp = tabbedPane.getTabComponentAt(0);
		if (!(comp instanceof ScriptingPanel))
		    return;
		ScriptingPanel panel = (ScriptingPanel) comp;
		panel.getTextArea().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrlMask)).actionPerformed(e);
	    }
	});
	// menuUndo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
	// ctrlMask));
	menuEdit.add(menuUndo);

	JMenuItem menuRedo = new JMenuItem("Redo");
	menuRedo.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		Component comp = tabbedPane.getTabComponentAt(0);
		if (!(comp instanceof ScriptingPanel))
		    return;
		ScriptingPanel panel = (ScriptingPanel) comp;
		panel.getTextArea().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrlMask)).actionPerformed(e);
	    }
	});
	// menuRedo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y,
	// ctrlMask));
	menuEdit.add(menuRedo);

	// MENU TEMPLATES
	JMenu menuTemplate = new JMenu("Templates");
	JMenu menuTemplateJS = new JMenu("Javascript");
	JMenuItem itemJSDuplicateSequence = new JMenuItem("Duplicate Sequence");

	itemJSDuplicateSequence.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		String current = new File(".").getAbsolutePath();
		current = current.substring(0, current.length() - 1);
		try
		{
		    InputStream is = getClass().getClassLoader().getResourceAsStream("plugins/tprovoost/scripteditor/templates/js/duplicateSequence.js");
		    openStream("duplicateSequence.js", is);
		}
		catch (IOException e1)
		{
		}
	    }
	});
	menuTemplateJS.add(itemJSDuplicateSequence);

	JMenuItem itemJSThreshold = new JMenuItem("Threshold");
	itemJSThreshold.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		String current = new File(".").getAbsolutePath();
		current = current.substring(0, current.length() - 1);
		try
		{
		    InputStream is = getClass().getClassLoader().getResourceAsStream("plugins/tprovoost/scripteditor/templates/js/tresholder.js");
		    openStream("thresholder.py", is);
		}
		catch (IOException e1)
		{
		}
	    }
	});
	// menuTemplateJS.add(itemJSThreshold);

	// add JS templates.
	menuTemplate.add(menuTemplateJS);

	JMenu menuOptions = new JMenu("Options");
	JMenuItem menuPreferences = new JMenuItem("Preferences");
	menuPreferences.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		PreferencesWindow prefs = PreferencesWindow.getPreferencesWindow();
		prefs.addToMainDesktopPane();
		prefs.setVisible(true);
	    }
	});
	menuOptions.add(menuPreferences);

	JMenuItem menuBindingsFrame = new JMenuItem("Bindings Frame");
	menuBindingsFrame.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		BindingsScriptFrame frame = BindingsScriptFrame.getInstance();
		frame.update();
		if (frame.isVisible())
		    frame.setVisible(false);
		else
		    frame.setVisible(true);
	    }
	});
	menuOptions.add(menuBindingsFrame);

	JMenuItem menuHelp = new JMenuItem("Help (online)");
	menuHelp.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent arg0)
	    {
		NetworkUtil.openURL("http://icy.bioimageanalysis.org/plugin/Script_Editor#documentation");
	    }
	});
	menuOptions.add(menuHelp);

	toReturn.add(menuFile);
	toReturn.add(menuEdit);
	toReturn.add(menuTemplate);
	toReturn.add(menuOptions);

	updateRecentFiles();

	return toReturn;
    }

    public void generateButtons(String title)
    {
	int index = tabbedPane.indexOfTab(title);
	JPanel pnlTab = new JPanel(new GridBagLayout());
	pnlTab.setOpaque(false);
	JLabel lblTitle = new JLabel(title);
	JButton btnClose = new JButton("x");

	GridBagConstraints gbc = new GridBagConstraints();
	gbc.gridx = 0;
	gbc.gridy = 0;
	gbc.weightx = 1;

	pnlTab.add(lblTitle, gbc);

	gbc.gridx++;
	gbc.weightx = 0;
	pnlTab.add(btnClose, gbc);

	tabbedPane.setTabComponentAt(index, pnlTab);

	btnClose.addActionListener(new ActionListener()
	{

	    @Override
	    public void actionPerformed(ActionEvent e)
	    {
		Component selected = tabbedPane.getSelectedComponent();
		if (selected != null)
		{
		    // remove the listener before the "remove"
		    if (selected instanceof JButton)
		    {
			((JButton) selected).removeActionListener(this);
		    }
		    tabbedPane.remove(selected);
		}
	    }
	});
	tabbedPane.addTab("+", addPaneButton);
    }
}
