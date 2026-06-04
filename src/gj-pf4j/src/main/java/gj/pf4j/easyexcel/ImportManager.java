/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.ReadSheet;

import com.alibaba.excel.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImportManager implements IImportManager {

    private static final Logger log = LoggerFactory.getLogger(ImportManager.class);

    private final ApplicationContext applicationContext;

    public ImportManager(ApplicationContext context) {
        this.applicationContext = context;
    }

    @Override
    public List<List<Object>> importFromXlsx(String fileName, InputStream stream, Class<?>... types) {
        if (types == null || types.length == 0) {
            return Collections.emptyList();
        }
        // Prepare independent data containers for each Sheet
        List<List<Object>> allSheetData = new ArrayList<>(types.length);
        List<ReadSheet> readSheets = new ArrayList<>(types.length);
        for (int i = 0; i < types.length; i++) {
            Class<?> clazz = types[i];
            // Each sheet uses an independent list to store data
            List<Object> sheetData = Collections.synchronizedList(new ArrayList<>());
            allSheetData.add(sheetData);
            // Create listener: use custom internationalization listener
            AnalysisEventListener<?> listener = new SimpleReadEventListener<>(applicationContext, clazz, sheetData::add);
            ReadSheet readSheet = EasyExcel.readSheet(i)
                    .head(clazz)
                    .registerReadListener(listener)
                    .build();
            readSheets.add(readSheet);
        }
        try (ExcelReader excelReader = EasyExcel.read(stream).build()) {
            excelReader.read(readSheets);
        }
        if (!StringUtils.isEmpty(fileName)) {
            log.debug("Excel filename[{}] imported successfully", fileName);
        }
        return allSheetData;
    }
}
