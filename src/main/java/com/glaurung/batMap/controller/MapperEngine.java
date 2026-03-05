package com.glaurung.batMap.controller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.*;

import com.glaurung.batMap.gui.*;
import com.glaurung.batMap.gui.corpses.CorpsePanel;
import com.glaurung.batMap.io.AreaDataPersister;
import com.glaurung.batMap.io.GuiDataPersister;
import com.glaurung.batMap.vo.Area;
import com.glaurung.batMap.vo.AreaSaveObject;
import com.glaurung.batMap.vo.Exit;
import com.glaurung.batMap.vo.Room;
import com.mythicscape.batclient.interfaces.BatWindow;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.CrossoverScalingControl;
import edu.uci.ics.jung.visualization.control.PluggableGraphMouse;
import edu.uci.ics.jung.visualization.control.ScalingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.TranslatingGraphMousePlugin;
import edu.uci.ics.jung.visualization.decorators.EdgeShape;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.picking.PickedState;
import edu.uci.ics.jung.visualization.transform.MutableTransformer;

public class MapperEngine implements ItemListener, ComponentListener {

    private static final int MINIMAP_GRID_SPACING = 120;
    private static final int PATH_BEHIND_MAX_ENTRIES = 400;
    private static final int FLEE_ANALYSIS_MAX_STATE_VISITS = 250000;

    SparseMultigraph<Room, Exit> graph;
    VisualizationViewer<Room, Exit> vv;
    MapperLayout mapperLayout;
    Room currentRoom = null;
    Area area = null;
    MapperPanel panel;

    PickedState<Room> pickedState;
    String baseDir;
    BatWindow batWindow;
    ScalingGraphMousePlugin scaler;
    MapperPickingGraphMousePlugin mapperPickingGraphMousePlugin;
    boolean snapMode = true;
    CorpsePanel corpsePanel;
    MapperPlugin plugin;
    boolean mazemode = false;
    boolean reversableDirsMode = false;
    boolean wainoModeEnabled = false;
    Point miniMapOrigin = null;
    boolean miniMapSessionInitialized = false;
    String miniMapParseStatus = "Minimap parser not run yet.";
    LinkedList<Point> pathBehindCoordinates = new LinkedList<Point>();

    public MapperEngine(SparseMultigraph<Room, Exit> graph, MapperPlugin plugin) {
        this(plugin);
        this.graph = graph;
        this.mapperLayout.setGraph(graph);
    }

    public MapperEngine(MapperPlugin plugin) {
        this.plugin = plugin;
        graph = new SparseMultigraph<Room, Exit>();
        mapperLayout = new MapperLayout(graph);
        mapperLayout.setSize(new Dimension(500, 500)); // ????
        vv = new VisualizationViewer<Room, Exit>(mapperLayout);
        pickedState = vv.getPickedVertexState();
        pickedState.addItemListener(this);
        vv.setPreferredSize(new Dimension(500, 500)); // ????
        RenderContext<Room, Exit> rc = vv.getRenderContext();

        rc.setEdgeLabelTransformer(new ToStringLabeller<Exit>());
        rc.setEdgeLabelRenderer(new ExitLabelRenderer());
        rc.setEdgeShapeTransformer(new EdgeShape.QuadCurve<Room, Exit>());
        rc.setEdgeShapeTransformer(new EdgeShape.Wedge<Room, Exit>(30));
        rc.setEdgeFillPaintTransformer(new ExitPaintTransformer(vv));

        rc.setVertexShapeTransformer(new RoomShape(graph));
        rc.setVertexIconTransformer(new RoomIconTransformer(this));

        vv.getRenderContext().setLabelOffset(5);

        PluggableGraphMouse pgm = new PluggableGraphMouse();
        mapperPickingGraphMousePlugin = new MapperPickingGraphMousePlugin(MouseEvent.BUTTON1_MASK,
                MouseEvent.BUTTON3_MASK);
        mapperPickingGraphMousePlugin.setEngine(this);
        pgm.add(mapperPickingGraphMousePlugin);
        pgm.add(new TranslatingGraphMousePlugin(MouseEvent.BUTTON1_MASK));
        scaler = new ScalingGraphMousePlugin(new CrossoverScalingControl(), 0, 1 / 1.1f, 1.1f);
        pgm.add(scaler);
        vv.setGraphMouse(pgm);
        panel = new MapperPanel(this);
    }

    public void setSize(Dimension dimension) {
        mapperLayout.setSize(dimension);
        vv.setPreferredSize(dimension);
    }

    // areaname;roomUID;exitUsed;indoor boolean;shortDesc;longDesc;exits
    public void moveToRoom(String areaName, String roomUID, String exitUsed, boolean indoors, String shortDesc,
            String longDesc, Set<String> exits) {
        if (this.area == null || !this.area.getName().equalsIgnoreCase(areaName)) {
            moveToArea(areaName);
        }

        moveToRoom(roomUID, exitUsed, longDesc, shortDesc, indoors, exits);
        setRoomDescsForRoom(currentRoom, longDesc, shortDesc, indoors, exits);

    }

    /**
     * This method will create new room if need be, or just draw the exit between
     * prior and new room if it doesn't exist.
     * Looping into same room will have exit drawn, but only if it doesn't exist
     * yet.
     *
     * @param roomUID
     * @param exitUsed
     * @param exits
     * @param indoors
     * @param shortDesc
     * @param longDesc
     * @return true if room created was new, false if it already existed
     */
    public boolean moveToRoom(String roomUID, String exitUsed, String longDesc, String shortDesc, boolean indoors,
            Set<String> exits) {
        // System.out.println(roomUID);
        Room newRoom = getRoomFromGraph(roomUID);
        boolean newRoomAddedToGraph = false;
        if (newRoom == null) {
            newRoom = new Room(roomUID, this.area);
            newRoomAddedToGraph = true;
        }

        RoomMiniMapParser.MiniMapData miniMapData = RoomMiniMapParser.parse(longDesc);
        updateMiniMapParseStatus(longDesc, miniMapData);
        if (miniMapData != null && miniMapData.hasPlayerCoordinate() && !miniMapSessionInitialized) {
            miniMapSessionInitialized = true;
        }
        applyMiniMapDataToRoom(newRoom, miniMapData);
        updatePathBehindTracker(newRoom, miniMapData);

        Exit exit = new Exit(exitUsed);
        boolean sameRoomAsCurrent = currentRoom != null && currentRoom.equals(newRoom);
        if (currentRoom == null && !didTeleportIn(exitUsed)) {
            newRoom.setAreaEntrance(true);
        }

        if (currentRoom == null || didTeleportIn(exitUsed)) {
            graph.addVertex(newRoom);// if room existed in this graph, then this just does nothing?
        } else if (sameRoomAsCurrent && !isCompassDirection(exit.getExit())) {
            // same-room refresh packet (for example look/l): keep room state updated
            // but do not add a synthetic self-edge with non-movement command text
        } else {
            addOrUpdateExitConnection(currentRoom, newRoom, exit.getExit());

            if (reversableDirsMode && exit.getOpposite() != null) {
                addOrUpdateExitConnection(newRoom, currentRoom, exit.getOpposite());
            }

        }

        Point2D miniMapLocation = getMiniMapLocationForRoom(newRoom);

        if (newRoomAddedToGraph) {

            if (miniMapLocation != null) {
                vv.getGraphLayout().setLocation(newRoom, miniMapLocation);
            } else {

                if (currentRoom != null) {
                    Point2D oldroomLocation = mapperLayout.transform(currentRoom);
                    Point2D relativeLocation = DrawingUtils.getRelativePosition(oldroomLocation, exit, this.snapMode);
                    // relativeLocation = getValidLocation(relativeLocation);
                    relativeLocation = mapperLayout.getValidLocation(relativeLocation);
                    vv.getGraphLayout().setLocation(newRoom, relativeLocation);
                } else {
                    // either first room in new area, or new room in old area, no connection
                    // anywhere, either way lets draw into middle
                    Point2D possibleLocation = new Point2D.Double(panel.getWidth() / 2, panel.getHeight() / 2);
                    // possibleLocation = getValidLocation(possibleLocation);
                    possibleLocation = mapperLayout.getValidLocation(possibleLocation);
                    vv.getGraphLayout().setLocation(newRoom, possibleLocation);
                }
            }

        } else if (miniMapLocation != null) {
            vv.getGraphLayout().setLocation(newRoom, miniMapLocation);
        }
        if (currentRoom != null && mazemode && isCompassDirection(exitUsed)) {
            currentRoom.useExit(exitUsed);
        }

        refreshRoomGraphicsAndSetAsCurrent(newRoom, longDesc, shortDesc, indoors, exits);
        repaint();
        moveMapToStayWithCurrentRoom();
        return newRoomAddedToGraph;
    }

    public void repaint() {
        vv.repaint();
    }

    private boolean didTeleportIn(String exitUsed) {
        return exitUsed.equalsIgnoreCase(new Exit("").TELEPORT);
    }

    private boolean isCompassDirection(String exitUsed) {
        if (exitUsed == null || exitUsed.trim().isEmpty()) {
            return false;
        }
        return Exit.checkWhatExitIs(exitUsed) != null;
    }

    /**
     * This method will set short and long descs for this room, aka room where
     * player currently is.
     * Should be called right after addRoomAndMoveToit if it returned true
     *
     * @param longDesc
     * @param shortDesc
     * @param indoors
     */
    public void setRoomDescsForRoom(Room room, String longDesc, String shortDesc, boolean indoors, Set<String> exits) {

        room.setLongDesc(longDesc);
        room.setShortDesc(shortDesc);
        room.setIndoors(indoors);
        room.addExits(exits);
    }

    protected void refreshRoomGraphicsAndSetAsCurrent(Room newRoom, String longDesc, String shortDesc, boolean indoors,
            Set<String> exits) {
        // System.out.println("newroom: "+newRoom+"\n\tcurrentroom:
        // "+currentRoom+"\n\tpickedRoom: "+pickedRoom);
        if (currentRoom != null) {
            currentRoom.setCurrent(false);
            if (currentRoom.isPicked()) {
                newRoom.setPicked(true);
                currentRoom.setPicked(false);
                setRoomDescsForRoom(newRoom, longDesc, shortDesc, indoors, exits);
                singleRoomPicked(newRoom);
            }
        }
        newRoom.setCurrent(true);
        currentRoom = newRoom;
    }

    /**
     * This will save mapdata for current area, try to load data for new area. If no
     * data found, empty data created.
     * moveToRoom method should be called after this one to know where player is
     *
     * @param areaName name for area, or pass null if leaving area into outerworld
     *                 or such.
     */
    protected void moveToArea(String areaName) {

        resetMiniMapTrackingState();

        if (areaName == null) {
            clearExtraCurrentAndChosenValuesFromRooms();
            saveCurrentArea();
            this.area = null;
            currentRoom = null;

            pickedState.clear();
            this.graph = new SparseMultigraph<Room, Exit>();
            mapperLayout.setGraph(graph);
            Room nullRoom = null;
            this.panel.setTextForDescs("", "", "", nullRoom);
        } else {
            saveCurrentArea();
            AreaSaveObject areaSaveObject = null;
            try {
                areaSaveObject = AreaDataPersister.loadData(baseDir, areaName);

            } catch (ClassNotFoundException e) {
                // log.error(e.getMessage()+" "+e.getStackTrace());
            } catch (IOException e) {
                // log.error("Unable to find file "+e.getMessage());
            }
            if (areaSaveObject == null) {// area doesn't exist so we create new saveobject which has empty graphs and
                                         // maps
                areaSaveObject = new AreaSaveObject();
            }
            this.graph = areaSaveObject.getGraph();
            mapperLayout.setGraph(graph);
            // mousePlugin.setGraph(graph);
            mapperLayout.displayLoadedData(areaSaveObject);
            if (graph.getVertexCount() > 0) {
                this.area = graph.getVertices().iterator().next().getArea();
            } else {
                this.area = new Area(areaName);
            }
            this.currentRoom = null;

            /**
             *
             * PluggableGraphMouse pgm = new PluggableGraphMouse();
             * pgm.add(new PickingGraphMousePlugin<Room,Exit>());
             * pgm.add(new TranslatingGraphMousePlugin(MouseEvent.BUTTON3_MASK));
             * pgm.add(new ScalingGraphMousePlugin(new CrossoverScalingControl(), 0, 1 /
             * 1.1f, 1.1f));
             * pgm.add(new MapperEditingGraphMousePlugin(graph));
             * vv.setGraphMouse(pgm);
             */
        }

        repaint();
    }

    private void clearExtraCurrentAndChosenValuesFromRooms() {
        for (Room room : this.graph.getVertices()) {
            room.setCurrent(false);
            room.setPicked(false);
        }

    }

    protected void saveCurrentArea() {
        try {
            if (this.area != null) {
                AreaDataPersister.save(baseDir, graph, mapperLayout);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        saveCurrentArea();
    }

    public void clearCurrentArea() {
        String areaName = area.getName();
        AreaSaveObject areaSaveObject = new AreaSaveObject();
        this.graph = areaSaveObject.getGraph();
        mapperLayout.setGraph(graph);
        mapperLayout.displayLoadedData(areaSaveObject);
        this.area = new Area(areaName);
        this.currentRoom = null;
        resetMiniMapTrackingState();
        repaint();
    }

    private Room getRoomFromGraph(String uid) {
        for (Room room : this.graph.getVertices()) {
            if (room.getId().equals(uid)) {
                return room;
            }
        }

        return null;
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Object subject = e.getItem();
        if (subject instanceof Room) {
            Room tempRoom = (Room) subject;
            if (e.getStateChange() == ItemEvent.SELECTED) {
                pickedState.pick(tempRoom, true);
                tempRoom.setPicked(true);

            } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                pickedState.pick(tempRoom, false);
                tempRoom.setPicked(false);
            }
            repaint();
        }
        if (pickedState.getPicked().size() == 1) {
            singleRoomPicked(pickedState.getPicked().iterator().next());
        } else {
            this.panel.setTextForDescs("", "", "", null);
        }

    }

    public void changeRoomColor(Color color) {
        for (Room room : pickedState.getPicked()) {
            room.setColor(color);
        }
        repaint();

    }

    protected void singleRoomPicked(Room room) {
        this.panel.setTextForDescs(room.getShortDesc(), room.getLongDesc(), makeExitsStringFromPickedRoom(room), room);

    }

    protected String makeExitsStringFromPickedRoom(Room room) {

        Collection<Exit> outExits = graph.getOutEdges(room);
        StringBuilder exitString = new StringBuilder();
        if (outExits != null) {
            Iterator<Exit> exitIterator = outExits.iterator();

            while (exitIterator.hasNext()) {
                Exit exit = exitIterator.next();
                room.getExits().add(exit.getExit());
            }
        }

        Iterator<String> roomExitIterator = room.getExits().iterator();
        while (roomExitIterator.hasNext()) {
            exitString.append(roomExitIterator.next());
            if (roomExitIterator.hasNext()) {
                exitString.append(", ");
            }
        }

        return exitString.toString();

    }

    public VisualizationViewer<Room, Exit> getVV() {
        return this.vv;
    }

    public void setMapperSize(Dimension size) {
        this.mapperLayout.setSize(size);
        this.vv.setSize(size);
        repaint();
    }

    public MapperPanel getPanel() {
        return this.panel;
    }

    /**
     * This method refocuses current room into middle of map,
     * if current room is over away from center by 50% of distance to windowedge
     */
    protected void moveMapToStayWithCurrentRoom() {

        Point2D currentRoomPoint = this.mapperLayout.transform(currentRoom);

        Point2D mapViewCenterPoint = this.panel.getMapperCentralPoint();
        Point2D viewPoint = vv.getRenderContext().getMultiLayerTransformer().transform(currentRoomPoint);
        if (needToRelocate(viewPoint, mapViewCenterPoint)) {
            MutableTransformer modelTransformer = vv.getRenderContext().getMultiLayerTransformer()
                    .getTransformer(Layer.LAYOUT);
            viewPoint = vv.getRenderContext().getMultiLayerTransformer().inverseTransform(viewPoint);
            mapViewCenterPoint = vv.getRenderContext().getMultiLayerTransformer().inverseTransform(mapViewCenterPoint);
            float dx = (float) (mapViewCenterPoint.getX() - viewPoint.getX());
            float dy = (float) (mapViewCenterPoint.getY() - viewPoint.getY());
            modelTransformer.translate(dx, dy);
            repaint();
        }
    }

    private boolean needToRelocate(Point2D currentRoomPoint, Point2D mapViewCenterPoint) {
        if (currentRoomPoint.getX() < 0.6 * mapViewCenterPoint.getX()
                || currentRoomPoint.getX() > 1.3 * mapViewCenterPoint.getX()) {
            return true;
        }

        if (currentRoomPoint.getY() < 0.6 * mapViewCenterPoint.getY()
                || currentRoomPoint.getY() > 1.3 * mapViewCenterPoint.getY()) {
            return true;
        }

        return false;
    }

    // public void redraw() {
    // this.mapperLayout.reDrawFromRoom( pickedRoom, this.mapperLayout.transform(
    // pickedRoom ) );
    // repaint();
    //
    // }

    public SparseMultigraph<Room, Exit> getGraph() {
        return graph;
    }

    public void toggleDescs() {
        this.panel.toggleDescs();

    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getBaseDir() {
        return this.baseDir;
    }

    public void setBatWindow(BatWindow clientWin) {
        this.batWindow = clientWin;

    }

    public void saveGuiData(Point location, Dimension size) {
        GuiDataPersister.save(this.baseDir, location, size);
    }

    @Override
    public void componentHidden(ComponentEvent e) {

    }

    @Override
    public void componentMoved(ComponentEvent e) {
        if (this.batWindow != null) {
            GuiDataPersister.save(this.baseDir, this.batWindow.getLocation(), this.batWindow.getSize());
        }
    }

    @Override
    public void componentResized(ComponentEvent e) {
        if (this.batWindow != null) {
            GuiDataPersister.save(this.baseDir, this.batWindow.getLocation(), this.batWindow.getSize());
        }
    }

    @Override
    public void componentShown(ComponentEvent e) {

    }

    public ScalingGraphMousePlugin getScaler() {
        return scaler;
    }

    public void setRoomSnapping(boolean roomsWillSnapIntoPlaces) {
        this.snapMode = roomsWillSnapIntoPlaces;
        mapperPickingGraphMousePlugin.setSnapmode(roomsWillSnapIntoPlaces);
        this.mapperLayout.setSnapMode(roomsWillSnapIntoPlaces);
    }

    public void zoomIn() {
        this.scaler.getScaler().scale(this.vv, 1.1f, this.vv.getCenter());
    }

    public void zoomOut() {
        this.scaler.getScaler().scale(this.vv, 1 / 1.1f, this.vv.getCenter());
    }

    public String checkDirsFromCurrentRoomTo(Room targetroom, boolean shortDirs) {
        DijkstraShortestPath<Room, Exit> algorithm = new DijkstraShortestPath<Room, Exit>(this.getGraph());
        StringBuilder returnvalue = new StringBuilder();
        String delim = this.corpsePanel.getDelim();
        List<Exit> path = algorithm.getPath(currentRoom, targetroom);
        if (shortDirs) {
            // plan is to transform "north, north, north, south, east, tunnel" into
            // 3 n;s;e;tunnel
            int count_repeated_dirs = 1;
            String previous = null;
            Iterator<Exit> pathIterator = path.iterator();

            while (pathIterator.hasNext()) {
                String shortExit = Exit.checkWhatExitIs(pathIterator.next().getExit());
                if (previous == null) {
                    previous = shortExit;
                } else {
                    if (previous.equalsIgnoreCase(shortExit)) {
                        count_repeated_dirs++;
                    } else {
                        if (count_repeated_dirs == 1) {
                            returnvalue.append(previous).append(delim);
                        } else {
                            returnvalue.append(count_repeated_dirs).append(" ").append(previous).append(delim);
                        }
                        previous = shortExit;
                        count_repeated_dirs = 1;
                    }
                }
            }

            if (count_repeated_dirs == 1) {
                returnvalue.append(previous).append(delim);
            } else {
                returnvalue.append(count_repeated_dirs).append(" ").append(previous).append(delim);
            }
        } else {
            for (Exit exit : path) {
                returnvalue.append(exit.getExit()).append(delim);
            }
        }
        return returnvalue.toString();
    }

    public void setCorpsePanel(CorpsePanel corpsePanel) {
        this.corpsePanel = corpsePanel;
    }

    public void sendToMud(String command) {
        this.plugin.doCommand(command);
    }

    public void sendToParty(String message) {
        this.plugin.doCommand("party say " + message.replace(this.corpsePanel.getDelim(), ","));
    }

    public void removeLabelFromCurrent() {
        this.currentRoom.setLabel(null);
    }

    public void setLabelToCurrentRoom(String label) {
        this.currentRoom.setLabel(label);
    }

    public boolean roomLabelExists(String label) {
        for (Room room : this.graph.getVertices()) {
            if (room.getLabel() != null && room.getLabel().equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    public void runtoLabel(String label) {
        Room targetroom = null;
        for (Room room : this.graph.getVertices()) {
            if (room.getLabel() != null && room.getLabel().equalsIgnoreCase(label)) {
                targetroom = room;
            }
        }
        String dirs = checkDirsFromCurrentRoomTo(targetroom, false);
        sendToMud(dirs);
    }

    public List<String> getLabels() {
        List<String> labels = new LinkedList<>();
        for (Room room : this.graph.getVertices()) {
            if (room.getLabel() != null) {
                labels.add(String.format("%-10s %s", room.getLabel(), room.getShortDesc()));
            }
        }
        return labels;
    }

    public void setMazeMode(boolean enabled) {
        this.mazemode = enabled;
        for (Room room : this.graph.getVertices()) {
            room.resetExitUsage();
        }
        repaint();
    }

    public boolean isMazeMode() {
        return this.mazemode;
    }

    public void setReversableDirsMode(boolean enabled) {
        this.reversableDirsMode = enabled;
    }

    public boolean toggleWainoMode() {
        this.wainoModeEnabled = !this.wainoModeEnabled;
        return this.wainoModeEnabled;
    }

    public boolean isWainoModeEnabled() {
        return this.wainoModeEnabled;
    }

    private void resetMiniMapTrackingState() {
        this.miniMapOrigin = null;
        this.miniMapSessionInitialized = false;
        this.miniMapParseStatus = "Minimap parser not run yet.";
        this.pathBehindCoordinates.clear();
    }

    private void clearCardinalMappingsForCurrentArea() {
        List<Exit> cardinalEdgesToRemove = new ArrayList<Exit>();

        for (Room room : this.graph.getVertices()) {
            room.clearCardinalExitTargets();

            Collection<Exit> outEdges = this.graph.getOutEdges(room);
            if (outEdges == null) {
                continue;
            }

            for (Exit exit : outEdges) {
                String normalizedDirection = Exit.checkWhatExitIs(exit.getExit());
                if (isCardinalDirection(normalizedDirection)) {
                    cardinalEdgesToRemove.add(exit);
                }
            }
        }

        for (Exit edge : cardinalEdgesToRemove) {
            this.graph.removeEdge(edge);
        }
    }

    private void addOrUpdateExitConnection(Room sourceRoom, Room destinationRoom, String exitUsed) {
        if (sourceRoom == null || destinationRoom == null || exitUsed == null || exitUsed.trim().isEmpty()) {
            return;
        }

        String normalizedDirection = Exit.checkWhatExitIs(exitUsed);
        if (isCardinalDirection(normalizedDirection)) {
            upsertCardinalExitConnection(sourceRoom, destinationRoom, normalizedDirection);
            return;
        }

        if (GraphUtils.canAddExit(graph.getOutEdges(sourceRoom), exitUsed)) {
            sourceRoom.addExit(exitUsed);
            graph.addEdge(new Exit(exitUsed), new Pair<Room>(sourceRoom, destinationRoom), EdgeType.DIRECTED);
        }
    }

    private void upsertCardinalExitConnection(Room sourceRoom, Room destinationRoom, String cardinalDirection) {
        String previousTargetRoomId = sourceRoom.getCardinalExitTarget(cardinalDirection);
        boolean mappingCreated = previousTargetRoomId == null;
        boolean mappingChanged = previousTargetRoomId != null && !previousTargetRoomId.equals(destinationRoom.getId());

        sourceRoom.setCardinalExitTarget(cardinalDirection, destinationRoom.getId());
        sourceRoom.addExit(cardinalDirection);

        Collection<Exit> outEdges = this.graph.getOutEdges(sourceRoom);
        List<Exit> matchingCardinalEdges = new ArrayList<Exit>();
        if (outEdges != null) {
            for (Exit existingExit : outEdges) {
                String existingDirection = Exit.checkWhatExitIs(existingExit.getExit());
                if (cardinalDirection.equals(existingDirection)) {
                    matchingCardinalEdges.add(existingExit);
                }
            }
        }

        boolean alreadyMappedToDestination = false;
        for (Exit existingCardinalEdge : matchingCardinalEdges) {
            Room existingDestination = getDestinationRoomForExit(sourceRoom, existingCardinalEdge);
            if (existingDestination != null && existingDestination.equals(destinationRoom)) {
                if (alreadyMappedToDestination) {
                    this.graph.removeEdge(existingCardinalEdge);
                } else {
                    alreadyMappedToDestination = true;
                }
            } else {
                this.graph.removeEdge(existingCardinalEdge);
            }
        }

        if (!alreadyMappedToDestination) {
            this.graph.addEdge(new Exit(cardinalDirection), new Pair<Room>(sourceRoom, destinationRoom),
                    EdgeType.DIRECTED);
        }

        if (mappingCreated) {
            printExitMappingCreatedMessage(sourceRoom.getId(), cardinalDirection, destinationRoom.getId());
        }

        if (mappingChanged) {
            printExitRemapMessage(sourceRoom.getId(), cardinalDirection, previousTargetRoomId, destinationRoom.getId());
        }
    }

    private void printExitMappingCreatedMessage(String sourceRoomId, String direction, String targetRoomId) {
        if (this.plugin == null) {
            return;
        }

        this.plugin.printMapperMessage(
                String.format("Mapped new exit: %s %s -> %s.", sourceRoomId, direction, targetRoomId));
    }

    private void printExitRemapMessage(String sourceRoomId, String direction, String previousTargetRoomId,
            String newTargetRoomId) {
        if (this.plugin == null || !this.wainoModeEnabled) {
            return;
        }

        this.plugin.printMapperMessage(String.format("Updated exit mapping: %s %s now points to %s (was %s).",
                sourceRoomId, direction, newTargetRoomId, previousTargetRoomId));
    }

    private void applyMiniMapDataToRoom(Room room, RoomMiniMapParser.MiniMapData miniMapData) {
        if (room == null || miniMapData == null) {
            return;
        }

        if (miniMapData.hasPlayerCoordinate()) {
            room.setMiniMapCoordinate(miniMapData.getPlayerX(), miniMapData.getPlayerY());
        }
    }

    private void updateMiniMapParseStatus(String longDesc, RoomMiniMapParser.MiniMapData miniMapData) {
        if (longDesc == null || longDesc.trim().isEmpty()) {
            this.miniMapParseStatus = "No minimap data in longDesc (empty).";
            return;
        }

        if (miniMapData == null) {
            this.miniMapParseStatus = "No 7x7 minimap block detected in longDesc.";
            return;
        }

        if (!miniMapData.hasPlayerCoordinate()) {
            this.miniMapParseStatus = "Minimap found but player marker (* or @) missing.";
            return;
        }

        StringBuilder status = new StringBuilder();
        status.append("Minimap parsed player@[")
                .append(miniMapData.getPlayerX())
                .append(",")
                .append(miniMapData.getPlayerY())
                .append("]");

        if (miniMapData.hasWainoCoordinate()) {
            status.append(" W@[")
                    .append(miniMapData.getWainoX())
                    .append(",")
                    .append(miniMapData.getWainoY())
                    .append("]");
        } else {
            status.append(" W@[unknown]");
        }

        this.miniMapParseStatus = status.toString();
    }

    private void updatePathBehindTracker(Room room, RoomMiniMapParser.MiniMapData miniMapData) {
        Point currentCoordinate = getRoomMiniMapCoordinate(room);
        if (currentCoordinate == null) {
            return;
        }

        if (miniMapData != null && miniMapData.hasWainoCoordinate()) {
            Point wainoCoordinate = new Point(miniMapData.getWainoX(), miniMapData.getWainoY());
            if (currentCoordinate.equals(wainoCoordinate)) {
                this.pathBehindCoordinates.clear();
                this.pathBehindCoordinates.addLast(currentCoordinate);
                return;
            }
        }

        if (!this.pathBehindCoordinates.isEmpty()) {
            Point lastCoordinate = this.pathBehindCoordinates.getLast();
            if (currentCoordinate.equals(lastCoordinate)) {
                return;
            }
        }

        this.pathBehindCoordinates.addLast(currentCoordinate);

        while (this.pathBehindCoordinates.size() > PATH_BEHIND_MAX_ENTRIES) {
            this.pathBehindCoordinates.removeFirst();
        }
    }

    public String getPathBehindStatus() {
        if (this.pathBehindCoordinates.isEmpty()) {
            return "Path behind: unavailable (no tracked coordinates yet).";
        }

        int roomsBehind = Math.max(0, this.pathBehindCoordinates.size() - 1);
        return String.format("Path behind (%d room%s): %s", roomsBehind, roomsBehind == 1 ? "" : "s",
                formatPathBehindCoordinates());
    }

    public String getBestFleePathStatus() {
        FleePathAnalysisResult analysis = analyzeBestFleePath();

        return buildBestFleePathStatus(analysis);
    }

    public String executeBestFleePath() {
        FleePathAnalysisResult analysis = analyzeBestFleePath();
        String status = buildBestFleePathStatus(analysis);

        if (!hasUsableFleePath(analysis)) {
            return status;
        }

        String command = buildFleeMudCommand(analysis.bestPathSteps);
        if (command == null || command.trim().isEmpty()) {
            return status;
        }

        sendToMud(command);
        return status + " Executing flee path now.";
    }

    private String buildBestFleePathStatus(FleePathAnalysisResult analysis) {
        if (analysis == null) {
            return "Flee analysis unavailable.";
        }

        if (analysis.blockedReason != null) {
            return analysis.blockedReason;
        }

        if (analysis.analysisLimitReached) {
            return String.format("Flee analysis stopped after %d states: too many possibilities to evaluate exactly.",
                    analysis.stateVisits);
        }

        if (analysis.bestPathSteps.isEmpty()) {
            return "No flee route found that avoids the tracked path behind from Waino.";
        }

        StringBuilder exits = new StringBuilder();
        for (int i = 0; i < analysis.bestPathSteps.size(); i++) {
            if (i > 0) {
                exits.append(", ");
            }
            exits.append(analysis.bestPathSteps.get(i).exitCommand);
        }

        StringBuilder coordinates = new StringBuilder("[");
        coordinates.append(formatCoordinate(analysis.startCoordinate));
        for (FleePathStep step : analysis.bestPathSteps) {
            coordinates.append(" -> ")
                    .append(formatCoordinate(step.coordinate));
        }
        coordinates.append("]");

        int roomsFled = analysis.bestPathSteps.size();
        return String.format("Best flee path (%d room%s; analyzed %d branches): exits=%s path=%s", roomsFled,
                roomsFled == 1 ? "" : "s", analysis.terminalPathsAnalyzed, exits.toString(),
                coordinates.toString());
    }

    private boolean hasUsableFleePath(FleePathAnalysisResult analysis) {
        return analysis != null
                && analysis.blockedReason == null
                && !analysis.analysisLimitReached
                && analysis.bestPathSteps != null
                && !analysis.bestPathSteps.isEmpty();
    }

    private String buildFleeMudCommand(List<FleePathStep> pathSteps) {
        if (pathSteps == null || pathSteps.isEmpty()) {
            return "";
        }

        String delimiter = getCommandDelimiter();
        StringBuilder command = new StringBuilder();
        for (FleePathStep step : pathSteps) {
            if (step == null || step.exitCommand == null || step.exitCommand.trim().isEmpty()) {
                continue;
            }
            command.append(step.exitCommand).append(delimiter);
        }

        return command.toString();
    }

    private String getCommandDelimiter() {
        if (this.corpsePanel == null) {
            return ";";
        }

        String delimiter = this.corpsePanel.getDelim();
        if (delimiter == null || delimiter.isEmpty()) {
            return ";";
        }

        return delimiter;
    }

    private FleePathAnalysisResult analyzeBestFleePath() {
        FleePathAnalysisResult result = new FleePathAnalysisResult();

        if (this.currentRoom == null) {
            result.blockedReason = "Flee analysis unavailable: no current room selected.";
            return result;
        }

        Point startCoordinate = getRoomMiniMapCoordinate(this.currentRoom);
        if (startCoordinate == null) {
            result.blockedReason = "Flee analysis unavailable: current room has no minimap coordinate.";
            return result;
        }

        if (this.pathBehindCoordinates.isEmpty()) {
            result.blockedReason = "Flee analysis unavailable: path behind from Waino is empty.";
            return result;
        }

        result.startCoordinate = startCoordinate;
        result.wainoCoordinate = copyCoordinate(this.pathBehindCoordinates.getFirst());

        Set<Point> forbiddenCoordinates = new HashSet<Point>();
        for (Point coordinate : this.pathBehindCoordinates) {
            if (coordinate != null) {
                forbiddenCoordinates.add(copyCoordinate(coordinate));
            }
        }
        forbiddenCoordinates.remove(startCoordinate);

        Set<String> visitedRoomIds = new HashSet<String>();
        visitedRoomIds.add(this.currentRoom.getId());

        List<FleePathStep> currentPath = new ArrayList<FleePathStep>();
        exploreFleePaths(this.currentRoom, forbiddenCoordinates, visitedRoomIds, currentPath, result);

        if (result.bestPathSteps == null) {
            result.bestPathSteps = new ArrayList<FleePathStep>();
        }

        return result;
    }

    private void exploreFleePaths(Room room, Set<Point> forbiddenCoordinates, Set<String> visitedRoomIds,
            List<FleePathStep> currentPath, FleePathAnalysisResult result) {
        if (result.analysisLimitReached) {
            return;
        }

        result.stateVisits++;
        if (result.stateVisits > FLEE_ANALYSIS_MAX_STATE_VISITS) {
            result.analysisLimitReached = true;
            return;
        }

        List<FleeMoveOption> options = getSortedFleeMoveOptions(room);
        boolean extended = false;

        for (FleeMoveOption option : options) {
            Room destination = option.destination;
            if (destination == null || destination.getId() == null) {
                continue;
            }
            if (visitedRoomIds.contains(destination.getId())) {
                continue;
            }

            Point destinationCoordinate = getRoomMiniMapCoordinate(destination);
            if (destinationCoordinate == null) {
                continue;
            }
            if (forbiddenCoordinates.contains(destinationCoordinate)) {
                continue;
            }

            extended = true;
            visitedRoomIds.add(destination.getId());
            currentPath.add(new FleePathStep(option.exitCommand, destinationCoordinate));

            exploreFleePaths(destination, forbiddenCoordinates, visitedRoomIds, currentPath, result);

            currentPath.remove(currentPath.size() - 1);
            visitedRoomIds.remove(destination.getId());

            if (result.analysisLimitReached) {
                return;
            }
        }

        if (!extended) {
            evaluateFleeCandidate(currentPath, result);
        }
    }

    private void evaluateFleeCandidate(List<FleePathStep> currentPath, FleePathAnalysisResult result) {
        result.terminalPathsAnalyzed++;

        int candidateLength = currentPath.size();
        Point endpoint = result.startCoordinate;
        if (!currentPath.isEmpty()) {
            endpoint = currentPath.get(currentPath.size() - 1).coordinate;
        }
        int candidateDistance = calculateCoordinateDistance(result.wainoCoordinate, endpoint);
        String candidateSignature = buildPathSignature(currentPath);

        if (result.bestPathSteps == null) {
            result.bestPathSteps = copyPath(currentPath);
            result.bestPathLength = candidateLength;
            result.bestEndpointDistance = candidateDistance;
            result.bestPathSignature = candidateSignature;
            return;
        }

        if (candidateLength > result.bestPathLength
                || (candidateLength == result.bestPathLength && candidateDistance > result.bestEndpointDistance)
                || (candidateLength == result.bestPathLength && candidateDistance == result.bestEndpointDistance
                        && candidateSignature.compareTo(result.bestPathSignature) < 0)) {
            result.bestPathSteps = copyPath(currentPath);
            result.bestPathLength = candidateLength;
            result.bestEndpointDistance = candidateDistance;
            result.bestPathSignature = candidateSignature;
        }
    }

    private List<FleeMoveOption> getSortedFleeMoveOptions(Room room) {
        List<FleeMoveOption> options = new ArrayList<FleeMoveOption>();
        if (room == null) {
            return options;
        }

        Collection<Exit> outEdges = this.graph.getOutEdges(room);
        if (outEdges == null) {
            return options;
        }

        for (Exit exit : outEdges) {
            if (exit == null || exit.getExit() == null || exit.getExit().trim().isEmpty()) {
                continue;
            }

            Room destination = getDestinationRoomForExit(room, exit);
            if (destination == null) {
                continue;
            }

            options.add(new FleeMoveOption(exit.getExit(), destination));
        }

        Collections.sort(options, new Comparator<FleeMoveOption>() {
            @Override
            public int compare(FleeMoveOption first, FleeMoveOption second) {
                int byExit = first.exitCommand.compareToIgnoreCase(second.exitCommand);
                if (byExit != 0) {
                    return byExit;
                }

                String firstRoomId = first.destination.getId() == null ? "" : first.destination.getId();
                String secondRoomId = second.destination.getId() == null ? "" : second.destination.getId();
                return firstRoomId.compareToIgnoreCase(secondRoomId);
            }
        });

        return options;
    }

    private List<FleePathStep> copyPath(List<FleePathStep> sourcePath) {
        List<FleePathStep> copy = new ArrayList<FleePathStep>();
        if (sourcePath == null) {
            return copy;
        }

        for (FleePathStep step : sourcePath) {
            copy.add(new FleePathStep(step.exitCommand, step.coordinate));
        }

        return copy;
    }

    private String buildPathSignature(List<FleePathStep> path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        StringBuilder signature = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                signature.append('|');
            }
            signature.append(path.get(i).exitCommand);
        }
        return signature.toString();
    }

    private int calculateCoordinateDistance(Point from, Point to) {
        if (from == null || to == null) {
            return Integer.MIN_VALUE;
        }

        return Math.abs(from.x - to.x) + Math.abs(from.y - to.y);
    }

    private Point copyCoordinate(Point point) {
        if (point == null) {
            return null;
        }
        return new Point(point.x, point.y);
    }

    private Point getRoomMiniMapCoordinate(Room room) {
        if (room == null || !room.hasMiniMapCoordinate()) {
            return null;
        }

        return new Point(room.getMiniMapX(), room.getMiniMapY());
    }

    private String formatPathBehindCoordinates() {
        if (this.pathBehindCoordinates.isEmpty()) {
            return "[]";
        }

        StringBuilder path = new StringBuilder("[");
        for (int i = 0; i < this.pathBehindCoordinates.size(); i++) {
            if (i > 0) {
                path.append(" -> ");
            }
            path.append(formatCoordinate(this.pathBehindCoordinates.get(i)));
        }
        path.append("]");
        return path.toString();
    }

    private String formatCoordinate(Point coordinate) {
        if (coordinate == null) {
            return "[?,?]";
        }
        return String.format("[%d,%d]", coordinate.x, coordinate.y);
    }

    private Point2D getMiniMapLocationForRoom(Room room) {
        if (room == null || !room.hasMiniMapCoordinate()) {
            return null;
        }

        if (this.miniMapOrigin == null) {
            this.miniMapOrigin = initializeMiniMapOrigin(room);
        }

        double x = this.miniMapOrigin.getX() + room.getMiniMapX() * MINIMAP_GRID_SPACING;
        double y = this.miniMapOrigin.getY() + room.getMiniMapY() * MINIMAP_GRID_SPACING;
        return new Point2D.Double(x, y);
    }

    private Point initializeMiniMapOrigin(Room room) {
        Point2D centerPoint = this.panel.getMapperCentralPoint();

        int centerX = (int) centerPoint.getX();
        int centerY = (int) centerPoint.getY();

        if (centerX <= 0) {
            centerX = this.vv.getWidth() / 2;
        }
        if (centerY <= 0) {
            centerY = this.vv.getHeight() / 2;
        }
        if (centerX <= 0) {
            centerX = 250;
        }
        if (centerY <= 0) {
            centerY = 250;
        }

        int originX = centerX - room.getMiniMapX() * MINIMAP_GRID_SPACING;
        int originY = centerY - room.getMiniMapY() * MINIMAP_GRID_SPACING;
        return new Point(originX, originY);
    }

    public String getCurrentRoomTrackingSummary() {
        if (this.currentRoom == null) {
            return "No current room selected yet.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("roomUID=").append(this.currentRoom.getId());

        if (this.currentRoom.hasMiniMapCoordinate()) {
            summary.append(" @[")
                    .append(this.currentRoom.getMiniMapX())
                    .append(",")
                    .append(this.currentRoom.getMiniMapY())
                    .append("]");
        } else {
            summary.append(" @[?,?]");
        }

        summary.append(" parser=").append(this.miniMapParseStatus);
        summary.append(" pathBehind=").append(formatPathBehindCoordinates());

        return summary.toString();
    }

    public boolean isCurrentRoomMiniMapTracked() {
        return this.currentRoom != null && this.currentRoom.hasMiniMapCoordinate();
    }

    public List<String> getCurrentRoomExitTargets() {
        List<String> mappings = new LinkedList<String>();
        if (this.currentRoom == null) {
            return mappings;
        }

        addCardinalExitMappingIfKnown("n", mappings);
        addCardinalExitMappingIfKnown("e", mappings);
        addCardinalExitMappingIfKnown("s", mappings);
        addCardinalExitMappingIfKnown("w", mappings);

        Collection<Exit> outEdges = this.graph.getOutEdges(this.currentRoom);
        if (outEdges != null) {
            for (Exit exit : outEdges) {
                String normalizedDirection = Exit.checkWhatExitIs(exit.getExit());
                if (isCardinalDirection(normalizedDirection)) {
                    continue;
                }

                Room destination = getDestinationRoomForExit(this.currentRoom, exit);
                if (destination == null) {
                    continue;
                }

                StringBuilder mapping = new StringBuilder();
                mapping.append(exit.getExit())
                        .append(" -> ")
                        .append(destination.getId());
                if (destination.hasMiniMapCoordinate()) {
                    mapping.append(" [")
                            .append(destination.getMiniMapX())
                            .append(",")
                            .append(destination.getMiniMapY())
                            .append("]");
                } else {
                    mapping.append(" [?,?]");
                }
                mappings.add(mapping.toString());
            }
        }

        Collections.sort(mappings);
        return mappings;
    }

    private void addCardinalExitMappingIfKnown(String cardinalDirection, List<String> mappings) {
        String targetRoomId = this.currentRoom.getCardinalExitTarget(cardinalDirection);
        if (targetRoomId == null) {
            return;
        }

        Room destination = getRoomFromGraph(targetRoomId);
        StringBuilder mapping = new StringBuilder();
        mapping.append(cardinalDirection)
                .append(" -> ")
                .append(targetRoomId);

        if (destination != null && destination.hasMiniMapCoordinate()) {
            mapping.append(" [")
                    .append(destination.getMiniMapX())
                    .append(",")
                    .append(destination.getMiniMapY())
                    .append("]");
        } else {
            mapping.append(" [?,?]");
        }

        mappings.add(mapping.toString());
    }

    private Room getDestinationRoomForExit(Room fromRoom, Exit exit) {
        Pair<Room> endpoints = this.graph.getEndpoints(exit);
        if (endpoints == null) {
            return null;
        }

        if (fromRoom.equals(endpoints.getFirst())) {
            return endpoints.getSecond();
        }
        if (fromRoom.equals(endpoints.getSecond())) {
            return endpoints.getFirst();
        }
        return null;
    }

    private static class FleeMoveOption {
        private final String exitCommand;
        private final Room destination;

        private FleeMoveOption(String exitCommand, Room destination) {
            this.exitCommand = exitCommand;
            this.destination = destination;
        }
    }

    private static class FleePathStep {
        private final String exitCommand;
        private final Point coordinate;

        private FleePathStep(String exitCommand, Point coordinate) {
            this.exitCommand = exitCommand;
            this.coordinate = coordinate == null ? null : new Point(coordinate.x, coordinate.y);
        }
    }

    private static class FleePathAnalysisResult {
        private String blockedReason;
        private boolean analysisLimitReached;
        private long stateVisits;
        private long terminalPathsAnalyzed;
        private Point startCoordinate;
        private Point wainoCoordinate;
        private List<FleePathStep> bestPathSteps;
        private int bestPathLength = Integer.MIN_VALUE;
        private int bestEndpointDistance = Integer.MIN_VALUE;
        private String bestPathSignature = "";
    }

    private boolean isCardinalDirection(String direction) {
        if (direction == null) {
            return false;
        }
        return direction.equals("n") || direction.equals("s") || direction.equals("e") || direction.equals("w");
    }

}
