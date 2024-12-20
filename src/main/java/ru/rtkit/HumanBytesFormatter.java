package ru.rtkit;

public class HumanBytesFormatter {

    private final static long KB_FACTOR = 1024;
    private final static long MB_FACTOR = 1024 * KB_FACTOR;
    private final static long GB_FACTOR = 1024 * MB_FACTOR;

    public static long toBytes(String humanBytes) {
        int spaceNdx = humanBytes.indexOf(" ");
        long ret = Long.parseLong(humanBytes.substring(0, spaceNdx));
        return switch (humanBytes.substring(spaceNdx + 1).toLowerCase()) {
            case "gb" -> ret * GB_FACTOR;
            case "mb" -> ret * MB_FACTOR;
            case "kb" -> ret * KB_FACTOR;
            default -> throw new RuntimeException("Unknown type: " + humanBytes);
        };
    }
}
