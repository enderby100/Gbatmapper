package com.glaurung.batMap.controller;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses a 7x7 minimap block from room long descriptions.
 *
 * Expected row format:
 * 
 * <pre>
 * hppp*ph some text...
 * </pre>
 *
 * where first 7 characters are map glyphs.
 */
public final class RoomMiniMapParser {

    private static final int MAP_SIZE = 7;
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern ESCAPED_ANSI_PATTERN = Pattern
            .compile("\\\\u001B\\[[0-9;]*[A-Za-z]|\\\\x1[Bb]\\[[0-9;]*[A-Za-z]");

    private RoomMiniMapParser() {
    }

    public static MiniMapData parse(String longDesc) {
        if (longDesc == null || longDesc.trim().isEmpty()) {
            return null;
        }

        String normalized = normalizeLineBreaks(longDesc);
        String[] lines = normalized.split("\\n");
        List<String> mapRows = findFirstContiguousMapBlock(lines);
        if (mapRows == null || mapRows.size() != MAP_SIZE) {
            return null;
        }

        Integer playerX = null;
        Integer playerY = null;
        Integer wainoX = null;
        Integer wainoY = null;

        for (int y = 0; y < mapRows.size(); y++) {
            String row = mapRows.get(y);
            for (int x = 0; x < row.length(); x++) {
                char marker = row.charAt(x);
                if (marker == '*' || marker == '@') {
                    playerX = x;
                    playerY = y;
                } else if (marker == 'W') {
                    wainoX = x;
                    wainoY = y;
                }
            }
        }

        if (playerX == null && playerY == null && wainoX != null && wainoY != null) {
            playerX = wainoX;
            playerY = wainoY;
        }

        return new MiniMapData(mapRows, playerX, playerY, wainoX, wainoY);
    }

    private static String normalizeLineBreaks(String longDesc) {
        String normalized = longDesc;
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n");
        normalized = ANSI_ESCAPE_PATTERN.matcher(normalized).replaceAll("");
        normalized = ESCAPED_ANSI_PATTERN.matcher(normalized).replaceAll("");
        normalized = stripControlCharacters(normalized);
        return normalized;
    }

    private static List<String> findFirstContiguousMapBlock(String[] lines) {
        List<String> rows = new ArrayList<String>();

        for (String line : lines) {
            String row = extractMapRow(line);
            if (row != null) {
                rows.add(row);
                continue;
            }

            List<String> selectedBlock = selectMapBlock(rows);
            if (selectedBlock != null) {
                return selectedBlock;
            }
            rows.clear();
        }

        return selectMapBlock(rows);
    }

    private static List<String> selectMapBlock(List<String> rows) {
        if (rows == null || rows.size() < MAP_SIZE) {
            return null;
        }

        for (int start = 0; start <= rows.size() - MAP_SIZE; start++) {
            List<String> block = rows.subList(start, start + MAP_SIZE);
            if (blockContainsPlayerOrWaino(block)) {
                return new ArrayList<String>(block);
            }
        }

        return null;
    }

    private static boolean blockContainsPlayerOrWaino(List<String> block) {
        for (String row : block) {
            if (row == null) {
                continue;
            }
            if (row.indexOf('*') >= 0 || row.indexOf('@') >= 0 || row.indexOf('W') >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String extractMapRow(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.length() < MAP_SIZE) {
            return null;
        }

        for (int start = 0; start <= trimmed.length() - MAP_SIZE; start++) {
            if (start > 0 && !Character.isWhitespace(trimmed.charAt(start - 1))) {
                continue;
            }

            String candidate = trimmed.substring(start, start + MAP_SIZE);
            if (isMapRowFragment(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean isMapRowFragment(String candidate) {
        if (candidate == null || candidate.length() != MAP_SIZE) {
            return false;
        }

        for (int i = 0; i < candidate.length(); i++) {
            if (!isMapGlyph(candidate.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isMapGlyph(char glyph) {
        if (Character.isLetterOrDigit(glyph)) {
            return true;
        }

        switch (glyph) {
            case '*':
            case '@':
            case '#':
            case '.':
            case '+':
            case '-':
            case '=':
            case '/':
            case '\\':
            case '|':
            case '~':
            case '^':
            case ':':
            case '_':
                return true;
            default:
                return false;
        }
    }

    private static String stripControlCharacters(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\n' || current == '\t' || !Character.isISOControl(current)) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static class MiniMapData implements Serializable {

        private static final long serialVersionUID = -4698863992685077039L;

        private final List<String> mapRows;
        private final Integer playerX;
        private final Integer playerY;
        private final Integer wainoX;
        private final Integer wainoY;

        public MiniMapData(List<String> mapRows, Integer playerX, Integer playerY, Integer wainoX, Integer wainoY) {
            this.mapRows = new ArrayList<String>(mapRows);
            this.playerX = playerX;
            this.playerY = playerY;
            this.wainoX = wainoX;
            this.wainoY = wainoY;
        }

        public List<String> getMapRows() {
            return new ArrayList<String>(mapRows);
        }

        public Integer getPlayerX() {
            return playerX;
        }

        public Integer getPlayerY() {
            return playerY;
        }

        public Integer getWainoX() {
            return wainoX;
        }

        public Integer getWainoY() {
            return wainoY;
        }

        public boolean hasPlayerCoordinate() {
            return playerX != null && playerY != null;
        }

        public boolean hasWainoCoordinate() {
            return wainoX != null && wainoY != null;
        }
    }
}