package com.googlecode.iptableslog;

import android.util.Log;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.view.LayoutInflater;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class AppView extends Activity implements IptablesLogListener
{
  private ArrayList<ListItem> listData;
  private CustomAdapter adapter;
  private enum Sort { UID, NAME, PACKETS, BYTES, TIMESTAMP }; 
  private Sort sortBy = Sort.UID;

  private class ListItem {
    protected Drawable mIcon;
    protected int mUid;
    protected String mName;
    protected int totalPackets;
    protected int totalBytes;
    protected String lastTimestamp;
    protected ArrayList<String> uniqueHosts;

    ListItem(Drawable icon, int uid, String name) {
      mIcon = icon;
      mUid = uid;
      mName = name;
    }
  }

  protected class SortAppsByBytes implements Comparator<ListItem> {
    public int compare(ListItem o1, ListItem o2) {
      return o1.totalBytes > o2.totalBytes ? -1 : (o1.totalBytes == o2.totalBytes) ? 0 : 1;
    }
  }

  protected class SortAppsByPackets implements Comparator<ListItem> {
    public int compare(ListItem o1, ListItem o2) {
      return o1.totalPackets > o2.totalPackets ? -1 : (o1.totalPackets == o2.totalPackets) ? 0 : 1;
    }
  }

  protected class SortAppsByTimestamp implements Comparator<ListItem> {
    public int compare(ListItem o1, ListItem o2) {
      return o2.lastTimestamp.compareToIgnoreCase(o1.lastTimestamp.equals("N/A") ? "" : o1.lastTimestamp);
    }
  }

  protected class SortAppsByName implements Comparator<ListItem> {
    public int compare(ListItem o1, ListItem o2) {
      return o1.mName.compareToIgnoreCase(o2.mName);
    }
  }

  protected class SortAppsByUid implements Comparator<ListItem> {
    public int compare(ListItem o1, ListItem o2) {
      return o1.mUid < o2.mUid ? -1 : (o1.mUid == o2.mUid) ? 0 : 1;
    }
  }

  protected void getInstalledApps() {
    for(ApplicationsTracker.AppEntry app : ApplicationsTracker.installedApps) {
      String name = app.name;
      Drawable icon = app.icon;
      int uid = app.uid;

      ListItem item = new ListItem(icon, uid, name);
      item.lastTimestamp = "N/A";
      item.uniqueHosts = new ArrayList<String>();
      item.uniqueHosts.add(0, "N/A");
      listData.add(item);
    }

    Collections.sort(listData, new SortAppsByUid());
    sortBy = Sort.UID;
  }

  @Override
    public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.layout.appmenu, menu);
      return true;
    }

  public void updateAdapter() {
    listData.add(0, null);
    listData.remove(0);
    adapter.notifyDataSetChanged();
  }

  @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
        case R.id.sort_by_uid:
          sortBy = Sort.UID;
          Collections.sort(listData, new SortAppsByUid());
          updateAdapter();
          return true;
        case R.id.sort_by_name:
          sortBy = Sort.NAME;
          Collections.sort(listData, new SortAppsByName());
          updateAdapter();
          return true;
        case R.id.sort_by_packets:
          sortBy = Sort.PACKETS;
          Collections.sort(listData, new SortAppsByPackets());
          updateAdapter();
          return true;
        case R.id.sort_by_bytes:
          sortBy = Sort.BYTES;
          Collections.sort(listData, new SortAppsByBytes());
          updateAdapter();
          return true;
        case R.id.sort_by_timestamp:
          sortBy = Sort.TIMESTAMP;
          Collections.sort(listData, new SortAppsByTimestamp());
          updateAdapter();
          return true;
        default:
          return super.onOptionsItemSelected(item);
      }
    }

  /** Called when the activity is first created. */
  @Override
    public void onCreate(Bundle savedInstanceState)
    {
      super.onCreate(savedInstanceState);

      LinearLayout layout = new LinearLayout(this);
      layout.setOrientation(LinearLayout.VERTICAL);

      TextView tv = new TextView(this);
      tv.setText("Application listing");

      layout.addView(tv);

      listData = new ArrayList<ListItem>();
      adapter = new CustomAdapter(this, R.layout.appitem, listData);

      ListView lv = new ListView(this);
      lv.setTextFilterEnabled(true);
      lv.setAdapter(adapter);

      layout.addView(lv);

      setContentView(layout);

      getInstalledApps();
      adapter.notifyDataSetChanged();

      IptablesLogTracker.addListener(this);
    }

  public void onNewLogEntry(IptablesLogTracker.LogEntry entry) {
    for(ListItem item : listData) {
      if(item.mUid == Integer.parseInt(entry.uid)) {
        item.totalPackets = entry.packets;
        item.totalBytes = entry.bytes;
        item.lastTimestamp = entry.timestamp;

        String src = entry.src + ":" + entry.spt;
        String dst = entry.dst + ":" + entry.dpt;

        if(item.uniqueHosts.get(0).equals("N/A")) {
          item.uniqueHosts.remove(0);
        }

        if(!entry.src.equals(IptablesLogTracker.localIpAddr) && !item.uniqueHosts.contains(src))
          item.uniqueHosts.add(src);

        if(!entry.dst.equals(IptablesLogTracker.localIpAddr) && !item.uniqueHosts.contains(dst))
          item.uniqueHosts.add(dst);

        Collections.sort(item.uniqueHosts);
      }
    }

    runOnUiThread(new Runnable() {
      public void run() {
        if(sortBy == Sort.PACKETS) {
          Collections.sort(listData, new SortAppsByPackets());
        } else if(sortBy == Sort.BYTES) {
          Collections.sort(listData, new SortAppsByBytes());
        } else if(sortBy == Sort.TIMESTAMP) {
          Collections.sort(listData, new SortAppsByTimestamp());
        }
        adapter.notifyDataSetChanged();
      }
    });
  }

  private class CustomAdapter extends ArrayAdapter<ListItem> {
    LayoutInflater mInflater = (LayoutInflater) getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

    public CustomAdapter(Context context, int resource, List<ListItem> objects) {
      super(context, resource, objects);
    }

    @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;

        ImageView icon;
        TextView name;
        TextView packets;
        TextView bytes;
        TextView timestamp;
        TextView hosts;

        ListItem item = getItem(position);

        if(convertView == null) {
          convertView = mInflater.inflate(R.layout.appitem, null);
          holder = new ViewHolder(convertView);
          convertView.setTag(holder);
        }

        holder = (ViewHolder) convertView.getTag();
        icon = holder.getIcon();
        icon.setImageDrawable(item.mIcon);

        name = holder.getName();
        name.setText("(" + item.mUid + ")" + " " + item.mName);

        packets = holder.getPackets();
        packets.setText("Packets: " + item.totalPackets);

        bytes = holder.getBytes();
        bytes.setText("Bytes: " + item.totalBytes);

        timestamp = holder.getTimestamp();
        timestamp.setText("Timestamp: " + item.lastTimestamp);

        hosts = holder.getUniqueHosts();
        String s = new String();
        if(item.uniqueHosts.get(0).equals("N/A")) {
          s = "N/A";
        } else {
          Iterator<String> itr = item.uniqueHosts.iterator();
          while(itr.hasNext()) {
            s += "\n  " + itr.next();
          }
        }
        hosts.setText("Addrs: " + s);

        return convertView;
      }
  }

  private class ViewHolder {
    private View mView;
    private ImageView mIcon = null;
    private TextView mName = null;
    private TextView mPackets = null;
    private TextView mBytes = null;
    private TextView mTimestamp = null;
    private TextView mUniqueHosts = null;

    public ViewHolder(View view) {
      mView = view;
    }

    public ImageView getIcon() {
      if(mIcon == null) {
        mIcon = (ImageView) mView.findViewById(R.id.appIcon);
      }
      return mIcon;
    }

    public TextView getName() {
      if(mName == null) {
        mName = (TextView) mView.findViewById(R.id.appName);
      }
      return mName;
    }

    public TextView getPackets() {
      if(mPackets == null) {
        mPackets = (TextView) mView.findViewById(R.id.appPackets);
      }
      return mPackets;
    }

    public TextView getBytes() {
      if(mBytes == null) {
        mBytes = (TextView) mView.findViewById(R.id.appBytes);
      }
      return mBytes;
    }

    public TextView getTimestamp() {
      if(mTimestamp == null) {
        mTimestamp = (TextView) mView.findViewById(R.id.appLastTimestamp);
      }
      return mTimestamp;
    }

    public TextView getUniqueHosts() {
      if(mUniqueHosts == null) {
        mUniqueHosts = (TextView) mView.findViewById(R.id.appUniqueHosts);
      }
      return mUniqueHosts;
    }
  }
}
