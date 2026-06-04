/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.util.StringUtils;
import com.alibaba.excel.write.metadata.WriteSheet;
import gj.pf4j.i18n.GJPluginReloadableMessageSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ExportManager implements IExportManager {

    private static final Logger log = LoggerFactory.getLogger(ExportManager.class);

    private String exportDir = Path.of(System.getProperty("user.dir"), "temp").toString();

    private final ApplicationContext applicationContext;

    public ExportManager(ApplicationContext context) {
        this.applicationContext = context;
    }

    @Override
    public String getExportDirectory() {
        return exportDir;
    }

    @Override
    public void setExportDirectory(String path) {
        exportDir = path;
    }

    @Override
    public <T> String exportToXlsx(List<T> items) throws IOException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items is null or empty");
        }
        Class<?> exportClass = items.get(0).getClass();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String fileName = now.format(formatter) + ".xlsx";
        var path = Path.of(exportDir, fileName);
        Path parent = Paths.get(path.toUri()).getParent();
        boolean exists = parent != null && Files.exists(parent);
        if (!exists && parent != null) {
            Files.createDirectories(parent);
        }
        try (ExcelWriter excelWriter = EasyExcel.write(path.toFile(), exportClass)
                .registerWriteHandler(new SimpleCellWriteHandler(applicationContext, exportClass))
                .build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(getSheetName(exportClass)).build();
            excelWriter.write(items, writeSheet);
        }
        return path.toString();
    }

    @Override
    public String exportMultiSheetToXlsx(Map<String, List<?>> items) throws IOException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items is null or empty");
        }
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String fileName = now.format(formatter) + ".xlsx";
        var path = Path.of(exportDir, fileName);
        Path parent = Paths.get(path.toUri()).getParent();
        boolean exists = parent != null && Files.exists(parent);
        if (!exists && parent != null) {
            Files.createDirectories(parent);
        }
        try (ExcelWriter excelWriter = EasyExcel.write(path.toFile())
                .build()) {
            for (var entry : items.entrySet()) {
                var exportClass = entry.getValue().get(0).getClass();
                WriteSheet writeSheet = EasyExcel.writerSheet(getSheetName(exportClass))
                        .registerWriteHandler(new SimpleCellWriteHandler(applicationContext, exportClass))
                        .head(exportClass)
                        .build();
                excelWriter.write(entry.getValue(), writeSheet);
            }
        }
        return path.toString();
    }

    @Override
    public <T> ByteArrayOutputStream exportToStream(List<T> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items is null or empty");
        }
        var exportClass = items.get(0).getClass();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter excelWriter = EasyExcel.write(out).build()) {
            WriteSheet sheet = EasyExcel
                    .writerSheet(0, getSheetName(exportClass))
                    .registerWriteHandler(new SimpleCellWriteHandler(applicationContext, exportClass))
                    .head(exportClass)
                    .build();
            excelWriter.write(items, sheet);
            excelWriter.finish();
        }
        return out;
    }

    @Override
    public ByteArrayOutputStream exportMultiSheetToStream(Map<String, List<?>> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items is null or empty");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter excelWriter = EasyExcel.write(out)
                .build()) {
            for (var entry : items.entrySet()) {
                var exportClass = entry.getValue().get(0).getClass();
                WriteSheet writeSheet = EasyExcel
                        .writerSheet(getSheetName(exportClass))
                        .registerWriteHandler(new SimpleCellWriteHandler(applicationContext, exportClass))
                        .head(exportClass)
                        .build();
                excelWriter.write(entry.getValue(), writeSheet);
            }
        }
        return out;
    }

    private String getSheetName(Class<?> classes) {
        GJPluginReloadableMessageSource messageSource = applicationContext.getBean(GJPluginReloadableMessageSource.class);
        String key = String.format("excel.sheet.name.%s", classes.getSimpleName().toLowerCase());
        String sheetName = messageSource.getMessage(key,null, LocaleContextHolder.getLocale());
        if (StringUtils.isEmpty(sheetName)) {
            log.warn("Class [{}] translation key [{}] returned empty value, will use class name [{}] as fallback", classes.getName(), key, classes.getSimpleName());
            sheetName = classes.getSimpleName().toLowerCase();
        }
        return sheetName;
    }
}
