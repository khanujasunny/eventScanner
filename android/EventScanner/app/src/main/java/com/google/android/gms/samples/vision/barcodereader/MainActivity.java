/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.samples.vision.barcodereader;

import android.Manifest;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.util.UUID;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

/**
 * Main activity demonstrating how to pass extra parameters to an activity that
 * reads barcodes.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    // use a compound button so either checkbox or switch widgets work.
    private CompoundButton autoFocus;
    private CompoundButton useFlash;
    private TextView statusMessage;
    private TextView barcodeValue;

    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final String TAG = "BarcodeMain";
    private String projectID;
    private Button resetProject;


    /*AWS Related stuffs*/

    // --- Constants to modify per your configuration ---

    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
//    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "arn:aws:iot:us-east-1:033833258996";
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a1a5vmkre2h9ju-ats.iot.us-east-1.amazonaws.com";
    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_EAST_1;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "eventscan.cert.pem";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "eventscan-private.pem.key";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "5eeefa2582cbf1d2c0ed87544a1953b8d173701a61b909866d5ebe62fa166510";

    AWSIotMqttManager mqttManager;
    private KeyStore clientKeyStore;
//    private String keystorePath = "/sdcard/Download";
    private String keystorePath;
    private boolean readyToScan = false;
    private Button btnReadBarcode;
    private CheckBox disableValidation;
    /*AWS Related Stuffs ENDS*/


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        resetProject = (Button) findViewById(R.id.reset_project);
        btnReadBarcode = (Button) findViewById(R.id.read_barcode);
        statusMessage = (TextView)findViewById(R.id.status_message);
        barcodeValue = (TextView)findViewById(R.id.barcode_value);

        autoFocus = (CompoundButton) findViewById(R.id.auto_focus);
        useFlash = (CompoundButton) findViewById(R.id.use_flash);
        disableValidation = (CheckBox) findViewById(R.id.disableValidation);

        autoFocus.setChecked(true);
        findViewById(R.id.read_barcode).setOnClickListener(this);
        resetProject.setOnClickListener(this);

        keystorePath = getApplicationContext().getFilesDir().getPath();
        connectAWSServer();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[], int[] grantResults)
    {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connectAWSServer();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                    statusMessage.setText("Permission denied to read your External storage.");
                    barcodeValue.setText("Please goto Settings and allow file storage");
                }
            }
        }
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.read_barcode) {
            // launch barcode activity.
            Intent intent = new Intent(this, BarcodeCaptureActivity.class);
            intent.putExtra(BarcodeCaptureActivity.AutoFocus, autoFocus.isChecked());
            intent.putExtra(BarcodeCaptureActivity.UseFlash, useFlash.isChecked());

            startActivityForResult(intent, RC_BARCODE_CAPTURE);
        }

        if (v.getId() == R.id.reset_project) {
            projectID = null;
            statusMessage.setText("Scan Barcode");
            barcodeValue.setText("Please select a valid Project");

        }

    }

    /**
     * Called when an activity you launched exits, giving you the requestCode
     * you started it with, the resultCode it returned, and any additional
     * data from it.  The <var>resultCode</var> will be
     * {@link #RESULT_CANCELED} if the activity explicitly returned that,
     * didn't return any result, or crashed during its operation.
     * <p/>
     * <p>You will receive this call immediately before onResume() when your
     * activity is re-starting.
     * <p/>
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     * @see #startActivityForResult
     * @see #createPendingResult
     * @see #setResult(int)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    Log.d(TAG, "Barcode read: " + barcode.displayValue);

                    this.postToServer(barcode.displayValue);
                } else {
                    statusMessage.setText(R.string.barcode_failure);
                    Log.d(TAG, "No barcode captured, intent data is null");
                }
            } else {
                statusMessage.setText(String.format(getString(R.string.barcode_error),
                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void postToServer(String code){
        if(projectID == null){
            if(!code.contains("ES") && !disableValidation.isChecked()){
                statusMessage.setText("Please scan valid project first");
                barcodeValue.setText(code+" is not a valid project id");
            }else{
                projectID = code;
                statusMessage.setText("Please scan SID");
                barcodeValue.setText("Project Selected: "+code);
            }
        }else{
            if((Character.isAlphabetic(code.charAt(0)) && !code.contains("ES")) || disableValidation.isChecked()){
                JSONObject postData = new JSONObject();
                statusMessage.setText("Sending to Server...");
                postToAWSServer(code,projectID);
                /*try {
                    postData.put("projectCode", projectID);
                    postData.put("SID", code);
                    new SendDeviceDetails().execute("http://sunnywiki.com/eventScanner/api/saveBarCode.php", postData.toString());
                } catch (JSONException e) {
                    statusMessage.setText("Error occured");
                    e.printStackTrace();
                }*/

            }else{
                statusMessage.setText(code+" is not a valid SID");
            }
        }

    }


    private void connectAWSServer(){
        String keyPem = "-----BEGIN RSA PRIVATE KEY-----\n" +
                "MIIEpQIBAAKCAQEA0Ew43PHViJUDoIjnUqN2+zHqIP8NPPBweSZF1OnuqVyafE61\n" +
                "RMotiHDQqnFQBY0IbM/FE9dy7xlIEJmWY55MQQgU49JRoUxdy5xs6526yKAL72Jo\n" +
                "UZDWV3Y601dnlJEQiJ4wSUdMeKTw6eC9f5J/BvFVOEdwDXph6BCTJGv1n5at/gRS\n" +
                "n/fCoGwS3sEutVruG8QOt8N7ojEZvaBavMMOvbe5P6EJ1b8UqHOBQJPvDNnUj3q3\n" +
                "+wWb7A7mGSYH2D+UNOJigQyXBawj9l4FckiQNHvDsUDpoOc9YabOFycJzMQWOHi0\n" +
                "aH0h/cWSKtH6o9tVJ5vHZOwETydiVQ7MvA1ayQIDAQABAoIBAQCTD5198Iev/HUp\n" +
                "HD8lix9vzhfc3/W6to7SGgFnhxnnHOg9J1y3in6HPA82pvW2VZJDMJWVoqEUyial\n" +
                "Aaq5/oMbztbg2haj6MS4AmKsHxpGlyiWBEZegAG0klaJ68XHkHu52OWCdwI4k0s7\n" +
                "9F4V+ZoRjsV6DIXCHkuNilY4erhZyMQciiMq2WwhRyhOrS46h/Sx7K7n2TDa56WM\n" +
                "cmOtHvOuV5wwwrPrWVDwJ3E2iI5XgqmSCL4eUuWxnFQznOHv4FE3n5SD3aqo7Jho\n" +
                "ajih7evm24JXNj8E2wnPyxVZ+xi0nTX3mArnQP2ySyE6wQYrOsdhECxBKGO7o3pR\n" +
                "iSFKEFN5AoGBAO8+I5um3c3ELhZiQJB+r6NXRG93gTfTFR52/h61qhqa6PKBwO29\n" +
                "wADKjCBEJDtNe/ntxH8J/G+JBVzfERtEede5VeE6Nf7367c1UHjE1N17ghH5NNE6\n" +
                "dZ0/xA5sd+XcktmznxUFEx6u1a+MYlyWRA483Waet88Iha4g82CeTRvbAoGBAN7j\n" +
                "NWDd9y7tmMUSQSQyL8LiUs1hTMMbF9zYZlNEmJFFDy+B9kJTH56er0IaOdSclQ3C\n" +
                "7by0FN6X/EfXXEyLQvr96wDjD005w2IMVslCn2hhXLOYmIxZa2GCGF2e4sd6BkQS\n" +
                "fP2dZdjXzSzpx4y5H3INFRRyvpaQQTO6R3gumxcrAoGAJsHhUOD6g9ApSzUFkqMD\n" +
                "XynPC2PHyjxm6nWKe30gnojD/i1pDNq1lSs7Aisn13eZAwcy0wXSIFuJQ99bTRiN\n" +
                "yJXcxM0CXFjbleWMMNRqS6srii/eD5sx3JSs9U07K0DNhXkk52nYDBt0wKi0cp1h\n" +
                "TxErKOnDi0WtKmVqKBfdFAsCgYEAlBgRinhRWfwCqsazQ7KY63tnmxEQaP6if1nF\n" +
                "u4Pzf2qMaXuHvY/vjXxQZLJ6RFt56jffsKdSyoff13gv2qgZbB20vNUhgKVlvcsH\n" +
                "CxjaRAeVCbvVeEOdxp8jQ2ljszjP2wERzY18c3UH3dTDgywpyaUJoZmQKwhUWmNm\n" +
                "Q2NsJxsCgYEAoSBM/tXZWP2Fy81IsH56aFsMvVGDvInQ3sG6BW4W8KKJFmM4v9xU\n" +
                "MzybkPDmIZGYVi8hESFRcNGSYZ+Hw2GWu3tzEvNnrZzoo9pFE4NKkgXnAZQ/O2iR\n" +
                "9RNDoWsKUWeZwuRJ2fP0XN5UwR3DtAlWcelqQHRgXave6LiwUQkCUco=\n" +
                "-----END RSA PRIVATE KEY-----\n";

        String certPem = "-----BEGIN CERTIFICATE-----\n" +
                "MIIDWjCCAkKgAwIBAgIVAPLbYhS4Z6fN7bSH0PkVUHUy12uZMA0GCSqGSIb3DQEB\n" +
                "CwUAME0xSzBJBgNVBAsMQkFtYXpvbiBXZWIgU2VydmljZXMgTz1BbWF6b24uY29t\n" +
                "IEluYy4gTD1TZWF0dGxlIFNUPVdhc2hpbmd0b24gQz1VUzAeFw0xODExMDExMjM0\n" +
                "MzJaFw00OTEyMzEyMzU5NTlaMB4xHDAaBgNVBAMME0FXUyBJb1QgQ2VydGlmaWNh\n" +
                "dGUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDQTDjc8dWIlQOgiOdS\n" +
                "o3b7Meog/w088HB5JkXU6e6pXJp8TrVEyi2IcNCqcVAFjQhsz8UT13LvGUgQmZZj\n" +
                "nkxBCBTj0lGhTF3LnGzrnbrIoAvvYmhRkNZXdjrTV2eUkRCInjBJR0x4pPDp4L1/\n" +
                "kn8G8VU4R3ANemHoEJMka/Wflq3+BFKf98KgbBLewS61Wu4bxA63w3uiMRm9oFq8\n" +
                "ww69t7k/oQnVvxSoc4FAk+8M2dSPerf7BZvsDuYZJgfYP5Q04mKBDJcFrCP2XgVy\n" +
                "SJA0e8OxQOmg5z1hps4XJwnMxBY4eLRofSH9xZIq0fqj21Unm8dk7ARPJ2JVDsy8\n" +
                "DVrJAgMBAAGjYDBeMB8GA1UdIwQYMBaAFJni/wPjzOFT4WHF873qWdnI1LwkMB0G\n" +
                "A1UdDgQWBBR75YVqXmTupuwNk7wH02eqvj8OBjAMBgNVHRMBAf8EAjAAMA4GA1Ud\n" +
                "DwEB/wQEAwIHgDANBgkqhkiG9w0BAQsFAAOCAQEAlGwgdTBnSJUumLtWNSWu2bhS\n" +
                "+guXTHvrRypS6R8UOoka/ZD3CC4HKRGN97OTM+OX2nE+wt7zSz5V5y6Hc1YwZCaj\n" +
                "20/mUAfYgjcz8o6wiZTW0BswvBEyI2E7pzjPDWcfIypLeyrsDuqwtX34XFoeZxjC\n" +
                "djCeawBSuhwyP4QjN9MmuXes2xpQGcXecANFMxjRssQOs51f9pihmk2Tngxc8Tt5\n" +
                "CEljRjroKtRmykM7XMdHqc+A4BqpnorVkjjsG2EKq4gkFDICy6o3h0D6J3icbMHK\n" +
                "ZwsIF3SRBk0JTpRI/86FvRaBf4ZXul1P4nWAjqbOKqcuqqFgcVnImOm7KfB4xA==\n" +
                "-----END CERTIFICATE-----\n";

        // Code started here
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath,KEYSTORE_NAME)){
                if(AWSIotKeystoreHelper.keystoreContainsAlias(CERTIFICATE_ID,keystorePath,KEYSTORE_NAME,"keystorePassword")){
                    AWSIotKeystoreHelper.deleteKeystoreAlias(CERTIFICATE_ID,keystorePath,KEYSTORE_NAME,"keystorePassword");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred delete alias from keystore.", e);
        }
        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(CERTIFICATE_ID, certPem, keyPem,keystorePath,KEYSTORE_NAME,"keystorePassword");

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, KEYSTORE_NAME)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(CERTIFICATE_ID, keystorePath,
                        KEYSTORE_NAME, "keystorePassword")) {
                    Log.i(TAG, "Certificate " + CERTIFICATE_ID
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(CERTIFICATE_ID,
                            keystorePath, KEYSTORE_NAME, "keystorePassword");
                    Log.i(TAG, "Client kystore is ready");
                } else {
                    Log.i(TAG, "Key/cert " + CERTIFICATE_ID + " not found in keystore.");
                }
            } else {
                Log.i(TAG, "Keystore " + keystorePath + "/" + KEYSTORE_NAME + " not found.");
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred retrieving cert/key from keystore.", e);
        }
        try {
            String clientId = UUID.randomUUID().toString();
            mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(TAG, "Status = " + String.valueOf(status));

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (status == AWSIotMqttClientStatus.Connecting) {
                                statusMessage.setText("Connecting...");
                            } else if (status == AWSIotMqttClientStatus.Connected) {
                                statusMessage.setText("Ready to Scan Barcode");
                                readyToScan = true;
                                btnReadBarcode.setEnabled(true);
                            } else if (status == AWSIotMqttClientStatus.Reconnecting) {
                                if (throwable != null) {
                                    statusMessage.setText("Connection error: Reconnecting");
                                    Log.e(TAG, "Connection error.", throwable);
                                }
                                statusMessage.setText("Reconnecting");
                            } else if (status == AWSIotMqttClientStatus.ConnectionLost) {
                                if (throwable != null) {
                                    statusMessage.setText("Connection error: ConnectionLost");
                                    Log.e(TAG, "Connection error.", throwable);
                                }
                                statusMessage.setText("Disconnected");
                            } else {
                                statusMessage.setText("Disconnected");

                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(TAG, "Connection error.", e);
            statusMessage.setText("Error! " + e.getMessage());
        }
    }


    private void postToAWSServer(String ssid, String meetid){

        final String topic = "meetingattendees";
        final String msg = "{\"ssid\":\""+ssid+"\",\"meetid\":\""+meetid+"\"}";

        try {
            mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0);
            statusMessage.setText("SID "+ssid+" Published");
        } catch (Exception e) {
            statusMessage.setText("Publish error."+e.getMessage());
            Log.e(TAG, "Publish error.", e);
        }



    }



    private class SendDeviceDetails extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            String data = "";

            HttpURLConnection httpURLConnection = null;
            try {

                httpURLConnection = (HttpURLConnection) new URL(params[0]).openConnection();
                httpURLConnection.setRequestMethod("POST");

                httpURLConnection.setDoOutput(true);

                DataOutputStream wr = new DataOutputStream(httpURLConnection.getOutputStream());
                wr.writeBytes("PostData=" + params[1]);
                wr.flush();
                wr.close();

                InputStream in = httpURLConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(in);

                int inputStreamData = inputStreamReader.read();
                while (inputStreamData != -1) {
                    char current = (char) inputStreamData;
                    inputStreamData = inputStreamReader.read();
                    data += current;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }

            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG,result);
            super.onPostExecute(result);
            try {
                JSONObject message = new JSONObject(result);
                statusMessage.setText(message.getString("message"));
            } catch (JSONException e) {
                statusMessage.setText("Server recived data");
                e.printStackTrace();
            }


            Log.e("someTAG here", result); // this is expecting a response code to be sent from your server upon receiving the POST data
        }
    }
}
