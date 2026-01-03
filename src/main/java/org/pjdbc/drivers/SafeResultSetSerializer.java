package org.pjdbc.drivers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Safe serializer for cached ResultSet data that avoids Java deserialization vulnerabilities.
 *
 * Only handles known safe types:
 * - null
 * - String
 * - Integer
 * - Long
 * - Double
 * - Float
 * - Boolean
 * - BigDecimal
 * - java.sql.Date
 * - java.sql.Timestamp
 * - byte[]
 *
 * Uses DataInputStream/DataOutputStream with explicit type tags instead of ObjectInputStream
 * to prevent arbitrary class instantiation attacks.
 */
public final class SafeResultSetSerializer {

    // Type tags for serialization
    private static final byte TYPE_NULL = 0;
    private static final byte TYPE_STRING = 1;
    private static final byte TYPE_INTEGER = 2;
    private static final byte TYPE_LONG = 3;
    private static final byte TYPE_DOUBLE = 4;
    private static final byte TYPE_FLOAT = 5;
    private static final byte TYPE_BOOLEAN = 6;
    private static final byte TYPE_BIGDECIMAL = 7;
    private static final byte TYPE_DATE = 8;
    private static final byte TYPE_TIMESTAMP = 9;
    private static final byte TYPE_BYTES = 10;

    // Magic bytes and version for format validation
    private static final int MAGIC = 0x50524353; // "PRCS" - PJDBC ResultSet Cache Safe
    private static final byte VERSION = 1;

    private SafeResultSetSerializer() {
        // Utility class
    }

    /**
     * Cached result set data structure.
     */
    public static final class CachedData {
        private final String[] columnNames;
        private final int[] columnTypes;
        private final List<Object[]> rows;

        public CachedData(String[] columnNames, int[] columnTypes, List<Object[]> rows) {
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.rows = rows;
        }

        public String[] getColumnNames() { return columnNames; }
        public int[] getColumnTypes() { return columnTypes; }
        public List<Object[]> getRows() { return rows; }
        public int getRowCount() { return rows.size(); }
    }

    /**
     * Extract data from a ResultSet into a cacheable structure.
     */
    public static CachedData fromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        String[] columnNames = new String[columnCount];
        int[] columnTypes = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columnNames[i] = meta.getColumnLabel(i + 1);
            columnTypes[i] = meta.getColumnType(i + 1);
        }

        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                Object val = rs.getObject(i + 1);
                row[i] = sanitizeValue(val);
            }
            rows.add(row);
        }

        return new CachedData(columnNames, columnTypes, rows);
    }

    /**
     * Convert a value to one of the allowed safe types.
     * Unsupported types are converted to their String representation.
     */
    private static Object sanitizeValue(Object val) {
        if (val == null) return null;
        if (val instanceof String) return val;
        if (val instanceof Integer) return val;
        if (val instanceof Long) return val;
        if (val instanceof Double) return val;
        if (val instanceof Float) return val;
        if (val instanceof Boolean) return val;
        if (val instanceof BigDecimal) return val;
        if (val instanceof Date) return val;
        if (val instanceof Timestamp) return val;
        if (val instanceof byte[]) return val;
        // Convert unsupported types to String for safety
        return val.toString();
    }

    /**
     * Serialize cached data to bytes using a safe binary format.
     */
    public static byte[] serialize(CachedData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            // Write header
            dos.writeInt(MAGIC);
            dos.writeByte(VERSION);

            // Write column metadata
            int columnCount = data.getColumnNames().length;
            dos.writeInt(columnCount);
            for (int i = 0; i < columnCount; i++) {
                dos.writeUTF(data.getColumnNames()[i]);
                dos.writeInt(data.getColumnTypes()[i]);
            }

            // Write rows
            int rowCount = data.getRows().size();
            dos.writeInt(rowCount);
            for (Object[] row : data.getRows()) {
                for (int i = 0; i < columnCount; i++) {
                    writeValue(dos, row[i]);
                }
            }
        }
        return baos.toByteArray();
    }

    /**
     * Deserialize bytes back to cached data using safe type-tagged reading.
     */
    public static CachedData deserialize(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (DataInputStream dis = new DataInputStream(bais)) {
            // Validate header
            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid cache format: bad magic number");
            }
            byte version = dis.readByte();
            if (version != VERSION) {
                throw new IOException("Unsupported cache format version: " + version);
            }

            // Read column metadata
            int columnCount = dis.readInt();
            if (columnCount < 0 || columnCount > 10000) {
                throw new IOException("Invalid column count: " + columnCount);
            }
            String[] columnNames = new String[columnCount];
            int[] columnTypes = new int[columnCount];
            for (int i = 0; i < columnCount; i++) {
                columnNames[i] = dis.readUTF();
                columnTypes[i] = dis.readInt();
            }

            // Read rows
            int rowCount = dis.readInt();
            if (rowCount < 0 || rowCount > 10_000_000) {
                throw new IOException("Invalid row count: " + rowCount);
            }
            List<Object[]> rows = new ArrayList<>(Math.min(rowCount, 1000));
            for (int r = 0; r < rowCount; r++) {
                Object[] row = new Object[columnCount];
                for (int c = 0; c < columnCount; c++) {
                    row[c] = readValue(dis);
                }
                rows.add(row);
            }

            return new CachedData(columnNames, columnTypes, rows);
        }
    }

    /**
     * Write a single value with its type tag.
     */
    private static void writeValue(DataOutputStream dos, Object val) throws IOException {
        if (val == null) {
            dos.writeByte(TYPE_NULL);
        } else if (val instanceof String s) {
            dos.writeByte(TYPE_STRING);
            dos.writeUTF(s);
        } else if (val instanceof Integer i) {
            dos.writeByte(TYPE_INTEGER);
            dos.writeInt(i);
        } else if (val instanceof Long l) {
            dos.writeByte(TYPE_LONG);
            dos.writeLong(l);
        } else if (val instanceof Double d) {
            dos.writeByte(TYPE_DOUBLE);
            dos.writeDouble(d);
        } else if (val instanceof Float f) {
            dos.writeByte(TYPE_FLOAT);
            dos.writeFloat(f);
        } else if (val instanceof Boolean b) {
            dos.writeByte(TYPE_BOOLEAN);
            dos.writeBoolean(b);
        } else if (val instanceof BigDecimal bd) {
            dos.writeByte(TYPE_BIGDECIMAL);
            dos.writeUTF(bd.toString());
        } else if (val instanceof Date d) {
            dos.writeByte(TYPE_DATE);
            dos.writeLong(d.getTime());
        } else if (val instanceof Timestamp ts) {
            dos.writeByte(TYPE_TIMESTAMP);
            dos.writeLong(ts.getTime());
            dos.writeInt(ts.getNanos());
        } else if (val instanceof byte[] bytes) {
            dos.writeByte(TYPE_BYTES);
            dos.writeInt(bytes.length);
            dos.write(bytes);
        } else {
            // Fallback to String for any unexpected types
            dos.writeByte(TYPE_STRING);
            dos.writeUTF(val.toString());
        }
    }

    /**
     * Read a single value based on its type tag.
     */
    private static Object readValue(DataInputStream dis) throws IOException {
        byte type = dis.readByte();
        return switch (type) {
            case TYPE_NULL -> null;
            case TYPE_STRING -> dis.readUTF();
            case TYPE_INTEGER -> dis.readInt();
            case TYPE_LONG -> dis.readLong();
            case TYPE_DOUBLE -> dis.readDouble();
            case TYPE_FLOAT -> dis.readFloat();
            case TYPE_BOOLEAN -> dis.readBoolean();
            case TYPE_BIGDECIMAL -> new BigDecimal(dis.readUTF());
            case TYPE_DATE -> new Date(dis.readLong());
            case TYPE_TIMESTAMP -> {
                long time = dis.readLong();
                int nanos = dis.readInt();
                Timestamp ts = new Timestamp(time);
                ts.setNanos(nanos);
                yield ts;
            }
            case TYPE_BYTES -> {
                int len = dis.readInt();
                if (len < 0 || len > 100_000_000) {
                    throw new IOException("Invalid byte array length: " + len);
                }
                byte[] bytes = new byte[len];
                dis.readFully(bytes);
                yield bytes;
            }
            default -> throw new IOException("Unknown type tag: " + type);
        };
    }
}
