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
            "integral REAL , " +
            "interval_last REAL, " +
            "interval_txrx REAL)";
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

    void AddRecord(String timestamp,
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
                          double integral,
                          double interval_last,
                          double interval_txrx) {
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
        contentvalues.put("interval_last", interval_last);
        contentvalues.put("interval_txrx", interval_txrx);

        db.insert("log", null, contentvalues);
        db.close();

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
        Cursor cursor = db.query("log", new String[]{
                "sessionid _id",
                "sessionid",
                "count(*) count",
                "strftime('%f',julianday(max(timestamp)) - julianday(min(timestamp))) duration",
                "datetime(min(timestamp)) start",
                "datetime(max(timestamp)) stop",
                "avg(abs(error) * interval_last) avg_error_interval",
                "kp",
                "ki",
                "kd"
        }, null, null, "sessionid", null, "sessionid" + " DESC");
        // "count(*) count"
        return cursor;
    }


    public int GetUpdatesCount() {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query("log",
                new String[]{"count(*) count"}, null,
                null, null, null, null);

        cursor.moveToFirst();
        int UpdatesCount = cursor.getInt(0);
        return UpdatesCount;
    }

    public Cursor GetCursorSession(int sessionid) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query("log", new String[]{
                "timestamp", "sessionid", "sp", "pv", "error", "kp", "ki", "kd", "p", "i", "d", "output", "integral", "interval_last", "interval_txrx"}, "sessionid="
                + sessionid, null, null, null, "timestamp");

        return cursor;
    }


    public Cursor GetCursorAllSessions() {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query("log", new String[]{
                "timestamp", "sessionid", "sp", "pv", "error", "kp", "ki", "kd", "p", "i", "d", "output", "integral", "interval_last", "interval_txrx"}, null, null, null, null, "timestamp");

        return cursor;
    }

    ;


    
}
