package ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.StringItem;

import App;
import Constants;
import Errors;
import Locale;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import models.AbstractModel;
import models.ChannelModel;
import models.PlaylistModel;
import models.VideoModel;

public class PlaylistForm extends ModelForm implements CommandListener, Constants {

	private PlaylistModel playlist;
	
	private ChannelModel channel;

	private Form formContainer;

	private StringItem loadingItem;

	private JSONArray vidsjson;

	private VideoModel[] videos;

	//private Vector vmodels = new Vector();

	//private int page = 1;

	public PlaylistForm(PlaylistModel p) {
		super(p.getTitle());
		loadingItem = new StringItem(null, Locale.s(TITLE_Loading));
		loadingItem.setLayout(Item.LAYOUT_CENTER | Item.LAYOUT_VCENTER | Item.LAYOUT_2);
		this.playlist = p;
		setCommandListener(this);
		addCommand(backCmd);
	}
	
	private void init() {
		try {
			if(get(0) == loadingItem) {
				delete(0);
			}
		} catch (Exception e) {
		}
		try {
			int l = vidsjson.size();
			System.out.println(l);
			App.gc();
			videos = new VideoModel[l];
			for(int i = 0; i < l; i++) {
				Item item = item(vidsjson.getObject(i), i);
				if(item == null) continue;
				append(item);
				App.gc();
			}
			vidsjson = null;
			App.gc();
			try {
				if(App.videoPreviews) {
					/*
					while(vmodels.size() > 0) {
						((VideoModel) vmodels.elementAt(0)).load();
						vmodels.removeElementAt(0);
					}
					*/
					for(int i = 0; i < l && i < 20; i++) {
						if(videos[i] == null) continue;
						videos[i].loadImage();
					}
				}
			} catch (RuntimeException e) {
				throw e;
			} catch (Throwable e) {
				e.printStackTrace();
				App.error(this, Errors.PlaylistForm_init_previews, e);
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			e.printStackTrace();
			App.error(this, Errors.PlaylistForm_init, e);
		}
	}

	private Item item(JSONObject j, int i) {
		VideoModel v = new VideoModel(j);
		v.setIndex(i);
		v.setFormContainer(this);
		videos[i] = v;
		//if(App.videoPreviews) vmodels.addElement(v);
		//if(App.videoPreviews) App.inst.addAsyncLoad(v);
		return v.makeItemForList();
	}

	public void commandAction(Command c, Displayable d) {
		if(c == backCmd) {
			if(formContainer != null) {
				App.display(formContainer);
			} else {
				App.back(this);
			}
			dispose();
		}
	}

	private void dispose() {
		deleteAll();
		playlist.disposeExtendedVars();
		playlist = null;
		//channel = null;
		App.gc();
	}

	public void load() {
		try {
			vidsjson = ((JSONObject) App.invApi("v1/playlists/" + playlist.getPlaylistId() + "?", PLAYLIST_EXTENDED_FIELDS)).getArray("videos");
			init();
		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			e.printStackTrace();
			App.error(this, Errors.PlaylistForm_load, e);
		}
	}

	public AbstractModel getModel() {
		return playlist;
	}

	public void setFormContainer(Form form) {
		this.formContainer = form;
		if(form instanceof ChannelForm) {
			channel = ((ChannelForm) form).getChannel();
		}
	}

	public int getLength() {
		return videos.length;
	}
	
	public VideoModel getVideo(int i) {
		return videos[i];
	}

}
