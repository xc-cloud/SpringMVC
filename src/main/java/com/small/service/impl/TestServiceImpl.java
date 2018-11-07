package com.small.service.impl;

import com.small.annotation.SmallService;
import com.small.service.TestService;
@SmallService("testService")
public class TestServiceImpl implements TestService {

	public String select(String name) {
		return "你好："+name;
	}

}
