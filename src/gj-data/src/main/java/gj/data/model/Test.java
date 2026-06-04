package gj.data.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@TableName(value = "test")
public class Test {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "name", condition = SqlCondition.LIKE, fill = FieldFill.DEFAULT, insertStrategy = FieldStrategy.NOT_NULL)
    private String name;
    @TableField(value = "age", insertStrategy = FieldStrategy.NOT_NULL)
    private Integer age;
    @TableField(value = "createtime", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
