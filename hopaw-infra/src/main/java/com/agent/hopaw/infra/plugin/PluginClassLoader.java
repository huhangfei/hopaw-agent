package com.agent.hopaw.infra.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginClassLoader extends ClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

    private final String jarFileName;
    private final Map<String, byte[]> classCache = new HashMap<>();

    public PluginClassLoader(File jarFile) throws IOException {
        super(PluginClassLoader.class.getClassLoader());
        this.jarFileName = jarFile.getName();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("module-info")) {
                    byte[] bytes = readAllBytes(jar.getInputStream(entry));
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    classCache.put(className, bytes);
                }
            }
        }
        logger.info("Preloaded {} classes from {}", classCache.size(), jarFileName);
    }

    public List<String> scanAgentToolClasses() {
        List<String> result = new ArrayList<>();
        for (String className : classCache.keySet()) {
            try {
                Class<?> clazz = loadClass(className);
                if (com.agent.hopaw.infra.tool.AgentTool.class.isAssignableFrom(clazz)
                        && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    result.add(className);
                    logger.info("Found AgentTool plugin class: {} in {}", className, jarFileName);
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                logger.debug("Skip class {}: {}", className, e.getMessage());
            }
        }
        return result;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classCache.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length, null);
        }
        throw new ClassNotFoundException(name);
    }

    public String getJarFileName() {
        return jarFileName;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        int total = 0;
        int capacity = 4096;
        byte[] result = new byte[capacity];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (total + n > capacity) {
                capacity = Math.max(capacity * 2, total + n);
                byte[] newResult = new byte[capacity];
                System.arraycopy(result, 0, newResult, 0, total);
                result = newResult;
            }
            System.arraycopy(buf, 0, result, total, n);
            total += n;
        }
        if (total < capacity) {
            byte[] trimmed = new byte[total];
            System.arraycopy(result, 0, trimmed, 0, total);
            return trimmed;
        }
        return result;
    }
}