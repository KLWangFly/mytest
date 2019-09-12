package com.hx.framework.mvc.service.impl;

import com.hx.framework.annotation.GPService;
import com.hx.framework.mvc.service.IDemoService;
@GPService
public class DemoServoce implements IDemoService {
    public String get(String name) {
        return "My Name is"+name+"from Service";
    }
}
