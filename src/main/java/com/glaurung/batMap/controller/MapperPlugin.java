package com.glaurung.batMap.controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.glaurung.batMap.gui.corpses.CorpsePanel;
import com.glaurung.batMap.gui.manual.ManualPanel;
import com.glaurung.batMap.gui.search.SearchPanel;
import com.glaurung.batMap.io.AreaDataPersister;
import com.glaurung.batMap.io.GuiDataPersister;
import com.glaurung.batMap.vo.GuiData;
import com.mythicscape.batclient.interfaces.BatClientPlugin;
import com.mythicscape.batclient.interfaces.BatClientPluginTrigger;
import com.mythicscape.batclient.interfaces.BatClientPluginUtil;
import com.mythicscape.batclient.interfaces.BatWindow;
import com.mythicscape.batclient.interfaces.ParsedResult;

public class MapperPlugin extends BatClientPlugin
        implements BatClientPluginTrigger, ActionListener, BatClientPluginUtil {

    protected static final String MAKERIPACTION = "rip_action set ";
    protected static final String RIPACTION_OFF = "rip_action off";
    protected static final String RIPACTION_ON = "rip_action on";
    protected static final String COMMAND_ADD_LABEL = "add";
    protected static final String COMMAND_REMOVE_LABEL = "del";
    protected static final String COMMAND_RUN_TO_LABEL = "run";
    protected static final String COMMAND_HELP = "help";
    protected static final String COMMAND_LIST_LABELS = "list";
    protected static final String COMMAND_APPEND_TO_NOTES = "append";
    protected static final String COMMAND_FIND_DESC = "find";
    protected static final String COMMAND_WHERE = "where";
    protected static final String COMMAND_FLEE = "flee";
    protected static final String COMMAND_GO = "go";
    protected static final String COMMAND_WAINO = "wainomode";
    protected static final String ALIAS_WAINO = "/alias " + COMMAND_WAINO + "=$mappercommand " + COMMAND_WAINO;

    private MapperEngine engine;
    private SearchEngine searchEngine;
    private SearchPanel searchPanel;
    private final String CHANNEL_PREFIX = "BAT_MAPPER";
    // batMap;areaname;roomUID;exitUsed;indoor boolean;shortDesc;longDesc;exits
    private final int PREFIX = 0;
    private final int AREA_NAME = 1;
    private final int ROOM_ID = 2;
    private final int EXIT_USED = 3;
    private final int IS_INDOORS = 4;
    private final int SHORT_DESC = 5;
    private final int LONG_DESC = 6;
    private final int EXITS = 7;

    private final int MESSAGE_LENGTH = 9;
    private final int EXIT_AREA_LENGTH = 2;

    private final String EXIT_AREA_MESSAGE = "REALM_MAP";
    private String BASEDIR = null;
    private String lastPrintedPathBehindStatus = null;
    private String lastMapperRoomId = null;

    public void loadPlugin() {
        BASEDIR = this.getBaseDirectory();
        GuiData guiData = GuiDataPersister.load(BASEDIR);

        BatWindow clientWin;
        if (guiData != null) {
            clientWin = this.getClientGUI().createBatWindow("Mapper", guiData.getX(), guiData.getY(),
                    guiData.getWidth(), guiData.getHeight());
        } else {
            clientWin = this.getClientGUI().createBatWindow("Mapper", 300, 300, 820, 550);
        }

        engine = new MapperEngine(this);
        searchEngine = new SearchEngine(this);
        engine.setBatWindow(clientWin);
        searchEngine.setBatWindow(clientWin);
        clientWin.removeTabAt(0);
        clientWin.newTab("batMap", engine.getPanel());
        CorpsePanel corpses = new CorpsePanel(BASEDIR, this);
        engine.setCorpsePanel(corpses);
        clientWin.newTab("Corpses", corpses);
        clientWin.newTab("manual", new ManualPanel());
        searchPanel = new SearchPanel(searchEngine);
        clientWin.newTab("map search", searchPanel);
        clientWin.setVisible(true);
        this.getPluginManager().addProtocolListener(this);
        AreaDataPersister.migrateFilesToNewLocation(BASEDIR);
        engine.setBaseDir(BASEDIR);
        searchEngine.setBaseDir(BASEDIR);
        clientWin.addComponentListener(engine);
        this.getClientGUI().doCommand(ALIAS_WAINO);
        try {
            printConsoleMessage("batMap loaded (v" + getLoadedVersion() + ").");
            printConsoleMessage("Command usage: $mappercommand <cmd>.");
            printConsoleMessage("Use $wainomode to toggle Waino mode.");
        } catch (Throwable ignored) {
        }

    }

    @Override
    public String getName() {
        return "batMap";
    }

    // ArrayList<BatClientPlugin> plugins=this.getPluginManager().getPlugins();
    @Override
    public ParsedResult trigger(ParsedResult input) {
        if (input.getStrippedText().startsWith("SAVED.")) {
            this.engine.save();
        }
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        /**
         *
         * received 99 protocol:
         * cMapper;;sunderland;;$apr1$dF!!_X#W$v3dsdL2khaffFpj1BvVrD0;;road;;0;;The long
         * road to Sunderland;;You see a long road stretching northward into the
         * distance. As far as
         * you can tell, the way ahead looks clear.
         * ;;north,south;;
         * 
         * event data amount: 9
         * 
         */

        // cMapper;areaname;roomUID;exitUsed;indoor boolean;shortDesc;longDesc;exits
        String input = event.getActionCommand();
        String[] values = input.split(";;", -1);
        if (values.length == 0 || !CHANNEL_PREFIX.equals(values[PREFIX])) {
            return;
        }

        if (values.length == EXIT_AREA_LENGTH && values[AREA_NAME].equals(EXIT_AREA_MESSAGE)) {
            this.engine.moveToArea(null);
            this.searchEngine.setMapperArea(null);
            this.lastPrintedPathBehindStatus = null;
            this.lastMapperRoomId = null;
            return;
        }

        MapperPacket mapperPacket = parseMapperPacket(values);
        if (mapperPacket == null) {
            return;
        }

        boolean movedToDifferentRoom = this.lastMapperRoomId == null
                || !this.lastMapperRoomId.equals(mapperPacket.roomUID);

        this.engine.moveToRoom(mapperPacket.areaName, mapperPacket.roomUID, mapperPacket.exitUsed,
                mapperPacket.indoors, mapperPacket.shortDesc, mapperPacket.longDesc, mapperPacket.exits);
        this.searchEngine.setMapperArea(mapperPacket.areaName);
        this.lastMapperRoomId = mapperPacket.roomUID;

        printPathBehindToScreenIfNeeded();
        boolean shouldPrintFleePreview = movedToDifferentRoom
                || this.engine.consumeWainoCoordinateChangedSinceLastPacket();
        printFleePreviewToScreenIfNeeded(shouldPrintFleePreview);
    }

    private void printPathBehindToScreenIfNeeded() {
        if (this.engine == null || !this.engine.isWainoModeEnabled()) {
            return;
        }

        String pathBehindStatus = this.engine.getPathBehindStatus();
        if (pathBehindStatus == null || pathBehindStatus.trim().isEmpty()) {
            return;
        }

        if (pathBehindStatus.equals(this.lastPrintedPathBehindStatus)) {
            return;
        }

        printConsoleMessage(pathBehindStatus);
        this.lastPrintedPathBehindStatus = pathBehindStatus;
    }

    private void printFleePreviewToScreenIfNeeded(boolean movedToDifferentRoom) {
        if (!movedToDifferentRoom) {
            return;
        }

        if (this.engine == null || !this.engine.isWainoModeEnabled()) {
            return;
        }

        printConsoleMessage(this.engine.getBestFleePathStatus());
    }

    private MapperPacket parseMapperPacket(String[] values) {
        if (values == null || values.length < 8) {
            return null;
        }

        int exitsIndex = values.length - 1;
        if (exitsIndex > EXITS && values[exitsIndex].isEmpty()) {
            exitsIndex--;
        }

        if (exitsIndex < EXITS) {
            return null;
        }

        String areaName = values[AREA_NAME];
        String roomUID = values[ROOM_ID];
        String exitUsed = values[EXIT_USED];
        boolean indoors;
        try {
            indoors = convertToBoolean(values[IS_INDOORS]);
        } catch (Exception e) {
            return null;
        }

        String shortDesc = values[SHORT_DESC];
        StringBuilder longDescBuilder = new StringBuilder();
        for (int i = LONG_DESC; i < exitsIndex; i++) {
            if (i > LONG_DESC) {
                longDescBuilder.append(";;");
            }
            longDescBuilder.append(values[i]);
        }
        String longDesc = longDescBuilder.toString();

        HashSet<String> exits = new HashSet<String>();
        String exitsValue = values[exitsIndex];
        if (exitsValue != null && !exitsValue.trim().isEmpty()) {
            exits.addAll(Arrays.asList(exitsValue.split(",")));
            exits.remove("");
        }

        return new MapperPacket(areaName, roomUID, exitUsed, indoors, shortDesc, longDesc, exits);
    }

    private boolean convertToBoolean(String oneOrZero) {
        if (Integer.valueOf(oneOrZero) == 0)
            return false;
        return true;

    }

    @Override
    public void clientExit() {
        this.engine.save();

    }

    public void saveRipAction(String ripString) {
        this.getClientGUI().doCommand(MAKERIPACTION + ripString);
    }

    public void toggleRipAction(boolean mode) {
        if (mode) {
            this.getClientGUI().doCommand(RIPACTION_ON);
        } else {
            this.getClientGUI().doCommand(RIPACTION_OFF);
        }
    }

    public void doCommand(String string) {
        this.getClientGUI().doCommand(string);

    }

    @Override
    public void process(Object input) {
        if (input == null) {
            printCommandHelp();
        }
        if (input instanceof String) {
            String string = (String) input;
            String[] params = string.split(" ");
            if (params.length == 1) {
                String command = params[0];
                if (command.equalsIgnoreCase(COMMAND_HELP)) {
                    printCommandHelp();
                } else if (command.equalsIgnoreCase(COMMAND_REMOVE_LABEL)) {
                    this.engine.removeLabelFromCurrent();
                } else if (command.equalsIgnoreCase(COMMAND_LIST_LABELS)) {
                    for (String entry : this.engine.getLabels()) {
                        printConsoleMessage(entry);
                    }
                } else if (command.equalsIgnoreCase(COMMAND_WHERE)) {
                    printConsoleMessage(this.engine.getCurrentRoomTrackingSummary());
                    printConsoleMessage(this.engine.getPathBehindStatus());
                    List<String> exits = this.engine.getCurrentRoomExitTargets();
                    if (exits.isEmpty()) {
                        printConsoleMessage("No mapped exits from current room yet.");
                    } else {
                        printConsoleMessage("Mapped exits:");
                        for (String mapping : exits) {
                            printConsoleMessage("\t" + mapping);
                        }
                    }
                } else if (command.equalsIgnoreCase(COMMAND_FLEE)) {
                    printConsoleMessage(this.engine.getBestFleePathStatus());
                } else if (command.equalsIgnoreCase(COMMAND_WAINO)) {
                    boolean enabled = this.engine.toggleWainoMode();
                    this.lastPrintedPathBehindStatus = null;
                    printConsoleMessage(String.format("Waino mode %s. Maze remap output is now %s.",
                            enabled ? "enabled" : "disabled", enabled ? "on" : "off"));
                    if (enabled) {
                        printPathBehindToScreenIfNeeded();
                    }
                } else {
                    printConsoleError(String.format("unknown command: [%s]", command));
                }
            } else if (params.length == 2) {
                String command = params[0];
                String label = params[1];
                if (command.equalsIgnoreCase(COMMAND_FLEE) && label.equalsIgnoreCase(COMMAND_GO)) {
                    printConsoleMessage(this.engine.executeBestFleePath());
                } else if (command.equalsIgnoreCase(COMMAND_ADD_LABEL)) {
                    if (!this.engine.roomLabelExists(label)) {
                        this.engine.setLabelToCurrentRoom(label);
                        printConsoleMessage(String.format("added label [%s] to this room", label));
                    } else {
                        printConsoleError(String.format("label [%s] already exists, must be unique per area", label));
                    }
                } else if (command.equalsIgnoreCase(COMMAND_RUN_TO_LABEL)) {
                    printConsoleMessage(String.format("running to room [%s]", label));
                    if (this.engine.roomLabelExists(label)) {
                        this.engine.runtoLabel(label);
                    } else {
                        printConsoleError(String.format("label [%s] not found", label));
                    }
                } else if (command.equalsIgnoreCase(COMMAND_APPEND_TO_NOTES)) {
                    printConsoleMessage(String.format("Appending to notes [%s]", label));
                    this.engine.getPanel().appentToNotes(label);
                } else {
                    printConsoleError(String.format("unknown command: [%s]", command));
                }

            } else if (params.length > 2) {
                String command = params[0];
                if (command.equalsIgnoreCase(COMMAND_APPEND_TO_NOTES)) {
                    String notes = string.substring(COMMAND_APPEND_TO_NOTES.length());
                    this.engine.getPanel().appentToNotes(notes);
                } else if (command.equalsIgnoreCase(COMMAND_FIND_DESC)) {
                    String findSring = string.substring(COMMAND_FIND_DESC.length()).trim();
                    searchPanel.setSearchText(findSring);
                    List<String> rooms = searchPanel.searchForRoomsWith(findSring);
                    for (String room : rooms) {
                        printConsoleMessage(room);
                    }
                } else {
                    printConsoleError(String.format("unknown command: [%s] or too many params, slow down!", command));
                }

            } else {
                printConsoleError("only 1 or 2 or KAZILLION params accepted");
            }
        }
    }

    private void printConsoleError(String msg) {
        this.getClientGUI().printText("general", "[Mapper error] " + msg + "\n", "F7856D");
    }

    private void printConsoleMessage(String msg) {
        this.getClientGUI().printText("general", "[Mapper] " + msg + "\n", "6AFA63");
    }

    public void printMapperMessage(String msg) {
        printConsoleMessage(msg);
    }

    private String getLoadedVersion() {
        Package pluginPackage = this.getClass().getPackage();
        if (pluginPackage == null || pluginPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return pluginPackage.getImplementationVersion();
    }

    private void printCommandHelp() {
        printConsoleMessage("Mapper has following commands:");
        printConsoleMessage(String.format("\t%s        - show this help", COMMAND_HELP));
        printConsoleMessage(String.format("\t%s <label> - to add label to current room", COMMAND_ADD_LABEL));
        printConsoleMessage(
                String.format("\t%s <label> - to run to room with that label ( need to set delim in corpsepanel)",
                        COMMAND_RUN_TO_LABEL));
        printConsoleMessage(String.format("\t%s         - to remove label from current room", COMMAND_REMOVE_LABEL));
        printConsoleMessage(String.format("\t%s        - to list labels and rooms", COMMAND_LIST_LABELS));
        printConsoleMessage(String.format("\t%s        - to append a line to roomnotes", COMMAND_APPEND_TO_NOTES));
        printConsoleMessage(String.format("\t%s <desc> - to find rooms by long desc", COMMAND_FIND_DESC));
        printConsoleMessage(String.format("\t%s        - show current room id and mapped exits", COMMAND_WHERE));
        printConsoleMessage(String.format("\t%s        - calculate best flee path away from Waino", COMMAND_FLEE));
        printConsoleMessage(String.format("\t%s %s     - run the calculated flee path immediately", COMMAND_FLEE,
                COMMAND_GO));
        printConsoleMessage(String.format("\t%s        - toggle Waino mode (also available as $%s)", COMMAND_WAINO,
                COMMAND_WAINO));
    }

    private static class MapperPacket {
        private final String areaName;
        private final String roomUID;
        private final String exitUsed;
        private final boolean indoors;
        private final String shortDesc;
        private final String longDesc;
        private final HashSet<String> exits;

        private MapperPacket(String areaName, String roomUID, String exitUsed, boolean indoors, String shortDesc,
                String longDesc, HashSet<String> exits) {
            this.areaName = areaName;
            this.roomUID = roomUID;
            this.exitUsed = exitUsed;
            this.indoors = indoors;
            this.shortDesc = shortDesc;
            this.longDesc = longDesc;
            this.exits = exits;
        }
    }
}
