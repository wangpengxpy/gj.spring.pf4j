/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

class MigrationSqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(MigrationSqlGenerator.class);

    List<String> generate(List<DiffOperation> operations, Dialect dialect) {
        List<String> sqls = new ArrayList<>();
        List<DiffOperation> sorted = new ArrayList<>(operations);
        sorted.sort((a, b) -> {
            int cmp = a.tableName().compareToIgnoreCase(b.tableName());
            if (cmp != 0) return cmp;
            if (a instanceof DiffOperation.AddTable) return -1;
            if (b instanceof DiffOperation.AddTable) return 1;
            return 0;
        });

        for (DiffOperation op : sorted) {
            if (op instanceof DiffOperation.AddTable addTable) {
                DdlStatement stmt = dialect.renderCreateTable(addTable.entity());
                List<String> expanded = stmt.toSqlList();
                log.debug("[{}] DdlStatement breakdown: pre={}, main={}, post={} → {} SQL(s)",
                        addTable.tableName(),
                        stmt.getPreStatements().size(),
                        stmt.getMainStatement() != null ? 1 : 0,
                        stmt.getPostStatements().size(),
                        expanded.size());
                for (int i = 0; i < stmt.getPreStatements().size(); i++) {
                    log.debug("[{}]   pre[{}]: {}", addTable.tableName(), i, stmt.getPreStatements().get(i));
                }
                log.debug("[{}]   main: {}", addTable.tableName(),
                        stmt.getMainStatement() != null ? "(present, see CREATE SQL above)" : "(null)");
                for (int i = 0; i < stmt.getPostStatements().size(); i++) {
                    log.debug("[{}]   post[{}]: {}", addTable.tableName(), i, stmt.getPostStatements().get(i));
                }
                sqls.addAll(expanded);
            } else if (op instanceof DiffOperation.AddColumn addCol) {
                String sql = generateAddColumn(addCol, dialect);
                sqls.add(sql);
            }
        }

        return sqls;
    }

    private String generateAddColumn(DiffOperation.AddColumn op, Dialect dialect) {
        DatabaseColumn col = op.column();
        String keyword = dialect.addColumnKeyword();
        log.debug("[{}] ADD COLUMN: name={} storeType={} nullable={} defaultValue={}, addColumnKeyword='{}'",
                op.tableName(), col.getName(), col.getStoreType(),
                col.isNullable(), col.getDefaultValue(), keyword.isEmpty() ? "(empty)" : keyword);

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(dialect.quoteIdentifier(op.tableName())).append(" ADD ");
        if (!keyword.isEmpty()) {
            sb.append(keyword).append(" ");
        }
        sb.append(dialect.quoteIdentifier(col.getName())).append(" ").append(col.getStoreType());
        String extra = dialect.renderColumn(col);
        if (extra != null && !extra.isEmpty()) {
            sb.append(" ").append(extra);
        }
        String sql = sb.toString();
        log.debug("[{}] ADD COLUMN SQL: {}", op.tableName(), sql);
        return sql;
    }
}
