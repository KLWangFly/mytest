package com.hx.framework.mvc.action;

import com.hx.framework.annotation.GPAutowired;
import com.hx.framework.annotation.GPController;
import com.hx.framework.annotation.GPRequestMapping;
import com.hx.framework.mvc.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@GPController
@GPRequestMapping("demo")
public class DemoController {
    @GPAutowired
    private IDemoService demoService;
    @GPRequestMapping("query")
    public void query(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,String name){
        String result=demoService.get(name);
        try {
            httpServletResponse.getWriter().write(result);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
