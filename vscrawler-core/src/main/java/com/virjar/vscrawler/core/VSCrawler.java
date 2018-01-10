package com.virjar.vscrawler.core;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.virjar.dungproxy.client.ningclient.concurrent.NamedThreadFactory;
import com.virjar.dungproxy.client.util.CommonUtil;
import com.virjar.vscrawler.core.event.systemevent.*;
import com.virjar.vscrawler.core.net.session.CrawlerSession;
import com.virjar.vscrawler.core.net.session.CrawlerSessionPool;
import com.virjar.vscrawler.core.processor.CrawlResult;
import com.virjar.vscrawler.core.processor.SeedProcessor;
import com.virjar.vscrawler.core.seed.BerkeleyDBSeedManager;
import com.virjar.vscrawler.core.seed.Seed;
import com.virjar.vscrawler.core.serialize.ConsolePipeline;
import com.virjar.vscrawler.core.serialize.Pipeline;
import com.virjar.vscrawler.core.util.VSCrawlerCommonUtil;
import com.virjar.vscrawler.core.util.VSCrawlerConstant;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by virjar on 17/4/16. <br/>
 * 爬虫入口,目前很多逻辑参考了webmagic
 *
 * @author virjar
 * @since 0.0.1
 */
@Slf4j
public class VSCrawler extends Thread implements CrawlerConfigChangeEvent, FirstSeedPushEvent {

    private CrawlerSessionPool crawlerSessionPool;
    private BerkeleyDBSeedManager berkeleyDBSeedManager;
    private SeedProcessor seedProcessor;
    private List<Pipeline> pipeline = Lists.newArrayList();
    private int threadNumber = 10;

    private ThreadPoolExecutor threadPool;
    private Date startTime;

    private AtomicInteger stat = new AtomicInteger(STAT_INIT);

    private final static int STAT_INIT = 0;

    private final static int STAT_RUNNING = 1;

    private final static int STAT_STOPPED = 2;

    private final static int STAT_STARING = 3;

    private ReentrantLock taskDispatchLock = new ReentrantLock();

    private Condition taskDispatchCondition = taskDispatchLock.newCondition();

    /**
     * 慢启动,默认为true,慢启动打开后,爬虫启动的时候线程不会瞬间变到最大,否则这个时候并发应该是最大的,因为这个时候没有线程阻塞, 另外考虑有些 资源分配问题,慢启动避免初始化的时候初始化资源请求qps过高
     */
    private boolean slowStart = false;

    /**
     * 慢启动过程是10分钟默认
     */
    private long slowStartDuration = 5 * 60 * 1000;

    private int slowStartThreadNumber = 0;

    @Getter
    private long lastActiveTime = 0L;

    private List<CrawlerStartCallBack> allStartCallBacks = Lists.newLinkedList();

    @Getter
    private VSCrawlerContext vsCrawlerContext;

    private Object componentInitSingnal = new Object();

    VSCrawler(VSCrawlerContext vsCrawlerContext, CrawlerSessionPool crawlerSessionPool, BerkeleyDBSeedManager berkeleyDBSeedManager,
              SeedProcessor seedProcessor, List<Pipeline> pipeline, int threadNum, boolean slowStart,
              long slowStartDuration) {
        super("VSCrawler-Dispatch");
        setDaemon(false);
        this.vsCrawlerContext = vsCrawlerContext;
        this.crawlerSessionPool = crawlerSessionPool;
        this.berkeleyDBSeedManager = berkeleyDBSeedManager;
        this.seedProcessor = seedProcessor;
        this.pipeline = pipeline;
        this.threadNumber = threadNum;
        this.slowStart = slowStart;
        this.slowStartDuration = slowStartDuration;
    }


    public void stopCrawler() {
        if (stat.compareAndSet(STAT_RUNNING, STAT_STOPPED)) {
            log.info("爬虫停止,发送爬虫停止事件消息:com.virjar.vscrawler.event.systemevent.CrawlerEndEvent");
            System.out.flush();// 刷新系统buffer,避免影响队形
            synchronized (System.out) {
                System.err.println("                      江城子 . 程序员之歌");
                System.err.println("");
                System.err.println("                  十年生死两茫茫，写程序，到天亮。");
                System.err.println("                      千行代码，Bug何处藏。");
                System.err.println("                  纵使上线又怎样，朝令改，夕断肠。");
                System.err.println("");
                System.err.println("                  领导每天新想法，天天改，日日忙。");
                System.err.println("                      相顾无言，惟有泪千行。");
                System.err.println("                  每晚灯火阑珊处，夜难寐，加班狂。");
            }
            vsCrawlerContext.getAutoEventRegistry().findEventDeclaring(CrawlerEndEvent.class).crawlerEnd(vsCrawlerContext);
            VSCrawlerContext.removeContext(vsCrawlerContext);
            //终止爬虫主派发线程,派发线程是宿主线程,需要最后中断,否则容易引起其他非守护线程提前被中断
            if (crawlerMainThread != null && !crawlerMainThread.isInterrupted()) {
                crawlerMainThread.interrupt();
            }
        } else {
            log.info("爬虫已经停止,不需要发生爬虫停止事件消息");
        }
    }

    public VSCrawler pushSeed(Seed seed) {
        this.berkeleyDBSeedManager.addNewSeeds(Lists.newArrayList(seed));
        return this;
    }

    public VSCrawler pushSeed(String seed) {
        berkeleyDBSeedManager.addNewSeeds(Lists.newArrayList(new Seed(seed)));
        return this;
    }


    private AtomicInteger activeTasks = new AtomicInteger(0);

    private Thread crawlerMainThread = null;

    @Override
    public void run() {
        checkRunningStat();
        initComponent();
        log.info("Spider  started!");
        while (!Thread.currentThread().isInterrupted() && stat.get() == STAT_RUNNING) {
            Seed seed = berkeleyDBSeedManager.pool();

            // 种子为空处理
            if (seed == null) {
                if (stat.get() == STAT_STOPPED) {
                    break;
                }
                vsCrawlerContext.getAutoEventRegistry().findEventDeclaring(SeedEmptyEvent.class).onSeedEmpty(vsCrawlerContext);
                if (!waitDispatchThread()) {
                    log.warn("爬虫线程休眠被打断");
                    break;
                }
                continue;
            }
            lastActiveTime = System.currentTimeMillis();

            // 执行抓取任务
            threadPool.execute(new SeedProcessTask(seed));

            // 当任务满的时候,暂时阻塞任务产生线程,直到有空闲线程资源
            if (activeTasks.get() >= threadPool.getMaximumPoolSize()) {
                if (!waitDispatchThread()) {
                    log.warn("爬虫线程休眠被打断");
                    break;
                }
            }

            // 慢启动控制
            if (slowStart && startTime.getTime() + slowStartDuration > System.currentTimeMillis()) {
                slowStartThreadNumber++;
                log.info("慢启动:{}", slowStartThreadNumber);
                if (threadPool.getActiveCount() >= slowStartThreadNumber) {
                    // 如果线程数活跃线程数目大于或者等于慢启动控制数目,则暂定线程
                    CommonUtil.sleep(slowStartDuration / threadNumber);
                }
            }

        }
        if (!threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                // 如果是主动停止爬虫,那么等待10分钟,等待爬虫任务执行结束
                threadPool.awaitTermination(10, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("crawler shop wait failed");
            }
        }
        stopCrawler();// 直接在外部终止爬虫,这里可能调两次
        log.info("爬虫结束");
    }

    private void activeDispatchThread() {
        try {
            taskDispatchLock.lock();
            taskDispatchCondition.signalAll();
        } finally {
            taskDispatchLock.unlock();
        }
    }

    private boolean waitDispatchThread() {
        try {
            taskDispatchLock.lock();
            taskDispatchCondition.await();
        } catch (InterruptedException e) {
            return false;
        } finally {
            taskDispatchLock.unlock();
        }
        return true;
    }

    /**
     * 同步执行抓取任务,适合booking场景,该抓取任务不入库,抓取结果不入pipeline,session创建不等待
     *
     * @param seed 任务种子
     * @return 抓取结果
     */
    public CrawlResult grabSync(Seed seed) {
        //start component
        if (stat.get() == STAT_INIT) {
            initComponentWithOutMainThread();
        }

        if (stat.get() != STAT_RUNNING) {
            throw new IllegalStateException("crawler is not running");
        }

        //set vsCrawlerContext into ThreadLocal ,for support event loop
        VSCrawlerCommonUtil.setVSCrawlerContext(vsCrawlerContext);
        //30秒资源请求超时,防止线程阻塞
        CrawlerSession session = crawlerSessionPool.borrowOne(30000, true);
        if (session == null) {
            //TODO store in crawlResult
            throw new IllegalStateException("can not allocate session resource from session pool");
        }

        CrawlResult crawlResult = new CrawlResult();
        try {
            seed.setStatus(Seed.STATUS_RUNNING);
            VSCrawlerCommonUtil.setCrawlerSession(session);
            seedProcessor.process(seed, session, crawlResult);
            return crawlResult;
        } catch (Exception e) {
            log.error("error when grab seed:{}", JSONObject.toJSONString(seed), e);
            throw e;
        } finally {
            // 归还一个session,session有并发控制,feedback之后session才能被其他任务复用
            VSCrawlerCommonUtil.clearCrawlerSession();
            crawlerSessionPool.recycle(session);
        }
    }

    public CrawlResult grabSync(String seed) {
        return grabSync(new Seed(seed));
    }

    private class SeedProcessTask implements Runnable {
        private Seed seed;

        SeedProcessTask(Seed seed) {
            this.seed = seed;
        }

        @Override
        public void run() {
            try {
                activeTasks.incrementAndGet();
                //为了性能,不打印json
                log.info("handle seed: {}", seed.getData());
                processSeed(seed);
            } catch (Exception e) {
                log.error("process request {} error", JSONObject.toJSONString(seed), e);
            } finally {
                if (activeTasks.decrementAndGet() < threadPool.getMaximumPoolSize()) {
                    activeDispatchThread();
                }
            }
        }


        private void processSeed(Seed seed) {
            //set vsCrawlerContext into ThreadLocal ,for support event loop
            VSCrawlerCommonUtil.setVSCrawlerContext(vsCrawlerContext);
            CrawlerSession session = crawlerSessionPool.borrowOne(-1, false);
            int originRetryCount = seed.getRetry();
            CrawlResult crawlResult = new CrawlResult();
            try {
                seed.setStatus(Seed.STATUS_RUNNING);
                VSCrawlerCommonUtil.setCrawlerSession(session);
                seedProcessor.process(seed, session, crawlResult);
                if (seed.getStatus() == Seed.STATUS_RUNNING) {
                    seed.setStatus(Seed.STATUS_SUCCESS);
                }
            } catch (Exception e) {// 如果发生了异常,并且用户没有主动重试,强制重试
                if (originRetryCount == seed.getRetry() && seed.getStatus() == Seed.STATUS_RUNNING
                        && !seed.isIgnore()) {
                    seed.retry();
                }
                throw e;
            } finally {
                // 归还一个session,session有并发控制,feedback之后session才能被其他任务复用
                VSCrawlerCommonUtil.clearCrawlerSession();
                crawlerSessionPool.recycle(session);
                berkeleyDBSeedManager.finish(seed);
            }
            processResult(seed, crawlResult);

        }

        private void processResult(Seed origin, CrawlResult crawlResult) {
            List<Seed> seeds = crawlResult.allSeed();
            if (seeds != null) {
                berkeleyDBSeedManager.addNewSeeds(seeds);
            }

            List<String> allResult = crawlResult.allResult();
            if (allResult != null) {
                for (Pipeline p : pipeline) {
                    p.saveItem(allResult, origin);
                }
            }
        }
    }

    private void checkRunningStat() {
        if (!stat.compareAndSet(STAT_INIT, STAT_STARING)) {
            throw new IllegalStateException("Spider is already running!");
        }
    }

    @Override
    public void configChange(VSCrawlerContext vsCrawlerContext, Properties newProperties) {
        config(newProperties);
    }

    private void config(Properties properties) {
        // 事件循环是单线程的,所以设计上来说,不会有并发问题
        int newThreadNumber = NumberUtils.toInt(properties.getProperty(String.format(VSCrawlerConstant.VSCRAWLER_THREAD_NUMBER, vsCrawlerContext.getCrawlerName())), -1);
        if (newThreadNumber < 0) {
            return;
        }
        if (newThreadNumber != threadNumber) {
            log.info("爬虫线程数目变更,由:{}  变化为:{}", threadNumber, newThreadNumber);
            threadPool.setCorePoolSize(newThreadNumber);
            threadPool.setMaximumPoolSize(newThreadNumber);
            threadNumber = newThreadNumber;
        }
    }

    private void initComponentWithOutMainThread() {

        // 开启事件循环
        vsCrawlerContext.getEventLoop().loop();
        vsCrawlerContext.getAutoEventRegistry().registerObserver(vsCrawlerContext.getEventLoop());

        // 开启文件监听,并发送初始化配置事件
        VSCrawlerContext.vsCrawlerConfigFileWatcher.watchAndBindEvent();


        // 加载初始化配置
        config(VSCrawlerContext.vsCrawlerConfigFileWatcher.loadedProperties());

        // 让本类监听配置文件变更事件
        vsCrawlerContext.getAutoEventRegistry().registerObserver(this);

        if (pipeline.size() == 0) {
            pipeline.add(new ConsolePipeline());
        }

        startTime = new Date();


        for (CrawlerStartCallBack crawlerStartCallBack : allStartCallBacks) {
            crawlerStartCallBack.onCrawlerStart(this);
        }

        vsCrawlerContext.getAutoEventRegistry().findEventDeclaring(CrawlerStartEvent.class).onCrawlerStart(vsCrawlerContext);

        // 如果爬虫是强制停止的,比如kill -9,那么尝试发送爬虫停止信号,请注意
        // 一般请求请正常停止程序,关机拦截这是挽救方案,并不一定可以完整的实现收尾工作
        Runtime.getRuntime().addShutdownHook(new ResourceCleanHookThread());
        synchronized (System.out) {// 血可流头可断,队形不能乱
            System.err.println("################################################");
            System.err.println("##############     VSCrawler      ##############");
            System.err.println("##############       0.2.3        ##############");
            System.err.println("############## 你有一个有意思的灵魂 ##############");
            System.err.println("################################################");
            System.err.println("##############       virjar       ##############");
            System.err.println("################################################");
        }

        stat.set(STAT_RUNNING);
    }

    private void initComponent() {
        initComponentWithOutMainThread();
        crawlerMainThread = Thread.currentThread();
        // config 会设置 threadPool
        if (threadNumber > 0 && threadPool == null || threadPool.isShutdown()) {
            threadPool = new ThreadPoolExecutor(threadNumber, threadNumber, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("VSCrawlerWorker", false));
        }
        berkeleyDBSeedManager.init();
    }

    private class ResourceCleanHookThread extends Thread {
        ResourceCleanHookThread() {
            super("vsCrawler-resource-clean");
        }

        @Override
        public void run() {
            log.warn("爬虫被外部中断,尝试进行资源关闭等收尾工作");
            VSCrawler.this.stopCrawler();
        }
    }

    @Override
    public void firstSeed(VSCrawlerContext vsCrawlerContext, Seed seed) {
        log.info("新的种子加入,激活爬虫派发线程");
        try {
            taskDispatchLock.lock();
            taskDispatchCondition.signalAll();
        } finally {
            taskDispatchLock.unlock();
        }
    }

    public interface CrawlerStartCallBack {
        void onCrawlerStart(VSCrawler vsCrawler);
    }

    public VSCrawler addCrawlerStartCallBack(CrawlerStartCallBack crawlerStartCallBack) {
        allStartCallBacks.add(crawlerStartCallBack);
        return this;
    }

    public VSCrawler clearTask() {
        berkeleyDBSeedManager.clear();
        return this;
    }

    public int activeWorker() {
        return threadPool.getActiveCount();
    }
}
