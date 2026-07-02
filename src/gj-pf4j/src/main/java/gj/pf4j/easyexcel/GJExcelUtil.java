/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.metadata.property.ExcelReadHeadProperty;
import com.alibaba.excel.util.ConverterUtils;
import com.alibaba.excel.util.StringUtils;
import gj.pf4j.i18n.GJPluginReloadableMessageSource;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class GJExcelUtil {

    private static final Logger log = LoggerFactory.getLogger(GJExcelUtil.class);

    /**
     * Rebuild header mapping: translate the internationalization key in annotations to the actual header in the current language, and match with actual Excel columns
     */
    public static void buildUpdateHeadAgain(ApplicationContext applicationContext, AnalysisContext analysisContext,
                                            Map<Integer, ReadCellData<?>> headMap,
                                            Class<?> clazz) {
        ExcelReadHeadProperty excelHeadPropertyData = analysisContext.readSheetHolder().excelReadHeadProperty();
        Map<Integer, Head> nowHeadMapData = excelHeadPropertyData.getHeadMap();
        // If a valid header mapping already exists, EasyExcel has matched successfully, no need to rebuild
        if (MapUtils.isNotEmpty(nowHeadMapData)) {
            return;
        }
        // 1. Regenerate the original headMap using clazz (based on @ExcelProperty key)
        ExcelReadHeadProperty originExcelHeadPropertyData = new ExcelReadHeadProperty(
                analysisContext.currentReadHolder(), clazz, null);
        Map<Integer, Head> originHeadMapData = originExcelHeadPropertyData.getHeadMap();
        // 2. Convert the actual headers read from Excel to a string map (column index -> actual header text)
        Map<Integer, String> dataMap = ConverterUtils.convertToStringMap(headMap, analysisContext);
        // 3. Build new matching relationship
        Map<Integer, Head> tmpHeadMap = new HashMap<>();
        GJPluginReloadableMessageSource messageSource = applicationContext.getBean(GJPluginReloadableMessageSource.class);
        for (Map.Entry<Integer, Head> entry : originHeadMapData.entrySet()) {
            Head headData = entry.getValue();
            // Build internationalization key (consistent with @ExcelProperty annotation value)
            String headKey = getExcelPropertyValue(clazz, headData.getFieldName());
            if (StringUtils.isEmpty(headKey)) {
                log.warn("Class [{}] field [{}] @ExcelProperty annotation is empty, possible causes: field does not exist or annotation is missing",
                        clazz.getName(),
                        headData.getFieldName());
            }
            // Translate to actual header text in the current language
            String translatedHeader = messageSource.getMessage(headKey, null, LocaleContextHolder.getLocale());
            if (StringUtils.isEmpty(translatedHeader)) {
                log.warn("Class [{}] translation key [{}] returned empty value", clazz.getName(), headKey);
            }
            // Find matching entry in actual Excel headers
            for (Map.Entry<Integer, String> actualHeader : dataMap.entrySet()) {
                String actualText = actualHeader.getValue();
                if (actualText == null) {
                    continue;
                }
                actualText = actualText.trim();
                if (StringUtils.isEmpty(actualText)) {
                    continue;
                }
                if (translatedHeader != null && translatedHeader.equals(actualText)) {
                    headData.setColumnIndex(actualHeader.getKey());
                    tmpHeadMap.put(actualHeader.getKey(), headData);
                    break;
                }
            }
        }
        // 4. Update header mapping in context
        excelHeadPropertyData.setHeadMap(tmpHeadMap);
    }

    public static String getExcelPropertyValue(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            if (field.isAnnotationPresent(ExcelProperty.class)) {
                ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
                return annotation.value().length > 0 ? annotation.value()[0] : "";
            }
        } catch (NoSuchFieldException ignored) {
        }
        return "";
    }
}
