package com.example.balancingphone;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;


public class ListSession extends ListActivity {
    final int colTimestamp = 0;
    final int colSessionID = 1;
    final int colSP = 2;
    final int colPV = 3;
    final int colError = 4;
    final int colKp = 5;
    final int colKi = 6;
    final int colKd = 7;
    final int colP = 8;
    final int colI = 9;
    final int colD = 10;
    final int colOutput = 11;
    final int colIntegral = 12;

    DbHelper mDbHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.customlistviewlayout);
        mDbHelper = new DbHelper(getApplicationContext());

        Cursor cursor = mDbHelper.GetCursorListviewExport();
        // startManagingCursor(cursor);
        ListAdapter listadapter = new SimpleCursorAdapter(this, R.layout.customlistviewrow, cursor, new String[]{"sessionid"}, new int[]{R.id.sessionID}, 0);
        // Bind to our new adapter.
        setListAdapter(listadapter);
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

    private class AsyncTaskHelper extends AsyncTask<Void, Integer, Void> {

        private ProgressDialog mProgressDialog = new ProgressDialog(
                ListSession.this);


        private FileOutputStream mFileOutputStream;
        private File mFile;


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
            mProgressDialog.setMessage("Please wait");
            mProgressDialog.setMax(mDbHelper.GetUpdatesCount(sessionid));
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            Cursor cursor = mDbHelper.GetCursorSession(sessionid);
            String timestamp;
            double SessionID;
            double SP;
            double PV;
            double error;
            double Kp;
            double Ki;
            double Kd;
            double P;
            double I;
            double D;
            double output;
            double integral;
            String sLine;
            cursor.moveToFirst();
            try {
                mFile = new File(getFilesDir() + "/temp");
                mFileOutputStream = new FileOutputStream(mFile);
                String sHeader = "\"timestamp\",\"sessionid\",\"sp\",\"pv\",\"error\",\"kp\",\"ki\",\"kd\",\"p\",\"i\",\"d\",\"output\",\"integral\"" + "\n";
                mFileOutputStream.write(sHeader.getBytes());


                while (cursor.isAfterLast() == false) {
                    publishProgress(cursor.getPosition());

                    timestamp = cursor.getString(colTimestamp);
                    SessionID = cursor.getDouble(colSessionID);
                    SP = cursor.getDouble(colSP);
                    PV = cursor.getDouble(colPV);
                    error = cursor.getDouble(colError);
                    Kp = cursor.getDouble(colKp);
                    Ki = cursor.getDouble(colKi);
                    Kd = cursor.getDouble(colKp);
                    P = cursor.getDouble(colP);
                    I = cursor.getDouble(colI);
                    D = cursor.getDouble(colD);
                    output = cursor.getDouble(colOutput);
                    integral = cursor.getDouble(colIntegral);

                    sLine = timestamp + "," + SessionID + "," + SP + "," + PV + "," + error + "," + Kp + "," + Ki + "," + Kd + "," + P + "," + I + "," + D + "," + output + "," + integral + "\n";
                    mFileOutputStream.write(sLine.getBytes());

                    // Longitude = cursor.getDouble(colLongitude);


                    cursor.moveToNext();
                }

            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            }
            CloseKml();

            cursor.close();
            FileOutput = zip();

            return null;
        }


        @Override
        protected void onPostExecute(Void result) {
            mProgressDialog.dismiss();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }


        private void CloseKml() {

            try {
                fileCloseKml = new File(getFilesDir() + "/CloseKml");

                FileWriter filewriter = new FileWriter(fileCloseKml);
                BufferedWriter bufferedwriter = new BufferedWriter(filewriter);
                bufferedwriter.write("</Document>");
                bufferedwriter.newLine();
                bufferedwriter.write("</kml>");
                bufferedwriter.close();
            } catch (Exception e) {
            }

        }

        public File zip() {

            String Filename = "export_sessionID_" + sessionid;

            // Create a buffer for reading the files
            byte[] buffer = new byte[1024];

            File OutputZip = new File(getExternalFilesDir(null),
                    Filename + ".zip");
            try {

                FileOutputStream fos = new FileOutputStream(OutputZip);
                ZipOutputStream zipoutputstream = new ZipOutputStream(fos);
                zipoutputstream.putNextEntry(new ZipEntry(Filename + ".kml"));
                int len;
                FileInputStream fileinputstream;


                fileinputstream = new FileInputStream(fileInitKml);
                while ((len = fileinputstream.read(buffer)) > 0) {
                    zipoutputstream.write(buffer, 0, len);
                }


                zipoutputstream.closeEntry();
                fileinputstream.close();


                zipoutputstream.closeEntry();


                // Complete the ZIP file
                zipoutputstream.close();

            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            }

            return OutputZip;
        }

    }// class
}