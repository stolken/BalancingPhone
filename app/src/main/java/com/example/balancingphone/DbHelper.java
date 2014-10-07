package com.example.balancingphone;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Database.db";
    private static final String SQL_CREATE_ENTRIES = "CREATE TABLE LOG (ID INTEGER PRIMARY KEY , " +
            "timestamp TEXT , " +
            "sessionid INTEGER , " +
            "sp REAL , " +
            "pv REAL , " +
            "error REAL , " +
            "kp REAL , " +
            "ki REAL , " +
            "kd REAL , " +
            "p REAL , " +
            "i REAL , " +
            "d REAL , " +
            "output REAL , " +
            "integral REAL)";
    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS LOG";

    public DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public long AddRecord(String timestamp,
                          double SessionID,
                          double SP,
                          double PV,
                          double error,
                          double Kp,
                          double Ki,
                          double Kd,
                          double P,
                          double I,
                          double D,
                          double output,
                          double integral) {
        SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues contentvalues = new ContentValues();
		contentvalues.put("timestamp", timestamp);
        contentvalues.put("sessionid", SessionID);
        contentvalues.put("sp", SP);
        contentvalues.put("pv", PV);
        contentvalues.put("error", error);
        contentvalues.put("kp", Kp);
        contentvalues.put("ki", Ki);
        contentvalues.put("kd", Kd);
        contentvalues.put("p", P);
        contentvalues.put("i", I);
        contentvalues.put("d", D);
        contentvalues.put("output", output);
        contentvalues.put("integral", integral);


        long rowid = db.insert("log", null, contentvalues);
        db.close();
		return rowid;
	}

    public int GetNewSessionID() {
        SQLiteDatabase db = this.getWritableDatabase();
		Cursor cursor = db
                .query("log", new String[]{"sessionid"}, null, null,
                        "sessionid", null, "sessionid" + " DESC", "1");

		cursor.moveToFirst();

        int last;

        if (cursor.getCount() != 0) {
            last = cursor.getInt(0) + 1;
        } else {
            last = 1;
        }
        return last;
	}

    public Cursor GetCursorListviewExport() {
        // select datetime(substr(max(timestamp),0,11),'unixepoch') stop from
        // locations group by sessionid;
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query("log", new String[]{"sessionid _id", "sessionid", "count(*) count", "strftime('%s',max(timestamp)) - strftime('%s',min(timestamp)) duration", "date(min(timestamp)) start", "date(max(timestamp)) stop"}, null, null, "sessionid", null, "sessionid" + " DESC");

        return cursor;
    }


    public int GetUpdatesCount(double sessionid) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query("log",
                new String[]{"count(*) count"}, "sessionid=" + sessionid,
                null, "sessionid", null, null);

        cursor.moveToFirst();
        int UpdatesCount = cursor.getInt(0);
        return UpdatesCount;
    }

    public Cursor GetCursorSession(int sessionid) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query("log", new String[]{
                "timestamp", "sessionid", "sp", "pv", "error", "kp", "ki", "kd", "p", "i", "d", "output", "integral"}, "sessionid="
                + sessionid, null, null, null, "timestamp");

        return cursor;
    }

    ;


    
}
