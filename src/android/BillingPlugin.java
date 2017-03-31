package com.jernung.plugins.billing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;

import org.apache.cordova.PluginResult;
import org.chromium.content.app.ContentApplication;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;

public class BillingPlugin extends CordovaPlugin implements ServiceConnection {

    private static final String PLUGIN_NAME = "BillingPlugin";

    private static final int BILLING_RESPONSE_RESULT_OK = 0;
    private static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    private static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
    private static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    private static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    private static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    private static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    private static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    private static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    private PluginResult mActivityResult;
    private CallbackContext mActivityContext;
    private IInAppBillingService mService;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        // bind billing service to the vending package
        Log.d(PLUGIN_NAME, "binding package to service");
        Intent mServiceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        mServiceIntent.setPackage("com.android.vending");
        cordova.getActivity().bindService(mServiceIntent, this, ContentApplication.BIND_AUTO_CREATE);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getProduct".equals(action)) {
            callbackContext.success(getProduct(args.getString(0), args.getString(1)));
            return true;
        }

        if ("getPurchases".equals(action)) {
            callbackContext.success(getPurchases(args.getString(0)));
            return true;
        }

        if ("makePurchase".equals(action)) {
            callbackContext.success(makePurchase(args.getString(0), args.getString(1)));
            return true;
        }

        if ("on".equals(action)) {
            callbackContext = mActivityContext;
            return true;
        }

        return false;
    }

    @Override
    public void onDestroy(){
        Log.d(PLUGIN_NAME, "unbinding package from service");
        cordova.getActivity().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Log.d(PLUGIN_NAME, "connected to service");
        mService = IInAppBillingService.Stub.asInterface(service);

    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(PLUGIN_NAME, "disconnected from service");
        mService = null;

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String purchaseSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
            PluginResult result;

            if (resultCode == Activity.RESULT_OK) {
                try {
                    JSONObject jo = new JSONObject(purchaseData);
                    String sku = jo.getString("productId");

                    result = new PluginResult(PluginResult.Status.OK, 'ok');
                } catch (JSONException error) {
                    result = new PluginResult(PluginResult.Status.ERROR, 'error');
                    error.printStackTrace();
                }

                result.setKeepCallback(true);

                this.mActivityContext.sendPluginResult(result);
            }
        }
    }

    private JSONObject getProduct(String productId, String productType) {
        JSONObject result = new JSONObject();
        String packageName = cordova.getActivity().getPackageName();
        ArrayList<String> productList = new ArrayList<String>();
        Bundle queryList = new Bundle();
        Bundle queryResult;

        try {
            // add requested productId to the productList
            productList.add(productId);

            // add requested productList to bundle queryList
            queryList.putStringArrayList("ITEM_ID_LIST", productList);

            // get sku details using queryList and productType
            queryResult = mService.getSkuDetails(3, packageName, productType, queryList);

            // check whether response was successful or not
            if (queryResult.getInt("RESPONSE_CODE") == 0) {
                ArrayList<String> productDetails = queryResult.getStringArrayList("DETAILS_LIST");

                // only create a new object if something exists
                if (productDetails != null && !productDetails.isEmpty()) {
                    result = new JSONObject(productDetails.get(0));
                }
            } else {
                Log.d(PLUGIN_NAME, "product request failed (code " + queryResult.getInt("RESPONSE_CODE") + ")");
            }
        } catch (JSONException error) {
            return result;
        } catch (RemoteException error) {
            return result;
        }

        return result;
    }

    private JSONArray getPurchases(String purchaseType) {
        JSONArray result = new JSONArray();
        String packageName = cordova.getActivity().getPackageName();
        Bundle queryResult;

        try {
            // get purchases using the purchaseType
            queryResult = mService.getPurchases(3, packageName, purchaseType, null);

            // check whether response was successful or not
            if (queryResult.getInt("RESPONSE_CODE") == 0) {
                ArrayList<String> purchasedDetails = queryResult.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

                // only put a new object if something exists
                if (purchasedDetails != null) {
                    for (int i = 0; i < purchasedDetails.size(); ++i) {
                        result.put(new JSONObject(purchasedDetails.get(i)));
                    }
                }
            } else {
                Log.d(PLUGIN_NAME, "purchases request failed (code " + queryResult.getInt("RESPONSE_CODE") + ")");
            }
        } catch (JSONException error) {
            return result;
        } catch (RemoteException error) {
            return result;
        }

        return result;
    }

    private JSONObject makePurchase(String productId, String productType) {
        JSONObject result = new JSONObject();
        String packageName = cordova.getActivity().getPackageName();
        Bundle purchaseBundle;
        PendingIntent pendingIntent;

        try {
            Log.d(PLUGIN_NAME, "initializing " + productType + " purchase:" + productId);

            purchaseBundle = mService.getBuyIntent(3, packageName, productId, productType, new BigInteger(130, new SecureRandom()).toString(32));

            pendingIntent = purchaseBundle.getParcelable("BUY_INTENT");

            if (pendingIntent != null) {
                Log.d(PLUGIN_NAME, "starting purchase intent");
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);
            } else {
                Log.d(PLUGIN_NAME, "already purchased product");
            }
        } catch (RemoteException error) {
            return result;
        } catch (IntentSender.SendIntentException error) {
            return result;
        }

        return result;
    }

}
