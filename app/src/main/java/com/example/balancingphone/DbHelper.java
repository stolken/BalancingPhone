package com.example.balancingphone;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Database.db";
    // If you change the database schema, you must increment the database version.
    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES = "CREATE TABLE LOG (ID INTEGER PRIMARY KEY , TIMESTAMPISO8601 TEXT, SP REAL , PV REAL , ERROR REAL , kP REAL , kI REAL , kD REAL , P REAL , I REAL , D REAL)";
    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS LOG";

    public DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
// This database is only a cache for online data, so its upgrade policy is        
// to simply to discard the data and start over        
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
    
    public long AddRecord(long timestamp, double latitude, double longitude,
			double altitude, float speed, float accuracy, float bearing,
			double sessionid) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues contentvalues = new ContentValues();
		contentvalues.put("timestamp", timestamp);
		contentvalues.put("latitude", latitude);
		contentvalues.put("longitude", longitude);
		contentvalues.put("altitude", altitude);
		contentvalues.put("speed", speed);
		contentvalues.put("accuracy", accuracy);
		contentvalues.put("bearing", bearing);
		contentvalues.put("sessionid", sessionid);
		contentvalues.put("airport1", airport1);
		contentvalues.put("airport2", airport2);
		contentvalues.put("airport3", airport3);
		contentvalues.put("airport4", airport4);
		
				

		long rowid = db.insert("log", null, contentvalues);
		db.close();
		return rowid;
	}
	
	public String GetLastSessionID(double sessionid) {
		SQLiteDatabase db = this.getWritableDatabase();
		Cursor cursor = db
				.query("log",
						new String[] { "time((max(timestamp) - min(timestamp)) / 1000,'unixepoch') last" },
						"sessionid=" + sessionid, null, "sessionid", null, null);

		cursor.moveToFirst();
		String last = cursor.getString(0);

		return last;
	}


    
}
