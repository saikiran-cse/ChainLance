package com.chainlance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares JPA entity annotations with the Flyway migration text, with no database involved.
 * The migration is the source of truth. This checks names, nullability, lengths, decimal sizes,
 * Java types and enum values. It does NOT replace Hibernate's own validation against MySQL.
 */
final class MigrationSchemaChecker {

    record SqlColumn(String name, String type, String args, boolean notNull, boolean generated) {}

    record SqlTable(String name, Map<String, SqlColumn> columns, Map<String, Set<String>> checkValues) {}

    private static final Pattern CREATE = Pattern.compile("^CREATE TABLE (\\w+) \\($");
    private static final Pattern COLUMN = Pattern.compile("^(\\w+)\\s+([A-Z]+)(\\(([^)]*)\\))?(.*)$");
    private static final Pattern IN_LIST = Pattern.compile("(\\w+) IN \\(([^)]*)\\)");

    private MigrationSchemaChecker() {
    }

    static Map<String, SqlTable> parse(String sql) {
        Map<String, SqlTable> tables = new LinkedHashMap<>();
        SqlTable current = null;
        for (String raw : sql.split("\\R")) {
            String line = raw.trim();
            if (current == null) {
                Matcher m = CREATE.matcher(line);
                if (m.matches()) {
                    current = new SqlTable(m.group(1), new LinkedHashMap<>(), new LinkedHashMap<>());
                    tables.put(current.name(), current);
                }
                continue;
            }
            if (line.startsWith(")")) {
                current = null;
                continue;
            }
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            if (line.startsWith("CONSTRAINT ck_")) {
                Matcher in = IN_LIST.matcher(line);
                while (in.find()) {
                    Set<String> values = new LinkedHashSet<>();
                    for (String v : in.group(2).split(",")) {
                        values.add(v.trim().replace("'", ""));
                    }
                    current.checkValues().put(in.group(1), values);
                }
                continue;
            }
            if (line.startsWith("PRIMARY KEY") || line.startsWith("UNIQUE KEY") || line.startsWith("KEY ")
                    || line.startsWith("CONSTRAINT")) {
                continue;
            }
            Matcher c = COLUMN.matcher(line);
            if (c.matches()) {
                String rest = c.group(5);
                boolean generated = rest.contains("GENERATED ALWAYS");
                boolean notNull = rest.contains("NOT NULL");
                current.columns().put(c.group(1),
                        new SqlColumn(c.group(1), c.group(2), c.group(4), notNull, generated));
            }
        }
        return tables;
    }

    static List<String> check(List<Class<?>> entities, String sql) {
        List<String> errors = new ArrayList<>();
        Map<String, SqlTable> tables = parse(sql);
        Set<String> mappedTables = new LinkedHashSet<>();

        for (Class<?> entity : entities) {
            Table table = entity.getAnnotation(Table.class);
            if (table == null) {
                errors.add(entity.getSimpleName() + ": missing @Table");
                continue;
            }
            SqlTable sqlTable = tables.get(table.name());
            if (sqlTable == null) {
                errors.add(entity.getSimpleName() + ": table '" + table.name() + "' is not in the migration");
                continue;
            }
            mappedTables.add(table.name());
            Set<String> mappedColumns = new LinkedHashSet<>();

            for (Field f : entity.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                String where = entity.getSimpleName() + "." + f.getName();
                Column col = f.getAnnotation(Column.class);
                JoinColumn join = f.getAnnotation(JoinColumn.class);
                boolean idColumn = f.isAnnotationPresent(jakarta.persistence.Id.class);
                String colName = join != null ? join.name() : (col != null ? col.name() : null);
                if ((colName == null || colName.isEmpty()) && idColumn) {
                    colName = f.getName(); // JPA default column name; the migration column is "id"
                }
                if (colName == null || colName.isEmpty()) {
                    errors.add(where + ": no column name (@Column or @JoinColumn with name)");
                    continue;
                }
                SqlColumn sc = sqlTable.columns().get(colName);
                if (sc == null) {
                    errors.add(where + ": column '" + colName + "' does not exist in table " + table.name());
                    continue;
                }
                mappedColumns.add(colName);
                if (sc.generated()) {
                    errors.add(where + ": column '" + colName + "' is generated by MySQL and must stay unmapped");
                }
                boolean nullable = join != null ? join.nullable() : (col != null ? col.nullable() : true);
                if (!idColumn && sc.notNull() == nullable) {
                    errors.add(where + ": nullable=" + nullable + " but SQL NOT NULL=" + sc.notNull());
                }
                checkType(errors, where, f, col, join, sc, sqlTable);
            }

            for (SqlColumn sc : sqlTable.columns().values()) {
                if (!sc.generated() && !mappedColumns.contains(sc.name())) {
                    errors.add(entity.getSimpleName() + ": column '" + sc.name() + "' is not mapped");
                }
            }
        }

        Set<String> missing = new LinkedHashSet<>(tables.keySet());
        missing.removeAll(mappedTables);
        if (!missing.isEmpty()) {
            errors.add("Tables without an entity: " + missing);
        }
        return errors;
    }

    private static void checkType(List<String> errors, String where, Field f, Column col, JoinColumn join,
                                  SqlColumn sc, SqlTable table) {
        Class<?> t = f.getType();
        String type = sc.type();
        if (join != null) {
            if (!type.equals("BIGINT")) {
                errors.add(where + ": foreign key column must be BIGINT but is " + type);
            }
            return;
        }
        switch (type) {
            case "BIGINT" -> expect(errors, where, t, Long.class, long.class);
            case "INT" -> expect(errors, where, t, Integer.class, int.class);
            case "TINYINT" -> expect(errors, where, t, Byte.class, byte.class);
            case "BOOLEAN" -> expect(errors, where, t, Boolean.class, boolean.class);
            case "DATETIME" -> expect(errors, where, t, Instant.class);
            case "JSON" -> expect(errors, where, t, Map.class);
            case "DECIMAL" -> {
                expect(errors, where, t, BigDecimal.class);
                String[] ps = sc.args().split(",");
                int precision = Integer.parseInt(ps[0].trim());
                int scale = Integer.parseInt(ps[1].trim());
                if (col.precision() != precision || col.scale() != scale) {
                    errors.add(where + ": DECIMAL(" + precision + "," + scale + ") but mapped precision="
                            + col.precision() + " scale=" + col.scale());
                }
            }
            case "TEXT" -> {
                expect(errors, where, t, String.class);
                if (!"TEXT".equalsIgnoreCase(col.columnDefinition())) {
                    errors.add(where + ": TEXT column needs columnDefinition = \"TEXT\"");
                }
            }
            case "CHAR", "VARCHAR" -> {
                int length = Integer.parseInt(sc.args().trim());
                if (t.isEnum()) {
                    Enumerated en = f.getAnnotation(Enumerated.class);
                    if (en == null || en.value() != EnumType.STRING) {
                        errors.add(where + ": enum must use @Enumerated(EnumType.STRING)");
                    }
                    Set<String> actual = new LinkedHashSet<>();
                    for (Object c : t.getEnumConstants()) {
                        actual.add(((Enum<?>) c).name());
                    }
                    Set<String> expected = table.checkValues().get(sc.name());
                    if (expected == null) {
                        errors.add(where + ": no CHECK (... IN (...)) constraint found for enum column");
                    } else if (!expected.equals(actual)) {
                        errors.add(where + ": enum values " + actual + " differ from CHECK values " + expected);
                    }
                } else {
                    expect(errors, where, t, String.class);
                }
                if (col.length() != length) {
                    errors.add(where + ": " + type + "(" + length + ") but mapped length=" + col.length());
                }
                if (type.equals("CHAR") && !("char(" + length + ")").equalsIgnoreCase(col.columnDefinition())) {
                    errors.add(where + ": CHAR column needs columnDefinition = \"char(" + length + ")\"");
                }
            }
            default -> errors.add(where + ": SQL type " + type + " is not handled by this checker");
        }
    }

    private static void expect(List<String> errors, String where, Class<?> actual, Class<?>... allowed) {
        for (Class<?> a : allowed) {
            if (a.equals(actual)) {
                return;
            }
        }
        errors.add(where + ": Java type " + actual.getSimpleName() + " does not fit the SQL column; expected one of "
                + Arrays.stream(allowed).map(Class::getSimpleName).toList());
    }
}
