package com.example.balancingphone;


import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.nio.ByteBuffer;
//test webcommit

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

public class MainActivity extends Activity implements SensorEventListener {
    public static Context context;
    //byte[] bytes = new byte[1];
    static short servo_neutral;
    static double SP_Pitch; //set point for pitch
    static double max_error;
    static double Kp_Pitch;
    static double Ki_Pitch;
    static double Kd_Pitch;
    UsbDeviceConnection mUsbDeviceConnection;
    UsbEndpoint mUsbEndpointIn;
    UsbEndpoint mUsbEndpointOut;
    UsbInterface mUsbInterface;
    GraphView graphView;
    TextView tvSensor;
    TextView tvConsole;
    Switch swLoop;

    StringBuilder mStringBuilder;
    double PV;
    short max_Output_servos = 2000;
    short min_Output_servos = 1000;
    short max_output_Pitch = 500;
    short min_output_Pitch = -500;
    double P;
    double I;
    double D;
    double error;
    double prev_error;
    double integral_Pitch = 0;
    double output_Pitch;
    short[] output_servos;
    double onSensorChangedTimestampMs;
    double intervalOnSensorEventMs;
    long intervalTxRxMs;
    GraphViewSeries mErrorGraphViewSeries;
    GraphViewSeries mOutputGraphViewSeries;
    GraphViewSeries mPGraphViewSeries;
    GraphViewSeries mIGraphViewSeries;
    GraphViewSeries mDGraphViewSeries;
    SQLiteDatabase mDb;


    UsbDevice mDevice;
    int TIMEOUT = 1000;
    int X = 0;
    boolean forceClaim = true;
    private SensorManager mSensorManager;
    private Sensor mRotationVector;

    public static void GetAllPreferences(Context ctxt) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctxt);
        Kp_Pitch = Double.parseDouble(sharedPref.getString("Kp_Pitch", "1"));
        Ki_Pitch = Double.parseDouble(sharedPref.getString("Ki_Pitch", "1"));
        Kd_Pitch = Double.parseDouble(sharedPref.getString("Kd_Pitch", "1"));
        SP_Pitch = Double.parseDouble(sharedPref.getString("SP_Pitch", "1"));
        max_error = Double.parseDouble(sharedPref.getString("max_error", "20"));
        servo_neutral = Short.parseShort(sharedPref.getString("servo_neutral", "1500"));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_relative);
        context = this;
        output_servos = new short[2];
        InitUI();
        InitConsole();
        InitGraphView();
        InitUsbConnection();
        InitSensor();
        GetAllPreferences(context);
        updatetvSensor();
        DbHelper mDbHelper = new DbHelper(this);
        mDb = mDbHelper.getWritableDatabase();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        PV = getPitch(event);
        error = (SP_Pitch - PV);

        intervalOnSensorEventMs = event.timestamp / 1000000 - onSensorChangedTimestampMs;
        onSensorChangedTimestampMs = event.timestamp / 1000000; //record for next


        if (swLoop.isChecked()) {
            if (error > max_error || error < -max_error) {
                AddToConsole("Over pitch!");
                swLoop.setChecked(false);
            }
            PID();
            mErrorGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, error / max_error * 100), true, 5000);
            mOutputGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, output_Pitch / max_output_Pitch * 100), true, 5000);
            mPGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, P / max_output_Pitch * 100), true, 5000);
            mIGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, I / max_output_Pitch * 100), true, 5000);
            mDGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, D / max_output_Pitch * 100), true, 5000);
            updatetvSensor();
            dbInsert();
        } else { //swLoop not checked
            output_Pitch = 0;
        }
        output_servos[0] = (short) (servo_neutral - output_Pitch);
        output_servos[1] = (short) (servo_neutral + output_Pitch);

        if (output_servos[0] < min_Output_servos) {
            output_servos[0] = min_Output_servos;
        }

        if (output_servos[0] > max_Output_servos) {
            output_servos[0] = max_Output_servos;
        }

        if (output_servos[1] < min_Output_servos) {
            output_servos[1] = min_Output_servos;
        }

        if (output_servos[1] > max_Output_servos) {
            output_servos[1] = max_Output_servos;
        }


        if (mUsbDeviceConnection != null) {
            long beforeSend = System.nanoTime();
            sendSerialMessage();
            byte rxByte = readSerialMessage();

            if (rxByte != 4) {
                swLoop.setChecked(false);
                AddToConsole("Connection error: " + rxByte);
            }
            long afterSend = System.nanoTime();
            intervalTxRxMs = (afterSend - beforeSend) / 1000000;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    protected void AddToConsole(String text) {
        SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]");
        String timeNow = sdf.format(new Date());
        mStringBuilder.append(timeNow);
        mStringBuilder.append(text);
        mStringBuilder.append("\n");
        tvConsole.setText(mStringBuilder.toString());
        final Layout layout = tvConsole.getLayout();
        if (layout != null) {
            int scrollDelta = layout.getLineBottom(tvConsole.getLineCount() - 1)
                    - tvConsole.getScrollY() - tvConsole.getHeight();
            if (scrollDelta > 0)
                tvConsole.scrollBy(0, scrollDelta);
        }
    }

    void updatetvSensor() {

        DecimalFormat decimalFormat = new DecimalFormat("#");

        tvSensor.setText("PV: " + decimalFormat.format(PV) + "\n"
                + "SP_Pitch: " + decimalFormat.format(SP_Pitch) + "\n"
                + "error: " + decimalFormat.format(error) + "\n"
                + "output_Pitch (P;I;D): " + decimalFormat.format(output_Pitch) + "(" + decimalFormat.format(P) + ";" + decimalFormat.format(I) + ";" + decimalFormat.format(D) + ")\n"
                + "output_Servo 0: " + output_servos[0] + "\n"
                + "output_Servo 1: " + output_servos[1] + "\n"
                + "integral_Pitch: " + decimalFormat.format(integral_Pitch) + "\n"
                + "intervalSensorEvent: " + Double.toString(intervalOnSensorEventMs) + "\n"
                + "intervalTxRx: " + Long.toString(intervalTxRxMs) + "\n");
    }

    void updateGraph() {

        X++;
    }

    void sendSerialMessage() {

        if (mUsbEndpointOut != null) {
            byte[] mMessage = new byte[4];
            ByteBuffer.wrap(mMessage, 0, 2).putShort(output_servos[0]);
            ByteBuffer.wrap(mMessage, 2, 2).putShort(output_servos[1]);
            //mMessage[4] = (byte) 10; //new line
            int txBytes = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, mMessage, mMessage.length, TIMEOUT); //do in another thread

            if (txBytes > 0) {

                //AddToConsole(txBytes + " byte sent");
                //for (int i = 0; i < txBytes; i++) {
                //    AddToConsole(">"+ i + "(dec):" + mMessage[i] + "," + "(hex):" + String.format("%02X ", mMessage[i]));
                //}
            } else {
                AddToConsole("Error no data transferred: " + txBytes);
            }
        }

    }

    private byte readSerialMessage() {

        byte ret = 0;
        int rxBytes;
        // do{
        byte[] mMessage = new byte[64];
        // reinitialize read value byte array
        Arrays.fill(mMessage, (byte) 0);

        rxBytes = mUsbDeviceConnection.bulkTransfer(mUsbEndpointIn, mMessage, mMessage.length, TIMEOUT); //do in another thread
        //AddToConsole(rxBytes + " byte(s) received");
        if (rxBytes > 0) {
            ret = mMessage[0];

            //String asciiMessage = new String(mMessage);
            //AddToConsole("ASCII: " + asciiMessage);

        } else {
            AddToConsole("Error data received");
            ret = 0;
        }
        return ret;
    }

    private double getPitch(SensorEvent event) {
        float[] mRotationMatrixFromVector = new float[9];
        float[] mRotationMatrix = new float[9];
        float[] orientationVals = new float[3];

        // Convert the rotation-vector to a 4x4 matrix.
        SensorManager.getRotationMatrixFromVector(mRotationMatrixFromVector, event.values);
        SensorManager.remapCoordinateSystem(mRotationMatrixFromVector, SensorManager.AXIS_X, SensorManager.AXIS_Z, mRotationMatrix);
        SensorManager.getOrientation(mRotationMatrix, orientationVals);

        // Optionally convert the result from radians to degrees
        return (float) Math.toDegrees(orientationVals[1]);


    }

    void InitGraphView() {
        mErrorGraphViewSeries = new GraphViewSeries("Error", new GraphViewSeries.GraphViewSeriesStyle(Color.RED, 4), new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        mOutputGraphViewSeries = new GraphViewSeries("Output", new GraphViewSeries.GraphViewSeriesStyle(Color.BLUE, 2), new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        mPGraphViewSeries = new GraphViewSeries("P", new GraphViewSeries.GraphViewSeriesStyle(Color.GREEN, 2), new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        mIGraphViewSeries = new GraphViewSeries("I", new GraphViewSeries.GraphViewSeriesStyle(Color.YELLOW, 2), new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        mDGraphViewSeries = new GraphViewSeries("D", new GraphViewSeries.GraphViewSeriesStyle(Color.MAGENTA, 2), new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});


        //mSP_PitchGraphViewSeries = new GraphViewSeries("SetPoint",new GraphViewSeries.GraphViewSeriesStyle(Color.BLACK,1),new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        X++;
        graphView = new LineGraphView(this, "PID");
        graphView.setScrollable(true);
        graphView.getGraphViewStyle().setVerticalLabelsColor(Color.RED);
        graphView.setShowLegend(true);
        graphView.getGraphViewStyle().setTextSize(40);
        graphView.setLegendAlign(GraphView.LegendAlign.MIDDLE);
        //graphView.setLegendWidth(500);
        graphView.getGraphViewStyle().setNumVerticalLabels(3);
        graphView.getGraphViewStyle().setGridColor(Color.GRAY);
        graphView.getGraphViewStyle().setVerticalLabelsWidth(1);
        graphView.setHorizontalLabels(null);
        // graphView.getGraphViewStyle().setNumHorizontalLabels(0);
        graphView.setManualYAxisBounds(100, -100);
        graphView.setViewPort(2, 3500);
        graphView.addSeries(mErrorGraphViewSeries);
        graphView.addSeries(mOutputGraphViewSeries);
        graphView.addSeries(mPGraphViewSeries);
        graphView.addSeries(mIGraphViewSeries);
        graphView.addSeries(mDGraphViewSeries);
        //graphView.addSeries(mSP_PitchGraphViewSeries);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mLayout);
        layout.addView(graphView);
    }

    void InitUsbConnection() {

        //Arrays.fill(bytes, (byte) 0);
        UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Intent intent = getIntent();
        String intentActionString = intent.getAction();
        AddToConsole("Intent action:");
        AddToConsole(intentActionString);
        if (intentActionString == "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
            mDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        } else {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();


            mDevice = deviceList.get("/dev/bus/usb/001/002");
            if (mDevice == null) {
                AddToConsole("Device not found");
            }

        }
        if (mDevice != null) {
            AddToConsole("Device name:");
            AddToConsole(mDevice.getDeviceName());
            int InterfaceCount = mDevice.getInterfaceCount();
            for (int i = 0; i <= InterfaceCount - 1; i++) {
                UsbInterface mUsbInterface = mDevice.getInterface(i);
                int mUsbInterfaceClass = mUsbInterface.getInterfaceClass();
                if (mUsbInterfaceClass == 10) {//if CDC
                    AddToConsole("Found a CDC interface: " + mUsbInterface.toString());
                    int EndpointCount = mUsbInterface.getEndpointCount();
                    for (int f = 0; f <= EndpointCount - 1; f++) {
                        UsbEndpoint mUsbEndpoint = mUsbInterface.getEndpoint(f);
                        int UsbEndpointDirection = mUsbEndpoint.getDirection();
                        if (UsbEndpointDirection == 128) {
                            AddToConsole("Found the IN endpoint: " + mUsbEndpoint.toString());
                            mUsbEndpointIn = mUsbEndpoint;
                        } else if (UsbEndpointDirection == 0) {
                            AddToConsole("Found the OUT endpoint: " + mUsbEndpoint.toString());
                            mUsbEndpointOut = mUsbEndpoint;
                        }
                    }
                    mUsbDeviceConnection = mUsbManager.openDevice(mDevice);
                    boolean ret = mUsbDeviceConnection.claimInterface(mUsbInterface, forceClaim);
                    if (ret == true) {
                        AddToConsole("Interface successfully claimed");
                        mUsbDeviceConnection.controlTransfer(0x21, 0x22, 0x1, 0, null, 0, 0);
                        AddToConsole("ControlTransfer Set");
                    }
                }

            }

        }


    }

    void PID() {


        if (((integral_Pitch + (error * intervalOnSensorEventMs)) * Ki_Pitch < max_output_Pitch) && ((integral_Pitch + (error * intervalOnSensorEventMs)) * Ki_Pitch > min_output_Pitch)) {
            integral_Pitch = (integral_Pitch + (error * intervalOnSensorEventMs));
        }
        P = Kp_Pitch * error;
        I = Ki_Pitch * integral_Pitch;
        D = Kd_Pitch * (error - prev_error) / intervalOnSensorEventMs;
        output_Pitch = P + I + D;

        if (output_Pitch > max_output_Pitch) {
            output_Pitch = max_output_Pitch;
        }
        if (output_Pitch < min_output_Pitch) {
            output_Pitch = min_output_Pitch;
        }
        prev_error = error;
    }

    void InitUI() {
        tvSensor = (TextView) findViewById(R.id.tvSensor);

    }

    void ResetSP() {
        SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putString("SP_Pitch", Double.toString(PV));
        edit.apply();
        GetAllPreferences(this);
    }

    void InitConsole() {
        tvConsole = (TextView) findViewById(R.id.tvConsole);
        mStringBuilder = new StringBuilder();
        tvConsole.setMovementMethod(new ScrollingMovementMethod());

    }

    void dbInsert() {
        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put("kP", Kp_Pitch);
        // Insert the new row, returning the primary key value of the new row
        long newRowId;
        newRowId = mDb.insert(
                "LOG",
                "ID",
                values);
    }

    void InitSensor() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        configureActionItem(menu);
        return super.onCreateOptionsMenu(menu);

    }

    private void configureActionItem(Menu menu) {
        swLoop = (Switch) menu.findItem(R.id.swLoopItem).getActionView()
                .findViewById(R.id.swLoop);

        // add.setOnEditorActionListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_ResetSP:
                ResetSP();
                return true;
            case R.id.action_settings:
                getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).addToBackStack(null).commit();
                return true;
            case R.id.action_ListSession:
                Intent ListSessionActivity = new Intent(getBaseContext(), ListSession.class);
			startActivity(ListSessionActivity); 
                return true;
            default:
                return super.onOptionsItemSelected(item);
                
        }
    }

    public static class PrefsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            view.setBackgroundColor(getResources().getColor(android.R.color.background_dark));
            return view;
        }

        public void onResume() {
            super.onResume();
            // Set up a listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            // Set up a listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // just update all
            GetAllPreferences(context);

        }

    }


}


