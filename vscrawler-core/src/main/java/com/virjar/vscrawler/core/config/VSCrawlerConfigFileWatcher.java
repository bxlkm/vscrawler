package com.virjar.vscrawler.core.config;

import com.virjar.vscrawler.core.VSCrawlerContext;
import com.virjar.vscrawler.core.event.systemevent.CrawlerConfigChangeEvent;
import com.virjar.vscrawler.core.event.systemevent.CrawlerStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by virjar on 17/5/2.<br/>
 * 监控vsCrawler.properties的配置变更,实现策略实时变更的功能
 *
 * @author virjar
 * @since 0.0.1
 */
@Slf4j
public class VSCrawlerConfigFileWatcher implements CrawlerStartEvent {
    private static final String configFileName = "vsCrawler.properties";

    private Properties oldProperties = null;
    private AtomicBoolean hasStartWatch = new AtomicBoolean(false);

    public VSCrawlerConfigFileWatcher() {
        // AutoEventRegistry.getInstance().registerObserver(this);
    }

    public Properties loadedProperties() {
        watchAndBindEvent();
        if (oldProperties == null) {
            throw new IllegalStateException("不能加载配置,加载发生过异常,请排查后重新启动程序");
        }
        return oldProperties;
    }

    public void watchAndBindEvent() {
        if (hasStartWatch.compareAndSet(false, true)) {
            URL resource = VSCrawlerConfigFileWatcher.class.getResource(configFileName);
            String dir;
            if (resource == null) {
                URL classPathRoot = VSCrawlerConfigFileWatcher.class.getResource("/");
                if (classPathRoot != null) {
                    dir = new File(classPathRoot.getPath()).getAbsolutePath();
                } else {
                    dir = System.getProperty("java.class.path ");
                }
            } else {
                dir = new File(resource.getFile()).getParent();
            }
            final String file = new File(dir, configFileName).getAbsolutePath();

            loadFileAndSendEvent(file);

            if (StringUtils.endsWithIgnoreCase(dir, ".jar!") && !new File(dir).isDirectory()) {
                //目录解析到jar包下面,证明用户使用all_in_one jar的方式,此方式没有热发文件,因为jar包不是可写文件
                return;
            }
            if (StringUtils.endsWithIgnoreCase(dir, "classes!")) {
                return;
            }
            DirectoryWatcher.WatcherCallback watcherCallback = new DirectoryWatcher.WatcherCallback() {
                private long lastExecute = System.currentTimeMillis();

                @Override
                public void execute(WatchEvent.Kind<?> kind, String path) {
                    if (System.currentTimeMillis() - lastExecute > 1000) {
                        lastExecute = System.currentTimeMillis();
                        if (!path.equals(file)) {
                            return;
                        }
                        loadFileAndSendEvent(file);
                    }
                }

            };
            DirectoryWatcher fileWatcher = DirectoryWatcher.getDirectoryWatcher(watcherCallback,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            fileWatcher.watchDirectory(dir);
        }
    }

    private void loadFileAndSendEvent(String filePath) {
        Properties tempProperties = new Properties();
        FileInputStream fileInputStream = null;
        InputStream defaultConfigInput = null;
        try {
            defaultConfigInput = VSCrawlerConfigFileWatcher.class.getResourceAsStream("/default_vsCrawler.properties");
            tempProperties.load(defaultConfigInput);// 先加载默认配置

            File configFile = new File(filePath);
            if (configFile.exists()) {
                fileInputStream = new FileInputStream(configFile);
                tempProperties.load(fileInputStream);// 然后使用用户热发配置覆盖
            }

            // 没有报异常才发送通知
            for (VSCrawlerContext vsCrawlerContext : VSCrawlerContext.getAllContext()) {
                vsCrawlerContext.getAutoEventRegistry().findEventDeclaring(CrawlerConfigChangeEvent.class)
                        .configChange(vsCrawlerContext, tempProperties);
            }
            oldProperties = tempProperties;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(defaultConfigInput);
            IOUtils.closeQuietly(fileInputStream);
        }
    }

    @Override
    public void onCrawlerStart(VSCrawlerContext vsCrawlerContext) {
        watchAndBindEvent();
        if (oldProperties == null) {
            throw new IllegalStateException("不能加载配置,加载发生过异常,请排查后重新启动程序");
        }
        vsCrawlerContext.getAutoEventRegistry().findEventDeclaring(CrawlerConfigChangeEvent.class).configChange(vsCrawlerContext,
                oldProperties);
    }
}
