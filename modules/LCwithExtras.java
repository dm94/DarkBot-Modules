package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.types.Num;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.CustomModule;
import com.github.manolo8.darkbot.core.manager.StatsManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;

public class LCwithExtras extends LootNCollectorModule implements CustomModule<LCwithExtras.LCConfig> {

    private Main main;
    private StatsManager statsManager;
    private long lastSent = 0;
    private long deliveryTime = 0;
    private String version = "v0.4";
    private LCConfig lcConfig;
    private long waitingTime = 0;
    private double lastUridium = 0;
    private final DecimalFormat formatter = new DecimalFormat("###,###,###");

    @Override
    public String name() { return "LC with extras"; }

    @Override
    public String author() { return "@Dm94Dani"; }

    @Override
    public void install(Main main, LCConfig config) {
        super.install(main);
        this.main = main;
        this.lcConfig = config;
        this.statsManager = main.statsManager;
    }

    public static class LCConfig {
        @Option("Send seprom")
        public boolean sendSeprom = true;

        @Option("Send discord menssages")
        public boolean discordMessage = false;

        @Option(value = "Message Interval", description = "How often a message is sent in minutes")
        @Num(min = 10, max = 500, step = 5)
        public int intervalMessage = 10;

        @Option(value = "Discord WebHook", description = "Link you get when you create a webhook in discord")
        public String discordWebHook = null;
    }

    @Override
    public Class configuration() {
        return LCwithExtras.LCConfig.class;
    }

    @Override
    public void tick() {
        if (lcConfig.sendSeprom){
            sendSeprom();
        }
        if (lcConfig.discordMessage && this.waitingTime <= System.currentTimeMillis() - (60000*lcConfig.intervalMessage)){
            waitingTime = System.currentTimeMillis();
            sendDiscordMessage("```Total Uridium: " + formatter.format(statsManager.uridium) + " | Total Credits: " + formatter.format(statsManager.credits)
                    + "\\nSID Status: " + main.backpage.sidStatus()
                    + "\\n" +
                    "cre/h " + formatter.format(statsManager.earnedCredits()) + "\\n" +
                    "uri/h " + formatter.format(statsManager.earnedUridium()) + "\\n" +
                    "exp/h " + formatter.format(statsManager.earnedExperience()) + "\\n" +
                    "hon/h " + formatter.format(statsManager.earnedHonor()) + "\\n" +
                    "death " + main.guiManager.deaths + "```");
            if (lastUridium == statsManager.uridium) {
                sendDiscordMessage("@here Bot stopped");
            }
            lastUridium = statsManager.uridium;
        }
        super.tick();
    }

    @Override
    public String status() {
        return id() + " " + version + " | " + super.status();
    }

    private void sendSeprom(){
        int sepromToSend = main.statsManager.depositTotal - (main.statsManager.deposit+50);
        if (sepromToSend > 500 && this.lastSent <= System.currentTimeMillis() - this.deliveryTime) {
            try {
                if (waitingTransport(main.backpage.getConnection("indexInternal.es?action=internalSkylab").getInputStream())){
                    lastSent = System.currentTimeMillis();
                    return;
                } else {
                    HttpURLConnection conn = main.backpage.getConnection("indexInternal.es?action=internalSkylab");
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
                    conn.disconnect();

                    deliveryTime = (sepromToSend/500) * 3600000 + 120000;
                    lastSent = System.currentTimeMillis();
                    System.out.println("Seprom sent");
                    if (lcConfig.discordMessage) {
                        sendDiscordMessage("Seprom sent");
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private boolean waitingTransport(InputStream input) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            String currentLine;

            while ((currentLine = in.readLine()) != null){
                if (currentLine.contains("progress_timer")) {
                    in.close();
                    input.close();
                    return true;
                }
            }
            in.close();
            input.close();
        } catch (Exception e){}

        return false;
    }

    private void sendDiscordMessage(String m) {
        if (lcConfig.discordWebHook == null || lcConfig.discordWebHook.isEmpty()) { return; }

        try {
            HttpURLConnection conn = (HttpURLConnection)new URL(lcConfig.discordWebHook).openConnection();
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            String jsonInputString = "{\"content\": \""+ m +"\"}";
            OutputStream os = conn.getOutputStream();
            os.write(jsonInputString.getBytes("UTF-8"));
            os.close();
            conn.getInputStream();
            conn.disconnect();
        }catch (Exception ex) {
            ex.printStackTrace();
        }


    }

}