package com.virjar.vscrawler.samples;

import com.virjar.dungproxy.client.ippool.IpPoolHolder;
import com.virjar.dungproxy.client.ippool.config.DungProxyContext;
import com.virjar.dungproxy.client.util.CommonUtil;
import com.virjar.vscrawler.core.VSCrawler;
import com.virjar.vscrawler.core.VSCrawlerBuilder;
import com.virjar.vscrawler.core.net.proxy.DefaultIPPool;
import com.virjar.vscrawler.core.net.proxy.strategy.ProxyStrategy;

/**
 * Created by virjar on 17/5/18.<br/>
 * 最简单的爬虫
 */
public class SimpleCrawler {
    public static void main(String[] args) {

        // 启动爬虫
        VSCrawler vsCrawler = VSCrawlerBuilder.create()
                .build();
        vsCrawler.clearTask();

        vsCrawler.start();

        // 增加种子
        System.out.println("注入一个种子任务");
        vsCrawler.pushSeed("https://www.hapag-lloyd.cn/zh/online-business/tracing/tracing-by-booking.html?booking=45119286");

       // CommonUtil.sleep(100000);
    }
}
