/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 
package org.onaips.vnc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.Uri; 
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity 
{      
	private static final int MENU_QUIT = 0;
	private static final int MENU_HELP = 1;  
	private static final int MENU_ONAIPS = 2;
	private static final int MENU_SENDLOG = 3; 
	private static final int MENU_CHANGELOG = 4;
	private static final int APP_ID = 123;
	private static final String changelog="(i'm looking for someone to write a tutorial for vnc server)<br><br>- DroidX not sliding now (must confirm)<br>- [Fix] Seach was not working in Ctrl<br>- [Add] System notification when client connects";

	private PowerManager.WakeLock wakeLock = null;
	private Timer watchdogTimer=null;

	SharedPreferences preferences;
	ProgressDialog dialog=null;
	AlertDialog startDialog;

 

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState); 

		setContentView(R.layout.main);


		// Initialize preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		if (watchdogTimer!=null)
			watchdogTimer.cancel();


		if (!hasRootPermission())
		{
			Log.v("VNC","You don't have root permissions...!!!");
			showTextOnScreen("You don't have root permissions...Please ROOT your phone first!!!");
			//System.exit(-1);
		}


		showInitialScreen(false);

		boolean serverRunning=isAndroidServerRunning();

		if (("On".equals(preferences.getString("sleep", "Off"))) && serverRunning)
		{
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,"droid VNC");
			wakeLock.acquire();	
		}
		else
		{
			if (wakeLock!=null)
				wakeLock.release();
		}


		// register wifi event receiver
		mReceiver receiver=new mReceiver();
		registerReceiver(receiver,  new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(receiver,  new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(activityReceiver, new IntentFilter("org.onaips.vnc.CLIENTCONNECTED"));
		registerReceiver(activityReceiver, new IntentFilter("org.onaips.vnc.CLIENTDISCONNECTED"));


		setStateLabels(isAndroidServerRunning());

		findViewById(R.id.Button01).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startServerButtonClicked();
				return;
			}
		}) ;
		findViewById(R.id.Button02).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (!isAndroidServerRunning())
				{
					showTextOnScreen("Server is not running");
					return;
				} 

				prepareWatchdog("Stopping server. Please wait...","Couldn't Stop server",false);

				Thread t=new Thread(){
					public void run()
					{
						stopServer();

					}
				};
				t.start();

				return;
			}
		}); 
	}

	public String packageVersion()
	{
		String version = "";
		try {
			PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
			version = pi.versionName;     
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			Log.v("VNC","onOptionsItemSelected: "+ e.getMessage());
		};
		return version;
	}

	public boolean free_version()
	{
		return getPackageName().equals("org.onaips.vnc");
	}

	public void showInitialScreen(boolean forceShow)
	{
		// Initialize preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this );
		SharedPreferences.Editor editor = preferences.edit();

		String version=packageVersion();

		if ((!forceShow) && (version.equals(preferences.getString("version", ""))))
			return;

		editor.putString("version", version);
		editor.commit();

		startDialog = new AlertDialog.Builder(this).create();
		startDialog.setTitle("Version " + version);
		startDialog.setMessage(Html.fromHtml(changelog));
		startDialog.setIcon(R.drawable.icon);

		if (free_version())
		{
			startDialog.setButton(AlertDialog.BUTTON1,"OK", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					startDialog.dismiss();			
				}
			});

			startDialog.setButton2("Donate Version", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.onaips.vnc_donate"));
					startActivity(myIntent);

				} 
			});

			startDialog.show();
		}
		else
			startDialog.show();
	}




	public void showTextOnScreen(final String t)
	{
		runOnUiThread(new Runnable(){
			public void run() {
				Toast.makeText(MainActivity.this,t,Toast.LENGTH_LONG).show();
			}
		});
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);

		menu.add(0, MENU_SENDLOG  ,0,"Report issue");
		menu.add(0, MENU_CHANGELOG,0,"Changelog");
		menu.add(0, MENU_ONAIPS   ,0,"About");
		menu.add(0, MENU_HELP     ,0, "Help");
		menu.add(0, MENU_QUIT     ,0, "Close");

		return true; 
	}

	public void prepareWatchdog(final String s1,final String s2, final boolean running)
	{
		dialog=ProgressDialog.show(MainActivity.this, "",s1, true);

		watchdogTimer=new Timer();
		watchdogTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable(){
					public void run() {
						if (isAndroidServerRunning() == (running==false))
						{
							try {
								dialog.dismiss();	
							}
							catch(IllegalArgumentException a)
							{
								Log.v("VNC","IllegalArgumentException, should be avoided");
							}
							showTextOnScreen(s2);
						}
					}
				});  
			} 
		}, (long)5000);
	}

	public boolean hasExecutable(String s)
	{
		boolean has = true;
		try {
			File exe = new File("/system/bin/" + s);
			if (exe.exists() == false) {
				exe = new File("/system/xbin/" + s);
				if (exe.exists() == false) {
					has = false;
				}
			}
		} catch (Exception e) {
			has = false;
		}

		return has;
	}

	public void setStateLabels(boolean state)
	{
		TextView stateLabel=(TextView)findViewById(R.id.stateLabel);
		stateLabel.setText(state?"Running":"Stopped");
		stateLabel.setTextColor(state?Color.GREEN:Color.RED);

		TextView t=(TextView)findViewById(R.id.TextView01);

		if (state)
		{
			String port=preferences.getString("port", "5901");
			String httpport;
			try
			{
				int port1=Integer.parseInt(port);
				port=String.valueOf(port1);
				httpport=String.valueOf(port1-100);
			}
			catch(NumberFormatException e)
			{
				port="5901";
				httpport="5801";
			}

			String ip=getIpAddress();
			if (ip.equals(""))
				t.setText(Html.fromHtml("Not connected to a network.<br> You can connect through USB with:<br>localhost:" + port + "<br>or<br>http://localhost:" + httpport + "<br>(use adb to forward ports)</font>"));
			else
				t.setText(Html.fromHtml("<font align=\"center\">Connect to:<br>" + ip+":" + port + "<br>or<br>http://" + ip + ":" + httpport + "</font>"));	


		}
		else
			t.setText("");

	}

	public String getIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("VNC", ex.toString());
		}
		return "";
	}


	public void stopServer()
	{
		try{					
			Process sh;

			sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();


			if (hasExecutable("killall"))
			{
				writeCommand(os, "killall androidvncserver");
				writeCommand(os, "killall -KILL androidvncserver");	
			}
			else
			{
				writeCommand(os, getFilesDir().getAbsolutePath() + "/busybox killall androidvncserver");
				writeCommand(os, getFilesDir().getAbsolutePath() + "/busybox killall -KILL androidvncserver");		
			}

			writeCommand(os, "exit");

			os.flush();
			os.close();
			
			//lets clear notifications
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
			mNotificationManager.cancel(APP_ID);
		} catch (IOException e) {
			showTextOnScreen("stopServer()" + e.getMessage());
			Log.v("VNC","stopServer()" + e.getMessage());
		} catch (Exception e) {
			Log.v("VNC","stopServer()" + e.getMessage());
		}	

	} 

	public void startServerButtonClicked()
	{
		if (isAndroidServerRunning())
			showTextOnScreen("Server is already running, stop it first");
		else
		{
			prepareWatchdog("Starting server. Please wait...","Couldn't Start server", true);

			Thread t=new Thread(){
				public void run()
				{
					startServer();
				}
			};
			t.start();
		}
	}


	public void startServer()
	{
		try{					
			Process sh; 

			String password=preferences.getString("password", "");
			String password_check="";
			if (!password.equals(""))
				password_check="-p " + password;


			String rotation=preferences.getString("rotation", "0");
			rotation="-r " + rotation;

			String scaling=preferences.getString("scale", "100");
 
			String scaling_string="";
			if (!scaling.equals("0"))
				scaling_string="-s " + scaling;

			String donate=free_version()?"":" -d ";
			
			String port=preferences.getString("port", "5901");
			try
			{
				int port1=Integer.parseInt(port);
				port=String.valueOf(port1);
			}
			catch(NumberFormatException e)
			{
				port="5901";
			}
			String port_string="-P " + port;


			sh = Runtime.getRuntime().exec("su");
			OutputStream os = sh.getOutputStream();
			writeCommand(os, getFilesDir().getAbsolutePath() + "/androidvncserver "+ password_check + " " + rotation + " " + scaling_string + " " + port_string + donate);


		} catch (IOException e) {
			Log.v("VNC","startServer():" + e.getMessage());
			showTextOnScreen("startServer():" + e.getMessage());
		} catch (Exception e) {
			Log.v("VNC","startServer():" + e.getMessage());
			showTextOnScreen("startServer():" + e.getMessage());
		}	

	}


	public void showHelp()
	{
		new AlertDialog.Builder(this)
		.setTitle("Help")
		.setMessage(Html.fromHtml("Mouse Mappings:<br><br>Right Click -> Back<br>Middle Click -> End Call<br>Left Click -> Touch<br><br>Keyboard Mappings<br><br>" +
				"Home Key -> Home<br>Escape -> Back<br>Page Up ->Menu<br>Left Ctrl -> Search<br>PgDown -> Start Call<br>" +
		"End Key -> End Call<br>F4 -> Rotate<br>F11 -> Disconnect Server<br>F12 -> Stop Server Daemon"))
		.setPositiveButton("Quit", null)
		.setNegativeButton("Open Website", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://onaips.blogspot.com"));
				startActivity(myIntent);
			}
		})
		.show();
	}

	// This method is called once the menu is selected
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// We have only one menu option
		case R.id.preferences:
			// Launch Preference activity
			Intent i = new Intent(MainActivity.this, preferences.class);
			startActivity(i);

			showTextOnScreen("Don't forget to stop/start the server after changes");

			break; 
		case MENU_CHANGELOG:
			showInitialScreen(true);
			break;
		case MENU_QUIT:
			System.exit(1);
			break;
		case MENU_HELP:
			showHelp();
			break;
		case MENU_SENDLOG:
			collectAndSendLog();
			break;
		case MENU_ONAIPS:

			new AlertDialog.Builder(this)
			.setTitle("About")
			.setMessage(Html.fromHtml("version " + packageVersion() + "<br><br>developed by oNaiPs<br><br>Graphics: Sandro Forbice (@sandroforbice)<br><br>Open-Source Software"))
			.setPositiveButton("Close", null)
			.setNegativeButton("Open Website", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://onaips.blogspot.com"));
					startActivity(myIntent);

				}
			})
			.show();
		} 
		return true;
	}

	public boolean isAndroidServerRunning()
	{

		String result="";
		Process sh;
		try {
			if (hasExecutable("ps"))
				sh = Runtime.getRuntime().exec("ps");
			else
				sh = Runtime.getRuntime().exec(getFilesDir().getAbsolutePath() + "/busybox ps w");	

			InputStream is=sh.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line;

			while ((line  = br.readLine()) != null) {
				result+=line;
				if (result.indexOf("androidvncserver")>0)
					return true;
			} 
			OutputStream os = sh.getOutputStream();
			writeCommand(os, "exit");
			os.flush();
			os.close();
		} catch (IOException e) {
			Log.v("VNC"," isAndroidServerRunning():" + e.getMessage());
		} catch (Exception e) {
			Log.v("VNC"," isAndroidServerRunning():" + e.getMessage());
		}

		return false;
	}



	public boolean hasRootPermission() {
		boolean rooted = true;
		try {
			File su = new File("/system/bin/su");
			if (su.exists() == false) {
				su = new File("/system/xbin/su");
				if (su.exists() == false) {
					rooted = false;
				}
			}
		} catch (Exception e) {
			Log.v("VNC", "Can't obtain root - Here is what I know: "+e.getMessage());
			rooted = false;
		}

		return rooted;
	}

	public static final String LOG_COLLECTOR_PACKAGE_NAME = "com.xtralogic.android.logcollector";//$NON-NLS-1$
	public static final String ACTION_SEND_LOG = "com.xtralogic.logcollector.intent.action.SEND_LOG";//$NON-NLS-1$
	public static final String EXTRA_SEND_INTENT_ACTION = "com.xtralogic.logcollector.intent.extra.SEND_INTENT_ACTION";//$NON-NLS-1$
	public static final String EXTRA_DATA = "com.xtralogic.logcollector.intent.extra.DATA";//$NON-NLS-1$
	public static final String EXTRA_ADDITIONAL_INFO = "com.xtralogic.logcollector.intent.extra.ADDITIONAL_INFO";//$NON-NLS-1$
	public static final String EXTRA_SHOW_UI = "com.xtralogic.logcollector.intent.extra.SHOW_UI";//$NON-NLS-1$
	public static final String EXTRA_FILTER_SPECS = "com.xtralogic.logcollector.intent.extra.FILTER_SPECS";//$NON-NLS-1$
	public static final String EXTRA_FORMAT = "com.xtralogic.logcollector.intent.extra.FORMAT";//$NON-NLS-1$
	public static final String EXTRA_BUFFER = "com.xtralogic.logcollector.intent.extra.BUFFER";//$NON-NLS-1$

	void collectAndSendLog(){
		final PackageManager packageManager = getPackageManager();
		final Intent intent = new Intent(ACTION_SEND_LOG);
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		final boolean isInstalled = list.size() > 0;

		if (!isInstalled){
			new AlertDialog.Builder(this)
			.setTitle(getString(R.string.app_name))
			.setIcon(android.R.drawable.ic_dialog_info)
			.setMessage("Please install Log Collector application to collect the device log and send it to dev.")
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int whichButton){
					Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + LOG_COLLECTOR_PACKAGE_NAME));
					marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(marketIntent); 
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
		}
		else{
			new AlertDialog.Builder(this)
			.setTitle(getString(R.string.app_name))
			.setIcon(android.R.drawable.ic_dialog_info)
			.setMessage("Do you want to send a bug report to the dev? Please specify what problem is ocurring.\n\nMake sure you started & stopped the server before submitting")
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int whichButton){
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					intent.putExtra(EXTRA_SEND_INTENT_ACTION, Intent.ACTION_SENDTO);
					final String email = "onaips@gmail.com";
					intent.putExtra(EXTRA_DATA, Uri.parse("mailto:" + email));
					intent.putExtra(EXTRA_ADDITIONAL_INFO,"Problem Description: \n\n\n\n---------DEBUG--------\n" + getString(R.string.device_info_fmt,getVersionNumber(getApplicationContext()),Build.MODEL,Build.VERSION.RELEASE, getFormattedKernelVersion(), Build.DISPLAY));
					intent.putExtra(Intent.EXTRA_SUBJECT, "droid VNC server: Debug Info");
					intent.putExtra(EXTRA_FORMAT, "time");

					//The log can be filtered to contain data relevant only to your app
					String[] filterSpecs = new String[4];
					filterSpecs[0] = "VNC:I";
					filterSpecs[1] = "VNC:D";
					filterSpecs[2] = "VNC:V";
					filterSpecs[3] = "*:S";
					intent.putExtra(EXTRA_FILTER_SPECS, filterSpecs);

					startActivity(intent);
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
		}
	}

	private String getFormattedKernelVersion() 
	{
		String procVersionStr;

		try {
			BufferedReader reader = new BufferedReader(new FileReader("/proc/version"), 256);
			try {
				procVersionStr = reader.readLine();
			} finally {
				reader.close();
			}

			final String PROC_VERSION_REGEX =
				"\\w+\\s+" + /* ignore: Linux */
				"\\w+\\s+" + /* ignore: version */
				"([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
				"\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /* group 2: (xxxxxx@xxxxx.constant) */
				"\\([^)]+\\)\\s+" + /* ignore: (gcc ..) */
				"([^\\s]+)\\s+" + /* group 3: #26 */
				"(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
				"(.+)"; /* group 4: date */

			Pattern p = Pattern.compile(PROC_VERSION_REGEX);
			Matcher m = p.matcher(procVersionStr);

			if (!m.matches()) {
				Log.e("VNC", "Regex did not match on /proc/version: " + procVersionStr);
				return "Unavailable";
			} else if (m.groupCount() < 4) {
				Log.e("VNC", "Regex match on /proc/version only returned " + m.groupCount()
						+ " groups");
				return "Unavailable";
			} else {
				return (new StringBuilder(m.group(1)).append("\n").append(
						m.group(2)).append(" ").append(m.group(3)).append("\n")
						.append(m.group(4))).toString();
			}
		} catch (IOException e) {  
			Log.e("VNC", "IO Exception when getting kernel version for Device Info screen", e);

			return "Unavailable";
		}
	}

	private static String getVersionNumber(Context context) 
	{
		String version = "?";
		try 
		{
			PackageInfo packagInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			version = packagInfo.versionName;
		} 
		catch (PackageManager.NameNotFoundException e){};

		return version;
	}


	static void writeCommand(OutputStream os, String command) throws Exception
	{
		os.write((command + "\n").getBytes("ASCII"));
	}

	class mReceiver extends BroadcastReceiver {
		@Override public void onReceive(Context context, Intent intent) {
			NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			if (info.getType() == ConnectivityManager.TYPE_MOBILE || info.getType()==ConnectivityManager.TYPE_WIFI) {
				setStateLabels(isAndroidServerRunning());
			}
		}
	};

	public BroadcastReceiver activityReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context1, Intent intent) {
			final int HELLO_ID = 1;
			if (intent.getAction().equalsIgnoreCase("org.onaips.vnc.CLIENTCONNECTED"))
			{
				String ns = Context.NOTIFICATION_SERVICE;
				NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

				int icon = R.drawable.icon;
				CharSequence tickerText = "Client connected to VNC server";
				long when = System.currentTimeMillis();

				Notification notification = new Notification(icon, tickerText, when);

				Context context = getApplicationContext();
				CharSequence contentTitle = "Droid VNC Server";
				CharSequence contentText = "Client Connected";
				Intent notificationIntent = new Intent();
				PendingIntent contentIntent = PendingIntent.getActivity(context1, 0, notificationIntent, 0);

				notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);


				mNotificationManager.notify(APP_ID, notification);
				
			}
			else if  (intent.getAction().equalsIgnoreCase("org.onaips.vnc.CLIENTDISCONNECTED"))
			{
				String ns = Context.NOTIFICATION_SERVICE;
				NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
				mNotificationManager.cancel(APP_ID);
			}
		}
	};
}