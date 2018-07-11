package com.player.licenta.androidplayer;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import com.player.licenta.androidplayer.MusicService.MusicBinder;
import com.player.licenta.androidplayer.lyrics.AccessTokenLoader;
import com.player.licenta.androidplayer.lyrics.LyricsWS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public class MainActivity extends Activity
{
    private static final int LOADER_ACCESS_TOKEN = 1;
    private ArrayList<Song> songList;
	private ListView songView;
    //private SeekBar volumeControl;

	private MusicService musicSrv;
	private Intent playIntent;
	private boolean musicBound=false;


	private MusicController controller;

	private SongAdapter songAdt;

	private boolean paused=false, playbackPaused=false;

	private MusicService.OnSongChangedListener songChangedLister;

    Credentials m_credentials;
    Integer m_selectedSongIndex = 0;

	public final static String EXTRA_MESSAGE = "com.mycompany.myfirstapp.MESSAGE";
	private final static String TAG = "MainActivity";

	//connect to the service
	private ServiceConnection musicConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			MusicBinder binder = (MusicBinder)service;
			musicSrv = binder.getService();
			musicSrv.setList(songList);
			musicSrv.setOnSongFinishedListener(songChangedLister);
			musicBound = true;
			setController();
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			musicBound = false;
		}


	};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        songView = (ListView)findViewById(R.id.song_list);
        //volumeControl = (SeekBar)findViewById(R.id.volume);
		//volumeControl.setProgress(100);
        songList = new ArrayList<Song>();
		getSongList();

        Collections.sort(songList, new Comparator<Song>()
        {
            public int compare(Song a, Song b)
            {
                return a.getTitle().compareTo(b.getTitle());
            }
        });

        songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);

		songChangedLister = new MusicService.OnSongChangedListener()
		{
			@Override
			public void onSongChanged(Song newSong)
			{
				songAdt.setHighlightRow(musicSrv.getSongIndex());
			}
		};

        /*volumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                System.out.println("Volume: " + progress);
                musicSrv.setVolume(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
            }
        });*/

		Log.d(TAG, "onCreate called");
	}

    @Override
    protected void onStart()
    {
    	super.onStart();
    	if(playIntent==null)
    	{
	        playIntent = new Intent(this, MusicService.class);
	        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
	        startService(playIntent);
    	}
		Log.d(TAG, "onStart called");
    }

    @Override
    protected void onResume()
    {
        super.onResume();
		if (controller != null)
        {
            controller.show();
            songAdt.setHighlightRow(musicSrv.getSongIndex());
			musicSrv.setOnSongFinishedListener(songChangedLister);
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void getSongList()
    {
    	  //retrieve song info
    	ContentResolver musicResolver = getContentResolver();
    	Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    	Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if(musicCursor!=null && musicCursor.moveToFirst())
        {
			//get columns
			int titleColumn = musicCursor.getColumnIndex
			(android.provider.MediaStore.Audio.Media.TITLE);
			int idColumn = musicCursor.getColumnIndex
			(android.provider.MediaStore.Audio.Media._ID);
			int artistColumn = musicCursor.getColumnIndex
			(android.provider.MediaStore.Audio.Media.ARTIST);
			//add songs to list
			do
			{
				long thisId = musicCursor.getLong(idColumn);
				String thisTitle = musicCursor.getString(titleColumn);
				String thisArtist = musicCursor.getString(artistColumn);
				songList.add(new Song(thisId, thisTitle, thisArtist));
			}
			while (musicCursor.moveToNext());
        }
    }

	public void songPicked(View view)
    {
		try
		{
			int songIndex = Integer.parseInt(view.getTag().toString());
			if(songIndex >= 0)
			{
				if (songIndex != musicSrv.getSongIndex())
				{
					musicSrv.playSong(songIndex);
					songAdt.setHighlightRow(musicSrv.getSongIndex());
				}
				controller.show();
				showCoverArtActivity(view);
                sendInfoToLyricsWS(view);
			}
		}
		catch(NumberFormatException ex)
		{
            // safe to ignore for now
            ex.printStackTrace();
		}
	}

	private void sendInfoToLyricsWS(View view)
    {
        m_selectedSongIndex = Integer.parseInt(view.getTag().toString());
        //retrieveCredentials(); // FIXME
        onCredentialsRetrieved();
    }

    private void retrieveCredentials() {
        // Initiate token refresh
        getLoaderManager().initLoader(LOADER_ACCESS_TOKEN, null,
                new LoaderManager.LoaderCallbacks<String>() {
                    @Override
                    public Loader<String> onCreateLoader(int id, Bundle args) {
                        return new AccessTokenLoader(MainActivity.this);
                    }

                    @Override
                    public void onLoadFinished(Loader<String> loader, String token) {
                        setAccessToken(token);
                    }

                    @Override
                    public void onLoaderReset(Loader<String> loader) {
                    }
                });
    }

    public void setAccessToken(String token) {
        Log.d(TAG, "setAccessToken:" + token);

        long oneHourMs = 3600 * 1000;
        Date expirationTime = new Date(oneHourMs);
        AccessToken accessToken = new AccessToken(token, expirationTime);
        m_credentials = OAuth2Credentials.create(accessToken);
        onCredentialsRetrieved();
    }

    private void onCredentialsRetrieved()
    {
        Song currentSong  = (Song)songList.get(m_selectedSongIndex);
        String songTitle = currentSong.getTitle().toString();
        String songArtist = currentSong.getArtist().toString();
        String[] songInfo = {songArtist, songTitle};
        LyricsWS getLyricsTask = new LyricsWS();
        getLyricsTask.setParams(songArtist, songTitle);
        getLyricsTask.setCredentials(m_credentials);
        getLyricsTask.execute();
    }

	private void showCoverArtActivity(View view)
	{
		Intent intent = new Intent(this, SongPickedActivity.class);
		Integer index = Integer.parseInt(view.getTag().toString());

		Song currentSong  = (Song)songList.get(index);
		String songTitle = currentSong.getTitle().toString();
		String songArtist = currentSong.getArtist().toString();

		String songPath = musicSrv.getSongPath();

		Bundle extras = new Bundle();
		extras.putString("SONG_PATH", songPath);
		extras.putString("SONG_ARTIST", songArtist);
		extras.putString("SONG_TITLE", songTitle);

		intent.putExtras(extras);

		intent.putExtra("songlist", songList);

/*		Context context = getApplicationContext();
		CharSequence text = songPath;
		int duration = Toast.LENGTH_SHORT;

		Toast toast = Toast.makeText(context, text, duration);
		toast.show();*/

		startActivity(intent);
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	//menu item selected
    	switch (item.getItemId())
    	{
    		case R.id.preferences_item:
    		    Intent intent = new Intent(this, SettingsActivity.class);
    		    startActivity(intent);
    			break;

    		case R.id.action_shuffle:
    			 musicSrv.setShuffle();
    			 break;
    	}
    	return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy()
	{
        Log.d(TAG, "onDestroy called");

        controller.hide();
	    stopService(playIntent);
		unbindService(musicConnection);

        super.onDestroy();
    }

	private void setController()
	{		//set the controller up
		controller = new MusicController(this);

		controller.setPrevNextListeners(
				new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						musicSrv.playNext();
						songAdt.setHighlightRow(musicSrv.getSongIndex());
					}
				},
				new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						musicSrv.playPrev();
						songAdt.setHighlightRow(musicSrv.getSongIndex());
					}
				}
		);
        MusicController.LayoutParams p = new MusicController.LayoutParams(controller.getLayoutParams());
        
        controller.setLayoutParams(p);


        controller.setMediaPlayer(musicSrv);
		controller.setAnchorView(findViewById(R.id.song_list));
		controller.setEnabled(true);
	}
}
