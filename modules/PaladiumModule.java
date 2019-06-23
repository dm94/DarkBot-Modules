package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.entities.BasePoint;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.entities.Npc;
import com.github.manolo8.darkbot.core.entities.Ship;
import com.github.manolo8.darkbot.core.itf.CustomModule;
import com.github.manolo8.darkbot.backpage.HangarManager;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.objects.Gui;
import com.github.manolo8.darkbot.core.objects.Map;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;


import java.util.Comparator;
import java.util.List;

import static com.github.manolo8.darkbot.Main.API;
import static java.lang.Double.max;
import static java.lang.Double.min;
import static java.lang.Math.random;

public class PaladiumModule extends LootNCollectorModule implements CustomModule<PaladiumModule.PaladiumConfig> {

    private String version = "Alpha v0.0.13";

    private HeroManager hero;
    private Drive drive;
    private Config config;
    private StatsManager statsManager;

    private String hangarActive = "";
    private long lastCheckupHangar = 0;
    private Main main;
    private long disconectTime = 0;
    private enum State {
        WAIT ("Waiting"),
        HANGAR_AND_MAP_BASE ( "Selling palladium"),
        HANGAR_BASE_OTHER_MAP ( "Hangar Base - To 5-2"),
        DEPOSIT_FULL_SWITCHING_HANGAR("Deposit full, switching hangar"),
        LOOT_PALADIUM("Loot paladium"),
        HANGAR_PALA_OTHER_MAP("Hangar paladium - To 5-3"),
        SWITCHING_PALA_HANGAR("Switching to the palladium hangar"),
        DISCONNECTING("Disconnecting"),
        SWITCHING_HANGAR("Switching Hangar"),
        RELOAD_GAME("Reloading the game"),
        READY("Ready");;

        private String message;

        State(String message) {
            this.message = message;
        }
    }
    private State currentStatus;
    private State subStatus;
    private long waitTime = 0;

    private PaladiumConfig configPa;
    private HangarManager hangarManager;

    private Map SELL_MAP;

    private List<BasePoint> bases;
    private Gui oreTrade;
    private long sellClick;

    public Class configuration() {
        return  PaladiumConfig.class;
    }

    public static class PaladiumConfig {
        @Option("Exit Key")
        public char exitKey = 'l';

        @Option(value = "Hangar Palladium", description = "Ship 5-3 Hangar ID")
        public String hangarPalladium = "";

        @Option(value = "Hangar Base", description = "Ship 5-2 Hangar ID")
        public String hangarBase = "";

    }

    @Override
    public String name() { return "Paladium Module"; }

    @Override
    public String author() { return "@Dm94Dani"; }

    @Override
    public void install(Main main,PaladiumConfig configPa ) {
        super.install(main);
        this.main = main;
        this.SELL_MAP = main.starManager.byName("5-2");
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.config = main.config;
        this.statsManager = main.statsManager;
        this.configPa = configPa;
        this.hangarManager = new HangarManager(main,main.backpage);
        this.bases = main.mapManager.entities.basePoints;
        this.oreTrade = main.guiManager.oreTrade;

        currentStatus = State.WAIT;
        subStatus = State.READY;

        hangarManager.updateHangars();
        hangarActive = hangarManager.getActiveHangar();
    }

    @Override
    public String status() {
        return  name() + " " + version + " | Status: " + currentStatus.message;
    }

    @Override
    public void tick() {
        if (subStatus == State.WAIT ) {
            if (System.currentTimeMillis() - 20000 >= waitTime) {
                subStatus = State.READY;
            } else if (waitTime == 0) {
                waitTime = System.currentTimeMillis();
                return;
            } else {
                return;
            }
        }

        if (lastCheckupHangar <= System.currentTimeMillis() - 300000){
            updateDataHangars();
        }

        if (statsManager.deposit >= statsManager.depositTotal) {
            if (hangarActive != null && hangarActive.equalsIgnoreCase(configPa.hangarBase)) {
                this.currentStatus = State.HANGAR_AND_MAP_BASE;
                sell();
            } else {
                this.currentStatus = State.DEPOSIT_FULL_SWITCHING_HANGAR;
                disconectAndChangeHangar(configPa.hangarBase);
            }

        } else if (hangarActive != null && hangarActive.equalsIgnoreCase(configPa.hangarPalladium)) {
            if (this.hero.map.id == 93){
                this.currentStatus = State.LOOT_PALADIUM;
                super.tick();
            } else {
                this.currentStatus = State.HANGAR_PALA_OTHER_MAP;
                hero.roamMode();
                this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(93));
                return;
            }
        } else {
            this.currentStatus = State.SWITCHING_PALA_HANGAR;
            disconectAndChangeHangar(configPa.hangarPalladium);
        }
    }

    public void disconectAndChangeHangar(String hangar) {
        if (this.disconectTime == 0 && this.currentStatus != State.DISCONNECTING) {
            this.currentStatus = State.DISCONNECTING;
            this.subStatus = State.WAIT;
            disconnect();
        } else if (this.disconectTime <= System.currentTimeMillis() - 21000 && this.currentStatus != State.SWITCHING_HANGAR) {
            this.currentStatus = State.SWITCHING_HANGAR;
            this.subStatus = State.WAIT;
            hangarManager.changeHangar(hangar);
            updateDataHangars();
        } else if (this.disconectTime <= System.currentTimeMillis() - 100000 && this.currentStatus == State.SWITCHING_HANGAR){
            this.currentStatus = State.RELOAD_GAME;
            this.subStatus = State.WAIT;
            this.disconectTime = 0;
            API.handleRefresh();
        }
    }

    public void disconnect() {
        API.keyboardClick(this.configPa.exitKey);
        this.disconectTime = System.currentTimeMillis();
    }

    private void updateDataHangars(){
        if (main.backpage.sidStatus().contains("OK")) {
            hangarManager.updateHangars();
            hangarActive = hangarManager.getActiveHangar();
            lastCheckupHangar = System.currentTimeMillis();
        }
    }

    private void sell() {
        pet.setEnabled(false);
        if (hero.map != SELL_MAP) main.setModule(new MapModule()).setTarget(SELL_MAP);
        else bases.stream().filter(b -> b.locationInfo.isLoaded()).findFirst().ifPresent(base -> {
            if (drive.movingTo().distance(base.locationInfo.now) > 200) { // Move to base
                double angle = base.locationInfo.now.angle(hero.locationInfo.now) + Math.random() * 0.2 - 0.1;
                drive.move(Location.of(base.locationInfo.now, angle, 100 + (100 * Math.random())));
            } else if (!hero.locationInfo.isMoving() && showTrade(true, base)
                    && System.currentTimeMillis() - 60_000 > sellClick) {
                oreTrade.click(611, 179);
                sellClick = System.currentTimeMillis();
            }
        });
    }

    private boolean showTrade(boolean value, BasePoint base) {
        if (oreTrade.trySetShowing(value)) {
            if (value) {
                base.clickable.setRadius(800);
                drive.clickCenter(true, base.locationInfo.now);
                base.clickable.setRadius(0);
            } else oreTrade.click(730, 9);
            return false;
        }
        return oreTrade.isAnimationDone();
    }

}
