/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Accessors(chain = true)
public class DdlStatement {

    private static final Logger log = LoggerFactory.getLogger(DdlStatement.class);

    private final List<String> preStatements = new ArrayList<>();
    private String mainStatement;
    private final List<String> postStatements = new ArrayList<>();

    public static DdlStatement of(String sql) {
        return new DdlStatement().setMainStatement(sql);
    }

    public List<String> getPreStatements() {
        return Collections.unmodifiableList(preStatements);
    }

    public DdlStatement addPreStatement(String sql) {
        this.preStatements.add(sql);
        return this;
    }

    public List<String> getPostStatements() {
        return Collections.unmodifiableList(postStatements);
    }

    public DdlStatement addPost(String sql) {
        this.postStatements.add(sql);
        return this;
    }

    /** Expand to full SQL list (pre + main + post) */
    public List<String> toSqlList() {
        List<String> all = new ArrayList<>(preStatements);
        if (mainStatement != null) {
            all.add(mainStatement);
        }
        all.addAll(postStatements);
        log.debug("DdlStatement.toSqlList → {} SQL(s): pre={}, main={}, post={}",
                all.size(), preStatements.size(), mainStatement != null ? 1 : 0, postStatements.size());
        return all;
    }
}
