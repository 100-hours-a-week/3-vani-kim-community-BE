package com.vani.week4.backend.term;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author vani
 * @since 10/27/25
 */
//HTML반환
@Controller
public class TermController {

    @GetMapping("/terms-of-service")
    public String termsOfServicePage(Model model){
        model.addAttribute("pageTitle", "이용 약관");

        return "pages/terms-of-service";
    }

    @GetMapping("/privacy-policy")
    public String privacyPolicePage(Model model){
        model.addAttribute("pageTitle", "개인정보 규정");

        return "pages/privacy-policy";
    }
}