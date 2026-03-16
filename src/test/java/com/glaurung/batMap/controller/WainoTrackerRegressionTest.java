package com.glaurung.batMap.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;

import org.junit.Test;

import com.glaurung.batMap.vo.Area;
import com.glaurung.batMap.vo.Exit;
import com.glaurung.batMap.vo.Room;

import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;

public class WainoTrackerRegressionTest {

    @Test
    public void shouldRebuildPathBehindWhenWainoMovesInSameRoomPacket() throws Exception {
        MapperEngine engine = new MapperEngine((MapperPlugin) null);

        Area area = new Area("test");
        Room wainoRoom = new Room("waino-room", area);
        Room middleRoom = new Room("middle-room", area);
        Room currentRoom = new Room("current-room", area);

        wainoRoom.setMiniMapCoordinate(1, 1);
        middleRoom.setMiniMapCoordinate(2, 1);
        currentRoom.setMiniMapCoordinate(3, 1);

        engine.graph.addVertex(wainoRoom);
        engine.graph.addVertex(middleRoom);
        engine.graph.addVertex(currentRoom);

        engine.graph.addEdge(new Exit("e"), new Pair<Room>(wainoRoom, middleRoom), EdgeType.DIRECTED);
        engine.graph.addEdge(new Exit("e"), new Pair<Room>(middleRoom, currentRoom), EdgeType.DIRECTED);

        // Seed stale trail to mimic the bad behavior from the original run.
        engine.pathBehindCoordinates = new LinkedList<Point>(Arrays.asList(
                new Point(9, 9),
                new Point(8, 9),
                new Point(7, 9)));
        engine.lastSeenWainoCoordinate = new Point(0, 0);

        RoomMiniMapParser.MiniMapData miniMapData = new RoomMiniMapParser.MiniMapData(
                Arrays.asList(".......", ".......", ".......", ".......", ".......", ".......", "......."),
                3,
                1,
                1,
                1);

        Method trackerMethod = MapperEngine.class.getDeclaredMethod(
                "updatePathBehindTracker",
                Room.class,
                RoomMiniMapParser.MiniMapData.class);
        trackerMethod.setAccessible(true);
        trackerMethod.invoke(engine, currentRoom, miniMapData);

        assertEquals(Arrays.asList(
                new Point(1, 1),
                new Point(2, 1),
                new Point(3, 1)), engine.pathBehindCoordinates);

        assertTrue("Waino coordinate change should be visible to plugin print trigger",
                engine.consumeWainoCoordinateChangedSinceLastPacket());
    }
}
