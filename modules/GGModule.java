package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.types.Editor;
import com.github.manolo8.darkbot.config.types.Options;
import com.github.manolo8.darkbot.config.types.suppliers.OptionList;
import com.github.manolo8.darkbot.core.entities.Npc;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.StarManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.gui.tree.components.JListField;
import com.github.manolo8.darkbot.gui.tree.components.JShipConfigField;
import com.github.manolo8.darkbot.modules.utils.NpcAttacker;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.CustomModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static java.lang.Double.max;
import static java.lang.Double.min;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class GGModule extends CollectorModule implements CustomModule<GGModule.GGConfig> {
    private String version = "v1 Beta 30";

    private Main main;
    private Config config;
    private List<Npc> npcs;
    private HeroManager hero;
    private Drive drive;
    private int radiusFix;
    private GGConfig ggConfig;
    private boolean repairing;
    private int rangeNPCFix = 0;
    private long lastCheck = System.currentTimeMillis();
    private int lasNpcHealth = 0;
    private int lasPlayerHealth = 0;
    private NpcAttacker attack;

    private boolean direction;

    public static class GGConfig {
        @Option(value = "Honor config", description = "Used on finish wave")
        @Editor(JShipConfigField.class)
        public Config.ShipConfig Honor = new Config.ShipConfig();

        @Option("GG Gate - Chosse GG Gamma to make ABG")
        @Editor(JListField.class)
        @Options(GGList.class)
        public int idGate = 51;

        @Option("Take materials")
        public boolean takeBoxes = true;

        @Option("Send NPCs to corner")
        public boolean sendNPCsCorner = true;

        @Option(value = "Dynamic Range", description = "Automatically changes the range")
        public boolean useDynamicRange = true;

        @Option(value = "GateModule Logic ABG", description = "Use GateModule logic in ABG")
        public boolean useGateModuleLogic = true;
    }

    @Override
    public String name() { return "GG Module"; }

    @Override
    public String author() { return "@Dm94Dani"; }

    @Override
    public void install(Main main, GGConfig config) {
        super.install(main);
        this.main = main;
        this.config = main.config;
        this.attack = new NpcAttacker(main);
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.npcs = main.mapManager.entities.npcs;
        this.ggConfig = config;
    }

    @Override
    public Class configuration() {
        return GGConfig.class;
    }

    public static class GGList extends OptionList<Integer> {
        private static final StarManager starManager = new StarManager();

        @Override
        public Integer getValue(String text) {
            return starManager.byName(text).id;
        }

        @Override
        public String getText(Integer value) {
            return starManager.byId(value).name;
        }

        @Override
        public List<String> getOptions() {
            return new ArrayList<>(starManager.getGGMaps());
        }
    }

    @Override
    public boolean canRefresh() {
        return attack.target == null;
    }

    @Override
    public String status() {
        return id() + " " + version + " | " + (repairing ? "Repairing" :
                attack.hasTarget() ? attack.status() : "Roaming") + " | NPCs: "+this.npcs.size();
    }

    @Override
    public void tick() {

        if (main.hero.map.gg) {
            main.guiManager.pet.setEnabled(true);
            if (findTarget()) {
                hero.attackMode();
                attack.doKillTargetTick();
                removeIncorrectTarget();
                if ((main.hero.map.id == 51 || main.hero.map.id == 52 || main.hero.map.id == 53) &&
                        ggConfig.useGateModuleLogic && !allLowLifeOrISH()) {
                    gateModuleLogic();
                } else {
                    eventLogic();
                }
            } else if (!main.mapManager.entities.portals.isEmpty()) {
                hero.roamMode();
                if (ggConfig.takeBoxes && isNotWaiting()) {
                    findBox();
                    tryCollectNearestBox();
                }
                if (!drive.isMoving() || drive.isOutOfMap()){
                    if (hero.health.hpPercent() >= config.GENERAL.SAFETY.REPAIR_TO_HP) {
                        repairing = false;
                        this.main.setModule(new MapModule()).setTarget(main.starManager.byId(main.mapManager.entities.portals.get(0).id));
                        return;
                    } else {
                        drive.moveRandom();
                        repairing = true;
                    }
                }
            } else if (!drive.isMoving()) {
                hero.setMode(ggConfig.Honor);
                drive.moveRandom();
            }
        } else if ( main.hero.map.id == 1 || main.hero.map.id == 5 || main.hero.map.id == 9) {
            if (ggConfig.idGate == 73 || ggConfig.idGate == 72){ ggConfig.idGate = 71; }
            hero.roamMode();
            for (int i=0; i < main.mapManager.entities.portals.size();i++){
                if (main.mapManager.entities.portals.get(i).target.id == ggConfig.idGate ||
                        (ggConfig.idGate == 53 && (main.mapManager.entities.portals.get(i).target.id == 52 ||
                                main.mapManager.entities.portals.get(i).target.id == 51))) {
                    this.main.setModule(new MapModule()).setTarget(main.mapManager.entities.portals.get(i).target);
                    return;
                }
            }
        } else {
            hero.roamMode();
            this.main.setModule(new MapModule()).setTarget(this.main.starManager.byId(ggConfig.idGate));
        }
    }

    private boolean findTarget() {
        if (attack.target == null || attack.target.removed) {
            if (!npcs.isEmpty()) {
                if (ggConfig.sendNPCsCorner && !allLowLifeOrISH()) {
                    attack.target = bestNpc(hero.locationInfo.now);
                } else {
                    attack.target = closestNpc(hero.locationInfo.now);
                }
            } else {
                attack.target = null;
            }
        } else if (attack.target.health.hpPercent() < 0.25 && !allLowLifeOrISH()) {
            attack.target = null;
        }
        return attack.target != null;
    }

    private void removeIncorrectTarget() {
        if (attack.target.ish){
            attack.target = null;
            return;
        }
        if (ggConfig.sendNPCsCorner && main.mapManager.isTarget(attack.target) &&
                isLowHealh(attack.target) && !allLowLifeOrISH()) {
            attack.target = null;
        }
    }

    private boolean isLowHealh(Npc npc){
        return npc.health.hpPercent() < 0.25;
    }

    private boolean allLowLifeOrISH(){
        int npcsLowLife = 0;

        for(Npc n:npcs){
            if (n.ish) { return true; }
            if (isLowHealh(n)) { npcsLowLife++; }
        }

        return npcsLowLife >= npcs.size();
    }

    private void eventLogic() {
        Npc target = attack.target;

        if (target == null || target.locationInfo == null) return;

        Location heroLoc = hero.locationInfo.now;
        Location targetLoc = target.locationInfo.destinationInTime(400);

        double angle = targetLoc.angle(heroLoc), distance = heroLoc.distance(targetLoc), radius = target.npcInfo.radius;

        if (hero.health.hpPercent() <= config.GENERAL.SAFETY.REPAIR_HP){
            rangeNPCFix = 1000;
            repairing = true;
        } else if  (hero.health.hpPercent() >= config.GENERAL.SAFETY.REPAIR_TO_HP){
            repairing = false;
            rangeNPCFix = 0;
        }

        if (ggConfig.useDynamicRange) {
            dynamicNPCRange(distance);
        }

        radius += rangeNPCFix;

        if (distance > radius) {
            radiusFix -= (distance - radius) / 2;
            radiusFix = (int) max(radiusFix, -target.npcInfo.radius / 2);
        } else {
            radiusFix += (radius - distance) / 6;
            radiusFix = (int) min(radiusFix, target.npcInfo.radius / 2);
        }

        distance = (radius += radiusFix);
        angle += Math.max((hero.shipInfo.speed * 0.625) + (min(200, target.locationInfo.speed) * 0.625)
                - heroLoc.distance(Location.of(targetLoc, angle, radius)), 0) / radius;

        Location direction = Location.of(targetLoc, angle, distance);
        while (!drive.canMove(direction) && distance < 10000)
            direction.toAngle(targetLoc, angle += 0.3, distance += 2);
        if (distance >= 10000) direction.toAngle(targetLoc, angle, 500);

        drive.move(direction);
    }

    private void dynamicNPCRange(double distance){
        if (lastCheck <= System.currentTimeMillis()-8000 && distance <= 1000) {
            if (lasPlayerHealth > hero.health.hp && rangeNPCFix < 500) {
                rangeNPCFix += 50;
            } else if (lasNpcHealth == attack.target.health.hp) {
                rangeNPCFix -= 50;
            }
            lasPlayerHealth =  hero.health.hp;
            lasNpcHealth = attack.target.health.hp;
            lastCheck = System.currentTimeMillis();
        }
    }

    private Npc closestNpc(Location location) {
        return this.npcs.stream()
                .filter(n -> (!n.ish))
                .min(Comparator.<Npc>comparingDouble(n -> n.locationInfo.now.distance(location))
                        .thenComparing(n -> n.npcInfo.priority)
                        .thenComparing(n -> n.health.hpPercent())).orElse(null);
    }

    private Npc bestNpc(Location location) {
        return this.npcs.stream()
                .filter(n -> (!n.ish && n.health.hpPercent() > 0.25))
                .min(Comparator.<Npc>comparingDouble(n -> (n.npcInfo.priority))
                        .thenComparing(n -> (n.locationInfo.now.distance(location)))).orElse(null);
    }


    /* Gate Module logic */

    private void gateModuleLogic() {
        if (attack.target == null || attack.target.locationInfo == null) return;

        Location center = new Location(4100, 4100);
        Location now = hero.locationInfo.now;
        double safeDistance = attack.target.npcInfo.radius;
        Location location = attack.target.locationInfo.now;
        double angle = now.angle(center);
        double distance = location.distance(now);

        if (ggConfig.useDynamicRange) {
            dynamicNPCRange(distance);
        }
        safeDistance += rangeNPCFix;

        if (safeDistance == 500) safeDistance = 600;

        if (attack.castingAbility()) safeDistance = Math.min(570, safeDistance);
        if (repairing || (repairing = (hero.health.hpPercent() < config.GENERAL.SAFETY.REPAIR_HP))) safeDistance = 2000;

        if (safeDistance > distance) {

            double radius = 3850;
            double move = (safeDistance - distance);

            move -= (distance - radius);
            move = max(min(move, (safeDistance - distance) * 1.5), 0);

            double speed = hero.shipInfo.speed / (double) 10;

            radius += direction ? speed : -speed;
            direction = !direction;

            double centerDistance = center.distance(now);
            double diff = centerDistance - radius;

            if (diff > 0 && diff > safeDistance) {
                radius = centerDistance - safeDistance + distance;
            } else if (-diff > safeDistance) {
                radius = centerDistance + safeDistance - distance;
            } else {
                angle += move / radius;
            }

            drive.move(
                    center.x + cos(angle) * radius,
                    center.y + sin(angle) * radius);

        } else if (Math.abs(safeDistance - distance) > 200) {

            angle = location.angle(center);

            drive.move(
                    location.x - cos(angle) * safeDistance,
                    location.y - sin(angle) * safeDistance);
        }
    }

}

