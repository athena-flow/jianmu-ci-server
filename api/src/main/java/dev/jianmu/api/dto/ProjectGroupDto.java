package dev.jianmu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * @author Daihw
 * @class ProjectGroupDto
 * @description 项目组Dto
 * @create 2021/11/25 3:29 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "项目组Dto")
public class ProjectGroupDto {
    @Schema(required = true)
    @NotBlank(message = "名称不能为空")
    private String name;
    @Size(max = 256, message = "描述不能超过256个字符")
    private String description;
    @NotNull(message = "是否展示不能为空")
    private Boolean isShow;
}
