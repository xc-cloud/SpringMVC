package com.small.controller;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.small.annotation.SmallAutowired;
import com.small.annotation.SmallController;
import com.small.annotation.SmallRequestMapping;
import com.small.annotation.SmallRequestParam;
import com.small.service.TestService;

@SmallController
public class TestController {
	@SmallAutowired("testService")
	private TestService testService;
	@SmallRequestMapping("/getUser")
	public void getName(@SmallRequestParam("name") String n,HttpServletRequest request,HttpServletResponse response){
		PrintWriter printWriter;
		try {
			printWriter = response.getWriter();
			printWriter.write(testService.select(n));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
