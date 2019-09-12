package com.hx.framework.v1.Servlet;

import com.hx.framework.annotation.GPAutowired;
import com.hx.framework.annotation.GPController;
import com.hx.framework.annotation.GPRequestMapping;
import com.hx.framework.annotation.GPService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class GPDispatcherServlet extends HttpServlet {

    private Properties properties=new Properties();
    private List<String> classNames=new ArrayList<String>();
    private Map<String,Object> ioc=new HashMap<String, Object>();
    private Map<String,Method> handlerMapping=new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            try {
                resp.getWriter().write("500");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url=req.getRequestURI();
        String contextPath=req.getContextPath();
        url=url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!handlerMapping.containsKey(url)){
           resp.getWriter().write("404");
           return;
        }
        Map<String,String[]> params=req.getParameterMap();
        Method method=handlerMapping.get(url);
        String beanName=toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(this.ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});
    }

    @Override
    public void init(ServletConfig config) {
        //1.加载配置文件,通过config.getInitParameter，传入key值，取的对应文件名称
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.初始化Ioc容器，并扫描相关的类  根据配置的Key取出要扫描的路径
        doScanner(properties.getProperty("scanPackage"));
        //3.实例化所有的类，通过反射机制
        doInstance();
        //4.依赖注入,赋值
        doAutoWired();
        //5.初始化handlerMapping
        initHandlerMapping();
        //结束
        System.out.println("初始换完成");
    }
    private void doLoadConfig(String contextConfigLocation) {
        //根据文件名称采用类加载器加载文件成文件流
        InputStream inputStream=this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            //采用properties存储该文件
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null!=inputStream){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void doScanner(String scanPackage) {
        /* 此处传入的是一个com.hx.framework包路径，我们需要转换成文件路径  /com/hx/framework
       采用类加载器加载文件路径，this.getClass.getClassLoader().getResource()，包路径转换成文件路径传入
       */
        //加在文件路径成对应文件，遍历文件把所以文件中类的全限定类名存下来，便于后面用反射创建对应文件
        URL url=this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        /*
           由文件路径得到文件
         */
        File classPath=new File(url.getFile());
        for (File file:classPath.listFiles()
             ) {
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else{
                //在classpath下，除了class文件，还有properties、xml、yml等文件
                if(!file.getName().endsWith(".class")){continue;}
                //得到全类名
                String className=(scanPackage+"."+file.getName().replaceAll(".class",""));
                classNames.add(className);
            }
        }
    }
    private void doInstance() {
        if(classNames.isEmpty()){return;}
        for (String className:classNames
             ) {
            Class clazz= null;
            try {

                clazz = Class.forName(className);
                //此处采用反射创建对象实例，这块要根据注解来，类上面标了注解交由spring来管理的类我们才存入springIoc容器中
                if(clazz.isAnnotationPresent(GPController.class)){
                    //默认规则，key值为对应类名首字母小写
                    String beanName=toLowerFirstCase(clazz.getSimpleName());
                    Object instance=clazz.newInstance();
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(GPService.class)){
                    //默认规则，key值为对应类名首字母小写
                    String beanName=toLowerFirstCase(clazz.getSimpleName());
                    //优先自定义命名
                    GPService gpService=(GPService) clazz.getAnnotation(GPService.class);
                    if(""!=gpService.value()){
                        beanName=gpService.value();
                    }
                    Object instance=clazz.newInstance();
                    ioc.put(beanName,instance);
                    //或者就用全类名
                    for (Class i: clazz.getInterfaces()) {
                        if(ioc.containsKey(i.getName())){
                            throw  new Exception("The BeanName"+i.getName()+"is exists!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private String toLowerFirstCase(String simpleName) {
     char[] chars=simpleName.toCharArray();
     chars[0]+=32;
     return String.valueOf(chars);
    }

    private void doAutoWired() {
        if(ioc.isEmpty()){
            return ;
        }
        //遍历Ioc容器
        for (Map.Entry<String,Object> entry:ioc.entrySet()) {
            //取出对应类的定义的属性字段
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields) {
                if(!field.isAnnotationPresent(GPAutowired.class)){
                    continue;
                }
                //属性字段上有@Autowired注解的，进行赋值
                 GPAutowired gpAutowired=field.getAnnotation(GPAutowired.class);
                String beanName=gpAutowired.value().trim();
                if("".equals(beanName)){
                  beanName=field.getType().getName();
                }
                //设置可访问私有成员
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void initHandlerMapping() {
        if(ioc.isEmpty()){
            return ;
        }
        for (Map.Entry<String,Object> entry:ioc.entrySet()) {
            Class clazz=entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(GPController.class)){continue;}
            String baseUrl="";
            if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping gpRequestMapping=(GPRequestMapping) clazz.getAnnotation(GPRequestMapping.class);
                baseUrl=gpRequestMapping.value().trim();
            }
            for (Method method:clazz.getMethods()) {
                if(!method.isAnnotationPresent(GPRequestMapping.class)){continue;}
                GPRequestMapping requestMapping=method.getAnnotation(GPRequestMapping.class);
                String url=("/"+baseUrl+"/"+requestMapping.value().trim()).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("Mapped"+url+","+method);
            }
        }
    }
}
