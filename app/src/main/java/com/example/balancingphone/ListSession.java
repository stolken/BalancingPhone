package com.example.balancingphone;

import java.text.SimpleDateFormat;
import java.util.Date;



import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class ListSession extends ListActivity {


    DbHelper mDbHelper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.listsession);
        mDbHelper = new DbHelper(getApplicationContext());

        Cursor cursor = mDbHelper.GetCursorListviewExport();
        startManagingCursor(cursor);
		// Now create a new list adapter bound to the cursor.
		// SimpleListAdapter is designed for binding to a Cursor.
        //ListAdapter listadapter = new SimpleCursorAdapter(this, // Context.
        //		R.layout.listsession, // Specify the row template
        // to use (here, two
											// columns bound to the
											// two retrieved cursor
											// rows).
        //cursor,
        // Pass in the cursor to bind to.
        // Array of cursor columns to bind to.
        //	new String[] { "start", "stop", "count", "sessionid", "last", "avgaccuracy" },
        // Parallel array of which template objects to bind to those
				// columns.
        //new int[] { R.id.start, R.id.stop, R.id.count, R.id.sessionID, R.id.last, R.id.avgaccuracy });
        // Bind to our new adapter.
        //setListAdapter(listadapter);
    }

	protected void onListItemClick(ListView l, View v, int position, long id) {


        //Intent ExportActivity = new Intent(getBaseContext(), Prefs_Export.class);

        //ExportActivity.putExtra("sessionid", (int)id);
        //startActivity(ExportActivity);
    }

	

	public String ConvertTimeStampToDate(int TimeStamp) {
		Date d = new Date(TimeStamp);

		SimpleDateFormat simpledateformat = new SimpleDateFormat(
				"yyyy'-'MM'-'dd'T'HH'h'mm");
		String date = simpledateformat.format(d);

		return date;

	}

}// class 
