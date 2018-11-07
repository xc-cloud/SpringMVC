package com.small.smallServlet;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
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

public class DispatcherServlet extends HttpServlet{
	private List<String> classnames = new ArrayList<String>();
	private Map<String,Object> beans = new HashMap<String,Object>();
	private Map<String,Object> request = new HashMap<String,Object>();
	//tomcat启动时，初始化所有注解
	public void init(ServletConfig conf){
		String smallURL = this.getClass().getClassLoader().getResource("/"+"com.small".replaceAll("\\.", "/")).getFile();
		searchSmall(smallURL);
		searchInstance();
		searchAutoWired();
		urlMapping();
	}
	
	private void urlMapping() {
		for(Map.Entry<String, Object> entry:beans.entrySet()){
			Object value = entry.getValue();
			Class<?> clazz = value.getClass();
			if(clazz.isAnnotationPresent(SmallController.class)){
				SmallRequestMapping smallRequestMapping= clazz.getAnnotation(SmallRequestMapping.class);
				String classPath = smallRequestMapping.value();//得到注解值
				Method[] methods = clazz.getMethods();//得到所有方法
				for(Method method : methods){
					if(method.isAnnotationPresent(SmallRequestMapping.class)){
						SmallRequestMapping requestMapping= clazz.getAnnotation(SmallRequestMapping.class);
						String methodPath = requestMapping.value();//得到注解值
						request.put(classPath+methodPath, method);
					}else{
						continue;
					}
				}
			}
		}
	}

	//寻找autowired并赋值
	private void searchAutoWired() {
		for(Map.Entry<String, Object> entry:beans.entrySet()){
			Object value = entry.getValue();
			Class<?> clazz = value.getClass();
			if(clazz.isAnnotationPresent(SmallController.class) || clazz.isAnnotationPresent(SmallService.class)){
				Field[] fields = clazz.getDeclaredFields();
				for(Field field : fields){
					if(field.isAnnotationPresent(SmallAutowired.class)){
						SmallAutowired annotation = field.getAnnotation(SmallAutowired.class);
						Object s = null;
						if(annotation.value() == ""){
							s = beans.get(field.getName());
						}else{
							s = beans.get(annotation.value());
						}
						field.setAccessible(true);
						try {
							field.set(value,s);
						} catch (IllegalArgumentException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}else{
						continue;
					}
				}
			}
		}
	}
	//从指定路径下获取所有class文件
	private void searchSmall(String basePackage){
		basePackage = basePackage.replaceAll("//", "/");
		File file = new File(basePackage);//创建一个路径为com.small
//		System.out.println("basePackage"+basePackage);
		String[] list = file.list();//从com.list下面获取所有子文件和目录
		for(String temp : list){//循环遍历
			File filePath = new File(basePackage+temp);//根据原路径+文件名创建一个file
			if(filePath.isDirectory()){//判断file是不是目录
				searchSmall(basePackage+"/"+temp);//如果是目录，那么继续调用解析该目录下所有文件
			}else{
				classnames.add(basePackage+"/"+temp);//否则添加该文件到list
			}
		}
	}
	//从获取的class文件列表中实例化所有对象
	private void searchInstance(){
		for(String classname : classnames){
			Class<?> obj;
			try {
				System.out.println("class=>"+classname.replace(".class", ""));
				obj = Class.forName(classname.replace(".class", ""));
				if(obj.isAnnotationPresent(SmallController.class)){//判断该类是不是controller类
					Object value = obj.newInstance();
					SmallRequestMapping annotation = obj.getAnnotation(SmallRequestMapping.class);
					beans.put(annotation == null ? value.getClass().toString() : annotation.value() , value);
				}else if(obj.isAnnotationPresent(SmallService.class)){//判断该类是不是service类
					Object value = obj.newInstance();
					SmallService annotation = obj.getAnnotation(SmallService.class);
					beans.put(annotation == null ? value.getClass().getName() : annotation.value() , value);
				}else{
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
		//获取请求路径   
		String uri = req.getRequestURI();//得到全部名称
		String context = req.getContextPath();//得到项目名
		String path = uri.replace(context, "");
		Method method = (Method) beans.get(path);
		Object instance = request.get("/"+path.split("/")[1]);
		try {
			method.invoke(instance, hand(req,resp,method));
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
	}
	//处理方法参数 
	private Object[] hand(HttpServletRequest req, HttpServletResponse resp,Method method){
		//得到当前待执行方法的参数
		Class<?>[] parameterTypes = method.getParameterTypes();
		//根据参数个数，new一个数组存放到args中
		Object[] args = new Object[parameterTypes.length];
		int args_i = 0;
		int index = 0;
		for(Class<?> paramClazz : parameterTypes){
			//判断该参数是否为Java servlet内置变量ServletRequest，如果是，那么直接返回req对象
			if(ServletRequest.class.isAssignableFrom(paramClazz)){
				args[args_i++] = req;
			}
			//判断该参数是否为Java servlet内置变量ServletResponse，如果是，那么直接返回resp对象
			if(ServletResponse.class.isAssignableFrom(paramClazz)){
				args[args_i++] = resp;
			}	
			//当0-3判断有没有requestparam注解，很明显paramClazz为0和1时，不是
			//获取所有带注解的参数
			Annotation[] paramAns = method.getParameterAnnotations()[index++];
			if(paramAns.length>0){//如果参数个数大于0，说明有参数
				for(Annotation paramAn : paramAns){
					if(SmallRequestParam.class.isAssignableFrom(paramAns.getClass())){
						SmallRequestParam smallRequestParam = (SmallRequestParam)paramAn;
						args[args_i++] = req.getParameter(smallRequestParam.value());
					}
				}
			}
		}
		return args;
	}
	public static void main(String[] args) throws ServletException {
//		DispatcherServlet dispatcherServlet = new DispatcherServlet();
//		dispatcherServlet.init(null);
		try {
//			Class.forName("/D:/JavaWorkspace/.metadata/.plugins/org.eclipse.wst.server.core/tmp0/wtpwebapps/SpringMVC/WEB-INF/classes/com/small/annotation/SmallAutowired");
			Class.forName("D:/JavaWorkspace/.metadata/.plugins/org.eclipse.wst.server.core/tmp0/wtpwebapps/SpringMVC/WEB-INF/classes/com/small/annotation/SmallAutowired.class");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		URL url = DispatcherServlet.class.getClassLoader().getResource("/"+"com.small".replace("\\.", "/"));
//		System.out.println(url.getFile());
	}
}
