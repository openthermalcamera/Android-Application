package com.themarpe.openthermalcamera;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Set;

class OTC {

    public static final String TAG = "OTC";

    //Otc private variables
    public static final float MLX_MIN_TEMP = -40.0f;
    public static final float MLX_MAX_TEMP = 300.0f;

    public static final int IR_WIDTH = 32;
    public static final int IR_HEIGHT = 24;

    public static final int IR_SPECTRUM_WIDTH = 1000;
    public static final int IR_SPECTRUM_HEIGHT = 1;

    private static double minIrTemp = 9999.0, maxIrTemp = -9999.0;

    private static double[] irTemp = new double[IR_WIDTH * IR_HEIGHT];
    private static double[] irTempFlipped = new double[IR_WIDTH * IR_HEIGHT];

    private static int taShift = 8;

    private Context ctx = null;

    private static Protocol protocol = null;
    private static MLX90640 mlxapi = new MLX90640();
    private static MLX90640.Params mlxparams = new MLX90640.Params();

    private static boolean parametersAvailable = false;

    private static double emissivity = 0.9;

    //Usb private variables
    private static UsbService usbService;
    private static MyHandler mHandler = null;

    enum UsbState {
        CONNECTED, DISCONNECTED
    }
    private static UsbState usbState = UsbState.DISCONNECTED;

    enum OTCState {
        READY, NO_PERMISSIONS, NOT_READY
    }
    private static OTCState otcState = OTCState.NOT_READY;


    public OTCState getOTCState(){
        return otcState;
    }

    public UsbState getUsbState(){
        return usbState;
    }


    //settings
    static class Settings {
        Protocol.RefreshRate refreshRate;
        Protocol.ScanMode scanMode;
        Protocol.Resolution resolution;
    }

    interface FirmwareVersionListener {
        void firmwareVersion(Protocol.FirmwareVersion firmwareVerion);
    }
    ArrayList<FirmwareVersionListener> fwListener = new ArrayList<>();

    interface ResponseListener{
        void onResponsePre(Protocol.RspStruct rsp);
        void onResponsePost(Protocol.RspStruct rsp);
    }
    ResponseListener responseListener = null;

    void setResponseListener(ResponseListener rl){
        responseListener = rl;
    }


    interface StateListener {
        void onStateChanged(OTCState otcState, UsbState usbState);
    }
    StateListener stateListener = null;


    private static ArrayList<WeakReference<OTC>> objectReferences = new ArrayList<>();

    public OTC(Context ctx, StateListener stateListener){
        //add to pool of OTC objects.
        objectReferences.add(new WeakReference<>(this));

        //state listener
        this.stateListener = stateListener;

        //context
        this.ctx = ctx;

        //create the protocol driver if it doesnt exist yet
        if(protocol == null) {
            protocol = new Protocol(new Protocol.ISender() {
                @Override
                public void sendBytes(byte[] bytesToSend) {
                    String test = "[";
                    for (int i = 0; i < bytesToSend.length; i++) {
                        if (i != 0) {
                            test += " ,";
                        }
                        test += bytesToSend[i];
                    }
                    test += "]";
                    Log.d("OTC", "About to send " + bytesToSend.length + " = " + test);
                    if (usbService != null) {
                        usbService.write(bytesToSend);
                    }
                }
            }, new Protocol.IResponseListener() {
                @Override
                public void onResponse(Queue<Protocol.RspStruct> q) {
                    while (q.size() > 0) {
                        Protocol.RspStruct rsp = q.poll();
                        handleResponse(rsp);
                    }
                }
            });
        }

        //create usb serial connection
        if(mHandler == null){
            mHandler = new MyHandler(protocol);
        }
    }



    // Events from UsbService
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {

                case UsbService.ACTION_USB_READY:
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();

                    //usb state disconnected, otc
                    usbState = UsbState.CONNECTED;
                    otcState = OTCState.READY;

                    break;

                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Permissions granted", Toast.LENGTH_SHORT).show();

                    //usb state disconnected, otc
                    usbState = UsbState.CONNECTED;
                    otcState = OTCState.NOT_READY;

                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();

                    //usb state disconnected, otc
                    usbState = UsbState.CONNECTED;
                    otcState = OTCState.NO_PERMISSIONS;

                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();

                    //usb state disconnected, otc
                    usbState = UsbState.DISCONNECTED;
                    otcState = OTCState.NOT_READY;

                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();

                    //usb state disconnected, otc
                    usbState = UsbState.DISCONNECTED;
                    otcState = OTCState.NOT_READY;

                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();

                    //otc not ready
                    otcState = OTCState.NOT_READY;
                    usbState = UsbState.CONNECTED;

                    break;
            }


            //notify listeners
            if(stateListener != null) {
                stateListener.onStateChanged(otcState, usbState);
            }

        }
    };



    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };


    public void startUsbService() {
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    public void stopUsbService() {
        //stop autoframe sending
        setAutoFrameSending(false);


        ctx.unregisterReceiver(mUsbReceiver);
        ctx.unbindService(usbConnection);
    }

    public void pauseOTC(){
        //stop autoframe sending
        if(usbState == UsbState.CONNECTED && otcState == OTCState.READY) {
            setAutoFrameSending(false);
        }
    }
    public void resumeOTC(){
        //if usb connected
        if(usbState == UsbState.CONNECTED && otcState == OTCState.READY) {
            //check if eeprom data is available
            if(!parametersAvailable){
                requestDumpEE();
            }

            setAutoFrameSending(true);
        }
    }

    public void setEmissivity(double emissivity){
        this.emissivity = emissivity;
    }

    public double getEmissivity(){
        return emissivity;
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(ctx, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            ctx.startService(startService);
        }
        Intent bindingIntent = new Intent(ctx, service);
        ctx.bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_READY);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        ctx.registerReceiver(mUsbReceiver, filter);
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<Protocol> mProtocol;

        public MyHandler(Protocol protocol) {
            mProtocol = new WeakReference<>(protocol);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:

                    mProtocol.get().handleNewData((byte[]) msg.obj);

                    break;

            }
        }
    }


    public void jumpToBootloader(){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_JUMP_TO_BOOTLOADER;
        cmd.dataLength = 0;
        protocol.sendCommand(cmd);
    }

    public void getFirmwareVersion(FirmwareVersionListener listener){
        fwListener.add(listener);
        requestFirmwareVersion();
    }


    public Settings getSettingsFromSharedPreferences(){
        Settings cur = new Settings();

        //get settings
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        cur.refreshRate = Protocol.RefreshRate.valueOf(prefs.getString("refresh_rate", Protocol.RefreshRate.HZ_2.name()));
        cur.resolution = Protocol.Resolution.valueOf(prefs.getString("resolution", Protocol.Resolution.BIT_16.name()));
        cur.scanMode = Protocol.ScanMode.valueOf(prefs.getString("mode", Protocol.ScanMode.CHESS.name()));

        return cur;
    }


    public void sendSettings(Settings settings){

        //initiate sequence of commands
        //request EE
        requestDumpEE();

        //set refresh rate
        setRefreshRate(settings.refreshRate);

        //set resolution
        setResolution(settings.resolution);

        //set ScanMode
        setScanMode(settings.scanMode);

        //enable auto frame sending
        setAutoFrameSending(true);

    }

    //2d temp datat
    static double[][] tempData2D = new double[OTC.IR_HEIGHT][OTC.IR_WIDTH];

    //returns ir temperature data [height][width]
    public double[][] getIrTemp(){
        return tempData2D;
    }

    public double getMinIrTemp(){
        return minIrTemp;
    }
    public double getMaxIrTemp(){
        return maxIrTemp;
    }


    private void setAutoFrameSending(boolean enabled){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_SET_AUTO_FRAME_DATA_SENDING;
        cmd.dataLength = 1;

        if(enabled){
            //send auto frame data enabled command
            cmd.data.add(1);
        }else{
            //send auto frame data disabled command
            cmd.data.add(0);
        }

        protocol.sendCommand(cmd);
    }


    private void setRefreshRate(Protocol.RefreshRate refreshRate){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_SET_REFRESH_RATE;
        cmd.dataLength = 1;

        cmd.data.add(refreshRate.getValue());

        protocol.sendCommand(cmd);
    }

    private void setResolution(Protocol.Resolution resolution){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_SET_RESOLUTION;
        cmd.dataLength = 1;

        cmd.data.add(resolution.getValue());

        protocol.sendCommand(cmd);
    }

    private void setScanMode(Protocol.ScanMode scanMode){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_SET_MODE;
        cmd.dataLength = 1;

        cmd.data.add(scanMode.getValue());

        protocol.sendCommand(cmd);
    }

    private void requestDumpEE(){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_DUMP_EE;
        cmd.dataLength = 0;

        protocol.sendCommand(cmd);
    }

    private void requestFirmwareVersion(){
        Protocol.CmdStruct cmd = new Protocol.CmdStruct();
        cmd.commandCode = Protocol.CMD_GET_FIRMWARE_VERSION;
        cmd.dataLength = 0;
        protocol.sendCommand(cmd);
    }

    public static void handleResponse(Protocol.RspStruct rsp) {

        for(WeakReference<OTC> wrOtc : objectReferences){
            OTC ref = wrOtc.get();
            if(ref != null){
                if(ref.responseListener != null) {
                    ref.responseListener.onResponsePre(rsp);
                }
            }
        }


        switch (rsp.responseCode) {
            case Protocol.RSP_PING: //PING
                if(rsp.data.size() == 1) {
                    int pong = rsp.data.get(0);
                    Log.d("PONG", "Pong = " + pong);
                }
                break;

            case Protocol.RSP_DUMP_EE: //DumpEE

                //eeDump are 16bit values, so we combine 2 bytes together
                int[] eedump = new int[832];
                for(int i = 0; i<832; i++){
                    eedump[i] = ((rsp.data.get(i*2 + 0) & 0xFF) << 8) | ((rsp.data.get(i*2 + 1) & 0xFF));
                }

                //extract parameters
                int error = mlxapi.ExtractParameters(eedump, mlxparams);

                if(error != 0){
                    Log.e("OTC","DumpEE, error code = " + error);
                } else {
                    //extraction successful
                    parametersAvailable = true;
                }

                break;

            case Protocol.RSP_GET_FRAME_DATA: //GetFrameData

                //framedata are 16bit values, so we combine 2 bytes together
                int[] frameData = new int[834];
                for(int i = 0; i<834; i++){
                    frameData[i] = ((rsp.data.get(i*2 + 0) & 0xFF) << 8) | ((rsp.data.get(i*2 + 1) & 0xFF));
                }

                //Log.d("GetFrameData", "rsp.data.size() = " + rsp.data.size() + ", rsp.dataLength = " + rsp.dataLength);


                //Get Subpage number first
                int subpage = mlxapi.GetSubPageNumber(frameData);

                //Get scan mode (0 = interleaved, 1 chessmode)
                //TODO get scanmode from settings
                Protocol.ScanMode scanMode = Protocol.ScanMode.CHESS;

                double tr = mlxapi.GetTa(frameData, mlxparams) - taShift;

                mlxapi.CalculateTo(frameData, mlxparams, emissivity, tr, irTemp);

                //flip  the temperature
                for(int y = 0; y<IR_HEIGHT; y++){
                    for(int x = 0; x<IR_WIDTH; x++){
                        irTempFlipped[x + (y * IR_WIDTH)] = irTemp[IR_WIDTH - 1 - x + (y * IR_WIDTH)];
                    }
                }

                //2d
                for(int i = 0; i<OTC.IR_HEIGHT * OTC.IR_WIDTH; i++){
                    tempData2D[i / OTC.IR_WIDTH][i % OTC.IR_WIDTH] = irTempFlipped[i];
                }

                //get min and max temps

                minIrTemp = irTempFlipped[0];
                maxIrTemp = irTempFlipped[0];

                for(int i = 1; i<IR_WIDTH * IR_HEIGHT; i++){
                    minIrTemp = Math.min(minIrTemp, irTempFlipped[i]);
                    maxIrTemp = Math.max(maxIrTemp, irTempFlipped[i]);
                }

                break;

            case Protocol.RSP_SET_RESOLUTION: //Set Resolution
                break;
            case Protocol.RSP_GET_CUR_RESOLUTION: //Get Current resolution
                break;

            case Protocol.RSP_SET_REFRESH_RATE: //Set Refresh Rate
                break;

            case Protocol.RSP_GET_REFRESH_RATE: //Get Refresh Rate
                break;

            case Protocol.RSP_SET_MODE: //Set Mode
                break;

            case Protocol.RSP_GET_CUR_MODE: //Get Cur Mode
                break;

            case Protocol.RSP_GET_FIRMWARE_VERSION :
                //parse response
                Protocol.FirmwareVersion deviceVersion = Protocol.FirmwareVersion.parse(rsp.data);

                for(WeakReference<OTC> wrOtc : objectReferences){
                    OTC ref = wrOtc.get();
                    if(ref != null){
                        for(FirmwareVersionListener listener :  ref.fwListener){
                            listener.firmwareVersion(deviceVersion);
                        }
                        ref.fwListener.clear();
                    }
                }


                Log.d("GetFirmwareVersion","Device version = " + deviceVersion);

                break;

            default:
                //NOT COOL!
                //TODO: Add handler for this
                break;
        }

        for(WeakReference<OTC> wrOtc : objectReferences){
            OTC ref = wrOtc.get();
            if(ref != null){
                if(ref.responseListener != null) {
                    ref.responseListener.onResponsePost(rsp);
                }
            }
        }

    }



}
