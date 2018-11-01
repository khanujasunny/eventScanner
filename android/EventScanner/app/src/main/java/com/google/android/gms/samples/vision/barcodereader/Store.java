package com.google.android.gms.samples.vision.barcodereader;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;

public class Store {
    private static String clientEndpoint = "a1a5vmkre2h9ju-ats.iot.us-east-1.amazonaws.com";       // replace <prefix> and <region> with your own
    private static String clientId = "EventScan12345";                              // replace with your own client ID. Use unique client IDs for concurrent connections.
    private static String certificateFile = "/home/pi/awskeys/eventscan.cert.pem";                       // X.509 based certificate file
    private static String privateKeyFile = "/home/pi/awskeys/eventscan-private.pem.key";                        // PKCS#1 or PKCS#8 PEM encoded private key file
    private static String topic = "meetingattendees";

    private AWSIotMqttClient client;

    public Store() {
        // SampleUtil.java and its dependency PrivateKeyReader.java can be copied from the sample source code.
        // Alternatively, you could load key store directly from a file - see the example included in this README.
        KeyChecks.KeyStorePasswordPair pair = KeyChecks.getKeyStorePasswordPair(certificateFile, privateKeyFile);

        client = new AWSIotMqttClient(clientEndpoint, clientId, pair.keyStore, pair.keyPassword);
    }

    public void connect() throws AWSIotException {
        client.connect();
    }

    public void publish(String json) throws AWSIotException {
        client.publish(topic, AWSIotQos.QOS0, json);
    }
}
