package chat.rocket.android.activity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import chat.rocket.android.Constants;
import chat.rocket.android.R;
import chat.rocket.android.content.RocketChatDatabaseHelper;
import chat.rocket.android.content.RocketChatProvider;
import chat.rocket.android.fragment.ChatRoomFragment;
import chat.rocket.android.model.Message;
import chat.rocket.android.model.MethodCall;
import chat.rocket.android.model.Room;
import chat.rocket.android.model.ServerConfig;
import chat.rocket.android.model.User;
import chat.rocket.android.preference.Cache;
import chat.rocket.android.view.CursorRecyclerViewAdapter;

public class MainActivity extends AbstractActivity {
    private static final String TAG = Constants.LOG_TAG;
    private static final int LOADER_ID = 0x12345;

    private RoomAdapter mAdapter;

    @Override
    protected int getContainerId() {
        return R.id.activity_main_container;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sliding_pane);

        setupUserInfo();
        setupUserActionToggle();
        loadRooms();
        openPaneIfNeededForInitialLayout();
    }

    private ServerConfig getPrimaryServerConfig() {
        return RocketChatDatabaseHelper.read(this, new RocketChatDatabaseHelper.DBCallback<ServerConfig>() {
            @Override
            public ServerConfig process(SQLiteDatabase db) {
                return ServerConfig.getPrimaryConfig(db);
            }
        });
    }

    private Handler mHandler = new Handler();
    private void setupUserInfo(){
        final ServerConfig s = getPrimaryServerConfig();
        final String username = Cache.get(this).getString(Cache.KEY_MY_USER_ID,"");
        setupUserInfoInner(s, username);
        Cache.waitForValue(this, Cache.KEY_MY_USER_ID, new Cache.ValueCallback<String>() {
            @Override
            public void onGetValue(String value) {
                setupUserInfoInner(s, value);
            }
        });
    }

    private void setupUserInfoInner(ServerConfig s, String username){
        if(s!=null){
            ((TextView) findViewById(R.id.txt_hostname_info)).setText(s.hostname);
            if(!TextUtils.isEmpty(username)) ((TextView) findViewById(R.id.txt_account_info)).setText(username);
            else ((TextView) findViewById(R.id.txt_account_info)).setText(s.account);
        }
        else {
            ((TextView) findViewById(R.id.txt_hostname_info)).setText("--");
            ((TextView) findViewById(R.id.txt_account_info)).setText("---");
        }
    }


    private String[] mUserActionItems = new String[]{
            "Logout"
    };
    private AdapterView.OnItemClickListener mUserActionItemCallbacks = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Context context = parent.getContext();
            final ServerConfig s = getPrimaryServerConfig();
            if(s!=null) {
                MethodCall.create("logout",null).putByContentProvider(context);
                RocketChatDatabaseHelper.writeWithTransaction(context, new RocketChatDatabaseHelper.DBCallbackEx<Object>() {
                    @Override
                    public Object process(SQLiteDatabase db) throws Exception {
                        Room.delete(db, null,null);
                        Message.delete(db, null,null);
                        User.delete(db, null,null);
                        s.delete(db);

                        return null;
                    }

                    @Override
                    public void handleException(Exception e) {
                        Log.e(TAG, "error", e);
                    }
                });
                Cache.get(context).edit().remove(Cache.KEY_MY_USER_ID).commit();
                showEntryActivity();
            }
        }
    };


    private void showEntryActivity(){
        Intent intent = new Intent(this, EntryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setupUserActionToggle(){
        final ListView userActionList = (ListView) findViewById(R.id.listview_user_actions);
        userActionList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mUserActionItems));
        userActionList.setOnItemClickListener(mUserActionItemCallbacks);

        findViewById(R.id.img_user_action_toggle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(userActionList.getVisibility()==View.GONE) {
                    v.animate()
                        .rotation(180)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                userActionList.setVisibility(View.VISIBLE);
                            }
                        }).start();
                }
                else {
                    v.animate()
                            .rotation(0)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    userActionList.setVisibility(View.GONE);
                                }
                            }).start();
                }
            }
        });
    }

    private void loadRooms(){
        mAdapter = new RoomAdapter(this, null);
        final RecyclerView roomListView = (RecyclerView) findViewById(R.id.listview_rooms);
        roomListView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        roomListView.setAdapter(mAdapter);

        getSupportLoaderManager().restartLoader(LOADER_ID, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                Uri uri = RocketChatProvider.getUriForQuery(Room.TABLE_NAME);
                return new CursorLoader(MainActivity.this, uri, null, null, null, null);
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                mAdapter.swapCursor(data);
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                mAdapter.swapCursor(null);
            }
        });
    }

    private void openPaneIfNeededForInitialLayout() {
        // pane.isOpen is not correct before OnLayout.
        // https://code.google.com/p/android/issues/detail?id=176340
        final SlidingPaneLayout pane = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
        pane.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                pane.removeOnLayoutChangeListener(this);
                pane.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openPaneIfNeeded();
                    }
                }, 500);
            }
        });
    }

    private void openPaneIfNeeded() {
        final SlidingPaneLayout pane = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
        if (pane.isSlideable() && !pane.isOpen()) {
            pane.openPane();
        }
    }

    private void closePaneIfNeeded(){
        final SlidingPaneLayout pane = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
        if (pane.isSlideable() && pane.isOpen()) {
            pane.closePane();
        }
    }

    private static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView roomName;

        public RoomViewHolder(View itemView) {
            super(itemView);
            roomName = (TextView) itemView.findViewById(R.id.list_item_room_name);
        }
    }

    private class RoomAdapter extends CursorRecyclerViewAdapter<RoomViewHolder> {

        LayoutInflater mInflater;

        public RoomAdapter(Context context, Cursor cursor) {
            super(context, cursor);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getItemViewType(int position) {
            return super.getItemViewType(position);
        }

        @Override
        public RoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RoomViewHolder(mInflater.inflate(R.layout.listitem_room, parent, false));
        }

        @Override
        public void bindView(RoomViewHolder viewHolder, Context context, Cursor cursor) {
            final String roomName = cursor.getString(cursor.getColumnIndex("name"));
            final String roomId = cursor.getString(cursor.getColumnIndex("id"));

            viewHolder.roomName.setText(roomName);
            viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closePaneIfNeeded();
                    showChatRoomFragment(roomId, roomName);
                }
            });
        }
    }

    private void showChatRoomFragment(String roomId, String roomName) {
        ServerConfig s = getPrimaryServerConfig();
        if(s!=null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(getContainerId(), ChatRoomFragment.create(s.hostname, roomId, roomName))
                    .commit();
        }
    }
}