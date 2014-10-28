package com.example.balancingphone;


import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.os.PowerManager;
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
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.os.PowerManager.WakeLock;


import java.nio.ByteBuffer;
//test webcommit

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

public class MainActivity extends Activity implements SensorEventListener {
    public static Context context;
    static short servo_neutral;
    static double SP; //set point for pitch
    static double max_error;
    static double Kp;
    static double Ki;
    static double Kd;
    UsbDeviceConnection mUsbDeviceConnection;
    UsbEndpoint mUsbEndpointIn;
    UsbEndpoint mUsbEndpointOut;
    GraphView graphView;


    TextView tvError;
    TextView tvOutput;
    TextView tvP;
    TextView tvI;
    TextView tvD;
    TextView tvConsole;
    Switch swLoop;

    StringBuilder mStringBuilder;
    double PV;
    short max_Output_servos = 2000;
    short min_Output_servos = 1000;
    short max_output = 500;
    short min_output = -500;
    boolean boolProximitySensorClosed;
    boolean proximityHold;
    double P;
    double I;
    double D;
    double error;
    double prev_error;
    double integral = 0;
    double output;
    short[] output_servos;
    double last_onSensorChangedTimestampMs;
    double intervalOnSensorEventMs;
    long intervalTxRxMs;
    int SessionID;
    GraphViewSeries mErrorGraphViewSeries;
    GraphViewSeries mOutputGraphViewSeries;
    GraphViewSeries mPGraphViewSeries;
    GraphViewSeries mIGraphViewSeries;
    GraphViewSeries mDGraphViewSeries;
    DbHelper mDbHelper;
    double valProximitySensor;
    WakeLock wakeLock;

    UsbDevice mDevice;
    int TIMEOUT = 1000;
    int X = 0;
    boolean forceClaim = true;
    private SensorManager mSensorManager;
    private Sensor mRotationVector;
    private Sensor mProximitySensor;

    public static void GetAllPreferences(Context ctxt) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctxt);
        Kp = Double.parseDouble(sharedPref.getString("Kp", "1"));
        Ki = Double.parseDouble(sharedPref.getString("Ki", "1"));
        Kd = Double.parseDouble(sharedPref.getString("Kd", "1"));
        SP = Double.parseDouble(sharedPref.getString("SP", "1"));
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
        mDbHelper = new DbHelper(this);
        last_onSensorChangedTimestampMs = 0;
        //mDb = mDbHelper.getWritableDatabase();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            PV = getPitch(event);
            error = (SP - PV);
            if (last_onSensorChangedTimestampMs != 0) {
                intervalOnSensorEventMs = event.timestamp / 1000000 - last_onSensorChangedTimestampMs;
            } else {
                intervalOnSensorEventMs = 0;
            }

            last_onSensorChangedTimestampMs = event.timestamp / 1000000; //record for next

            PID();
            intervalTxRxMs = sendSerialMessage();

            if ((swLoop.isChecked()) & (error < max_error & error > -max_error) & proximityHold == false) {
                updateGraphView();
                updatetvSensor();
                mDbHelper.AddRecord(ConvertToIso8601(event.timestamp), SessionID, SP, PV, error, Kp, Ki, Kd, P, I, D, output, integral, intervalOnSensorEventMs, intervalTxRxMs);
            } else if ((swLoop.isChecked()) & (error > max_error || error < -max_error)) {
                AddToConsole("Over pitch!");
                swLoop.setChecked(false);
            }
        } else if (sensor.getType() == Sensor.TYPE_PROXIMITY) {
            valProximitySensor = event.values[0];
            if (valProximitySensor != 0) {
                proximityHold = false;
            }
        }

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
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

        DecimalFormat decimalFormat = new DecimalFormat("#.#");

        tvError.setText("Error: " + decimalFormat.format(error));
        tvOutput.setText("Output: " + decimalFormat.format(output));
        tvP.setText("P: " + decimalFormat.format(P));
        tvI.setText("I: " + decimalFormat.format(I));
        tvD.setText("D: " + decimalFormat.format(D));






    }

    private String ConvertToIso8601(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String Iso8601String = sdf.format(new Date(timestamp / 1000000));
        return Iso8601String;
    }


    private long sendSerialMessage() {
        long ret = 0;
        long beforeSend = System.nanoTime();
        if (mUsbEndpointOut != null) {
            byte[] mMessageTx = new byte[4];
            ByteBuffer.wrap(mMessageTx, 0, 2).putShort(output_servos[0]);
            ByteBuffer.wrap(mMessageTx, 2, 2).putShort(output_servos[1]);
            //mMessage[4] = (byte) 10; //new line
            int txBytes = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, mMessageTx, mMessageTx.length, TIMEOUT); //do in another thread

            if (txBytes > 0) {
                byte[] mMessageRx = new byte[64];
                // reinitialize read value byte array
                Arrays.fill(mMessageRx, (byte) 0);
                int rxBytes;
                rxBytes = mUsbDeviceConnection.bulkTransfer(mUsbEndpointIn, mMessageRx, mMessageRx.length, TIMEOUT);
                if (rxBytes > 0) {
                    byte mByte = mMessageRx[0];
                    if (mByte == mMessageTx.length) {
                        long afterSend = System.nanoTime();
                        ret = (afterSend - beforeSend);
                    }

                } else {
                    ret = -3;
                }
            } else {
                ret = -1;
            }
        } else {
            ret = -2;
        }
        return ret;
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

    private void WakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();
    }


    private void WakeRelease() {
        wakeLock.release();
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


    void updateGraphView() {
        mErrorGraphViewSeries.appendData(new GraphView.GraphViewData(last_onSensorChangedTimestampMs, error / max_error * 100), true, 5000);
        mOutputGraphViewSeries.appendData(new GraphView.GraphViewData(last_onSensorChangedTimestampMs, output / max_output * 100), true, 5000);
        mPGraphViewSeries.appendData(new GraphView.GraphViewData(last_onSensorChangedTimestampMs, P / max_output * 100), true, 5000);
        mIGraphViewSeries.appendData(new GraphView.GraphViewData(last_onSensorChangedTimestampMs, I / max_output * 100), true, 5000);
        mDGraphViewSeries.appendData(new GraphView.GraphViewData(last_onSensorChangedTimestampMs, D / max_output * 100), true, 5000);
    }


    void resetGraphView() {
        graphView.removeAllSeries();
        //new GraphView.GraphViewData(X, 0)
        mErrorGraphViewSeries = new GraphViewSeries("Error", new GraphViewSeries.GraphViewSeriesStyle(Color.RED, 5), new GraphView.GraphViewData[]{});
        mOutputGraphViewSeries = new GraphViewSeries("Output", new GraphViewSeries.GraphViewSeriesStyle(Color.WHITE, 5), new GraphView.GraphViewData[]{});
        mPGraphViewSeries = new GraphViewSeries("P", new GraphViewSeries.GraphViewSeriesStyle(Color.BLUE, 2), new GraphView.GraphViewData[]{});
        mIGraphViewSeries = new GraphViewSeries("I", new GraphViewSeries.GraphViewSeriesStyle(Color.GREEN, 2), new GraphView.GraphViewData[]{});
        mDGraphViewSeries = new GraphViewSeries("D", new GraphViewSeries.GraphViewSeriesStyle(Color.YELLOW, 2), new GraphView.GraphViewData[]{});
        graphView.addSeries(mErrorGraphViewSeries);
        graphView.addSeries(mOutputGraphViewSeries);
        graphView.addSeries(mPGraphViewSeries);
        graphView.addSeries(mIGraphViewSeries);
        graphView.addSeries(mDGraphViewSeries);

    }

    void InitGraphView() {


        X++;
        graphView = new LineGraphView(this, "PID");
        graphView.setScrollable(true);
        graphView.getGraphViewStyle().setVerticalLabelsColor(Color.RED);
        // graphView.setShowLegend(true);
        graphView.setShowHorizontalLabels(false);
        graphView.setShowVerticalLabels(false);
        graphView.getGraphViewStyle().setTextSize(40);
        //graphView.setLegendAlign(GraphView.LegendAlign.MIDDLE);
        graphView.getGraphViewStyle().setNumVerticalLabels(3);
        graphView.getGraphViewStyle().setGridColor(Color.GRAY);
        graphView.getGraphViewStyle().setVerticalLabelsWidth(1);
        graphView.setHorizontalLabels(null);
        graphView.setManualYAxisBounds(100, -100);
        graphView.setViewPort(2, 2000);


        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mLayout);
        layout.addView(graphView);
    }

    void InitUsbConnection() {

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


        if (((integral + (error * intervalOnSensorEventMs)) * Ki < max_output) && ((integral + (error * intervalOnSensorEventMs)) * Ki > min_output)) {
            integral = (integral + (error * intervalOnSensorEventMs));
        }
        P = Kp * error;
        I = Ki * integral;
        D = Kd * (error - prev_error) / intervalOnSensorEventMs;

        if (swLoop.isChecked()) {
            output = P + I + D;
        } else {
            output = 0;
        }

        if (output > max_output) {
            output = max_output;
        }
        if (output < min_output) {
            output = min_output;
        }
        prev_error = error;


        output_servos[0] = (short) (servo_neutral - output);
        output_servos[1] = (short) (servo_neutral + output);

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
    }

    void InitUI() {
        tvError = (TextView) findViewById(R.id.tvError);
        tvOutput = (TextView) findViewById(R.id.tvOutput);
        tvP = (TextView) findViewById(R.id.tvP);
        tvI = (TextView) findViewById(R.id.tvI);
        tvD = (TextView) findViewById(R.id.tvD);



    }

    void ResetSP() {
        SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putString("SP", Double.toString(PV));
        edit.apply();
        GetAllPreferences(this);
        AddToConsole("New SP: " + SP);

    }

    void InitConsole() {
        tvConsole = (TextView) findViewById(R.id.tvConsole);
        mStringBuilder = new StringBuilder();
        tvConsole.setMovementMethod(new ScrollingMovementMethod());

    }


    void InitSensor() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
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
        swLoop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    WakeLock();
                SessionID = mDbHelper.GetNewSessionID();
                    setTitle("Session ID " + SessionID);
                integral = 0;
                    resetGraphView();

                    if (valProximitySensor == 0) {
                        proximityHold = true;
                    }
                } else {
                    WakeRelease();
                }
            
            }
        });

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
            case R.id.action_DeleteSession:
                int sessionDeteled = mDbHelper.DeleteLastSession();

                AddToConsole("Session " + sessionDeteled + " deleted...");
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


