/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface IExportManager {
    /**
     * Export directory (read-only)
     */
    String getExportDirectory();

    /**
     * Set export directory
     *
     * @param path Absolute path of the directory
     */
    void setExportDirectory(String path);

    /**
     * Export a single sheet to Excel and return the file path
     *
     * @param items    Data collection
     * @param <T>      Data type
     * @return Absolute path of the generated file
     */
    <T> String exportToXlsx(List<T> items) throws IOException;

    /**
     * Export multiple sheets to Excel and return the file path
     *
     * @param items    Data grouped by sheet name
     * @return Absolute path of the generated file
     */
    String exportMultiSheetToXlsx(Map<String, List<?>> items) throws IOException;

    /**
     * Export a single sheet to an output stream
     *
     * @param items     Data collection
     * @param <T>       Data type
     * @return Excel byte stream
     */
    <T> ByteArrayOutputStream exportToStream(List<T> items) throws IOException;

    /**
     * Export multiple sheets to an output stream
     *
     * @param items Data grouped by sheet name
     * @return Excel byte stream
     */
    ByteArrayOutputStream exportMultiSheetToStream(Map<String, List<?>> items) throws IOException;
}
