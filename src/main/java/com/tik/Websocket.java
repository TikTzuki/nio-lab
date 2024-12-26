package com.tik;

import com.binance.connector.client.WebSocketApiClient;
import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketApiClientImpl;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.binance.connector.client.utils.signaturegenerator.SignatureGenerator;
import org.json.JSONObject;

import java.util.ArrayList;

public class Websocket {
    public static void main(String[] args) throws InterruptedException {
        test();
    }

    public void test1() throws InterruptedException {
        WebSocketStreamClient wsClient = new WebSocketStreamClientImpl();

//        int streamId1 = wsClient.aggTradeStream("btcusdt", (event) -> {
//            System.out.println(event);
//        });

        // Combined streams
        ArrayList<String> streams = new ArrayList<>();
        streams.add("btcusdt@trade");
//        streams.add("bnbusdt@trade");

//        int streamID2 = wsClient.combineStreams(streams, ((event) -> {
//            System.out.println(event);
//        }));

        wsClient.klineStream("btcusdt", "1s", ((event) -> {
            System.out.println(event);
        }));

        Thread.sleep(10_000);

        // Close all streams
        wsClient.closeAllConnections();


    }

    public static void test() throws InterruptedException {
        SignatureGenerator signatureGenerator = new MyRsaSig("id_rsa");
        WebSocketApiClient wsApiClient = new WebSocketApiClientImpl("ljWwvXmOoE4RmkmEbCNHJn6cnO67fRaVidugZT1cephKYaJzE5YWyTTDW7tCglHX", signatureGenerator);

        wsApiClient.connect(((message) -> {
            System.out.println(message);
        }));

        JSONObject optionalParams = new JSONObject();
        optionalParams.put("requestId", "request123");
        optionalParams.put("quantity", 1);

//        wsApiClient.trade().testNewOrder("BTCUSDT", "BUY", "MARKET", optionalParams);
        wsApiClient.trade().getOrder("ETHUSDT", new JSONObject());

        Thread.sleep(1_000);

        wsApiClient.close();
    }
}
