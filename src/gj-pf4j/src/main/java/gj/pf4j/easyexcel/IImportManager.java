/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import java.io.InputStream;
import java.util.List;

public interface IImportManager {
    /**
     * Import multiple sheets from Excel
     *
     * @param fileName Original file name (for log/error messages only)
     * @param stream   Excel data stream (xlsx)
     * @param types    Entity types for each sheet, corresponding to sheet0, sheet1... in order
     * @return Outer List corresponds to sheets, inner List corresponds to all row records for that sheet (converted to target type)
     */
    List<List<Object>> importFromXlsx(String fileName, InputStream stream, Class<?>... types);
}
