package other;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import javax.servlet.*;
import javax.servlet.http.*;

import annotation.Get;
import controller.*;

public class Utils {

    public static String initializeControllerPackage(ServletConfig config) throws ServletException {
        String controllerPackage = config.getInitParameter("base_package");
        if (controllerPackage == null) {
            throw new ServletException("Base package is not specified in web.xml");
        }
        return controllerPackage;
    }

    public static void validateUniqueMappingValues(List<Class<?>> controllers) throws ServletException {
        if (controllers == null) {
            throw new ServletException("The controllers list is null");
        }
    
        HashMap<String, String> urlMethodMap = new HashMap<>();
    
        for (Class<?> controller : controllers) {
            if (controller == null) {
                continue;
            }
            
            Method[] methods = controller.getDeclaredMethods();
            if (methods == null) {
                continue;
            }
    
            for (Method method : methods) {
                if (method == null) {
                    continue;
                }
    
                if (method.isAnnotationPresent(Get.class)) {
                    Get getAnnotation = method.getAnnotation(Get.class);
                    String url = getAnnotation.value();
                    
                    if (url == null) {
                        throw new ServletException("URL mapping value is null for method: " + method.getName());
                    }
    
                    if (urlMethodMap.containsKey(url)) {
                        String existingMethod = urlMethodMap.get(url);
                        
                        throw new ServletException(String.format(
                                "Duplicate mapping value '%s' found. URL already exists for method: %s and method: %s. " +
                                        "A URL mapping value must be unique across all controllers.",
                                url, existingMethod, method.getName()));
                    }
    
                    urlMethodMap.put(url, controller.getName() + "." + method.getName());
                }
            }
        }
    }

    public static String getRelativeURI(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestURI = request.getRequestURI();
        return requestURI.substring(contextPath.length());
    }

    public static void displayDebugInfo(PrintWriter out, String relativeURI, HashMap<String, Mapping> methodList) {
        System.out.println("Requested URL: " + relativeURI);
        out.println("<h1>Hello World</h1>");
        out.println("<h2>You are here now:</h2>");
        out.println("<h3>URL: " + relativeURI + "</h3>");
        methodList.forEach((key, mapping) -> out.println("Mapping - Path: " + key + ", Class: " + mapping.getClassName() + ", Method: " + mapping.getMethodName() + "<br>"));
    }

    public static void displayFormData(PrintWriter out, HashMap<String, String> formData) {
        out.println("<h2>Form Data</h2>");
        formData.forEach((key, value) -> out.println("<p>" + key + ": " + value + "</p>"));
    }

    public static void executeMappingMethod(String relativeURI, HashMap<String, Mapping> methodList, PrintWriter out, HttpServletRequest request, HttpServletResponse response, HashMap<String, String> formData) throws ServletException, IOException {
        Mapping mapping = methodList.get(relativeURI);
        if (mapping == null) {
            throw new ServletException("No associated method found for URL: " + relativeURI);
        }
    
        out.println("<p>Found mapping:</p>");
        out.println("<p>Class: " + mapping.getClassName() + "</p>");
        out.println("<p>Method: " + mapping.getMethodName() + "</p>");
        invokeMethod(mapping, out, request, response, formData);
    }

    public static void invokeMethod(Mapping mapping, PrintWriter out, HttpServletRequest request, HttpServletResponse response, HashMap<String, String> formData) throws ServletException, IOException {
        try {
            Object result = executeControllerMethod(mapping, formData);
            processMethodResult(result, out, request, response);
        } catch (Exception e) {
            out.println("<p>Error invoking method: " + e.getMessage() + "</p>");
            e.printStackTrace();
        }
    }

    public static Object executeControllerMethod(Mapping mapping, HashMap<String, String> formData) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> cls = Class.forName(mapping.getClassName());
        Method method = cls.getMethod(mapping.getMethodName(), HashMap.class);
        Object obj = cls.getConstructor().newInstance();
        return method.invoke(obj, formData);
    }

    public static void processMethodResult(Object result, PrintWriter out, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (result instanceof String) {
            out.println("<p>Result: " + result + "</p>");
        } else if (result instanceof ModelView) {
            handleModelView((ModelView) result, request, response);
        } else {
            throw new ServletException("Unsupported return type: " + result.getClass().getName());
        }
        out.println("<p>Method executed successfully.</p>");
    }

    public static void handleModelView(ModelView modelView, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String url = modelView.getUrl();
        if (url == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "La vue spécifiée est introuvable");
            return;
        }
        modelView.getData().forEach(request::setAttribute);
        RequestDispatcher dispatcher = request.getRequestDispatcher(url);
        dispatcher.forward(request, response);
    }

    public static void findMethodsAnnotated(Class<?> clazz, HashMap<String, Mapping> methodList) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Get.class)) {
                Get getAnnotation = method.getAnnotation(Get.class);
                Mapping map = new Mapping(method.getName(), clazz.getName());
                methodList.put(getAnnotation.value(), map);
            }
        }
    }

    public static HashMap<String, String> getFormParameters(HttpServletRequest request) {
        HashMap<String, String> formData = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            String[] strValues = (String[]) values;
            if (strValues.length > 0) {
                formData.put(key.toString(), strValues[0]);
            }
        });
        return formData;
    }
}
