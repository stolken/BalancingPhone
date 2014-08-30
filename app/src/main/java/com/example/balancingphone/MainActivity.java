package com.example.balancingphone;


import java.util.Arrays;
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
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;


import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

public class MainActivity extends Activity implements SensorEventListener {
    UsbDeviceConnection mUsbDeviceConnection;
    UsbEndpoint mUsbEndpointIn;
    UsbEndpoint mUsbEndpointOut;
    UsbInterface mUsbInterface;
    GraphView graphView;
    TextView tvSensor;
    TextView tvConsole;
    Boolean bArduinoReady;
    Switch swLoop;
    Button bPreferences;
    Button bRX;
    Button bTX;
    Button bResetSP;
    StringBuilder mStringBuilder;
    //byte[] bytes = new byte[1];
    short servo_neutral;
    double SP_Pitch; //set point for pitch
    double PV_Pitch;

    double max_Output = 2000;
    double min_Output = 1000;


    double err_Pitch;
    double prev_err_Pitch;

    double Kp_Pitch;
    double Ki_Pitch;
    double Kd_Pitch;
    double integral_Pitch = 0;
    double output_Pitch;
    short [] output_servos;
    double onSensorChangedTimestampMs;
    double intervalTimestampMs;


    GraphViewSeries mErrorGraphViewSeries;
    GraphViewSeries mOutputGraphViewSeries;


    UsbDevice mDevice;
    int TIMEOUT = 1000;
    int X = 0;
    boolean forceClaim = true;
    private SensorManager mSensorManager;
    private Sensor mRotationVector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bArduinoReady = false;
        InitUI();
        InitConsole();
        InitGraphView();
        InitUsbConnection();
        InitSensor();
       GetAllPreferences();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
            PV_Pitch = getPitch(event);

            if (err_Pitch > 40 || err_Pitch < -40) {
                swLoop.setChecked(false);
            }
            intervalTimestampMs = event.timestamp / 1000000 - onSensorChangedTimestampMs;
            onSensorChangedTimestampMs = event.timestamp / 1000000;
            PID();

        if (bArduinoReady == true) {
            sendSerialMessage();
            readSerialMessage();
        }

            if (swLoop.isChecked()) {
                mErrorGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, err_Pitch), true, 5000);
                mOutputGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, -output_Pitch), true, 5000);
                // mSP_PitchGraphViewSeries.appendData(new GraphView.GraphViewData(onSensorChangedTimestampMs, SP_Pitch), true, 5000);
                updatetvSensor();
            }


    }





    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }


    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    protected void AddToConsole(String text) {
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
        tvSensor.setText("PV_Pitch: " + PV_Pitch + "\n"
                + "SP_Pitch: " + SP_Pitch + "\n"
                + "err_Pitch: " + err_Pitch + "\n"
                + "output_Pitch: " + output_Pitch + "\n"
                + "integral_Pitch: " + Double.toString(integral_Pitch) + "\n"
                + "interval: " + Double.toString(intervalTimestampMs) + "\n");
    }

    void updateGraph() {

        X++;
    }

    void sendSerialMessage(){

        if (mUsbEndpointOut!= null) {
            byte[] mMessage = new byte[6];



            mMessage[0] = (byte)(output_servos[0] & 0xff);
            mMessage[1] = (byte)((output_servos[0] >> 8) & 0xff);
            mMessage[2]=  (byte) 44;
            mMessage[3] = (byte)(output_servos[1] & 0xff);
            mMessage[4] = (byte)((output_servos[1] >> 8) & 0xff);
            mMessage[5] = (byte) 10; //new line
            int txBytes = mUsbDeviceConnection.bulkTransfer(mUsbEndpointOut, mMessage, mMessage.length, TIMEOUT); //do in another thread

            if (txBytes > 0) {

            } else {
                AddToConsole("Error no data transferred: " + txBytes);
            }
        }

    }

    void readSerialMessage(){

        if (mUsbEndpointIn!= null) {
            int rxBytes;
           // do{
            byte[] mMessage = new byte[64];
            // reinitialize read value byte array
            Arrays.fill(mMessage, (byte) 0);

            rxBytes = mUsbDeviceConnection.bulkTransfer(mUsbEndpointIn, mMessage, mMessage.length, TIMEOUT); //do in another thread

            if (rxBytes > 0) {
                AddToConsole(rxBytes + " byte received");
                for (int i = 0;i < rxBytes;i++){
                    AddToConsole( i + ":" + mMessage[i]);
                }
            } else {
                AddToConsole("Error no data received");
            }
          //  }while( (rxBytes > 1) );
        }

    }

    private double  getPitch(SensorEvent event) {
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
    void InitGraphView()
    {
        mErrorGraphViewSeries = new GraphViewSeries("Error",new GraphViewSeries.GraphViewSeriesStyle(Color.RED,1),new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        mOutputGraphViewSeries = new GraphViewSeries("Output",new GraphViewSeries.GraphViewSeriesStyle(Color.BLUE,1),new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        //mSP_PitchGraphViewSeries = new GraphViewSeries("SetPoint",new GraphViewSeries.GraphViewSeriesStyle(Color.BLACK,1),new GraphView.GraphViewData[]{new GraphView.GraphViewData(X, 0)});
        X++;
        graphView = new LineGraphView(this, "GraphView");
        graphView.setScrollable(true);
        graphView.getGraphViewStyle().setVerticalLabelsColor(Color.RED);
        graphView.getGraphViewStyle().setNumVerticalLabels(3);
        graphView.getGraphViewStyle().setGridColor(Color.BLACK);
        graphView.getGraphViewStyle().setVerticalLabelsWidth(1);
        graphView.setManualYAxisBounds(100, -100);
        graphView.setViewPort(2, 3500);
        graphView.addSeries(mErrorGraphViewSeries);
        graphView.addSeries(mOutputGraphViewSeries);
        //graphView.addSeries(mSP_PitchGraphViewSeries);

        LinearLayout layout = (LinearLayout) findViewById(R.id.mLayout);
        layout.addView(graphView);
    }

    void InitUsbConnection(){

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
                        AddToConsole("ControlTranfer Set");
                    }
                }

            }

        }


    }


    private short[] PID(){
        output_servos = new short[2];
        prev_err_Pitch = err_Pitch;
        err_Pitch = SP_Pitch - PV_Pitch;
        integral_Pitch = integral_Pitch + (err_Pitch * intervalTimestampMs);

        if (swLoop.isChecked()) {
            output_Pitch = (Kp_Pitch * err_Pitch) +  (Ki_Pitch * integral_Pitch) + (Kd_Pitch *  (err_Pitch -  prev_err_Pitch)/ intervalTimestampMs);
        }else{
            output_Pitch = 0;
            integral_Pitch = 0;
        }

        if (output_Pitch < min_Output){
            output_Pitch = min_Output;
        }

        if (output_Pitch > max_Output){
            output_Pitch = max_Output;
        }


        output_servos[0] = (short) (servo_neutral - output_Pitch);
        output_servos[1] = (short) (servo_neutral + output_Pitch);
        return output_servos;
    }

    void InitUI(){
        swLoop = (Switch) findViewById(R.id.swLoop);
        tvSensor = (TextView) findViewById(R.id.tvSensor);
        bPreferences = (Button) findViewById(R.id.bPreferences);
        bResetSP = (Button) findViewById(R.id.bResetSP);
        bRX = (Button) findViewById(R.id.bRX);
        bTX = (Button) findViewById(R.id.bTX);

        bPreferences.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, new PrefsFragment()).addToBackStack(null).commit();
            }
        });

        bTX.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            sendSerialMessage();

            }
        });
        bRX.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                readSerialMessage();
            }
        });

        bResetSP.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ResetSP();
            }
        });

        swLoop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                GetAllPreferences();
            }
        });
    }

    void ResetSP(){
        SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putString("SP_Pitch",Double.toString(PV_Pitch));
        edit.apply();
    }

    void InitConsole(){
        tvConsole = (TextView) findViewById(R.id.tvConsole);
                mStringBuilder = new StringBuilder();
        tvConsole.setMovementMethod(new ScrollingMovementMethod());

    }

    void  GetAllPreferences(){
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Kp_Pitch = Double.parseDouble(sharedPref.getString("Kp_Pitch", "1"));
        Ki_Pitch = Double.parseDouble(sharedPref.getString("Ki_Pitch", "1"));
        Kd_Pitch = Double.parseDouble(sharedPref.getString("Kd_Pitch", "1"));
        SP_Pitch = Double.parseDouble(sharedPref.getString("SP_Pitch", "1"));


        servo_neutral = Short.parseShort(sharedPref.getString("servo_neutral", "1500"));
    }

    void InitSensor(){

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

    }

    public  static class PrefsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            view.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_bright));

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

        }

    }

}
