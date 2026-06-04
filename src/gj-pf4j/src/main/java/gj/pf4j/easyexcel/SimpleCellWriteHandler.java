/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.util.StringUtils;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import gj.pf4j.i18n.GJPluginReloadableMessageSource;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Collections;

public class SimpleCellWriteHandler implements CellWriteHandler {

    private static final Logger log = LoggerFactory.getLogger(SimpleCellWriteHandler.class);

    private final ApplicationContext applicationContext;
    private final Class<?> clazz;

    public SimpleCellWriteHandler(ApplicationContext applicationContext, Class<?> clazz) {
        this.applicationContext = applicationContext;
        this.clazz = clazz;
    }

    @Override
    public void beforeCellCreate(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Row row, Head head, Integer columnIndex, Integer relativeRowIndex, Boolean isHead) {
        if (isHead) {
            String fieldName = head.getFieldName();
            String headerKey = IoTExcelUtil.getExcelPropertyValue(clazz, fieldName);
            if (StringUtils.isEmpty(headerKey)) {
                log.warn("Class [{}] field [{}] @ExcelProperty annotation is empty, possible causes: field does not exist or annotation is missing",
                        clazz.getName(),
                        fieldName);
            }
            GJPluginReloadableMessageSource messageSource = applicationContext.getBean(GJPluginReloadableMessageSource.class);
            String result = messageSource.getMessage(headerKey, null, LocaleContextHolder.getLocale());
            if (StringUtils.isEmpty(result)) {
                log.warn("Class [{}] translation key [{}] returned empty value, will use field name [{}] as fallback", clazz.getName(), headerKey, fieldName);
                result = fieldName.toLowerCase();
            }
            head.setHeadNameList(Collections.singletonList(result));
        }
    }
}
