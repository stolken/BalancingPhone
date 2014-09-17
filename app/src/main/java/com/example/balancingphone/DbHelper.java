private static final String TEXT_TYPE = " TEXT";
private static final String COMMA_SEP = ",";
private static final String SQL_CREATE_ENTRIES =    "CREATE TABLE LOG (ID INETEGER PRIMARY KEY , TIMESTAMPISO8601 TEXT, SP REAL , PV REAL , ERROR REAL , kP REAL , kI REAL , kD REAL , P REAL , I REAL , D REAL)";
private static final String SQL_DELETE_ENTRIES =    "DROP TABLE IF EXISTS LOG";

public class DbHelper extends SQLiteOpenHelper {
// If you change the database schema, you must increment the database version.    
public static final int DATABASE_VERSION = 1;    
public static final String DATABASE_NAME = "Database.db";    
public DbHelper(Context context) {        
super(context, DATABASE_NAME, null, DATABASE_VERSION);    
}    
public void onCreate(SQLiteDatabase db) {
db.execSQL(SQL_CREATE_ENTRIES);    }    
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
// This database is only a cache for online data, so its upgrade policy is        
// to simply to discard the data and start over        
db.execSQL(SQL_DELETE_ENTRIES);
onCreate(db);    }    

public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
onUpgrade(db, oldVersion, newVersion);    }
}
