package com.example.balancingphone;


private class Export extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog mProgressDialog = new ProgressDialog(
                ListSession.this);
        private FileOutputStream mFileOutputStream;
        private File mFile;
        @Override
        protected void onPreExecute() {
                 mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Drive.API)
            .addScope(Drive.SCOPE_FILE)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();
            .connect();
                
            super.onPreExecute();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
            mProgressDialog.setMessage("Please wait");
            mProgressDialog.setMax(mDbHelper.GetUpdatesCount());
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.show();
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);
        }
        @Override
        protected Void doInBackground(Void... arg0) {
            Cursor cursor = mDbHelper.GetCursorAllSessions();
            String timestamp;
            double expSessionID = 0;
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
            double interval_last;
            double interval_txrx;
            String sLine;
            cursor.moveToFirst();
            try {
                mFile = new File(getFilesDir() + "/temp");
                mFileOutputStream = new FileOutputStream(mFile);
                String sHeader = "\"timestamp\",\"sessionid\",\"sp\",\"pv\",\"error\",\"kp\",\"ki\",\"kd\",\"p\",\"i\",\"d\",\"output\",\"integral\",\"interval_last\",\"interval_txrx\"" + "\n";
                mFileOutputStream.write(sHeader.getBytes());
                while (cursor.isAfterLast() == false) {
                    publishProgress(cursor.getPosition());
                    timestamp = cursor.getString(colTimestamp);
                    expSessionID = cursor.getDouble(colSessionID);
                    SP = cursor.getDouble(colSP);
                    PV = cursor.getDouble(colPV);
                    error = cursor.getDouble(colError);
                    Kp = cursor.getDouble(colKp);
                    Ki = cursor.getDouble(colKi);
                    Kd = cursor.getDouble(colKd);
                    P = cursor.getDouble(colP);
                    I = cursor.getDouble(colI);
                    D = cursor.getDouble(colD);
                    output = cursor.getDouble(colOutput);
                    integral = cursor.getDouble(colIntegral);
                    interval_last = cursor.getDouble(colInterval_Last);
                    interval_txrx = cursor.getDouble(colInterval_TxRx);
                    sLine = timestamp + "," + expSessionID + "," + SP + "," + PV + "," + error + "," + Kp + "," + Ki + "," + Kd + "," + P + "," + I + "," + D + "," + output + "," + integral + "," + interval_last + "," + interval_txrx + "\n";
                    mFileOutputStream.write(sLine.getBytes());
                    // Longitude = cursor.getDouble(colLongitude);
                    cursor.moveToNext();
                }
            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            }
            cursor.close();
            //File FileOutput = zip();
            String Filename = "export_balbot";
            // Create a buffer for reading the files
            byte[] buffer = new byte[1024];
            File OutputZip = new File(getExternalFilesDir(null),
                    Filename + ".zip");
            try {
                FileOutputStream fos = new FileOutputStream(OutputZip);
                ZipOutputStream zipoutputstream = new ZipOutputStream(fos);
                zipoutputstream.putNextEntry(new ZipEntry(Filename + ".csv"));
                int len;
                FileInputStream fileinputstream;
                fileinputstream = new FileInputStream(mFile);
                while ((len = fileinputstream.read(buffer)) > 0) {
                    zipoutputstream.write(buffer, 0, len);
                }
                zipoutputstream.closeEntry();
                fileinputstream.close();
                zipoutputstream.closeEntry();
                // Complete the ZIP file
                zipoutputstream.close();
                    ResultCallback<DriveContentsResult> contentsCallback = new
            ResultCallback<DriveContentsResult>() {
        @Override
        public void onResult(DriveContentsResult result) {
            if (!result.getStatus().isSuccess()) {
                // Handle error
                return;
            }

            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle("New file")
                    .setMimeType("text/plain").build();
            // Create a file in the root folder
            Drive.DriveApi.getRootFolder(getGoogleApiClient())
                    .createFile(getGoogleApiClient(), changeSet, result.getDriveContents())
                    .setResultCallback(this);
        }
    }
            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            mProgressDialog.dismiss();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        
        @Override
public void onConnectionFailed(ConnectionResult connectionResult) {
    if (connectionResult.hasResolution()) {
        try {
            connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            // Unable to resolve, message user appropriately
        }
    } else {
        GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
    }
}
    }// class
