package ${packagePrefix}.${pluginName}.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("test")
public class Test {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;
}