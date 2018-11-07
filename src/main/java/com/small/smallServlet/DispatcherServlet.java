package com.small.smallServlet;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.small.annotation.SmallAutowired;
import com.small.annotation.SmallController;
import com.small.annotation.SmallRequestMapping;
import com.small.annotation.SmallRequestParam;
import com.small.annotation.SmallService;

@SuppressWarnings("serial")
public class DispatcherServlet extends HttpServlet {
	private List<String> classnames = new ArrayList<String>();
	private Map<String, Object> beans = new HashMap<String, Object>();
	private Map<String, Object> request = new HashMap<String, Object>();

	// tomcat启动时，初始化所有注解
	public void init(ServletConfig conf) {
		String rootURL = this.getClass().getClassLoader().getResource("/").getFile();
		rootURL += "com.small".replaceAll("\\.", "/");
		System.out.println("正在搜索所有class文件......");
		searchSmall(rootURL);
		System.out.println("正在初始化SmallController、SmallService文件......");
		searchInstance();
		System.out.println("正在创建带有SmallAutowired注解的对象.......");
		searchAutoWired();
		System.out.println("正在绑定所有SmallRequestMapping......");
		urlMapping();
	}

	private void urlMapping() {
		for (Map.Entry<String, Object> entry : beans.entrySet()) {
			Object value = entry.getValue();
			Class<?> clazz = value.getClass();
			if (clazz.isAnnotationPresent(SmallController.class)) {
				SmallRequestMapping smallRequestMapping = clazz.getAnnotation(SmallRequestMapping.class);
				String classPath = smallRequestMapping == null ? "" : smallRequestMapping.value();// 得到注解值
				Method[] methods = clazz.getMethods();// 得到所有方法
				for (Method method : methods) {
					if (method.isAnnotationPresent(SmallRequestMapping.class)) {
						SmallRequestMapping requestMapping = method.getAnnotation(SmallRequestMapping.class);
						String methodPath = requestMapping == null ? "" : requestMapping.value();// 得到注解值
						request.put(classPath + methodPath, method);
					} else {
						continue;
					}
				}
			}
		}
	}

	// 寻找autowired并赋值
	private void searchAutoWired() {
		for (Map.Entry<String, Object> entry : beans.entrySet()) {
			Object value = entry.getValue();
			Class<?> clazz = value.getClass();
			if (clazz.isAnnotationPresent(SmallController.class) || clazz.isAnnotationPresent(SmallService.class)) {
				Field[] fields = clazz.getDeclaredFields();
				for (Field field : fields) {
					if (field.isAnnotationPresent(SmallAutowired.class)) {
						SmallAutowired annotation = field.getAnnotation(SmallAutowired.class);
						Object s = null;
						if (annotation.value() == "") {
							s = beans.get(field.getName());
						} else {
							s = beans.get(annotation.value());
						}
						field.setAccessible(true);
						try {
							field.set(value, s);
						} catch (IllegalArgumentException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						continue;
					}
				}
			}
		}
	}

	// 从指定路径下获取所有class文件
	private void searchSmall(String basePackage) {
		File file = new File(basePackage);// 创建一个路径为com.small
		// System.out.println("basePackage"+basePackage);
		String[] list = file.list();// 从com.list下面获取所有子文件和目录
		for (String temp : list) {// 循环遍历
			File filePath = new File(basePackage + "/" + temp);// 根据原路径+文件名创建一个file
			if (filePath.isDirectory()) {// 判断file是不是目录
				searchSmall(basePackage + "/" + temp);// 如果是目录，那么继续调用解析该目录下所有文件
			} else {
				classnames.add(basePackage + "/" + temp);// 否则添加该文件到list
			}
		}
	}

	// 从获取的class文件列表中实例化所有对象
	private void searchInstance() {
		for (String classname : classnames) {
			Class<?> obj;
			try {
				String _classname = classname.replace(".class", "");
				_classname = _classname.replace(this.getClass().getClassLoader().getResource("/").getFile(), "");
				_classname = _classname.replaceAll("/", ".");
				obj = Class.forName(_classname);
				if (obj.isAnnotationPresent(SmallController.class)) {// 判断该类是不是controller类
					Object value = obj.newInstance();
					SmallController annotation = obj.getAnnotation(SmallController.class);
					String beanName = annotation.value();// 如果有别名，那就用别名
					beans.put("".equals(beanName) ? _classname : beanName, value);
				} else if (obj.isAnnotationPresent(SmallService.class)) {// 判断该类是不是service类
					Object value = obj.newInstance();
					SmallService annotation = obj.getAnnotation(SmallService.class);
					String beanName = annotation.value();// 如果有别名，那就用别名
					beans.put("".equals(beanName) ? _classname : beanName, value);
				} else {
					continue;
				}
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// 获取请求路径
		String uri = req.getRequestURI();// 得到全部名称
		String context = req.getContextPath();// 得到项目名
		String path = uri.replace(context, "");
		System.out.println("访问请求[" + path + "]");
		Method method = (Method) request.get(path);
		if (method != null) {
			String requestName = method.getDeclaringClass().getName();
			System.out.println("返回请求[" + path + "]的处理方法" + requestName + "." + method.getName());
			Object instance = beans.get(requestName);
			resp.setStatus(HttpServletResponse.SC_OK);
			try {
				method.invoke(instance, hand(req, resp, method));
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.out.println("未找到[" + path + "]的处理方法");
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
	}

	// 处理方法参数
	private Object[] hand(HttpServletRequest req, HttpServletResponse resp, Method method) {
		// 得到当前待执行方法的参数
		Class<?>[] parameterTypes = method.getParameterTypes();
		// 根据参数个数，new一个数组存放到args中
		Object[] args = new Object[parameterTypes.length];
		// 获取所有参数的注解
		Annotation[][] parameterAnnotations = method.getParameterAnnotations();
		// 按顺序处理参数
		for (int i = 0; i < parameterTypes.length; i++) {
			// 判断参数中是否有request，有的话，添加
			if (ServletRequest.class.isAssignableFrom(parameterTypes[i])) {
				args[i] = req;
			} else
			// 判断参数中是否有response，有的话，添加
			if (ServletResponse.class.isAssignableFrom(parameterTypes[i])) {
				resp.setCharacterEncoding("UTF-8");
				resp.setHeader("Content-type", "text/html;charset=UTF-8");
				args[i] = resp;
			} else {
				Annotation[] paramAns = parameterAnnotations[i];
				for (Annotation paramAn : paramAns) {
					if (SmallRequestParam.class.isAssignableFrom(paramAn.getClass())) {
						SmallRequestParam smallRequestParam = (SmallRequestParam) paramAn;
						args[i] = req.getParameter(smallRequestParam.value());
					}
				}
			}
		}
		for (Object temp : args) {
			System.out.println("obj=>>" + temp);
		}
		return args;
	}
}
