package com.jack;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class VideoListAdapter extends ArrayAdapter {
    private Context context;
    private ArrayList<String> videoList;

    public VideoListAdapter(Activity activity, ArrayList<String> fileList) {
        super(activity.getApplicationContext(), R.layout.video_list_row, fileList);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String fileName = (String) getItem(position);

        // add the layout
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View videoListRow = inflater.inflate(R.layout.video_list_row, parent, false);

        // Time
        TextView timeTextView = (TextView) videoListRow.findViewById(R.id.timeTextView);
        String timestamp_s = fileName.substring(0, 13);
        long timestamp = Long.parseLong(timestamp_s);
        String time = String.format("%02d:%02d:%02d", (timestamp / 3600000) % 24,  (timestamp / 60000) % 60, (timestamp / 1000) % 60);
        timeTextView.setText(time);

        // Heat
        TextView heatTextView = (TextView) videoListRow.findViewById(R.id.heatTextView);

        if (fileName.length() > 37) {
            String heatID = fileName.substring(14, 34);
            // TODO Get heat label
            heatTextView.setText(heatID);
        } else
            heatTextView.setVisibility(View.INVISIBLE);

        // Bow marker
        TextView bowMarkerTextView = (TextView) videoListRow.findViewById(R.id.bowMarkerTextView);

        if (fileName.length() > 38) {
            String heatID = fileName.substring(35, fileName.length()-4);
            bowMarkerTextView.setText(heatID);
        } else
            bowMarkerTextView.setVisibility(View.INVISIBLE);

        return videoListRow;
    }
}
