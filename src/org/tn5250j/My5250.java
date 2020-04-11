/**
 * Title: tn5250J
 * Copyright:   Copyright (c) 2001
 * Company:
 * @author  Kenneth J. Pouncey
 * @version 0.4
 *
 * Description:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307 USA
 *
 * 
 * Changes from Version 0.7.6 done by Germann Ergang 2019
 *
 */
package org.tn5250j;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import org.tn5250j.connectdialog.ConnectDialog;
import org.tn5250j.event.BootEvent;
import org.tn5250j.event.BootListener;
import org.tn5250j.event.EmulatorActionEvent;
import org.tn5250j.event.EmulatorActionListener;
import org.tn5250j.event.SessionChangeEvent;
import org.tn5250j.event.SessionListener;
import org.tn5250j.framework.Tn5250jController;
import org.tn5250j.framework.common.SessionManager;
import org.tn5250j.framework.common.Sessions;
import org.tn5250j.gui.TN5250jSplashScreen;
import org.tn5250j.interfaces.ConfigureFactory;
import org.tn5250j.interfaces.GUIViewInterface;
import org.tn5250j.tools.LangTool;
import org.tn5250j.tools.logging.TN5250jLogFactory;
import org.tn5250j.tools.logging.TN5250jLogger;




public class My5250 implements BootListener, SessionListener, EmulatorActionListener {

	private static final String PARAM_START_SESSION = "-s";
		

	private static BootStrapper strapper = null;
	private static List<GUIViewInterface> frames;
	
	private static Properties sessions = new Properties();
	private static SessionManager manager;

	private TN5250jSplashScreen splash;
	private int step;
	public StringBuilder viewNamesForNextStartBuilder = null;

	protected GUIViewInterface frame1;
	protected String[] sessionArgs = null;
	protected TN5250jLogger log = TN5250jLogFactory.getLogger(this.getClass());

	

	public static void main(String []args){
		new My5250(args);
	}

	
	public My5250 (String[] args) {

		prepare(args);
		if (strapper != null) {
			strapper.addBootListener(this);
		}
		splash = new TN5250jSplashScreen("tn5250jSplash.jpg");
		splash.setSteps(5);
		splash.setVisible(true);

		loadLookAndFeel();

		loadSessions();
		splash.updateProgress(++step);

		initJarPaths();

		initScripting();

		// sets the starting frame type.  At this time there are tabs which is default and Multiple Document Interface.
		// startFrameType();

		frames = new ArrayList<GUIViewInterface>();
		makeFrame1();

		newView();

		setDefaultLocale();
		manager = SessionManager.instance();
		splash.updateProgress(++step);
		Tn5250jController.getCurrent();
		addSessions(args);
	}

	





	/**
	 * we only want to try and load the Nimbus look and feel if it is not
	 * for the MAC operating system.
	 */
	private void loadLookAndFeel() {
		try  {
			for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		}
		catch(Exception e) {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				// we don't care. Cause this should always work.
			}
		}
	}

	/**
	 * if we do not find a running instance and the -d options is
	 * specified, start up the bootstrap daemon to allow checking for running instances
	 * @param args
	 */
	private void checkBootStrapper(String[] args){
		if (!isSpecified("-nc",args)) {
			if (!findBootStrapperDeamon(args)) {
				if (isSpecified("-d",args)) {
					strapper = new BootStrapper();
					strapper.start();
				}
			}
			else {
				exit();
			}
		}
	}
	/**
	 * Check if there are any other instances of tn5250j running
	 */
	private boolean findBootStrapperDeamon (String[] args) {
		try {
			Socket boot = new Socket("localhost", 3036);
			PrintWriter out = new PrintWriter(boot.getOutputStream(), true);
			// parse args into a string to send to the other instance of tn5250j
			String opts = null;
			for (int x = 0;x < args.length; x++) {
				if (opts != null)
					opts += args[x] + " ";
				else
					opts = args[x] + " ";
			}
			out.println(opts);
			out.flush();
			out.close();
			boot.close();
			return true;

		}
		catch (UnknownHostException e) {
			// TODO: Should be logged @ DEBUG level
			//         System.err.println("localhost not known.");
		}
		catch (IOException e) {
			// TODO: Should be logged @ DEBUG level
			//         System.err.println("No other instances of tn5250j running.");
		}
		
		return false;
	}


	public void bootOptionsReceived(BootEvent bootEvent) {
		log.info(" boot options received " + bootEvent.getNewSessionOptions());

		// reload setting, to ensure correct bootstraps
		ConfigureFactory.getInstance().reloadSettings();

		// If the options are not equal to the string 'null' then we have
		//    boot options
		if (!bootEvent.getNewSessionOptions().equals("null")) {
			// check if a session parameter is specified on the command line
			String[] args = new String[TN5250jConstants.NUM_PARMS];
			parseArgs(bootEvent.getNewSessionOptions(), args);

			if (isSpecified("-s",args)) {
				String sd = getParm("-s",args);
				if (sessions.containsKey(sd)) {
					parseArgs(sessions.getProperty(sd), args);
					final String[] args2 = args;
					final String sd2 = sd;
					runLater(sd2,args2);
				}
			}
			else {
				if (args[0].startsWith("-")) {
					runLater(null,null);
				}
				else {
					final String[] args2 = args;
					final String sd2 = args[0];
					runLater(sd2,args2);runLater(sd2,args2);
				}
			}
		}
		else {
			runLater(null,null);
		}
	}

	
	private void runLater(final String sd2, final String[] args2){
		SwingUtilities.invokeLater(new Runnable () {
	        @Override
	        public void run () {
	        	if(sd2!=null)  {
	        		newSession(sd2,args2);
	        	}else{
	        		startNewSession();
	        	}
	        }
	    });
	}



	private void checkFrameSize(String[] args){
		if (args.length > 0) {
			if (isSpecified("-width",args) ||
					isSpecified("-height",args)) {
				int width =  frame1.getWidth();
				int height = frame1.getHeight();

				if (isSpecified("-width",args)) {
					width = Integer.parseInt(getParm("-width",args));
				}
				if (isSpecified("-height",args)) {
					height = Integer.parseInt(getParm("-height",args));
				}
				frame1.setSize(width,height);
				frame1.centerFrame();
			}
		}
	}

	private void checkLocale(String[] args){
		if (args.length>0 && args[0].startsWith("-")) {
			if (isSpecified("-s",args)) {
				String sd = getParm("-s",args);
				if (sessions.containsKey(sd)) {
					sessions.setProperty("emul.default",sd);
				}
			}
			// check if a locale parameter is specified on the command line
			if (isSpecified("-L",args)) {
				Locale.setDefault(parseLocal(getParm("-L",args)));
			}
		}
		LangTool.init(); 
	}




	/**
	 * adds connections from sessions list in user.home  (C:\Users\(user)\.tn5250j\sessions) see GlobalConfigure class
	 * and open a new session so splash screen goes away 
	 * @param args 
	 * @param args can be empty 
	 */
	private void addSessions(String[] args){
		List<String> lastViewNames = new ArrayList<String>();
		lastViewNames.addAll(loadLastSessionViewNames());
		if(args!=null){
			lastViewNames.addAll(loadLastSessionViewNamesFrom(args));
		}
		lastViewNames = filterExistingViewNames(lastViewNames);

		if (lastViewNames.size() > 0) {
			insertDefaultSessionIfConfigured(lastViewNames);
			startSessionsFromList(this, lastViewNames);
			if (sessions.containsKey("emul.showConnectDialog")) {
				openConnectSessionDialogAndStartSelectedSession();
			}
		}
		else {
			startNewSession();
		}
	}

	private void startSessionsFromList(My5250 m, List<String> lastViewNames) {
		for (int i=0; i<lastViewNames.size(); i++) {
			String viewName = lastViewNames.get(i);
			if (!frame1.isVisible()) {
				setFrameVisible(true);
			}
			sessionArgs = new String[TN5250jConstants.NUM_PARMS];
			parseArgs(sessions.getProperty(viewName),m.sessionArgs);
			newSession(viewName, m.sessionArgs);
		}
	}

	private void insertDefaultSessionIfConfigured(List<String> lastViewNames) {
		if (getDefaultSession() != null && !lastViewNames.contains(getDefaultSession())) {
			lastViewNames.add(0, getDefaultSession());
		}
	}
	/**
	 * keep accessible from package for testing
	 */
	protected static  List<String> loadLastSessionViewNamesFrom(String[] commandLineArgs) {
		List<String> sessionNames = new ArrayList<String>();
		boolean foundRightParam = false;
		for (String arg : commandLineArgs) {
			if (foundRightParam && !PARAM_START_SESSION.equals(arg)) {
				sessionNames.add(arg);
			}
			foundRightParam = PARAM_START_SESSION.equals(arg);
		}
		return sessionNames;
	}
	/**
	 * keep accessible from package for testing
	 */
	protected static  List<String> loadLastSessionViewNames() {
		List<String> sessionNames = new ArrayList<String>();
		if (sessions.containsKey("emul.startLastView")) {
			String emulview = sessions.getProperty("emul.view", "");
			int idxstart = 0;
			int idxend = emulview.indexOf(PARAM_START_SESSION, idxstart);
			for (; idxend > -1; idxend = emulview.indexOf(PARAM_START_SESSION, idxstart)) {
				String sessname = emulview.substring(idxstart, idxend).trim();
				if (sessname.length() > 0) {
					sessionNames.add(sessname);
				}
				idxstart = idxend + PARAM_START_SESSION.length();
			}
			if (idxstart + PARAM_START_SESSION.length() < emulview.length()) {
				String sessname = emulview.substring(idxstart + PARAM_START_SESSION.length() - 1).trim();
				if (sessname.length() > 0) {
					sessionNames.add(sessname);
				}
			}
		}
		return sessionNames;
	}
	/**
	 * keep accessible from package for testing
	 */
	protected static  List<String> filterExistingViewNames(List<String> lastViewNames) {
		List<String> result = new ArrayList<String>();
		for (String viewName : lastViewNames) {
			if (sessions.containsKey(viewName)) {
				result.add(viewName);
			}
		}
		return result;
	}

	public boolean containsNotOnlyNullValues(String[] stringArray) {
		if (stringArray != null) {
			for (String s : stringArray) {
				if (s != null) {
					return true;
				}
			}
		}
		return false;
	}

	private void setDefaultLocale () {

		if (sessions.containsKey("emul.locale")) {
			Locale.setDefault(parseLocal(sessions.getProperty("emul.locale")));
		}

	}

	private String getParm(String parm, String[] args) {

		for (int x = 0; x < args.length; x++) {

			if (args[x].equals(parm))
				return args[x+1];

		}
		return null;
	}

	private boolean isSpecified(String parm, String[] args) {

		if (args == null)
			return false;

		for (int x = 0; x < args.length; x++) {

			if (args[x] != null && args[x].equals(parm))
				return true;

		}
		return false;
	}


	public String getDefaultSession() {
		String defaultSession = sessions.getProperty("emul.default");
		if (defaultSession != null && !defaultSession.trim().isEmpty()) {
			return defaultSession;
		}
		return null;
	}

	/**
	 * this was also called via ActionListener from 
	 * newSession() ->
	 * (sessionPanel).addEmulatorActionListener(this) -> 
	 * SessionPopup ("popup.connections") in Line 532-> 
	 * SessionPanel startNewSession()-> 
	 * my5250.onEmulatorAction(START_NEW_SESSION) -> my5250.startNewSession()  
	 * this last part has been changed to my5250.onEmulatorAction(CONNECTIONDIALOG) -> openConnectSessionDialogAndStartSelectedSession();
	 */
	public void startNewSession() {
		String sel = "";
		if (containsNotOnlyNullValues(sessionArgs) && !sessionArgs[0].startsWith("-")) {
			sel = sessionArgs[0];
		} else {
			sel = getDefaultSession();
		}
		openSelectedSession(sel);
	}

	public void openSelectedSession(String sel){	
		Sessions sess = manager.getSessions();
		if (sel != null && sess.getCount() == 0 && sessions.containsKey(sel)) {
			sessionArgs = new String[TN5250jConstants.NUM_PARMS];
			parseArgs(sessions.getProperty(sel), sessionArgs);
		}

		if (sessionArgs == null || sess.getCount() > 0 || sessions.containsKey("emul.showConnectDialog")) {
			openConnectSessionDialogAndStartSelectedSession();
		} else {
			newSession(sel, sessionArgs);
		}
	}




	/**
	 * Connect a session with a given session name <br>
	 * get session name from GUI Dialog
	 */
	public void openConnectSessionDialogAndStartSelectedSession() {
		String sel = openConnectSessionDialog();
		Sessions sess = manager.getSessions();
		if (sel != null) {
			openConnectSessionName(sel);
		} else {
			if (sess.getCount() == 0){
				//exit();
			}
		}
	}


	private void startDuplicateSession(SessionPanel ses) {
		loadSessions();
		if (ses == null) {
			Sessions sess = manager.getSessions();
			for (int x = 0; x < sess.getCount(); x++) {
				if ((sess.item(x).getGUI()).isVisible()) {
					ses = sess.item(x).getGUI();
					break;
				}
			}
		}
		openConnectSessionName(ses.getSessionName());
	}


	/**
	 * Code put into own method.<br>
	 */
	public void openConnectSessionName(String sessionName){
		if(sessionName == null) {
			return ;
		}
		sessionName=sessionName.trim().toUpperCase();
		String sessionProperties = sessions.getProperty(sessionName);
		if (sessionProperties ==null) {
			log.debug(sessionName + " not found in session list.");
		} else {
			sessionArgs = new String[TN5250jConstants.NUM_PARMS];
			parseArgs(sessionProperties, sessionArgs);
			newSession(sessionName, sessionArgs);
		}
	}


	private String openConnectSessionDialog () {

		splash.setVisible(false);
		ConnectDialog sc = new ConnectDialog(frame1,LangTool.getString("ss.title"),sessions);

		// load the new session information from the session property file
		loadSessions();
		return sc.getConnectKey();
	}


	/**
	 * @param sel  system name
	 * @param args the args from this instance
	 */
	public synchronized void newSession(String sel,String[] args) {

		Properties sesProps = new Properties();

		String propFileName = null;
		String session = args[0];

		// Start loading properties
		sesProps.put(TN5250jConstants.SESSION_HOST,session);

		if (isSpecified("-e",args))
			sesProps.put(TN5250jConstants.SESSION_TN_ENHANCED,"1");

		String port = null;
		if (isSpecified("-p",args)) {
			port = getParm("-p",args);
			sesProps.put(TN5250jConstants.SESSION_HOST_PORT,port);
		}

		if (isSpecified("-f",args))
			propFileName = getParm("-f",args);

		if (isSpecified("-cp",args))
			sesProps.put(TN5250jConstants.SESSION_CODE_PAGE ,getParm("-cp",args));

		if (isSpecified("-gui",args))
			sesProps.put(TN5250jConstants.SESSION_USE_GUI,"1");

		if (isSpecified("-t", args))
			sesProps.put(TN5250jConstants.SESSION_TERM_NAME_SYSTEM, "1");

		if (isSpecified("-132",args))
			sesProps.put(TN5250jConstants.SESSION_SCREEN_SIZE,TN5250jConstants.SCREEN_SIZE_27X132_STR);
		else
			sesProps.put(TN5250jConstants.SESSION_SCREEN_SIZE,TN5250jConstants.SCREEN_SIZE_24X80_STR);

		// are we to use a socks proxy
		if (isSpecified("-usp",args)) {

			// socks proxy host argument
			if (isSpecified("-sph",args)) {
				sesProps.put(TN5250jConstants.SESSION_PROXY_HOST ,getParm("-sph",args));
			}

			// socks proxy port argument
			if (isSpecified("-spp",args))
				sesProps.put(TN5250jConstants.SESSION_PROXY_PORT ,getParm("-spp",args));
		}

		
		String connectionType=TN5250jConstants.SSL_TYPE_NONE;
		if (isSpecified("-sslType",args)) {
			connectionType = getParm("-sslType",args);
			sesProps.put(TN5250jConstants.SSL_TYPE, connectionType);
		}
		
		if(ConnectDialog.isEnforceTls() && connectionType.equals(TN5250jConstants.SSL_TYPE_NONE)){
			sesProps.put(TN5250jConstants.SSL_TYPE, TN5250jConstants.SSL_TYPE_TLS);
			sesProps.put(TN5250jConstants.SESSION_HOST_PORT,"992");
			log.info("Encryption setting of NONE was overwritten to TLS on Port 992.");
		}
		
		/*
		 * test for always unencrypted connection:
		 * */
		//sesProps.put(TN5250jConstants.SSL_TYPE, TN5250jConstants.SSL_TYPE_NONE);
		

		// check if device name is specified
		if (isSpecified("-dn=hostname",args)){
			String dnParam;

			// use IP address as device name
			try{
				dnParam = InetAddress.getLocalHost().getHostName();
			}
			catch(UnknownHostException uhe){
				dnParam = "UNKNOWN_HOST";
			}

			sesProps.put(TN5250jConstants.SESSION_DEVICE_NAME ,dnParam);
		}
		else if (isSpecified("-dn",args)){

			sesProps.put(TN5250jConstants.SESSION_DEVICE_NAME ,getParm("-dn",args));
		}

		if (isSpecified("-hb",args))
			sesProps.put(TN5250jConstants.SESSION_HEART_BEAT,"1");

		int sessionCount = manager.getSessions().getCount();

		Session5250 s2 = manager.openSession(sesProps,propFileName,sel);
		SessionPanel s = new SessionPanel(s2);


		if (!frame1.isVisible()) {
			// Here we check if this is the first session created in the system.
						//  We have to create a frame on initialization for use in other scenarios
						//  so if this is the first session being added in the system then we
						//  use the frame that is created and skip the part of creating a new
						//  view which would increment the count and leave us with an unused
						//  frame.
			if (isSpecified("-noembed",args) && sessionCount > 0) {
				newView();
			}
			setFrameVisible(true);
		}
		else {
			if (isSpecified("-noembed",args)) {
				newView();
				setFrameVisible(true);

			}
		}

		if (isSpecified("-t",args)){
			frame1.addSessionView(sel,s);
		}
		else{
			frame1.addSessionView(session,s);
		}

		s.connect();
		s.addEmulatorActionListener(this); 
	}


	public void setFrameVisible(boolean increment){
		if(increment)splash.updateProgress(++step);
		splash.setVisible(false);
		frame1.setVisible(true);
		frame1.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}


	private void newView() {

		// we will now to default the frame size to take over the whole screen
		//    this is per unanimous vote of the user base
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		int width = screenSize.width;
		int height = screenSize.height;

		if (sessions.containsKey("emul.width"))
			width = Integer.parseInt(sessions.getProperty("emul.width"));
		if (sessions.containsKey("emul.height"))
			height = Integer.parseInt(sessions.getProperty("emul.height"));


		frame1.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		if (sessions.containsKey("emul.frame" + frame1.getFrameSequence())) {
			String location = sessions.getProperty("emul.frame" + frame1.getFrameSequence());
			//         System.out.println(location + " seq > " + frame1.getFrameSequence() );
			restoreFrame(frame1,location);
		}
		else {
			frame1.setSize(width,height);
			frame1.centerFrame();
		}

		frames.add(frame1);

	}

	
	public void makeFrame1(){
		Gui5250Frame.addTlsHintToFrameName(ConnectDialog.isEnforceTls());
		frame1 = new Gui5250Frame(this);	
	}

	private void restoreFrame(GUIViewInterface frame,String location) {

		StringTokenizer tokenizer = new StringTokenizer(location, ",");
		int x = Integer.parseInt(tokenizer.nextToken());
		int y = Integer.parseInt(tokenizer.nextToken());
		int width = Integer.parseInt(tokenizer.nextToken());
		int height = Integer.parseInt(tokenizer.nextToken());

		frame.setLocation(x,y);
		frame.setSize(width,height);
	}

	protected void closingDown(GUIViewInterface view) {
		Sessions sess = manager.getSessions();
		if (log.isDebugEnabled()) {
			log.debug("number of active sessions we have " + sess.getCount());
		}
		if (viewNamesForNextStartBuilder == null) {
			// preserve sessions for next boot
			viewNamesForNextStartBuilder = new StringBuilder();
		}
		closeSessionsInternal(view);

		sessions.setProperty("emul.frame" + view.getFrameSequence(),
				view.getX() + "," +
						view.getY() + "," +
						view.getWidth() + "," +
						view.getHeight());

		frames.remove(view);
		view.dispose();

		if (log.isDebugEnabled()) {
			log.debug("number of active sessions we have after shutting down " + sess.getCount());
		}

		log.info("view settings " + viewNamesForNextStartBuilder);

		if (sess.getCount() == 0) {
			sessions.setProperty("emul.width",Integer.toString(view.getWidth()));
			sessions.setProperty("emul.height",Integer.toString(view.getHeight()));
			sessions.setProperty("emul.view",viewNamesForNextStartBuilder.toString());

			// save off the session settings before closing down
			ConfigureFactory.getInstance().saveSettings(ConfigureFactory.SESSIONS,
					ConfigureFactory.SESSIONS,
					"------ Defaults --------");
			if (strapper != null) {
				strapper.interrupt();
			}
		}

		exit();
	}



	private void parseArgs(String theStringList, String[] s) {
		int x = 0;
		StringTokenizer tokenizer = new StringTokenizer(theStringList, " ");
		while (tokenizer.hasMoreTokens()) {
			s[x++] = tokenizer.nextToken();
		}
	}

	private Locale parseLocal(String localString) {
		int x = 0;
		String[] s = {"","",""};
		StringTokenizer tokenizer = new StringTokenizer(localString, "_");
		while (tokenizer.hasMoreTokens()) {
			s[x++] = tokenizer.nextToken();
		}
		return new Locale(s[0],s[1],s[2]);
	}

	/**
	 * Initialise ConfigureFactory if not yet done.<br>
	 * ConfigureFactory initialises GlobalConfigure and<br>
	 * GlobalConfigure then loads the "sessions" file in user.home into the sessions property.<br>
	 * ConfigureFactory.getInstance() returns the GlobalConfigure object.
	 */
	private void loadSessions() {
		sessions = (ConfigureFactory.getInstance()).getProperties(ConfigureFactory.SESSIONS);
		return;
	}

	/**
	 * used by Session5250
	 */
	@Override
	public void onSessionChanged(SessionChangeEvent changeEvent) {
		Session5250 ses5250 = (Session5250)changeEvent.getSource();
		SessionPanel ses = ses5250.getGUI();

		switch (changeEvent.getState()) {
		case TN5250jConstants.STATE_REMOVE:
			closeSessionInternal(ses);
			break;
		}
	}

	/**
	 * used by SessionPanel
	 */
	@Override
	public void onEmulatorAction(EmulatorActionEvent actionEvent) {

		SessionPanel ses = (SessionPanel)actionEvent.getSource();

		switch (actionEvent.getAction()) {
		case EmulatorActionEvent.CLOSE_SESSION:
			closeSessionInternal(ses);
			break;
		case EmulatorActionEvent.CLOSE_EMULATOR:
			throw new UnsupportedOperationException("Not yet implemented!");
		case EmulatorActionEvent.START_NEW_SESSION:
			startNewSession();
			break;
		case EmulatorActionEvent.START_DUPLICATE:
			startDuplicateSession(ses);
			break;
		case EmulatorActionEvent.FIND_AND_OPEN_EXISTING_SESSION:
			openConnectSessionDialogAndStartSelectedSession();
			break;
		}
	}

	
	private GUIViewInterface getParentView(SessionPanel session) {
		GUIViewInterface f = null;

		for (int x = 0; x < frames.size(); x++) {
			f = frames.get(x);
			if (f.containsSession(session))
				return f;
		}
		return null;
	}

	/**
	 * Initializes the scripting environment if the jython interpreter exists
	 * in the classpath
	 */
	private void initScripting() {

		try {
			Class.forName("org.tn5250j.scripting.JPythonInterpreterDriver");
		}
		catch (java.lang.NoClassDefFoundError ncdfe) {
			log.warn("Information Message: Can not find scripting support"
					+ " files, scripting will not be available: "
					+ "Failed to load interpreter drivers " + ncdfe);
		}
		catch (Exception ex) {
			log.warn("Information Message: Can not find scripting support"
					+ " files, scripting will not be available: "
					+ "Failed to load interpreter drivers " + ex);
		}

		splash.updateProgress(++step);

	}

	/**
	 * Sets the jar path for the available jars.
	 * Sets the python.path system variable to make the jython jar available
	 * to scripting process.
	 *
	 * This needs to be rewritten to loop through and obtain all jars in the
	 * user directory.  Maybe also additional paths to search.
	 */
	private void initJarPaths() {

		String jarClassPaths = System.getProperty("python.path")
				+ File.pathSeparator + "jython.jar"
				+ File.pathSeparator + "jythonlib.jar"
				+ File.pathSeparator + "jt400.jar"
				+ File.pathSeparator + "itext.jar";

		if (sessions.containsKey("emul.scriptClassPath")) {
			jarClassPaths += File.pathSeparator + sessions.getProperty("emul.scriptClassPath");
		}

		System.setProperty("python.path",jarClassPaths);

		splash.updateProgress(++step);

	}




	public static SessionManager getSessionManager(){
		return manager;
	}
	
	
	
	
	
	
	
	//***changes from here***********************************************/



	private void prepare(String[] args){
		if(args==null) args = new String[]{};
		checkBootStrapper(args);
		checkFrameSize(args);
		checkLocale(args);
	}


	/**
	 * Logic changed to avoid buffer overflow. <br>
	 */
	private void closeSessionsInternal(GUIViewInterface view){
		int count = view.getSessionViewCount()-1;
		for(int i=count;i>-1;i--){
			SessionPanel sesspanel = view.getSessionAt(i);
			viewNamesForNextStartBuilder.append("-s ")
			.append(sesspanel.getSessionName())
			.append(" ");
			closeSessionInternal(sesspanel);
		}
		log.info("view.getSessionViewCount() = " + view.getSessionViewCount());
	}
	
	private void closeSessionInternal(SessionPanel sesspanel) {
		try{
			GUIViewInterface f = getParentView(sesspanel);
			if (f != null) {
				Sessions sess = manager.getSessions();
				if ((sess.item(sesspanel.getSession())) != null) {
					f.removeSessionView(sesspanel);
				}
			}
			manager.closeSession(sesspanel);  
			if (manager.getSessions().getCount() < 1) {
				closingDown(f);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}



	public void exit(){
		disconnectAll();
		System.exit(0);
	}

	public void disconnectAll(){
		Sessions sess = manager.getSessions();
		for (int x = 0; x < sess.getCount(); x++) {
			try{
				Session5250 s5250= sess.item(x);
				s5250.disconnect();
			}catch(Exception e){
				log.debug("Error closing a tn5250j session : " + e.getMessage());
			}
		}
	}

	public static void setEnforceTLS(boolean b){
		ConnectDialog.setEnforceTls(b);
	}
	public static Properties getSessions(){
		return sessions;
	}

	

}
