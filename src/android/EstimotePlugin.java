package org.apache.cordova.estimote;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.util.Log;
import android.os.Build;
import android.annotation.TargetApi;

import com.estimote.sdk.Region;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class EstimotePlugin extends CordovaPlugin 
{

    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    private static final String LOG_TAG					= "EstimotePlugin";
	
	private static final String ACTION_START	= "start";
    private static final String ACTION_STOP 	= "stop";

    private BeaconManager beaconManager;

	/**
	 * Callback context for device ranging actions.
	 */
	private CallbackContext rangingCallback;

    public EstimotePlugin() {
        this.beaconManager = null;
    }

	
	/**
	 * Executes the given action.
	 * 
	 * @param action		    The action to execute.
	 * @param args			    Potential arguments.
	 * @param callbackContext	The callback context used when calling back into JavaScript.
	 */
	@Override
	public boolean execute(final String action, JSONArray args, final CallbackContext callbackContext)
	{
        if (action.equals("start")) {
            if (this.rangingCallback != null) {
                callbackContext.error( "Beacon listener already running.");
                return true;
            }

            this.rangingCallback = callbackContext;

            if (beaconManager == null) {

                final Activity activity = this.cordova.getActivity();
                final Context context = activity.getApplicationContext();
                beaconManager = new BeaconManager(context);

                beaconManager.setRangingListener(new BeaconManager.RangingListener() {
                    @Override
                    public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
                        Log.d(LOG_TAG, "Ranged beacons: " + beacons);
                        for(Beacon b: beacons) {
                            updateBeaconInfo(b);
                        };
                    }
                });

                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        if (!beaconManager.hasBluetooth()) {
                            rangingCallback.error("Device does not have Bluetooth Low Energy");
                        } else {
                            connectToService();
                        }
                    }
                });
            }

            // Don't return any result now, since status results will be sent when events come in from broadcast receiver
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);

            return true;
        }

        else if (action.equals("stop")) {
            removeBeaconListener();
            this.sendUpdate(new JSONObject(), false); // release status callback in JS side
            this.rangingCallback = null;
            callbackContext.success();
            return true;
        }

        return false;
	}

    @Override
    public void onDestroy() {
        removeBeaconListener();
    }

    @Override
    public void onReset() {
        removeBeaconListener();
    }

    private void removeBeaconListener() {
        if (this.beaconManager != null) {
            try {
                this.beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS);
                this.beaconManager.disconnect();
                this.beaconManager = null;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error disconnecting beacon manager: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a JSONObject with the current beacon information
     *
     * @param beacon the current beacon information
     * @return a JSONObject containing the beacon status information
     */
    private JSONObject getBeaconInfo(Beacon beacon) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", beacon.getName());
            obj.put("address", beacon.getMacAddress());
            obj.put("proximityUUID", beacon.getProximityUUID());
            obj.put("major", beacon.getMajor());
            obj.put("minor", beacon.getMinor());
            obj.put("measuredPower", beacon.getMeasuredPower());
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return obj;
    }

    /**
     * Updates the JavaScript side whenever a beacon is discovered
     *
     * @param beacon the current beacon information
     * @return
     */
    private void updateBeaconInfo(Beacon beacon) {
        sendUpdate(this.getBeaconInfo(beacon), true);
    }

    /**
     * Create a new plugin result and send it back to JavaScript
     *
     */
    private void sendUpdate(JSONObject info, boolean keepCallback) {
        if (this.rangingCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            this.rangingCallback.sendPluginResult(result);
        }
    }

    private void connectToService() {
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                try {
                    beaconManager.startRanging(ALL_ESTIMOTE_BEACONS);
                    JSONObject event = new JSONObject();
                    event.put("event", "connected");
                    sendUpdate(event, true);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Cannot start ranging", e);
                    EstimotePlugin.this.error(rangingCallback, "Cannot start ranging::" + e.getMessage(), BluetoothError.ERR_UNKNOWN);
                }
            }
        });
    }
	

	/**
	 * Send an error to given CallbackContext containing the error code and message.
	 * 
	 * @param ctx	Where to send the error.
	 * @param msg	What seems to be the problem.
	 * @param code	Integer value as a an error "code"
	 */
	private void error(CallbackContext ctx, String msg, int code)
	{
		try
		{
			JSONObject result = new JSONObject();
			result.put("message", msg);
			result.put("code", code);
			
			ctx.error(result);
		}
		catch(Exception e)
		{
			Log.e(LOG_TAG, "Error with... error raising, " + e.getMessage());
		}
	}	
		
}
