package com.jack;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
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
        // add the layout
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View videoListRow = inflater.inflate(R.layout.video_list_row, parent, false);

        // Retrieve content
        final String videoName = (String) getItem(position);

        // Time
        TextView timeTextView = (TextView) videoListRow.findViewById(R.id.timeTextView);
        timeTextView.setText(videoName);

        // Event
        TextView eventTextView = (TextView) videoListRow.findViewById(R.id.eventTextView);
        eventTextView.setText("Test");

        return videoListRow;
    }
}
