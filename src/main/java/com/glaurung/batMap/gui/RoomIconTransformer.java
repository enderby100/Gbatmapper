package com.glaurung.batMap.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.commons.collections15.Transformer;

import com.glaurung.batMap.vo.Room;
import com.glaurung.batMap.controller.MapperEngine;

public class RoomIconTransformer implements Transformer<Room, Icon> {

    private final int WIDTH = 90;
    private final int HEIGHT = 90;
    private MapperEngine engine;

    public RoomIconTransformer() {
        this(null);
    }

    public RoomIconTransformer(MapperEngine engine) {
        this.engine = engine;
    }

    @Override
    public Icon transform(Room room) {
        return new ImageIcon(drawRoom(room));
    }

    private HashMap<String, Boolean> getExitsMap(Set<String> exits) {
        HashMap<String, Boolean> exitsmap = new HashMap<>();
        exitsmap.put("n", false);
        exitsmap.put("ne", false);
        exitsmap.put("e", false);
        exitsmap.put("se", false);
        exitsmap.put("s", false);
        exitsmap.put("sw", false);
        exitsmap.put("w", false);
        exitsmap.put("nw", false);
        exitsmap.put("u", false);
        exitsmap.put("d", false);
        exitsmap.put("special", false);

        for (String exit : exits) {
            if (exit.equalsIgnoreCase("n") || exit.equalsIgnoreCase("north")) {
                exitsmap.put("n", true);
            } else if (exit.equalsIgnoreCase("e") || exit.equalsIgnoreCase("east")) {
                exitsmap.put("e", true);
            } else if (exit.equalsIgnoreCase("s") || exit.equalsIgnoreCase("south")) {
                exitsmap.put("s", true);
            } else if (exit.equalsIgnoreCase("w") || exit.equalsIgnoreCase("west")) {
                exitsmap.put("w", true);
            } else if (exit.equalsIgnoreCase("ne") || exit.equalsIgnoreCase("northeast")) {
                exitsmap.put("ne", true);
            } else if (exit.equalsIgnoreCase("nw") || exit.equalsIgnoreCase("northwest")) {
                exitsmap.put("nw", true);
            } else if (exit.equalsIgnoreCase("se") || exit.equalsIgnoreCase("southeast")) {
                exitsmap.put("se", true);
            } else if (exit.equalsIgnoreCase("sw") || exit.equalsIgnoreCase("southwest")) {
                exitsmap.put("sw", true);
            } else if (exit.equalsIgnoreCase("u") || exit.equalsIgnoreCase("up")) {
                exitsmap.put("u", true);
            } else if (exit.equalsIgnoreCase("d") || exit.equalsIgnoreCase("down")) {
                exitsmap.put("d", true);
            } else {
                exitsmap.put("special", true);
            }
        }
        return exitsmap;
    }

    private Color getExitColor(Room room, String exitDir, boolean mazeModeEnabled, Graphics2D g) {
        if (!mazeModeEnabled) {
            return g.getColor();
        }

        // In maze mode, check if this exit has been used
        if (room.isExitUsed(exitDir)) {
            return RoomColors.MAZE_USED;
        } else {
            return RoomColors.MAZE_UNUSED;
        }
    }

    private BufferedImage drawRoom(Room room) {
        BufferedImage newgfx = new BufferedImage(90, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newgfx.createGraphics();
        g.setColor(RoomColors.OUTDOOR);
        if (room.isIndoors()) {
            g.setColor(RoomColors.INDOOR);
        }
        Color exitColor = RoomColors.EXIT;

        if (room.allExitsHaveBeenUSed()) {
            g.setColor(RoomColors.MAZEMODE_FULLYEXPLORED);
            exitColor = RoomColors.LIGHT_EXIT;
        } else if (room.getColor() != null) {
            g.setColor(room.getColor());
            if (room.getColor().equals(RoomColors.BLUE) || room.getColor().equals(RoomColors.BROWN) ||
                    room.getColor().equals(RoomColors.MAZEMODE_FULLYEXPLORED)
                    || room.getColor().equals(RoomColors.PURPLE)) {
                exitColor = RoomColors.LIGHT_EXIT;
            }
        }

        g.fillRect(newgfx.getMinX(), newgfx.getMinY(), newgfx.getWidth(), newgfx.getHeight());

        HashMap<String, Boolean> exitsMap = getExitsMap(room.getExits());
        g.setStroke(new BasicStroke(5));
        g.setColor(exitColor);

        // Check if maze mode is enabled
        boolean mazeModeEnabled = (engine != null && engine.isMazeMode());

        drawNorth(g, exitsMap.get("n"), room, "n", mazeModeEnabled);
        drawNE(g, exitsMap.get("ne"), room, "ne", mazeModeEnabled);
        drawEast(g, exitsMap.get("e"), room, "e", mazeModeEnabled);
        drawSE(g, exitsMap.get("se"), room, "se", mazeModeEnabled);
        drawSouth(g, exitsMap.get("s"), room, "s", mazeModeEnabled);
        drawSW(g, exitsMap.get("sw"), room, "sw", mazeModeEnabled);
        drawWest(g, exitsMap.get("w"), room, "w", mazeModeEnabled);
        drawNW(g, exitsMap.get("nw"), room, "nw", mazeModeEnabled);
        if (exitsMap.get("u")) {
            drawU(g, room, "u", mazeModeEnabled);
        }
        if (exitsMap.get("d")) {
            drawD(g, room, "d", mazeModeEnabled);
        }
        if (exitsMap.get("special")) {
            drawSpecial(g);
        }

        g.dispose();
        if (room.isPicked()) {
            BufferedImage pickedRoom = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
            g = pickedRoom.createGraphics();
            g.setColor(RoomColors.PICKED);
            g.fillRect(0, 0, 96, 96);
            g.drawImage(newgfx, 3, 3, 90, 90, null);
            g.dispose();
            newgfx = pickedRoom;
        }

        if (room.isCurrent()) {
            BufferedImage currentRoom = new BufferedImage(102, 102, BufferedImage.TYPE_INT_RGB);
            g = currentRoom.createGraphics();
            g.setColor(RoomColors.CURRENT);
            g.fillRect(0, 0, 102, 102);
            if (room.isPicked()) {
                g.drawImage(newgfx, 3, 3, 96, 96, null);
            } else {
                g.drawImage(newgfx, 6, 6, 90, 90, null);
            }
            g.dispose();
            newgfx = currentRoom;
        }

        return newgfx;
    }

    private void drawNorth(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(WIDTH / 2, 2);
            exit.addPoint(WIDTH / 2 + 6, 12 + 2);
            exit.addPoint(WIDTH / 2 - 6, 12 + 2);
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(30, 2, 30, 5);

        }

    }

    private void drawSouth(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(WIDTH / 2, HEIGHT - 2);
            exit.addPoint(WIDTH / 2 + 6, HEIGHT - (12 + 2));
            exit.addPoint(WIDTH / 2 - 6, HEIGHT - (12 + 2));
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(30, HEIGHT - 7, 30, 5);
        }

    }

    private void drawEast(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(WIDTH - 2, HEIGHT / 2);
            exit.addPoint(WIDTH - (2 + 12), HEIGHT / 2 - 6);
            exit.addPoint(WIDTH - (2 + 12), HEIGHT / 2 + 6);
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(WIDTH - 7, 30, 5, 30);
        }

    }

    private void drawWest(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(2, HEIGHT / 2);
            exit.addPoint(2 + 12, HEIGHT / 2 - 6);
            exit.addPoint(2 + 12, HEIGHT / 2 + 6);
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(2, 30, 5, 30);
        }

    }

    private void drawNW(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(4, 4);
            exit.addPoint(4 + 12, 4 + 3);
            exit.addPoint(4 + 3, 4 + 12);
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(2, 2, 28, 5);
            g.fillRect(2, 2, 5, 28);
        }

    }

    private void drawNE(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(WIDTH - 4, 4);
            exit.addPoint(WIDTH - (4 + 12), 4 + 3);
            exit.addPoint(WIDTH - (4 + 3), 4 + 12);
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(WIDTH - (2 + 28), 2, 28, 5);
            g.fillRect(WIDTH - (2 + 5), 2, 5, 28);
        }

    }

    private void drawSE(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(WIDTH - 4, HEIGHT - 4);
            exit.addPoint(WIDTH - (4 + 12), HEIGHT - (4 + 3));
            exit.addPoint(WIDTH - (4 + 3), HEIGHT - (4 + 12));
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(WIDTH - (2 + 5), HEIGHT - (2 + 28), 5, 28);
            g.fillRect(WIDTH - (2 + 28), HEIGHT - (2 + 5), 28, 5);
        }

    }

    private void drawSW(Graphics2D g, boolean exists, Room room, String exitDir, boolean mazeModeEnabled) {
        if (exists) {
            Color originalColor = g.getColor();
            g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
            Polygon exit = new Polygon();
            exit.addPoint(4, HEIGHT - 4);
            exit.addPoint(4 + 12, HEIGHT - (4 + 3));
            exit.addPoint(4 + 3, HEIGHT - (4 + 12));
            g.fill(exit);
            g.setColor(originalColor);
        } else {
            g.fillRect(2, HEIGHT - (2 + 5), 28, 5);
            g.fillRect(2, HEIGHT - (2 + 28), 5, 28);
        }

    }

    private void drawSpecial(Graphics2D g) {
        g.setStroke(new BasicStroke(1));
        g.setColor(RoomColors.EXIT);
        g.fillOval(WIDTH / 2 - 9, HEIGHT / 2 - 6, 18, 12);
    }

    private void drawU(Graphics2D g, Room room, String exitDir, boolean mazeModeEnabled) {
        g.setStroke(new BasicStroke(1));
        Color originalColor = g.getColor();
        g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
        Polygon exit = new Polygon();
        Point startpoint = new Point(WIDTH / 2 - 9, HEIGHT / 2 - 6);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(9, -15);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(9, 15);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(-9, -6);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(-9, 6);
        exit.addPoint(startpoint.x, startpoint.y);
        g.fill(exit);
        g.setColor(originalColor);
    }

    private void drawD(Graphics2D g, Room room, String exitDir, boolean mazeModeEnabled) {
        g.setStroke(new BasicStroke(1));
        Color originalColor = g.getColor();
        g.setColor(getExitColor(room, exitDir, mazeModeEnabled, g));
        Polygon exit = new Polygon();
        Point startpoint = new Point(WIDTH / 2 + 9, HEIGHT / 2 + 6);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(-9, 15);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(-9, -15);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(9, 6);
        exit.addPoint(startpoint.x, startpoint.y);
        startpoint.translate(9, -6);
        exit.addPoint(startpoint.x, startpoint.y);
        g.fill(exit);
        g.setColor(originalColor);
    }

}
