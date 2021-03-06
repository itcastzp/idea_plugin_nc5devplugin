package com.air.nc5dev.util;

import com.intellij.debugger.impl.descriptors.data.LocalData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 导出NC补丁 工具类      </br>
 * </br>
 * </br>
 * </br>
 *
 * @author air Email: 209308343@qq.com
 * @version NC505, JDK1.5+, V1.0
 * @date 2020/1/16 0016 18:28
 * @project
 */
public class ExportNCPatcherUtil {
    /**
     * 模块补丁输出配置文件名字
     **/
    public static final String MODULE_OUTPATCHER_CONFIG_FILENAME = "patcherconfig.properties";
    /**
     * NC 源码 3大类型
     **/
    public static final String NC_TYPE_PUBLIC = "public";
    /**
     * NC 源码 3大类型
     **/
    public static final String NC_TYPE_PRIVATE = "private";
    /**
     * NC 源码 3大类型
     **/
    public static final String NC_TYPE_CLIENT = "client";
    /**
     * test 代码
     **/
    public static final String NC_TYPE_TEST = "test";

    /**
     * 导出补丁到指定的文件夹   </br>
     * </br>
     * </br>
     * </br>
     *
     * @return void
     * @author air Email: 209308343@qq.com
     * @date 2020/1/16 0016 18:32
     * @Param [outPath, project] 输出文件夹，项目对象
     */
    public static final void export(@NotNull String outPath, @NotNull Project project) {
        //获得所有的 模块
        Module[] modules = ModuleManager.getInstance(project).getModules();
        //模块文件夹根路径 ： 模块对象
        HashMap<String, Module> moduleHomeDir2ModuleMap = new HashMap<>();
        for (Module module : modules) {
            moduleHomeDir2ModuleMap.put(new File(module.getModuleFilePath()).getParent(), module);
        }

        //循环模块，根据编译对象情况，输出补丁
        CompilerModuleExtension compilerModuleExtension;
        Iterator<Map.Entry<String, Module>> moduleIterator = moduleHomeDir2ModuleMap.entrySet().iterator();
        Map.Entry<String, Module> entry;
        //模块 自定义输出配置信息
        Properties modulePatcherConfig;
        //不输出 test
        boolean noOutTestClass = true;
        //是否导出 源文件
        boolean hasJavaFile = true;
        //是否 打包代码到jar
        boolean compressjar = false;
        //是否删除打包代码到jar后的 class文件
        boolean compressEndDeleteClass = false;
        //src 源文件夹 顶级集合
        VirtualFile[] sourceRoots;
        //生产class输出文件夹
        VirtualFile classDir;
        //test class 输出文件夹
        String testClassDirPath;

        String moduleName;
        while (moduleIterator.hasNext()) {
            entry = moduleIterator.next();
            compilerModuleExtension = CompilerModuleExtension.getInstance(entry.getValue());

            //读取自定义配置文件
            modulePatcherConfig = readModuleOutConfigFile(entry.getKey());
            hasJavaFile = !"false".equals(modulePatcherConfig.getProperty("config-exportsourcefile"));
            noOutTestClass = !"false".equals(modulePatcherConfig.getProperty("config-notest"));
            compressjar = "true".equals(modulePatcherConfig.getProperty("config-compressjar"));
            compressEndDeleteClass = "true".equals(modulePatcherConfig.getProperty("config-compressEndDeleteClass"));
            sourceRoots = ModuleRootManager.getInstance(entry.getValue()).getSourceRoots();
            classDir = compilerModuleExtension.getCompilerOutputPath();
            testClassDirPath = null == compilerModuleExtension.getCompilerOutputPathForTests() ? null : compilerModuleExtension.getCompilerOutputPathForTests().getPath();
            moduleName = entry.getValue().getName();
            //循环输出 NC 3大文件夹
            for (VirtualFile sourceRoot : sourceRoots) {
                if (null == classDir) {
                    continue;
                }

                if (sourceRoot.getName().equals(NC_TYPE_PUBLIC)) {
                    export(NC_TYPE_PUBLIC, outPath, moduleName
                            , sourceRoot.getPath(), classDir.getPath(), testClassDirPath
                            , hasJavaFile, noOutTestClass, modulePatcherConfig);
                } else if (sourceRoot.getName().equals(NC_TYPE_PRIVATE)) {
                    export(NC_TYPE_PRIVATE, outPath, moduleName
                            , sourceRoot.getPath(), classDir.getPath(), testClassDirPath
                            , hasJavaFile, noOutTestClass, modulePatcherConfig);
                } else if (sourceRoot.getName().equals(NC_TYPE_CLIENT)) {
                    export(NC_TYPE_CLIENT, outPath, moduleName
                            , sourceRoot.getPath(), classDir.getPath(), testClassDirPath
                            , hasJavaFile, noOutTestClass, modulePatcherConfig);
                } else if (null != testClassDirPath
                        && !noOutTestClass
                        && sourceRoot.getName().equals(NC_TYPE_TEST)) {
                    export(NC_TYPE_TEST, outPath, moduleName
                            , sourceRoot.getPath(), classDir.getPath(), testClassDirPath
                            , hasJavaFile, noOutTestClass, modulePatcherConfig);
                }
                //其他无视掉
            }

            //复制模块配置文件！
            File umpDir = new File(new File(entry.getValue().getModuleFilePath()).getParentFile(), "META-INF");
            if (umpDir.isDirectory()) {
                IoUtil.copyAllFile(umpDir
                        , new File(outPath + File.separatorChar + moduleName + File.separatorChar + "META-INF"));
            }

            File bmfDir = new File(new File(entry.getValue().getModuleFilePath()).getParentFile(), "METADATA");
            //复制模块元数据
            if (bmfDir.isDirectory()) {
                IoUtil.copyAllFile(bmfDir
                        , new File(outPath + File.separatorChar + moduleName + File.separatorChar + "METADATA"));
            }
            //检查是否需要把代码打包成 jar文件
            if (compressjar) {
                File manifest = null;
                if(StringUtil.notEmpty(modulePatcherConfig.getProperty("config-ManifestFilePath"))){
                    manifest = new File(modulePatcherConfig.getProperty("config-ManifestFilePath"));
                    if(!manifest.isFile()){
                        manifest = null;
                    }
                }
                compressJar(new File(outPath + File.separatorChar + moduleName), compressEndDeleteClass, manifest);
            }
        }


    }

    /**
     * 打包模块class到jar文件       </br>
     * </br>
     * </br>
     * </br>
     *
     * @return void
     * @author air Email: 209308343@qq.com
     * @date 2020/2/9 0009 14:50
     * @Param [moduleHomeDir, compressEndDeleteClass,modulePatcherConfig]  模块路径， 是否不保留class文件  true删除class文件,模块配置文件
     */
    private static void compressJar(File moduleHomeDir, boolean compressEndDeleteClass, File manifest) {
        File baseDir;
        //public
        baseDir= moduleHomeDir;
        compressJar(new File(baseDir, "classes")
                , new File(baseDir , "lib")
                , "pub" + moduleHomeDir.getName()
                , manifest );
        //private
        baseDir = new File(moduleHomeDir, File.separatorChar + "META-INF");
        compressJar(new File(baseDir, "classes")
                , new File(baseDir , "lib")
                , "ui" + moduleHomeDir.getName()
                , manifest );
        //client
        baseDir = new File(moduleHomeDir, File.separatorChar + "client");
        compressJar(new File(baseDir,  "classes")
                , new File(baseDir , "lib")
                , moduleHomeDir.getName()
                , manifest );

        //删除打包前文件
        if(compressEndDeleteClass){
            //public
            baseDir= moduleHomeDir;
            IoUtil.cleanUpDirFiles(new File(baseDir, "classes"));
            //private
            baseDir = new File(moduleHomeDir, File.separatorChar + "META-INF");
            IoUtil.cleanUpDirFiles(new File(baseDir, "classes"));
            //client
            baseDir = new File(moduleHomeDir, File.separatorChar + "client");
            IoUtil.cleanUpDirFiles(new File(baseDir, "classes"));
        }
    }

    /**
     *  打包成jar文件   </br>
     * </br>
     * </br>
     * </br>
     *
     * @return java.io.File
     * @author air Email: 209308343@qq.com
     * @date 2020/2/9 0009 14:53
     * @Param [dir, outDir, jarName,manifest] 要打包的文件，要输出到的文件夹，jar文件名（注意，源码会自动加_src后缀），manifest
     */
    private static void compressJar(File dir, File outDir, String jarName, File manifest) {
        try {
            if(dir.listFiles() == null
                || dir.listFiles().length < 1
                    || (dir.listFiles().length < 2 && dir.listFiles()[0].getName().equals("META-INF"))){
                return ;
            }
            outDir.mkdirs();
            //创建MANIFEST.MF

            File meteInfoFile = new File(dir, "META-INF" + File.separatorChar + "MANIFEST.MF");
            meteInfoFile.getParentFile().mkdirs();

            if(null == manifest){
                HashMap<String,String> manifestMap = new HashMap<>();
                manifestMap.put("Manifest-Version", "1.0");
                manifestMap.put("Class-Path", "");
                manifestMap.put("Main-Class", "");
                manifestMap.put("Name", "");
                manifestMap.put("Specification-Title", "");
                manifestMap.put("Specification-Version", "1.0");
                manifestMap.put("Specification-Vendor", "");
                manifestMap.put("Implementation-Title", "");
                manifestMap.put("Implementation-Version", "");
                manifestMap.put("Implementation-Vendor", "");
                manifestMap.put("CreateDate", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(LocalDateTime.now()));
                manifestMap.put("Created-By", "");
                manifestMap.put("Created-ByIde", "IDEA-plugin-nc5devtoolidea by air Email 209308343@qq.com,QQ 209308343 ");
                manifestMap.put("IDEA-plugin-nc5devtoolidea-github", "https://gitee.com/yhlx/idea_plugin_nc5devplugin");
                PrintWriter printWriter = new PrintWriter(new FileOutputStream(meteInfoFile));
                Iterator<Map.Entry<String, String>> infos = manifestMap.entrySet().iterator();
                Map.Entry<String, String> info;
                while (infos.hasNext()){
                    info = infos.next();
                    printWriter.println(info.getKey() + ':' + info.getValue());
                }
                printWriter.flush();
                printWriter.close();
            }else{
                IoUtil.copyFile(manifest, meteInfoFile.getParentFile());
            }

            File jarFile = new File(outDir, jarName + ".jar");
            File jarSrcFile = new File(outDir, jarName + "_src.jar");
            IoUtil.zip(dir, jarFile, CompressType.JAR, new String[]{".java"});
            IoUtil.zip(dir, jarSrcFile, CompressType.JAR, new String[]{".class"});
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
        }
    }

    /**
     * 导出一个包       </br>
     * </br>
     * </br>
     * </br>
     *
     * @return void
     * @author air Email: 209308343@qq.com
     * @date 2020/1/16 0016 19:34
     * @Param [ncType, exportDir, moduleName, sourceRoot, classDir , testClassDir, hasJavaFile, noOutTestClass , modulePatcherConfig]    NC3大文件夹，导出文件夹路径，模块名字, java源码路径，生产代码路径，test代码路径，是否导出源码，是否不输出test代码
     */
    public static void export(@NotNull String ncType, @NotNull String exportDir
            , @NotNull String moduleName, @NotNull String sourceRoot
            , @NotNull String classDir, @Nullable String testClassDir
            , boolean hasJavaFile, boolean noOutTestClass, @NotNull Properties modulePatcherConfig) {
        File sourceBaseDirFile = new File(sourceRoot);

        File classBaseDirFile;
        if (NC_TYPE_TEST.equals(ncType)) {
            classBaseDirFile = new File(testClassDir);
        } else {
            classBaseDirFile = new File(classDir);
        }

        //获取所有的 有源码的包路径文件夹！
        List<File> allSourcePackges = IoUtil.getAllLastPackges(sourceBaseDirFile);

        for (final File sourcePackge : allSourcePackges) {
            String packgePath = sourcePackge.getPath().substring(sourceBaseDirFile.getPath().length());
            //class文件位置
            File classFileDir = new File(classBaseDirFile, packgePath);

            copyClassAndJavaSourceFiles(sourcePackge, sourceBaseDirFile
                    , modulePatcherConfig, exportDir, moduleName
                    , ncType, packgePath, hasJavaFile, classFileDir);

            copyClassPathOtherFile(sourcePackge, exportDir, moduleName, ncType, packgePath, modulePatcherConfig);
        }
    }

    /**
     * 把一个包 当前的文件夹内所有 java源代码和类文件 复制到补丁对应位置（不包含下级包）      </br>
     * </br>
     * </br>
     * </br>
     *
     * @return void
     * @author air Email: 209308343@qq.com
     * @date 2020/2/4 0004 17:31
     * @Param [sourcePackge, sourceBaseDirFile, modulePatcherConfig, exportDir, moduleName, ncType, packgePath, hasJavaFile, classFileDir]
     */
    private static final void copyClassAndJavaSourceFiles(File sourcePackge, File sourceBaseDirFile
            , Properties modulePatcherConfig, String exportDir, String moduleName
            , String ncType, String packgePath, boolean hasJavaFile, final File classFileDir) {
        Stream.of(sourcePackge.listFiles()).forEach(javaFile -> {
            //循环复制所有java文件 ,因为idea编译后 没有分NC这3个文件夹！
            if (!javaFile.isFile()) {
                return;
            }

            if (!javaFile.getName().toLowerCase().endsWith(".java")) {
                return;
            }

            String javaFullName = javaFile.getPath().substring((sourceBaseDirFile.getPath()).length() + 1);
            javaFullName = javaFullName.substring(0, javaFullName.length() - ".java".length());
            if (javaFullName.startsWith(File.separator)) {
                javaFullName = javaFullName.substring(File.separator.length());
            } else if (javaFullName.startsWith("\\")) {
                javaFullName = javaFullName.substring("\\".length());
            } else if (javaFullName.startsWith("/")) {
                javaFullName = javaFullName.substring("/".length());
            }
            String javaFullClassName = StringUtil.replaceAll(javaFullName, File.separator, ".");
            javaFullClassName = StringUtil.replaceAll(javaFullClassName, "\\", ".");
            javaFullClassName = StringUtil.replaceAll(javaFullClassName, "/", ".");
            //检查是否配置了 特殊输出路径
            String outModuleName = modulePatcherConfig.getProperty(javaFullClassName);
            final String baseOutDirPath = exportDir + File.separatorChar
                    + (StringUtil.isEmpty(outModuleName) ? moduleName : outModuleName);

            File outDir = null;
            if (NC_TYPE_PUBLIC.equals(ncType)) {
                outDir = new File(baseOutDirPath, "classes");
            } else if (NC_TYPE_PRIVATE.equals(ncType)) {
                outDir = new File(baseOutDirPath, "META-INF" + File.separatorChar + "classes");
            } else if (NC_TYPE_CLIENT.equals(ncType)) {
                outDir = new File(baseOutDirPath, "client" + File.separatorChar + "classes");
            } else if (NC_TYPE_TEST.equals(ncType)) {
                outDir = new File(baseOutDirPath, "test");
            }

            outDir = new File(outDir, packgePath);

            if (hasJavaFile) {
                //复制源码
                IoUtil.copyFile(javaFile, outDir);
            }

            //复制class 这个麻烦，要正确导出匿名类。 如果是 一个java多个类 反人类写法不考虑让用户手工处理！
            List<File> classFiles = getJavaClassByClassName(javaFullClassName, classFileDir);
            for (File classFile : classFiles) {
                IoUtil.copyFile(classFile, outDir);
            }

        });
    }

    /**
     * 把一个包里的 非代码文件 复制到补丁 对应位置里      </br>
     * 比如 wsdl json 各种类路径配置文件等     </br>
     * </br>
     * </br>
     *
     * @return void
     * @author air Email: 209308343@qq.com
     * @date 2020/2/4 0004 17:28
     * @Param [sourcePackge, exportDir, moduleName, ncType, packgePath]
     */
    private static final void copyClassPathOtherFile(File sourcePackge
            , String exportDir, String moduleName, String ncType
            , String packgePath, Properties modulePatcherConfig) {
        //复制包路径文件夹内所有其他文件，比如 wsdl文件 配置文件等等
        Stream.of(sourcePackge.listFiles()).forEach(file -> {
            if (!file.isFile()) {
                return;
            }
            final String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".class")
                    || fileName.endsWith(".java")) {
                return;
            }

            //检查是否有特殊模块路径设置
            String configKey = packgePath + '.' + file.getName();
            if (configKey.charAt(0) == '/'
                    || configKey.charAt(0) == '\\'
                    || configKey.charAt(0) == File.separatorChar) {
                configKey = configKey.substring(1);
            }
            configKey = StringUtil.replaceAll(configKey, "/", ".");
            configKey = StringUtil.replaceAll(configKey, "\\", ".");
            configKey = StringUtil.replaceAll(configKey, File.separator, ".");

            String outModuleName = modulePatcherConfig.getProperty(configKey);
            String outClasPathOtherFileDirPath = exportDir + File.separatorChar + (StringUtil.isEmpty(outModuleName) ? moduleName : outModuleName);
            File outClasPathOtherFileDir = null;
            if (NC_TYPE_PUBLIC.equals(ncType)) {
                outClasPathOtherFileDir = new File(outClasPathOtherFileDirPath, "classes");
            } else if (NC_TYPE_PRIVATE.equals(ncType)) {
                outClasPathOtherFileDir = new File(outClasPathOtherFileDirPath, "META-INF" + File.separatorChar + "classes");
            } else if (NC_TYPE_CLIENT.equals(ncType)) {
                outClasPathOtherFileDir = new File(outClasPathOtherFileDirPath, "client" + File.separatorChar + "classes");
            } else if (NC_TYPE_TEST.equals(ncType)) {
                outClasPathOtherFileDir = new File(outClasPathOtherFileDirPath, "test");
            }

            File outClasPathOtherFileDirFinal = new File(outClasPathOtherFileDir, packgePath);

            IoUtil.copyFile(file, outClasPathOtherFileDirFinal);
        });
    }

    /**
     * 根据class全名，获得所有他的编译后class     </br>
     * </br>
     * </br>
     * </br>
     *
     * @return java.io.File
     * @author air Email: 209308343@qq.com
     * @date 2020/1/16 0016 20:42
     * @Param [classFullName, javaFileDir] class名 比如 nc.ui.gl.ErrorUI  , java源文件夹根目录
     */
    private static List<File> getJavaClassByClassName(@NotNull String classFullName, @NotNull File classFileDir) {
        final String classPath = StringUtil.replaceAll(classFullName, ".", File.separator);
        return Stream.of(classFileDir.listFiles()).filter(file -> {
            if (file.getPath().lastIndexOf(classPath) < 0) {
                return false;
            }

            String classFullPath = file.getPath().substring(file.getPath().lastIndexOf(classPath), file.getPath().lastIndexOf('.'));
            return classFullPath.equals(classPath) || classFullPath.startsWith(classPath + '$');
        }).collect(Collectors.toList());
    }

    /**
     * 读取模块补丁输出设置的配置文件       </br>
     * </br>
     * </br>
     * </br>
     *
     * @return java.util.Properties
     * @author air Email: 209308343@qq.com
     * @date 2020/1/16 0016 18:47
     * @Param [moduleDir]
     */
    public static Properties readModuleOutConfigFile(@NotNull String moduleDir) {
        Properties configProperties = new Properties();
        File config = new File(moduleDir, MODULE_OUTPATCHER_CONFIG_FILENAME);
        if (!config.isFile()) {
            return configProperties;
        }

        try {
            FileInputStream fileInputStream = new FileInputStream(config);
            configProperties.load(fileInputStream);
            fileInputStream.close();
            return configProperties;
        } catch (IOException e) {
            return configProperties;
        }
    }
}
