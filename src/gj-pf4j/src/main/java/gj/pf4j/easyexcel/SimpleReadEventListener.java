/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.easyexcel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.metadata.holder.ReadRowHolder;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.function.Consumer;

public class SimpleReadEventListener<T> extends AnalysisEventListener<T> {

    private final ApplicationContext applicationContext;

    private final Class<T> clazz;
    private final Consumer<T> dataConsumer;

    public SimpleReadEventListener(ApplicationContext context, Class<T> clazz, Consumer<T> dataConsumer) {
        this.applicationContext = context;
        this.clazz = clazz;
        this.dataConsumer = dataConsumer;
    }

    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        ReadRowHolder readRowHolder = context.readRowHolder();
        int rowIndex = readRowHolder.getRowIndex();
        int currentHeadRowNumber = context.readSheetHolder().getHeadRowNumber();
        if (rowIndex + 1 == currentHeadRowNumber) {
            IoTExcelUtil.buildUpdateHeadAgain(applicationContext, context, headMap, clazz);
        }
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        if (dataConsumer != null) {
            dataConsumer.accept(data);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
    }
}