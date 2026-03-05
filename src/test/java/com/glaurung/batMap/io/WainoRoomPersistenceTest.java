package com.glaurung.batMap.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.glaurung.batMap.vo.Area;
import com.glaurung.batMap.vo.AreaSaveObject;
import com.glaurung.batMap.vo.Exit;
import com.glaurung.batMap.vo.Room;

import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.StaticLayout;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;

public class WainoRoomPersistenceTest {

    @Test
    public void persistsRoomDataUsedByWainoModeAcrossSaveLoad() throws Exception {
        File baseDir = Files.createTempDirectory("batmapper-waino-persist").toFile();
        try {
            File confDir = new File(baseDir, "conf");
            if (!confDir.exists()) {
                confDir.mkdirs();
            }

            Area area = new Area("wainoPersistenceArea");

            Room sourceRoom = new Room("room-a", area);
            sourceRoom.setMiniMapCoordinate(4, 5);
            sourceRoom.setCardinalExitTarget("n", "room-b");
            sourceRoom.addExit("n");

            Room destinationRoom = new Room("room-b", area);
            destinationRoom.setMiniMapCoordinate(4, 4);

            SparseMultigraph<Room, Exit> graph = new SparseMultigraph<Room, Exit>();
            graph.addVertex(sourceRoom);
            graph.addVertex(destinationRoom);
            graph.addEdge(new Exit("n"), new Pair<Room>(sourceRoom, destinationRoom), EdgeType.DIRECTED);

            Layout<Room, Exit> layout = new StaticLayout<Room, Exit>(graph);
            layout.setSize(new Dimension(500, 500));
            layout.setLocation(sourceRoom, new Point2D.Double(100, 100));
            layout.setLocation(destinationRoom, new Point2D.Double(100, 40));

            AreaDataPersister.save(baseDir.getAbsolutePath(), graph, layout);
            AreaSaveObject loadedSave = AreaDataPersister.loadData(baseDir.getAbsolutePath(), area.getName());

            Room loadedSourceRoom = findRoomById(loadedSave.getGraph(), "room-a");
            assertNotNull("source room should be loaded", loadedSourceRoom);
            assertEquals(Integer.valueOf(4), loadedSourceRoom.getMiniMapX());
            assertEquals(Integer.valueOf(5), loadedSourceRoom.getMiniMapY());
            assertEquals("room-b", loadedSourceRoom.getCardinalExitTarget("n"));
            assertTrue(loadedSourceRoom.getExits().contains("n"));
        } finally {
            FileUtils.deleteQuietly(baseDir);
        }
    }

    private Room findRoomById(SparseMultigraph<Room, Exit> graph, String roomId) {
        for (Room room : graph.getVertices()) {
            if (roomId.equals(room.getId())) {
                return room;
            }
        }
        return null;
    }
}
