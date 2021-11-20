import java.io.IOException;
import java.util.Vector;

import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

import cc.nnproject.json.AbstractJSON;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import cc.nnproject.ytapp.App2;
import models.AbstractModel;
import models.ChannelModel;
import models.ILoader;
import models.VideoModel;
import ui.ModelForm;
import ui.Settings;
import ui.VideoForm;

public class App implements CommandListener, Constants {
	
	// Settings
	public static String videoRes;
	public static String region;
	public static int watchMethod; // 0 - platform request 1 - mmapi player
	public static String downloadDir;
	public static String serverstream = streamphp;
	public static boolean videoPreviews;
	public static boolean searchChannels;
	public static boolean rememberSearch;
	public static boolean httpStream;
	public static int startScreen; // 0 - Trends 1 - Popular
	public static String inv = iteroni;
	public static boolean customItems;
	public static String imgproxy = hproxy;
	
	public static App inst;
	public static App2 midlet;
	
	private Form mainForm;
	private Form searchForm;
	public Settings settingsForm;
	//private TextField searchText;
	//private StringItem searchBtn;
	private VideoForm videoForm;
	private Item loadingItem;
	private static PlayerCanvas playerCanv;

	public static boolean asyncLoading;
	
	private Object lazyLoadLock = new Object();
	private LoaderThread t0;
	private LoaderThread t1;
	private LoaderThread t2;
	private Vector v0;
	private Vector v1;
	private Vector v2;
	private Object addLock = new Object();
	
	public static int width;
	public static int height;

	public void startApp() {
		region = System.getProperty("user.country");
		if(region == null) {
			region = System.getProperty("microedition.locale");
			if(region == null) {
				region = "US";
			} else if(region.length() == 5) {
				region = region.substring(3, 5);
			} else if(region.length() > 2) {
				region = region.substring(0, 2);
			} else {
				region = "US";
			}
		} else if(region.length() > 2) {
			region = region.substring(0, 2);
		}
		region = region.toUpperCase();
		v0 = new Vector();
		testCanvas();
		initForm();
		Settings.loadConfig();
		if(!Settings.isLowEndDevice() && asyncLoading) {
			v1 = new Vector();
			v2 = new Vector();
			t0 = new LoaderThread(5, lazyLoadLock, v0, addLock);
			t1 = new LoaderThread(5, lazyLoadLock, v1, addLock);
			t2 = new LoaderThread(5, lazyLoadLock, v2, addLock);
			t0.start();
			t1.start();
			t2.start();
		} else {
			t0 = new LoaderThread(5, lazyLoadLock, v0, addLock);
			t0.start();
		}
		try {
			loadingItem = new StringItem(null, Locale.s(TITLE_Loading));
			loadingItem.setLayout(Item.LAYOUT_CENTER);
			mainForm.append(loadingItem);
			if(startScreen == 0) {
				loadTrends();
			} else {
				loadPopular();
			}
			gc();
		} catch (InvidiousException e) {
			error(this, Errors.App_startApp_load, e + "\n JSON: \n" + e.getJSON());
		} catch (OutOfMemoryError e) {
			gc();
			error(this, Errors.App_startApp_load, "Out of memory!");
		} catch (Throwable e) {
			e.printStackTrace();
			error(this, Errors.App_startApp_load, e);
		}
	}

	private void initForm() {
		mainForm = new Form(NAME);
		
		/*
		searchText = new TextField("", "", 256, TextField.ANY);
		searchText.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_2);
		mainForm.append(searchText);
		searchBtn = new StringItem(null, "Поиск", StringItem.BUTTON);
		searchBtn.setLayout(Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_RIGHT | Item.LAYOUT_2);
		searchBtn.addCommand(searchCmd);
		searchBtn.setDefaultCommand(searchCmd);
		mainForm.append(searchBtn);
		*/
		mainForm.setCommandListener(this);
		mainForm.addCommand(searchCmd);
		mainForm.addCommand(idCmd);
		mainForm.addCommand(settingsCmd);
		mainForm.addCommand(exitCmd);
		display(mainForm);
	}
	
	public static byte[] hproxy(String s) throws IOException {
		if(s.startsWith("/")) return Util.get(iteroni + s.substring(1));
		if(imgproxy == null || imgproxy.length() <= 1) return Util.get(s);
		if(s.indexOf("ggpht.com") != -1) return Util.get(Util.replace(s, "https:", "http:"));
		return Util.get(imgproxy + Util.url(s));
	}

	public static AbstractJSON invApi(String s) throws InvidiousException, IOException {
		if(!s.endsWith("?")) s = s.concat("&");
		s = s.concat("region=" + region);
		s = Util.getUtf(inv + "api/" + s);
		AbstractJSON res;
		if(s.charAt(0) == '{') {
			res = JSON.getObject(s);
			if(((JSONObject) res).has("code")) {
				System.out.println(res.toString());
				throw new InvidiousException((JSONObject) res, ((JSONObject) res).getString("code") + ": " + ((JSONObject) res).getNullableString("message"));
			}
			if(((JSONObject) res).has("error")) {
				System.out.println(res.toString());
				throw new InvidiousException((JSONObject) res);
			}
		} else {
			res = JSON.getArray(s);
		}
		return res;
	}

	private void loadTrends() {
		mainForm.addCommand(switchToPopularCmd);
		try {
			mainForm.setTitle(NAME + " - " + Locale.s(TITLE_Trends));
			JSONArray j = (JSONArray) invApi("v1/trending?fields=" + TRENDING_FIELDS + (videoPreviews ? ",videoThumbnails" : ""));
			try {
				if(mainForm.get(0) == loadingItem) {
					mainForm.delete(0);
				}
			} catch (Exception e) {
			}
			int l = j.size();
			for(int i = 0; i < l; i++) {
				Item item = parseAndMakeItem(j.getObject(i), false);
				if(item == null) continue;
				mainForm.append(item);
				if(i >= TRENDS_LIMIT) break;
			}
			notifyAsyncTasks();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			error(this, Errors.App_loadTrends, e);
		}
	}
	
	private void loadPopular() {
		mainForm.addCommand(switchToTrendsCmd);
		try {
			mainForm.setTitle(NAME + " - " + Locale.s(TITLE_Popular));
			JSONArray j = (JSONArray) invApi("v1/popular?fields=" + TRENDING_FIELDS + (videoPreviews ? ",videoThumbnails" : ""));
			try {
				if(mainForm.get(0) == loadingItem) {
					mainForm.delete(0);
				}
			} catch (Exception e) {
			}
			int l = j.size();
			for(int i = 0; i < l; i++) {
				Item item = parseAndMakeItem(j.getObject(i), false);
				if(item == null) continue;
				mainForm.append(item);
				if(i >= TRENDS_LIMIT) break;
			}
			notifyAsyncTasks();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			error(this, Errors.App_loadPopular, e);
		}
	}

	private void search(String q) {
		searchForm = new Form(NAME + " - " + Locale.s(TITLE_SearchQuery));
		searchForm.setCommandListener(this);
		searchForm.addCommand(backCmd);
		searchForm.addCommand(settingsCmd);
		searchForm.addCommand(searchCmd);
		display(searchForm);
		stopDoingAsyncTasks();
		try {
			JSONArray j = (JSONArray) invApi("v1/search?q=" + Util.url(q) + (searchChannels ? "&type=all" : ""));
			int l = j.size();
			for(int i = 0; i < l; i++) {
				Item item = parseAndMakeItem(j.getObject(i), true);
				if(item == null) continue;
				searchForm.append(item);
				if(i >= SEARCH_LIMIT) break;
			}
			notifyAsyncTasks();
		} catch (Exception e) {
			e.printStackTrace();
			error(this, Errors.App_search, e);
		}
	}
	
	private Item parseAndMakeItem(JSONObject j, boolean search) {
		String type = j.getNullableString("type");
		if(type == null) {
			// video
			VideoModel v = new VideoModel(j);
			if(search) v.setFromSearch();
			if(videoPreviews) addAsyncLoad(v);
			return v.makeItemForList();
		}
		if(type.equals("video")) {
			VideoModel v = new VideoModel(j);
			if(search) v.setFromSearch();
			if(videoPreviews) addAsyncLoad(v);
			return v.makeItemForList();
		}
		if(searchChannels && type.equals("channel")) {
			ChannelModel c = new ChannelModel(j);
			if(search) c.setFromSearch();
			if(videoPreviews) addAsyncLoad(c);
			return c.makeItemForList();
		}
		return null;
	}

	private void openVideo(String id) {
		final String https = "https://";
		final String ytshort = "youtu.be/";
		final String www = "www.";
		final String watch = "youtube.com/watch?v=";
		if(id.startsWith(https)) id = id.substring(https.length());
		if(id.startsWith(ytshort)) id = id.substring(ytshort.length());
		if(id.startsWith(www)) id = id.substring(www.length());
		if(id.startsWith(watch)) id = id.substring(watch.length());
		try {
			open(new VideoModel(id).extend());
		} catch (Exception e) {
			error(this, Errors.App_openVideo, e);
		}
	}

	static JSONObject getVideoInfo(String id, String res) throws JSONException, IOException {
		JSONObject j = (JSONObject) invApi("v1/videos/"  + id + "?fields=formatStreams");
		JSONArray arr = j.getArray("formatStreams");
		if(j.size() == 0) {
			throw new RuntimeException("failed to get link for video: " + id);
		}
		JSONObject _144 = null;
		JSONObject _360 = null;
		JSONObject _720 = null;
		JSONObject other = null;
		int l = arr.size();
		for(int i = 0; i < l; i++) {
			JSONObject o = arr.getObject(i);
			String q = o.getString("qualityLabel");
			if(q.startsWith("720p")) {
				_720 = o;
			} else if(q.startsWith("360p")) {
				_360 = o;
			} else if(q.startsWith("144p")) {
				_144 = o;
			} else {
				other = o;
			}
		}
		JSONObject o = null;
		if(res == null) {
			if(_360 != null) {
				o = _360;
			} else if(other != null) {
				o = other;
			} else if(_144 != null) {
				o = _144;
			} 
		} else if(res.equals("144p")) {
			if(_144 != null) {
				o = _144;
			} else if(_360 != null) {
				o = _360;
			} else if(other != null) {
				o = other;
			}
		} else if(res.equals("360p")) {
			if(_360 != null) {
				o = _360;
			} else if(other != null) {
				o = other;
			} else if(_144 != null) {
				o = _144;
			} 
		} else if(res.equals("720p")) {
			if(_720 != null) {
				o = _720;
			} else if(_360 != null) {
				o = _360;
			} else if(other != null) {
				o = other;
			} else if(_144 != null) {
				o = _144;
			} 
		}
		return o;
	}

	public static String getVideoLink(String id, String res) throws JSONException, IOException {
		JSONObject o = getVideoInfo(id, res);
		String s = o.getString("url");
		if(httpStream) {
			s = serverstream + "?url=" + Util.url(s);
		}
		return s;
	}

	public static void open(AbstractModel model) {
		open(model, null);
	}

	public static void open(AbstractModel model, Form formContainer) {
		App app = inst;
		if(model.isFromSearch() && !rememberSearch) {
			app.disposeSearchForm();
		}
		app.stopDoingAsyncTasks();
		ModelForm form = model.makeForm();
		display(form);
		if(form instanceof VideoForm) {
			app.videoForm = (VideoForm) form;
		}
		if(formContainer != null) {
			form.setFormContainer(formContainer);
		}
		gc();
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
		app.addAsyncLoad(form);
		app.notifyAsyncTasks();
	}
	
	public static void download(final String id) {
		Downloader d = new Downloader(id, videoRes, inst.videoForm, downloadDir);
		d.start();
	}
	
	public static void watch(String id) {
		// TODO other variants
		try {
			String url = getVideoLink(id, videoRes);
			switch(watchMethod) {
			case 0: {
				platReq(url);
				break;
			}
			case 1: {
				Player p = Manager.createPlayer(url);
				playerCanv = new PlayerCanvas(p);
				display(playerCanv);
				playerCanv.init();
				break;
			}
			}
		} catch (Exception e) {
			e.printStackTrace();
			error(null, Errors.App_watch, e);
		}
	}

	public static void back(Form f) {
		if(f instanceof ModelForm && ((ModelForm)f).getModel().isFromSearch() && inst.searchForm != null) {
			App.display(inst.searchForm);
		} else {
			App.display(inst.mainForm);
		}
	}

	public void commandAction(Command c, Displayable d) {
		if(c == exitCmd) {
			midlet.notifyDestroyed();
		}
		if(c == settingsCmd) {
			stopDoingAsyncTasks();
			if(settingsForm == null) {
				settingsForm = new Settings();
			}
			display(settingsForm);
			settingsForm.show();
		}
		if(c == searchCmd && d instanceof Form) {
			stopDoingAsyncTasks();
			if(searchForm != null) {
				disposeSearchForm();
			}
			TextBox t = new TextBox("", "", 256, TextField.ANY);
			t.setCommandListener(this);
			t.setTitle(Locale.s(CMD_Search));
			t.addCommand(searchOkCmd);
			t.addCommand(cancelCmd);
			display(t);
		}
		if(c == idCmd && d instanceof Form) {
			stopDoingAsyncTasks();
			TextBox t = new TextBox("", "", 256, TextField.ANY);
			t.setCommandListener(this);
			t.setTitle("Video URL or ID");
			t.addCommand(goCmd);
			t.addCommand(cancelCmd);
			display(t);
		}
		/*if(c == browserCmd) {
			try {
				platReq(getVideoInfo(video.getVideoId(), videoRes).getString("url"));
			} catch (Exception e) {
				e.printStackTrace();
				msg(e.toString());
			}
		}*/
		if(c == cancelCmd && d instanceof TextBox) {
			display(mainForm);
		}
		if(c == backCmd && d == searchForm) {
			stopDoingAsyncTasks();
			display(mainForm);
			disposeSearchForm();
		}
		if(c == goCmd && d instanceof TextBox) {
			openVideo(((TextBox) d).getString());
		}
		if(c == searchOkCmd && d instanceof TextBox) {
			search(((TextBox) d).getString());
		}
		if(c == switchToPopularCmd) {
			startScreen = 1;
			stopDoingAsyncTasks();
			mainForm.deleteAll();
			d.removeCommand(c);
			loadPopular();
			Settings.saveConfig();
		}
		if(c == switchToTrendsCmd) {
			startScreen = 0;
			stopDoingAsyncTasks();
			mainForm.deleteAll();
			d.removeCommand(c);
			loadTrends();
			Settings.saveConfig();
		}
	}

	public static void msg(String s) {
		Alert a = new Alert("", s, null, null);
		a.setTimeout(-2);
		display(a);
	}
	
	public static void display(Displayable d) {
		if(d == null) {
			if(inst.videoForm != null) {
				d = inst.videoForm;
			} else if(inst.searchForm != null) {
				d = inst.searchForm;
			} else {
				d = inst.mainForm;
			}
		}
		Display.getDisplay(midlet).setCurrent(d);
	}

	public void disposeVideoForm() {
		videoForm.dispose();
		videoForm = null;
		gc();
	}
	
	public static void gc() {
		System.gc();
	}

	private void disposeSearchForm() {
		searchForm.deleteAll();
		searchForm = null;
		gc();
	}
	
	public static void platReq(String s) throws ConnectionNotFoundException {
		midlet.platformRequest(s);
	}
	
	public void addAsyncLoad(ILoader v) {
		synchronized(lazyLoadLock) {
			if(v1 == null) {
				v0.addElement(v);
			} else {
				int s0 = v0.size();
				int s1 = v1.size();
				int s2 = v2.size();
				if(s0 < s1) {
					v0.addElement(v);
				} else if(s1 < s2) {
					v1.addElement(v);
				} else {
					v2.addElement(v);
				}
			}
		}
	}
	
	public void notifyAsyncTasks() {
		synchronized(lazyLoadLock) {
			lazyLoadLock.notifyAll();
		}
	}
	
	void waitAsyncTasks() {
		synchronized(lazyLoadLock) {
			lazyLoadLock.notifyAll();
		}
		try {
			synchronized(addLock) {
				addLock.wait();
			}
		} catch (Exception e) {
		}
	}

	public void stopDoingAsyncTasks() {
		if(t0 != null) t0.pleaseInterrupt();
		if(t1 != null) t1.pleaseInterrupt();
		if(t2 != null) t2.pleaseInterrupt();
		v0.removeAllElements();
		waitAsyncTasks();
	}

	private void testCanvas() {
		Canvas c = new TestCanvas();
		display(c);
		width = c.getWidth();
		height = c.getHeight();
	}
	
	public static String getThumbUrl(JSONArray arr, int tw) {
		JSONObject s = null;
		int ld = 16384;
		int l = arr.size();
		for(int i = 0; i < l; i++) {
			JSONObject j = arr.getObject(i);
			int d = Math.abs(tw - j.getInt("width"));
			if (d < ld) {
				ld = d;
				s = j;
			}
		}
		return s.getString("url");
	}

	public static void warn(Object o, String str) {
		String cls = "";
		if(o != null) cls = "at " + o.getClass().getName();
		String s = str + " \n\n" + cls + " \nt:" + Thread.currentThread().getName();
		Alert a = new Alert("", s, null, AlertType.WARNING);
		a.setTimeout(-2);
		display(a);
	}

	public static void error(Object o, int i, Throwable e) {
		error(o, i, e.toString());
	}

	public static void error(Object o, int i, String str) {
		String cls = "null";
		if(o != null) cls = o.getClass().getName();
		String s = str + " \n\ne: " + i + " \nat " + cls + " \nt: " + Thread.currentThread().getName();
		Alert a = new Alert("", s, null, AlertType.ERROR);
		a.setTimeout(-2);
		display(a);
	}

}
