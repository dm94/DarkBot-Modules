package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.itf.CustomModule;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.LinkedHashMap;

public class LCSendSepromModule extends LootNCollectorModule implements CustomModule {

    private Main main;
    private long lastSent = 0;
    private long deliveryTime = 0;

    @Override
    public String name() { return "Kill-Collect Seprom"; }

    @Override
    public String author() { return "@Dm94Dani"; }

    @Override
    public void install(Main main, Object config) {
        super.install(main);
        this.main = main;
    }

    @Override
    public void tick() {
        sendSeprom();
        super.tick();
    }

    private void sendSeprom(){
        int sepromToSend = main.statsManager.depositTotal - (main.statsManager.deposit+50);
        if (sepromToSend > 500 && this.lastSent <= System.currentTimeMillis() - this.deliveryTime) {
            try {
                HttpURLConnection conn = main.backpage.getConnection("indexInternal.es?action=internalSkylab");
                if (!waitingTransport(conn.getInputStream())){
                    LinkedHashMap<String,Object> params = new LinkedHashMap();
                    params.put("action", "internalSkylab");
                    params.put("subaction", "startTransport");
                    params.put("mode", "normal");
                    params.put("construction", "TRANSPORT_MODULE");
                    params.put("count_prometium", "0");
                    params.put("count_endurium", "0");
                    params.put("count_terbium", "0");
                    params.put("count_prometid", "0");
                    params.put("count_duranium", "0");
                    params.put("count_xenomit", "0");
                    params.put("count_promerium", "0");
                    params.put("count_seprom", String.valueOf(sepromToSend));
                    StringBuilder postData = new StringBuilder();
                    for (java.util.Map.Entry<String,Object> param : params.entrySet()) {
                        if (postData.length() != 0) postData.append('&');
                        postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                        postData.append('=');
                        postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
                    }
                    byte[] postDataBytes = postData.toString().getBytes("UTF-8");
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(postDataBytes);
                    conn.getInputStream();
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            deliveryTime = (sepromToSend/500) * 3600000 + 120000;
            lastSent = System.currentTimeMillis();
        }
    }

    private boolean waitingTransport(InputStream input) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            String currentLine;

            while ((currentLine = in.readLine()) != null){
                if (currentLine.contains("progress_timer")) {
                    return true;
                }
            }
        } catch (Exception e){ }

        return false;
    }

}