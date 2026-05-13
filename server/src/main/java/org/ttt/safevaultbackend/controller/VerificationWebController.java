package org.ttt.safevaultbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 邮箱验证网页控制器
 * 提供验证页面，直接在网页上完成验证
 */
@Controller
@RequiredArgsConstructor
@Tag(name = "验证页面", description = "邮箱验证网页相关接口")
public class VerificationWebController {

    @GetMapping("/verify/email")
    @Operation(summary = "邮箱验证页面", description = "显示邮箱验证页面，直接在网页上完成验证")
    public ModelAndView verifyEmailPage(
            @RequestParam String token,
            @RequestParam(defaultValue = "zh") String lang) {

        // 构建页面数据
        org.springframework.ui.ModelMap modelMap = new org.springframework.ui.ModelMap();
        modelMap.addAttribute("token", token);
        modelMap.addAttribute("downloadUrl", "https://github.com/yunluoxincheng/SafeVault/releases");
        modelMap.addAttribute("appName", "SafeVault");
        modelMap.addAttribute("appDescription", "安全的密码管理器");
        modelMap.addAttribute("lang", lang);

        return new ModelAndView("verify-email", modelMap);
    }
}
